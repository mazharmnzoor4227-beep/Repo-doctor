package com.repopilot.app.core

object AppConstants {
    const val APP_NAME = "RepoPilot"
    const val PACKAGE_NAME = "com.repopilot.app"
    const val DEFAULT_WORKSPACE = "/data/data/com.termux/files/home/repopilot"
    const val BRIDGE_PATH = "/data/data/com.termux/files/usr/bin/repopilot-bridge"
}

data class GitHubRepoRef(val owner: String, val repo: String) {
    val slug: String get() = "$owner/$repo"
}

object GitHubUrlParser {
    private val https = Regex("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$")
    private val ssh = Regex("^git@github\\.com:([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?$")
    fun parse(input: String): GitHubRepoRef? {
        val s = input.trim()
        val m = https.matchEntire(s) ?: ssh.matchEntire(s) ?: return null
        val repo = m.groupValues[2].removeSuffix(".git")
        if (repo.isBlank()) return null
        return GitHubRepoRef(m.groupValues[1], repo)
    }
}

object BranchNameGenerator {
    fun forIssue(title: String, id: Long): String {
        val slug = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(42)
            .ifBlank { "issue" }
        return "fix/$slug-$id"
    }
}

data class PatchValidationResult(val ok: Boolean, val reason: String? = null) {
    val isSuccess: Boolean get() = ok
    val isFailure: Boolean get() = !ok
}

object PatchValidator {
    private val diffPath = Regex("^diff --git a/(.+) b/(.+)$")
    fun validate(patch: String): PatchValidationResult {
        if (patch.contains("GIT binary patch") || patch.indexOf('\u0000') >= 0) {
            return PatchValidationResult(false, "Binary patches are blocked")
        }
        val paths = mutableListOf<String>()
        for (line in patch.lineSequence()) {
            val m = diffPath.matchEntire(line) ?: continue
            paths += m.groupValues[1]
            paths += m.groupValues[2]
        }
        if (paths.isEmpty()) return PatchValidationResult(false, "No file diff found")
        for (p in paths) {
            if (p.startsWith("/") || p.startsWith("~") || p.split('/').any { it == ".." })
                return PatchValidationResult(false, "Path escapes repository: $p")
            if (p == ".git" || p.startsWith(".git/"))
                return PatchValidationResult(false, ".git changes are blocked")
        }
        return PatchValidationResult(true)
    }
}

object SecretRedactor {
    private val patterns = listOf(
        Regex("gh[pousr]_[A-Za-z0-9_]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("sk-[A-Za-z0-9_-]{20,}"),
        Regex("AIza[0-9A-Za-z_-]{20,}"),
        Regex("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*['\"]?([^\\s'\"]+)")
    )
    fun redact(text: String): String {
        var out = text
        for (p in patterns) {
            out = if (p.pattern.startsWith("(?i)(api")) {
                p.replace(out) { mr -> "${mr.groupValues[1]}=[REDACTED]" }
            } else p.replace(out, "[REDACTED]")
        }
        return out
    }
}

class BoundedLogBuffer(private val maxLines: Int = 300) {
    private val data = ArrayDeque<String>()
    @Synchronized fun add(line: String) {
        data.add(line)
        while (data.size > maxLines.coerceAtLeast(1)) data.removeFirst()
    }
    @Synchronized fun lines(): List<String> = data.toList()
    @Synchronized fun text(): String = data.joinToString("\n")
    @Synchronized fun clear() = data.clear()
}

enum class Severity { ERROR, WARNING, INFO }

data class DoctorIssue(
    val id: String,
    val severity: Severity,
    val category: String,
    val title: String,
    val explanation: String,
    val evidence: String = "",
    val path: String? = null,
    val line: Int? = null,
    val autoFix: Boolean = false,
    val aiFix: Boolean = true
)

object DoctorParser {
    fun fromText(raw: String): List<DoctorIssue> {
        val text = SecretRedactor.redact(raw)
        val out = linkedMapOf<String, DoctorIssue>()
        fun add(issue: DoctorIssue) { out.putIfAbsent(issue.id, issue) }
        if (Regex("(?i)detached HEAD").containsMatchIn(text)) add(DoctorIssue("git.detached", Severity.ERROR, "Git", "Detached HEAD", "Create or switch to a branch before applying fixes.", text.take(500)))
        if (Regex("(?i)(CONFLICT|unmerged paths|MERGE_HEAD)").containsMatchIn(text)) add(DoctorIssue("git.conflict", Severity.ERROR, "Git", "Git conflict in progress", "Resolve the existing conflict before RepoPilot changes files.", text.take(500), aiFix = false))
        if (Regex("(?i)Could not resolve|Could not find .* for configuration|Failed to resolve").containsMatchIn(text)) add(DoctorIssue("gradle.dependency", Severity.ERROR, "Gradle", "Dependency resolution failed", "A Gradle dependency or repository could not be resolved.", text.take(700)))
        if (Regex("(?i)Manifest merger failed").containsMatchIn(text)) add(DoctorIssue("android.manifest", Severity.ERROR, "Android", "Manifest merge failed", "Android manifests contain incompatible declarations.", text.take(700)))
        if (Regex("(?i)resource .* not found|Android resource linking failed").containsMatchIn(text)) add(DoctorIssue("android.resource", Severity.ERROR, "Android", "Android resource error", "A referenced resource is missing or invalid.", text.take(700)))
        if (Regex("(?i)Unsupported class file major version|requires Java 17|JDK.*incompatible").containsMatchIn(text)) add(DoctorIssue("gradle.jdk", Severity.ERROR, "Environment", "JDK / Gradle mismatch", "The project build tools and installed Java version are incompatible.", text.take(700), autoFix = false))
        if (Regex("(?i)gradlew.*permission denied").containsMatchIn(text)) add(DoctorIssue("gradle.wrapper.exec", Severity.ERROR, "Gradle", "Gradle wrapper is not executable", "The gradlew script needs executable permission.", text.take(500), path = "gradlew", autoFix = true))
        if (out.isEmpty() && text.isNotBlank()) add(DoctorIssue("scan.note", Severity.INFO, "Scan", "No known critical pattern detected", "Review the raw scan output for project-specific warnings.", text.take(700), aiFix = false))
        return out.values.toList()
    }
}

object AiPromptGenerator {
    fun build(issue: DoctorIssue, repo: String, validationCommand: String = "./gradlew assembleDebug"): String = SecretRedactor.redact(
        """You are fixing an Android/Git repository named $repo.
Issue: ${issue.title}
Category: ${issue.category}
Explanation: ${issue.explanation}
Evidence:\n${issue.evidence}
${issue.path?.let { "File: $it${issue.line?.let { n -> ":$n" } ?: ""}" } ?: ""}
Constraints:
- Do not change unrelated files.
- Do not expose secrets or credentials.
- Do not force-push or rewrite Git history.
- Return a unified diff only for the proposed code change.
- Preserve existing behavior outside this issue.
Validation command: $validationCommand
""".trim()
    )
}
