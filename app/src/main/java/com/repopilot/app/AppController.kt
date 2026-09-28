package com.repopilot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.repopilot.app.ai.AiClient
import com.repopilot.app.ai.AiConfig
import com.repopilot.app.ai.UnifiedDiffExtractor
import com.repopilot.app.core.AiPromptGenerator
import com.repopilot.app.core.AiProviderCatalog
import com.repopilot.app.core.AppConstants
import com.repopilot.app.core.BranchNameGenerator
import com.repopilot.app.core.DoctorIssue
import com.repopilot.app.core.DoctorParser
import com.repopilot.app.core.GitHubUrlParser
import com.repopilot.app.core.PatchValidator
import com.repopilot.app.core.SecretRedactor
import com.repopilot.app.data.HistoryRecord
import com.repopilot.app.data.RepoDb
import com.repopilot.app.data.RepoRecord
import com.repopilot.app.security.EncryptedSecretStore
import com.repopilot.app.termux.TermuxDispatcher
import com.repopilot.app.termux.TermuxResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TopTab { HOME, REPOS, AI, SETTINGS }
enum class RepoPage { LIST, DASHBOARD, ISSUES, PROMPT, FIX, BUILD, GIT, HISTORY, LOGS }

class AppController(private val activity: ComponentActivity) {
    private val db = RepoDb(activity)
    private val secrets = EncryptedSecretStore(activity)
    private val prefs = activity.getSharedPreferences("repopilot", Context.MODE_PRIVATE)
    private val time = SimpleDateFormat("MMM d, HH:mm", Locale.US)

    var selectedTab by mutableStateOf(TopTab.HOME)
        private set
    var repoPage by mutableStateOf(RepoPage.LIST)
        private set
    var repos by mutableStateOf(db.repos())
        private set
    var currentRepo by mutableStateOf(repos.firstOrNull())
        private set
    var issues by mutableStateOf<List<DoctorIssue>>(emptyList())
        private set
    var history by mutableStateOf<List<HistoryRecord>>(emptyList())
        private set
    var lastLog by mutableStateOf("")
        private set
    var isBusy by mutableStateOf(false)
        private set
    var snackbarMessage by mutableStateOf<String?>(null)
        private set

    var workspace by mutableStateOf(prefs.getString("workspace", AppConstants.DEFAULT_WORKSPACE) ?: AppConstants.DEFAULT_WORKSPACE)
        private set
    var aiProvider by mutableStateOf(prefs.getString("ai-provider", "gemini") ?: "gemini")
        private set
    var aiModel by mutableStateOf(
        prefs.getString("ai-model", null)
            ?: AiProviderCatalog.byId(prefs.getString("ai-provider", "gemini") ?: "gemini").defaultModel
    )
        private set
    var aiEndpoint by mutableStateOf(prefs.getString("ai-endpoint", "") ?: "")
        private set
    var aiKeySaved by mutableStateOf(secrets.has(AiProviderCatalog.keyId(aiProvider)))
        private set
    var aiTesting by mutableStateOf(false)
        private set
    var aiTestMessage by mutableStateOf<String?>(null)
        private set

    var pendingPatch by mutableStateOf("")
        private set
    var promptPreview by mutableStateOf("")
        private set
    private var pendingIssue: DoctorIssue? = null
    private var currentOperation = ""

    init {
        refreshHistory()
    }

    fun close() = db.close()

    fun selectTab(tab: TopTab) {
        selectedTab = tab
        if (tab == TopTab.REPOS && currentRepo == null) repoPage = RepoPage.LIST
    }

    fun clearSnackbar() {
        snackbarMessage = null
    }

    fun notify(message: String) {
        snackbarMessage = message
    }

    fun updateWorkspace(value: String) {
        workspace = value
    }

    fun saveWorkspace(value: String) {
        workspace = value.trim().ifBlank { AppConstants.DEFAULT_WORKSPACE }
        prefs.edit().putString("workspace", workspace).apply()
        notify("Workspace saved")
    }

    fun refreshRepos() {
        repos = db.repos()
        currentRepo?.let { current -> currentRepo = repos.firstOrNull { it.id == current.id } ?: current }
    }

    fun addOrOpenRepo(url: String, workspaceRoot: String): Boolean {
        val ref = GitHubUrlParser.parse(url)
        if (ref == null) {
            notify("Enter a valid GitHub repository URL")
            return false
        }
        val root = workspaceRoot.trim().ifBlank { AppConstants.DEFAULT_WORKSPACE }
        saveWorkspace(root)
        val path = "$root/${ref.repo}"
        val id = db.upsertRepo(ref.repo, url.trim(), path)
        refreshRepos()
        currentRepo = repos.firstOrNull { it.id == id }
        selectedTab = TopTab.REPOS
        repoPage = RepoPage.DASHBOARD
        runBridge("repo-open", listOf(path, url.trim()), "repo-open")
        return true
    }

    fun openRepo(repo: RepoRecord) {
        currentRepo = repo
        selectedTab = TopTab.REPOS
        repoPage = RepoPage.DASHBOARD
        refreshHistory()
    }

    fun backToRepoList() {
        repoPage = RepoPage.LIST
    }

    fun openRepoPage(page: RepoPage) {
        if (currentRepo == null && page != RepoPage.LIST) {
            notify("Open a repository first")
            repoPage = RepoPage.LIST
            return
        }
        repoPage = page
        if (page == RepoPage.HISTORY) refreshHistory()
    }

    fun scanRepo() = currentRepo?.let { runBridge("scan", listOf(it.path), "scan") } ?: notify("Open a repository first")
    fun buildRepo() = currentRepo?.let { runBridge("build", listOf(it.path), "build") } ?: notify("Open a repository first")
    fun testRepo() = currentRepo?.let { runBridge("test", listOf(it.path), "test") } ?: notify("Open a repository first")
    fun pullRepo() = currentRepo?.let { runBridge("git-pull", listOf(it.path), "pull") } ?: notify("Open a repository first")
    fun gitStatus() = currentRepo?.let { runBridge("git-status", listOf(it.path), "status") } ?: notify("Open a repository first")
    fun refreshDiff() = currentRepo?.let { runBridge("diff", listOf(it.path), "diff") } ?: notify("Open a repository first")
    fun cleanGradleCache() = currentRepo?.let { runBridge("gradle-cache-clean", listOf(it.path), "cache-clean") } ?: notify("Open a repository first")
    fun checkEnvironment() = runBridge("environment-status", emptyList(), "environment")

    fun formattedLastScan(repo: RepoRecord): String = if (repo.lastScan == 0L) "Never" else time.format(Date(repo.lastScan))

    fun showPrompt(issue: DoctorIssue) {
        val repo = currentRepo ?: return
        pendingIssue = issue
        promptPreview = AiPromptGenerator.build(issue, repo.url)
        repoPage = RepoPage.PROMPT
    }

    fun copyPrompt() {
        if (promptPreview.isNotBlank()) {
            copyToClipboard("RepoPilot AI prompt", promptPreview)
            notify("AI prompt copied")
        }
    }

    fun beginFix(issue: DoctorIssue) {
        pendingIssue = issue
        if (issue.autoFix && issue.id == "gradle.wrapper.exec") {
            val repo = currentRepo ?: return
            runBridge("fix-gradlew-permission", listOf(repo.path), "auto-fix-gradlew")
            return
        }
        val prompt = AiPromptGenerator.build(issue, currentRepo?.url ?: "repo")
        promptPreview = prompt
        val key = secrets.get(AiProviderCatalog.keyId(aiProvider))
        if (key.isNullOrBlank()) {
            selectedTab = TopTab.AI
            notify("Add an AI API key first, then return to Fix It")
            return
        }
        requestAiFix(issue, prompt, key)
    }

    fun useConfiguredAiForPrompt() {
        val issue = pendingIssue ?: return
        val key = secrets.get(AiProviderCatalog.keyId(aiProvider))
        if (key.isNullOrBlank()) {
            selectedTab = TopTab.AI
            notify("Add an AI API key first")
            return
        }
        requestAiFix(issue, promptPreview, key)
    }

    private fun requestAiFix(issue: DoctorIssue, prompt: String, key: String) {
        isBusy = true
        repoPage = RepoPage.LOGS
        lastLog = "Asking ${AiProviderCatalog.byId(aiProvider).label} for a safe patch…"
        Thread {
            val result = AiClient.request(AiConfig(aiProvider, aiModel, aiEndpoint), key, prompt)
            activity.runOnUiThread {
                isBusy = false
                result.onSuccess { text ->
                    val diff = UnifiedDiffExtractor.extract(text)
                    if (diff == null) {
                        lastLog = "AI response did not contain a safe unified diff.\n\n${SecretRedactor.redact(text).take(6000)}"
                        repoPage = RepoPage.LOGS
                    } else {
                        pendingIssue = issue
                        pendingPatch = diff
                        repoPage = RepoPage.FIX
                    }
                }.onFailure { error ->
                    lastLog = "AI request failed: ${SecretRedactor.redact(error.message.orEmpty())}"
                    repoPage = RepoPage.LOGS
                }
            }
        }.start()
    }

    fun setPendingPatch(value: String) {
        pendingPatch = value
    }

    fun cancelPatch() {
        pendingPatch = ""
        pendingIssue = null
        repoPage = RepoPage.ISSUES
    }

    fun approvePatch() {
        val repo = currentRepo ?: return
        val validation = PatchValidator.validate(pendingPatch)
        if (validation.isFailure) {
            notify(validation.reason ?: "Patch is not safe")
            return
        }
        val issue = pendingIssue ?: DoctorIssue("manual", com.repopilot.app.core.Severity.INFO, "Manual", "Manual patch", "")
        val branch = BranchNameGenerator.forIssue(issue.title, System.currentTimeMillis() % 100000)
        val encoded = Base64.encodeToString(pendingPatch.toByteArray(), Base64.NO_WRAP)
        prefs.edit().putString("last-patch", encoded).putString("last-patch-repo", repo.path).apply()
        runBridge("apply-patch-check", listOf(repo.path, encoded, branch), "apply-check:$branch")
    }

    fun undoLastPatch() {
        val repo = currentRepo ?: return
        val encoded = prefs.getString("last-patch", null)
        val patchRepo = prefs.getString("last-patch-repo", null)
        if (encoded.isNullOrBlank() || patchRepo != repo.path) {
            notify("No reversible patch stored for this repository")
            return
        }
        runBridge("rollback", listOf(repo.path, encoded), "rollback")
    }

    fun commitChanges(message: String) {
        val repo = currentRepo ?: return
        if (message.isBlank()) {
            notify("Enter a commit message")
            return
        }
        runBridge("commit", listOf(repo.path, message.trim()), "commit")
    }

    fun pushFixBranch() {
        val repo = currentRepo ?: return
        runBridge("push", listOf(repo.path), "push")
    }

    fun setAiProvider(provider: String) {
        val spec = AiProviderCatalog.byId(provider)
        val previousDefault = AiProviderCatalog.byId(aiProvider).defaultModel
        aiProvider = spec.id
        if (aiModel.isBlank() || aiModel == previousDefault) aiModel = spec.defaultModel
        aiKeySaved = secrets.has(AiProviderCatalog.keyId(aiProvider))
        aiTestMessage = null
    }

    fun setAiModel(value: String) {
        aiModel = value
    }

    fun setAiEndpoint(value: String) {
        aiEndpoint = value
    }

    fun saveAiSettings(newApiKey: String) {
        val spec = AiProviderCatalog.byId(aiProvider)
        if (aiModel.isBlank() && !spec.requiresEndpoint) {
            notify("Enter a model ID")
            return
        }
        if (spec.requiresEndpoint && aiEndpoint.isBlank()) {
            notify("Custom provider needs an endpoint")
            return
        }
        prefs.edit()
            .putString("ai-provider", spec.id)
            .putString("ai-model", aiModel.trim())
            .putString("ai-endpoint", aiEndpoint.trim())
            .apply()
        if (newApiKey.isNotBlank()) secrets.put(AiProviderCatalog.keyId(spec.id), newApiKey.trim())
        aiKeySaved = secrets.has(AiProviderCatalog.keyId(spec.id))
        notify(if (aiKeySaved) "AI provider saved securely" else "Provider saved — add an API key to use AI fixes")
    }

    fun removeAiKey() {
        secrets.remove(AiProviderCatalog.keyId(aiProvider))
        aiKeySaved = false
        aiTestMessage = null
        notify("API key removed")
    }

    fun testAiConnection(candidateApiKey: String) {
        val spec = AiProviderCatalog.byId(aiProvider)
        val key = candidateApiKey.trim().ifBlank { secrets.get(AiProviderCatalog.keyId(spec.id)).orEmpty() }
        if (key.isBlank()) {
            notify("Paste an API key first")
            return
        }
        if (aiModel.isBlank() && !spec.requiresEndpoint) {
            notify("Enter a model ID")
            return
        }
        if (spec.requiresEndpoint && aiEndpoint.isBlank()) {
            notify("Custom provider needs an endpoint")
            return
        }
        aiTesting = true
        aiTestMessage = "Testing connection…"
        Thread {
            val result = AiClient.request(
                AiConfig(spec.id, aiModel.trim(), aiEndpoint.trim()),
                key,
                "Reply with exactly: RepoPilot OK"
            )
            activity.runOnUiThread {
                aiTesting = false
                aiTestMessage = result.fold(
                    onSuccess = { "Connected • ${SecretRedactor.redact(it).trim().take(80)}" },
                    onFailure = { "Connection failed • ${SecretRedactor.redact(it.message.orEmpty()).take(120)}" }
                )
            }
        }.start()
    }

    fun isTermuxInstalled(): Boolean = TermuxDispatcher.isInstalled(activity)

    fun termuxSetupCommand(): String =
        "mkdir -p ~/.termux && grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-reload-settings; curl -fsSL https://raw.githubusercontent.com/mazharmnzoor4227-beep/Repo-doctor/main/termux-bridge/repopilot-bridge -o \\$PREFIX/bin/repopilot-bridge && chmod +x \\$PREFIX/bin/repopilot-bridge"

    fun copyTermuxSetup() {
        copyToClipboard("RepoPilot Termux setup", termuxSetupCommand())
        notify("Termux setup command copied")
    }

    fun copyLog() {
        copyToClipboard("RepoPilot log", lastLog)
        notify("Log copied")
    }

    fun copyDebugPrompt() {
        val prompt = SecretRedactor.redact(
            "Help diagnose this repository operation. Do not expose secrets. Suggest minimal safe changes only.\n\n$lastLog"
        )
        copyToClipboard("RepoPilot debug prompt", prompt)
        notify("Debug prompt copied")
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun refreshHistory() {
        history = currentRepo?.let { db.history(it.id) }.orEmpty()
    }

    private fun runBridge(command: String, args: List<String>, operation: String) {
        if (!TermuxDispatcher.isInstalled(activity)) {
            selectedTab = TopTab.SETTINGS
            notify("Termux is not installed or not detected")
            return
        }
        currentOperation = operation
        currentRepo?.let { db.addHistory(it.id, "start", "$command ${args.firstOrNull().orEmpty()}") }
        lastLog = "Starting: $command\n"
        isBusy = true
        repoPage = RepoPage.LOGS
        val result = TermuxDispatcher.execute(activity, command, args)
        if (result.isFailure) {
            isBusy = false
            lastLog += "\nCould not start Termux: ${SecretRedactor.redact(result.exceptionOrNull()?.message.orEmpty())}"
        }
    }

    fun onTermuxResult(result: TermuxResult) {
        isBusy = false
        val combined = SecretRedactor.redact(
            (result.stdout + if (result.stderr.isBlank()) "" else "\nSTDERR:\n${result.stderr}").takeLast(120_000)
        )
        lastLog = combined.ifBlank { result.error ?: "No output (exit ${result.exitCode})" }
        currentRepo?.let {
            db.addHistory(it.id, if (result.exitCode == 0) "success" else "failed", "$currentOperation • exit ${result.exitCode}\n${lastLog.take(1200)}")
        }
        refreshHistory()

        when {
            currentOperation == "scan" -> {
                issues = DoctorParser.fromText(lastLog)
                currentRepo?.let { repo ->
                    db.touchScan(repo.id)
                    refreshRepos()
                }
                repoPage = RepoPage.ISSUES
            }
            currentOperation.startsWith("apply-check:") -> {
                if (result.exitCode != 0) {
                    repoPage = RepoPage.LOGS
                } else {
                    val branch = currentOperation.substringAfter(':')
                    val repo = currentRepo ?: return
                    val encoded = Base64.encodeToString(pendingPatch.toByteArray(), Base64.NO_WRAP)
                    runBridge("apply-patch", listOf(repo.path, encoded, branch), "apply")
                }
            }
            currentOperation == "apply" -> {
                if (result.exitCode == 0) {
                    notify("Fix applied. Build and test it before committing")
                    repoPage = RepoPage.BUILD
                } else repoPage = RepoPage.LOGS
            }
            currentOperation == "auto-fix-gradlew" -> {
                if (result.exitCode == 0) {
                    notify("Gradle wrapper permission fixed")
                    scanRepo()
                } else repoPage = RepoPage.LOGS
            }
            currentOperation == "build" || currentOperation == "test" -> repoPage = RepoPage.BUILD
            currentOperation == "diff" || currentOperation == "commit" || currentOperation == "push" -> repoPage = RepoPage.GIT
            currentOperation == "repo-open" -> repoPage = RepoPage.DASHBOARD
            else -> repoPage = RepoPage.LOGS
        }
    }
}
