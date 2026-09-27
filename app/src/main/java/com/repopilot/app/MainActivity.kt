package com.repopilot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import com.repopilot.app.ai.AiClient
import com.repopilot.app.ai.AiConfig
import com.repopilot.app.ai.UnifiedDiffExtractor
import com.repopilot.app.core.*
import com.repopilot.app.data.RepoDb
import com.repopilot.app.data.RepoRecord
import com.repopilot.app.security.EncryptedSecretStore
import com.repopilot.app.termux.TermuxDispatcher
import com.repopilot.app.termux.TermuxResult
import com.repopilot.app.termux.TermuxResultReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var db: RepoDb
    private lateinit var secrets: EncryptedSecretStore
    private val prefs by lazy { getSharedPreferences("repopilot", MODE_PRIVATE) }
    private var currentRepo: RepoRecord? = null
    private var currentOperation: String = ""
    private var lastLog: String = ""
    private var issues: List<DoctorIssue> = emptyList()
    private var pendingPatch: String = ""
    private var pendingIssue: DoctorIssue? = null
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private val bg = Color.rgb(5,5,5)
    private val panel = Color.rgb(16,16,16)
    private val border = Color.rgb(43,43,43)
    private val white = Color.rgb(245,245,245)
    private val muted = Color.rgb(155,155,155)
    private val green = Color.rgb(69,212,131)
    private val blue = Color.rgb(103,167,255)
    private val purple = Color.rgb(178,140,255)
    private val amber = Color.rgb(243,184,91)
    private val red = Color.rgb(255,107,107)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = RepoDb(this)
        secrets = EncryptedSecretStore(this)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildShell()
        TermuxResultReceiver.listener = { result -> runOnUiThread { onTermuxResult(result) } }
        currentRepo = db.repos().firstOrNull()
        showHome()
    }

    override fun onDestroy() {
        if (TermuxResultReceiver.listener != null) TermuxResultReceiver.listener = null
        db.close()
        super.onDestroy()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(16), dp(10), dp(16), dp(8)) }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(tv("RepoPilot", 23f, white, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        titleRow.addView(tv("SAFE MODE", 11f, green, true).apply { gravity = Gravity.CENTER; background = box(panel, green, 14f) }, LinearLayout.LayoutParams(dp(88), dp(32)))
        root.addView(titleRow)

        val navScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val items = listOf(
            "Home" to ::showHome, "Dashboard" to ::showDashboard, "Issues" to ::showIssues,
            "Fix" to ::showFixPreview, "Build" to ::showBuildResult, "Git" to ::showGit,
            "AI" to ::showAi, "Termux" to ::showTermux, "History" to ::showHistory, "Settings" to ::showSettings
        )
        for ((label, action) in items) nav.addView(button(label, blue, compact = true).apply { setOnClickListener { action() } })
        navScroll.addView(nav); root.addView(navScroll, LinearLayout.LayoutParams(-1, dp(48)))

        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(60)) }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun clear(title: String, subtitle: String? = null) {
        content.removeAllViews()
        content.addView(tv(title, 26f, white, true))
        if (!subtitle.isNullOrBlank()) content.addView(tv(subtitle, 13f, muted).apply { setPadding(0, dp(4), 0, dp(14)) })
    }

    private fun showHome() {
        clear("Repositories", "Open a GitHub repository. Cloning and heavy work run in Termux.")
        val url = input("https://github.com/owner/repo")
        content.addView(label("GITHUB REPOSITORY URL")); content.addView(url)
        val workspace = input(prefs.getString("workspace", AppConstants.DEFAULT_WORKSPACE) ?: AppConstants.DEFAULT_WORKSPACE)
        content.addView(label("TERMUX WORKSPACE ROOT")); content.addView(workspace)
        content.addView(button("Add / Open Repository", green).apply {
            setOnClickListener {
                val ref = GitHubUrlParser.parse(url.text.toString())
                if (ref == null) { toast("Enter a valid GitHub repository URL"); return@setOnClickListener }
                val root = workspace.text.toString().trim().ifBlank { AppConstants.DEFAULT_WORKSPACE }
                prefs.edit().putString("workspace", root).apply()
                val path = "$root/${ref.repo}"
                AlertDialog.Builder(this@MainActivity).setTitle("Open ${ref.slug}?")
                    .setMessage("Local Termux path:\n$path\n\nRepoPilot will ask Termux to open it if present, otherwise clone it.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ ->
                        val id = db.upsertRepo(ref.repo, url.text.toString().trim(), path)
                        currentRepo = db.repos().first { it.id == id }
                        runBridge("repo-open", listOf(path, url.text.toString().trim()), "repo-open")
                    }.show()
            }
        })
        section("RECENT REPOSITORIES")
        val repos = db.repos()
        if (repos.isEmpty()) content.addView(tv("No repositories yet.", 14f, muted))
        repos.forEach { r ->
            content.addView(card().apply {
                addView(tv(r.name, 17f, white, true)); addView(tv(r.url, 12f, muted)); addView(tv(r.path, 12f, blue).apply { typeface = Typeface.MONOSPACE })
                addView(button("Open Dashboard", blue, compact = true).apply { setOnClickListener { currentRepo = r; showDashboard() } })
            })
        }
    }

    private fun showDashboard() {
        val r = currentRepo ?: return showHome()
        clear(r.name, "Repository Dashboard • ${r.path}")
        content.addView(statusCard("Branch", r.branch, blue))
        content.addView(statusCard("Last scan", if (r.lastScan == 0L) "Never" else time.format(Date(r.lastScan)), green))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        grid.addView(button("Scan Repo", green).apply { setOnClickListener { runBridge("scan", listOf(r.path), "scan") } })
        grid.addView(button("Build APK", blue).apply { setOnClickListener { runBridge("build", listOf(r.path), "build") } })
        grid.addView(button("Pull Latest", white).apply { setOnClickListener { runBridge("git-pull", listOf(r.path), "pull") } })
        grid.addView(button("Git Status", white).apply { setOnClickListener { runBridge("git-status", listOf(r.path), "status") } })
        grid.addView(button("Issues (${issues.size})", amber).apply { setOnClickListener { showIssues() } })
        grid.addView(button("Terminal Logs", purple).apply { setOnClickListener { showOperation("Last Operation") } })
        content.addView(grid)
        content.addView(card().apply {
            addView(tv("Safety gates", 16f, white, true))
            addView(tv("Fix preview → Approve Fix → Build/Test → Commit → separate Push approval. RepoPilot never force-pushes or auto-pushes main.", 13f, muted))
        })
    }

    private fun showIssues() {
        val r = currentRepo
        clear("Issues", if (r == null) "Open a repository first." else "${r.name} • ${issues.size} findings")
        if (r == null) return
        if (issues.isEmpty()) {
            content.addView(tv("No scan results yet. Run Scan Repo first.", 14f, muted))
            content.addView(button("Scan Now", green).apply { setOnClickListener { runBridge("scan", listOf(r.path), "scan") } })
            return
        }
        issues.forEach { issue ->
            val c = when (issue.severity) { Severity.ERROR -> red; Severity.WARNING -> amber; Severity.INFO -> blue }
            content.addView(card(c).apply {
                addView(tv("${issue.severity}  •  ${issue.category}", 11f, c, true))
                addView(tv(issue.title, 17f, white, true)); addView(tv(issue.explanation, 13f, muted))
                if (issue.path != null) addView(tv("${issue.path}${issue.line?.let { ":$it" } ?: ""}", 12f, blue).apply { typeface = Typeface.MONOSPACE; setOnClickListener { copy(issue.path); toast("Path copied") } })
                val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(button("Fix It", purple, true).apply { isEnabled = issue.aiFix || issue.autoFix; setOnClickListener { beginFix(issue) } }, LinearLayout.LayoutParams(0, dp(44), 1f))
                row.addView(button("Copy AI Prompt", blue, true).apply { setOnClickListener { showPrompt(issue) } }, LinearLayout.LayoutParams(0, dp(44), 1f))
                addView(row)
            })
        }
    }

    private fun showPrompt(issue: DoctorIssue) {
        val r = currentRepo ?: return
        val prompt = AiPromptGenerator.build(issue, r.url)
        clear("AI Prompt Preview", "Secrets are redacted before copy or cloud requests.")
        content.addView(codeBox(prompt))
        content.addView(button("Copy Prompt", blue).apply { setOnClickListener { copy(prompt); toast("Prompt copied") } })
        content.addView(button("Use Configured AI Fix", purple).apply { setOnClickListener { requestAiFix(issue, prompt) } })
    }

    private fun beginFix(issue: DoctorIssue) {
        pendingIssue = issue
        val prompt = AiPromptGenerator.build(issue, currentRepo?.url ?: "repo")
        if (!secrets.has("ai-key")) showPrompt(issue) else requestAiFix(issue, prompt)
    }

    private fun requestAiFix(issue: DoctorIssue, prompt: String) {
        val key = secrets.get("ai-key")
        if (key.isNullOrBlank()) { toast("Save an AI API key first"); showAi(); return }
        val provider = prefs.getString("ai-provider", "gemini") ?: "gemini"
        val model = prefs.getString("ai-model", if (provider == "gemini") "gemini-2.5-flash" else "") ?: ""
        val endpoint = prefs.getString("ai-endpoint", "") ?: ""
        clear("AI Fix", "Requesting a proposed patch. Nothing is being changed yet.")
        content.addView(progress("Waiting for $provider…"))
        Thread {
            val result = AiClient.request(AiConfig(provider, model, endpoint), key, prompt)
            runOnUiThread {
                result.onSuccess { text ->
                    val diff = UnifiedDiffExtractor.extract(text)
                    if (diff == null) { lastLog = "AI response did not contain a safe unified diff.\n\n${SecretRedactor.redact(text).take(5000)}"; showOperation("AI Fix Rejected") }
                    else { pendingIssue = issue; pendingPatch = diff; showFixPreview() }
                }.onFailure { e -> lastLog = "AI request failed: ${SecretRedactor.redact(e.message.orEmpty())}"; showOperation("AI Error") }
            }
        }.start()
    }

    private fun showFixPreview() {
        clear("Fix Preview", "Review every changed file before approving.")
        if (pendingPatch.isBlank()) {
            content.addView(tv("No proposed patch yet. Choose Fix It from an issue, or paste a unified diff below.", 14f, muted))
            val pasted = input("Paste unified diff here", multiline = true)
            content.addView(pasted)
            content.addView(button("Preview Pasted Patch", purple).apply { setOnClickListener { pendingPatch = pasted.text.toString(); showFixPreview() } })
            return
        }
        val validation = PatchValidator.validate(pendingPatch)
        content.addView(statusCard("Patch validation", if (validation.ok) "SAFE TO CHECK IN TERMUX" else validation.reason.orEmpty(), if (validation.ok) green else red))
        content.addView(codeBox(pendingPatch))
        if (validation.ok) content.addView(button("Approve Fix", green).apply {
            setOnClickListener {
                val r = currentRepo ?: return@setOnClickListener
                val issue = pendingIssue ?: DoctorIssue("manual", Severity.INFO, "Manual", "Manual patch", "")
                val branch = BranchNameGenerator.forIssue(issue.title, System.currentTimeMillis() % 100000)
                val b64 = Base64.encodeToString(pendingPatch.toByteArray(), Base64.NO_WRAP)
                prefs.edit().putString("last-patch", b64).putString("last-patch-repo", r.path).apply()
                runBridge("apply-patch-check", listOf(r.path, b64, branch), "apply-check:$branch")
            }
        })
        content.addView(button("Cancel Proposal", red).apply { setOnClickListener { pendingPatch = ""; pendingIssue = null; showIssues() } })
    }

    private fun showBuildResult() {
        clear("Build Result", "Builds execute in Termux with the repository's Gradle wrapper.")
        if (lastLog.isBlank()) content.addView(tv("No build run yet.", 14f, muted)) else content.addView(codeBox(lastLog))
        currentRepo?.let { r ->
            content.addView(button("Build APK", blue).apply { setOnClickListener { runBridge("build", listOf(r.path), "build") } })
            content.addView(button("Run Unit Tests", green).apply { setOnClickListener { runBridge("test", listOf(r.path), "test") } })
        }
    }

    private fun showGit() {
        val r = currentRepo ?: return showHome()
        clear("Git Changes", "Commit and Push are two separate explicit actions.")
        content.addView(button("Refresh Diff", white).apply { setOnClickListener { runBridge("diff", listOf(r.path), "diff") } })
        if (lastLog.isNotBlank()) content.addView(codeBox(lastLog))
        val msg = input("fix: resolve RepoPilot finding")
        content.addView(label("COMMIT MESSAGE")); content.addView(msg)
        content.addView(button("Commit Changes", green).apply {
            setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Commit these changes?").setMessage("This creates a local Git commit on the current fix branch. It does not push.").setNegativeButton("Cancel", null).setPositiveButton("Commit") { _, _ -> runBridge("commit", listOf(r.path, msg.text.toString()), "commit") }.show() }
        })
        content.addView(button("Push to GitHub", blue).apply {
            setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Push fix branch?").setMessage("RepoPilot refuses to push main/master. This is a separate network action.").setNegativeButton("Cancel", null).setPositiveButton("Push") { _, _ -> runBridge("push", listOf(r.path), "push") }.show() }
        })
    }

    private fun showAi() {
        clear("AI Providers", "Optional cloud AI. Repo Doctor works without AI. Keys stay in Android Keystore.")
        val provider = input(prefs.getString("ai-provider", "gemini") ?: "gemini")
        val model = input(prefs.getString("ai-model", "gemini-2.5-flash") ?: "gemini-2.5-flash")
        val endpoint = input(prefs.getString("ai-endpoint", "") ?: "")
        val key = input(if (secrets.has("ai-key")) "•••••••• saved" else "Paste API key")
        content.addView(label("PROVIDER: gemini / groq / openrouter / custom")); content.addView(provider)
        content.addView(label("MODEL")); content.addView(model)
        content.addView(label("CUSTOM ENDPOINT (only for custom)")); content.addView(endpoint)
        content.addView(label("API KEY")); content.addView(key)
        content.addView(button("Save Provider", purple).apply { setOnClickListener {
            prefs.edit().putString("ai-provider", provider.text.toString().trim().lowercase()).putString("ai-model", model.text.toString().trim()).putString("ai-endpoint", endpoint.text.toString().trim()).apply()
            val raw = key.text.toString().trim(); if (raw.isNotBlank() && !raw.startsWith("••")) secrets.put("ai-key", raw)
            toast("AI provider saved securely"); showAi()
        } })
        content.addView(button("Remove API Key", red).apply { setOnClickListener { secrets.remove("ai-key"); toast("API key removed"); showAi() } })
    }

    private fun showTermux() {
        clear("Termux Setup", "RepoPilot uses the supported RUN_COMMAND integration; Termux does the heavy work.")
        content.addView(statusCard("Termux app", if (TermuxDispatcher.isInstalled(this)) "Installed" else "Not detected", if (TermuxDispatcher.isInstalled(this)) green else red))
        content.addView(statusCard("Bridge", AppConstants.BRIDGE_PATH, blue))
        val setup = "mkdir -p ~/.termux && grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-reload-settings; curl -fsSL https://raw.githubusercontent.com/mazharmnzoor4227-beep/Repo-doctor/main/termux-bridge/repopilot-bridge -o \$PREFIX/bin/repopilot-bridge && chmod +x \$PREFIX/bin/repopilot-bridge"
        content.addView(codeBox(setup))
        content.addView(button("Copy Setup Command", blue).apply { setOnClickListener { copy(setup); toast("Setup command copied") } })
        content.addView(button("Check Environment", green).apply { setOnClickListener { runBridge("environment-status", emptyList(), "environment") } })
        content.addView(tv("After changing allow-external-apps, fully restart Termux once. RepoPilot never stores your GitHub token.", 13f, amber))
    }

    private fun showHistory() {
        val r = currentRepo ?: return showHome()
        clear("Fix History & Recovery", "Operations are recorded locally. No automatic destructive recovery is used.")
        val lastPatch = prefs.getString("last-patch", null)
        val lastPatchRepo = prefs.getString("last-patch-repo", null)
        if (!lastPatch.isNullOrBlank() && lastPatchRepo == r.path) content.addView(button("Undo Last Uncommitted Patch", amber).apply {
            setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Undo last patch?").setMessage("This applies the exact approved patch in reverse. It is blocked if Git cannot apply it safely.").setNegativeButton("Cancel", null).setPositiveButton("Undo") { _, _ -> runBridge("rollback", listOf(r.path, lastPatch), "rollback") }.show() }
        })
        val h = db.history(r.id)
        if (h.isEmpty()) content.addView(tv("No history yet.", 14f, muted))
        h.forEach { item -> content.addView(card().apply { addView(tv(item.kind.uppercase(), 11f, blue, true)); addView(tv(item.message, 13f, white)); addView(tv(time.format(Date(item.createdAt)), 11f, muted)) }) }
    }

    private fun showSettings() {
        clear("Settings", "Low-RAM defaults are enabled.")
        val workspace = input(prefs.getString("workspace", AppConstants.DEFAULT_WORKSPACE) ?: AppConstants.DEFAULT_WORKSPACE)
        content.addView(label("DEFAULT TERMUX WORKSPACE")); content.addView(workspace)
        content.addView(statusCard("Background scanning", "OFF", green))
        content.addView(statusCard("Heavy job concurrency", "1", green))
        content.addView(statusCard("Live log buffer", "300 lines", green))
        content.addView(statusCard("Force push / repo delete", "UNAVAILABLE", green))
        content.addView(button("Save Settings", white).apply { setOnClickListener { prefs.edit().putString("workspace", workspace.text.toString().trim()).apply(); toast("Settings saved") } })
        currentRepo?.let { r -> content.addView(button("Clean Gradle Cache for Current Repo", amber).apply { setOnClickListener { runBridge("gradle-cache-clean", listOf(r.path), "cache-clean") } }) }
    }

    private fun runBridge(command: String, args: List<String>, operation: String) {
        if (!TermuxDispatcher.isInstalled(this)) { toast("Termux is not installed/detected"); showTermux(); return }
        currentOperation = operation
        val r = currentRepo
        if (r != null) db.addHistory(r.id, "start", "$command ${args.firstOrNull().orEmpty()}")
        lastLog = "Starting: $command\n"
        showOperation("Running $command")
        val result = TermuxDispatcher.execute(this, command, args)
        if (result.isFailure) { lastLog += "\nCould not start Termux: ${result.exceptionOrNull()?.message}"; showOperation("Termux Error") }
    }

    private fun onTermuxResult(result: TermuxResult) {
        val combined = SecretRedactor.redact((result.stdout + if (result.stderr.isBlank()) "" else "\nSTDERR:\n${result.stderr}").takeLast(120_000))
        lastLog = combined.ifBlank { result.error ?: "No output (exit ${result.exitCode})" }
        currentRepo?.let { db.addHistory(it.id, if (result.exitCode == 0) "success" else "failed", "$currentOperation • exit ${result.exitCode}\n${lastLog.take(1200)}") }
        when {
            currentOperation == "scan" -> {
                issues = DoctorParser.fromText(lastLog)
                currentRepo?.let { db.touchScan(it.id); currentRepo = db.repos().firstOrNull { rr -> rr.id == it.id } }
                showIssues()
            }
            currentOperation.startsWith("apply-check:") -> {
                if (result.exitCode != 0) showOperation("Patch Check Failed") else {
                    val branch = currentOperation.substringAfter(':')
                    val r = currentRepo ?: return
                    val b64 = Base64.encodeToString(pendingPatch.toByteArray(), Base64.NO_WRAP)
                    runBridge("apply-patch", listOf(r.path, b64, branch), "apply")
                }
            }
            currentOperation == "apply" -> { if (result.exitCode == 0) { toast("Fix applied. Build/test before committing."); showBuildResult() } else showOperation("Apply Failed") }
            currentOperation == "build" || currentOperation == "test" -> showBuildResult()
            currentOperation == "diff" || currentOperation == "commit" || currentOperation == "push" -> showGit()
            currentOperation == "repo-open" -> showDashboard()
            currentOperation == "environment" -> showOperation("Environment Status")
            else -> showOperation(if (result.exitCode == 0) "Operation Complete" else "Operation Failed")
        }
    }

    private fun showOperation(title: String) {
        clear(title, "Terminal output is bounded in the UI; secrets are redacted.")
        content.addView(codeBox(lastLog.ifBlank { "Waiting for Termux result…" }))
        content.addView(button("Copy Log", blue).apply { setOnClickListener { copy(lastLog); toast("Log copied") } })
        content.addView(button("Copy AI Debug Prompt", purple).apply { setOnClickListener {
            val p = SecretRedactor.redact("Help diagnose this repository operation. Do not expose secrets. Suggest minimal safe changes only.\n\n$lastLog")
            copy(p); toast("Debug prompt copied")
        } })
    }

    private fun button(text: String, color: Int, compact: Boolean = false): Button = Button(this).apply {
        this.text = text; setTextColor(if (color == white) bg else white); textSize = if (compact) 12f else 14f; isAllCaps = false
        background = box(if (color == white) white else panel, color, 9f); setPadding(dp(10), 0, dp(10), 0)
        val lp = LinearLayout.LayoutParams(if (compact) -2 else -1, if (compact) dp(40) else dp(50)); lp.setMargins(dp(3), dp(5), dp(3), dp(5)); layoutParams = lp
    }

    private fun input(hint: String, multiline: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint; setHintTextColor(muted); setTextColor(white); textSize = 13f; background = box(panel, border, 8f); setPadding(dp(12), dp(10), dp(12), dp(10));
        if (multiline) { minLines = 8; maxLines = 20; gravity = Gravity.TOP; typeface = Typeface.MONOSPACE }
        layoutParams = LinearLayout.LayoutParams(-1, if (multiline) dp(260) else dp(52)).apply { setMargins(0, dp(4), 0, dp(10)) }
    }

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); setLineSpacing(0f, 1.1f)
    }
    private fun label(text: String) = tv(text, 11f, muted, true).apply { setPadding(0, dp(12), 0, dp(2)) }
    private fun section(text: String) { content.addView(label(text).apply { setPadding(0, dp(22), 0, dp(8)) }) }
    private fun card(stroke: Int = border): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = box(panel, stroke, 10f); setPadding(dp(14), dp(12), dp(14), dp(12));
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(6)) }
    }
    private fun statusCard(k: String, v: String, c: Int): View = card().apply {
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(tv(k, 13f, muted, true), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(tv(v, 13f, c, true)); addView(row)
    }
    private fun codeBox(text: String): TextView = tv(text, 12f, white).apply {
        typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(12), dp(12), dp(12), dp(12)); background = box(Color.rgb(9,9,9), border, 8f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(8)) }
    }
    private fun progress(text: String): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; addView(ProgressBar(this@MainActivity), LinearLayout.LayoutParams(dp(40), dp(40))); addView(tv(text, 14f, muted).apply { setPadding(dp(12),0,0,0) })
    }
    private fun box(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply { setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radius.toInt()).toFloat() }
    private fun copy(text: String) { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("RepoPilot", text)) }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
