package com.repopilot.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GitHub
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repopilot.app.AppController
import com.repopilot.app.RepoPage
import com.repopilot.app.TopTab
import com.repopilot.app.core.AiProviderCatalog
import com.repopilot.app.core.DoctorIssue
import com.repopilot.app.core.PatchValidator
import com.repopilot.app.core.Severity
import com.repopilot.app.data.HistoryRecord
import com.repopilot.app.data.RepoRecord

private data class DockDestination(val tab: TopTab, val label: String, val icon: ImageVector)

private val dockDestinations = listOf(
    DockDestination(TopTab.HOME, "Home", Icons.Rounded.Home),
    DockDestination(TopTab.REPOS, "Repos", Icons.Rounded.Folder),
    DockDestination(TopTab.AI, "AI", Icons.Rounded.AutoAwesome),
    DockDestination(TopTab.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun RepoPilotApp(controller: AppController) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(controller.snackbarMessage) {
        controller.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            controller.clearSnackbar()
        }
    }

    Scaffold(
        containerColor = RpColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                selectedTab = controller.selectedTab,
                isBusy = controller.isBusy,
                modifier = Modifier.statusBarsPadding(),
            )
        },
        bottomBar = {
            Box(Modifier.navigationBarsPadding()) {
                BottomDock(
                    selected = controller.selectedTab,
                    onSelected = controller::selectTab,
                )
            }
        },
    ) { innerPadding ->
        val transitionKey = controller.selectedTab to if (controller.selectedTab == TopTab.REPOS) controller.repoPage else RepoPage.LIST
        AnimatedContent(
            targetState = transitionKey,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f),
                    initialOffsetX = { it / 12 },
                )) togetherWith (fadeOut(tween(130)) + slideOutHorizontally(
                    animationSpec = tween(160),
                    targetOffsetX = { -it / 18 },
                ))
            },
            label = "RepoPilot screen transition",
        ) {
            when (controller.selectedTab) {
                TopTab.HOME -> HomeScreen(controller)
                TopTab.REPOS -> ReposRoot(controller)
                TopTab.AI -> AiScreen(controller)
                TopTab.SETTINGS -> SettingsScreen(controller)
            }
        }
    }
}

@Composable
private fun AppHeader(selectedTab: TopTab, isBusy: Boolean, modifier: Modifier = Modifier) {
    val subtitle = when (selectedTab) {
        TopTab.HOME -> "Developer command center"
        TopTab.REPOS -> "Repository doctor"
        TopTab.AI -> "Cloud AI providers"
        TopTab.SETTINGS -> "Device & Termux setup"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RpColors.Background)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("RepoPilot", color = RpColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = RpColors.TextMuted, fontSize = 11.sp)
        }
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = RpColors.Text,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        StatusPill("SAFE MODE", RpColors.Success, Icons.Rounded.Shield)
    }
}

@Composable
private fun BottomDock(selected: TopTab, onSelected: (TopTab) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = RpColors.SurfaceRaised,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, RpColors.Border),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            dockDestinations.forEach { destination ->
                val active = selected == destination.tab
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.94f else 1f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
                    label = "dock press",
                )
                val background = if (active) RpColors.Text else Color.Transparent
                val foreground = if (active) RpColors.Background else RpColors.TextMuted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(22.dp))
                        .background(background)
                        .clickable(interactionSource = interaction, indication = null) { onSelected(destination.tab) }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(destination.icon, destination.label, tint = foreground, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(destination.label, color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(controller: AppController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Your repos,\nunder control.", style = MaterialTheme.typography.displaySmall, color = RpColors.Text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Scan, understand, fix, build and push with explicit safety gates.",
                style = MaterialTheme.typography.bodyLarge,
                color = RpColors.TextMuted,
            )
        }
        item {
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = RpColors.Text, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Bolt, null, tint = RpColors.Background, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Start with a repository", color = RpColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Paste a GitHub URL. Termux handles the heavy work.", color = RpColors.TextMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Add repository", Icons.Rounded.Add) {
                    controller.selectTab(TopTab.REPOS)
                    controller.backToRepoList()
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Repos", controller.repos.size.toString(), Icons.Rounded.Folder, Modifier.weight(1f))
                MetricCard("Issues", controller.issues.size.toString(), Icons.Rounded.Build, Modifier.weight(1f))
            }
        }
        item { SectionTitle("RECENT REPOSITORIES") }
        if (controller.repos.isEmpty()) {
            item { EmptyCard("No repositories yet", "Add a GitHub repository to begin.") }
        } else {
            items(controller.repos.take(4), key = { it.id }) { repo ->
                RepoCard(repo, controller.formattedLastScan(repo)) { controller.openRepo(repo) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ReposRoot(controller: AppController) {
    when (controller.repoPage) {
        RepoPage.LIST -> RepoListScreen(controller)
        else -> RepoWorkspaceScreen(controller)
    }
}

@Composable
private fun RepoListScreen(controller: AppController) {
    var url by rememberSaveable { mutableStateOf("") }
    var workspace by remember(controller.workspace) { mutableStateOf(controller.workspace) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Repositories", style = MaterialTheme.typography.displaySmall, color = RpColors.Text)
            Spacer(Modifier.height(6.dp))
            Text("Open an existing repo or clone one into Termux.", color = RpColors.TextMuted, fontSize = 13.sp)
        }
        item {
            PremiumCard {
                FieldLabel("GITHUB REPOSITORY URL")
                RpTextField(value = url, onValueChange = { url = it }, placeholder = "https://github.com/owner/repo")
                Spacer(Modifier.height(10.dp))
                FieldLabel("TERMUX WORKSPACE")
                RpTextField(value = workspace, onValueChange = { workspace = it }, placeholder = "/data/data/com.termux/files/home/repopilot")
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Add / Open Repository", Icons.Rounded.Folder) {
                    if (controller.addOrOpenRepo(url, workspace)) url = ""
                }
            }
        }
        item { SectionTitle("SAVED REPOSITORIES") }
        if (controller.repos.isEmpty()) {
            item { EmptyCard("Nothing saved", "Your opened repositories will appear here.") }
        } else {
            items(controller.repos, key = { it.id }) { repo ->
                RepoCard(repo, controller.formattedLastScan(repo)) { controller.openRepo(repo) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun RepoWorkspaceScreen(controller: AppController) {
    val repo = controller.currentRepo
    if (repo == null) {
        LaunchedEffect(Unit) { controller.backToRepoList() }
        return
    }

    Column(Modifier.fillMaxSize()) {
        RepoContextHeader(repo, controller.repoPage, controller::backToRepoList)
        when (controller.repoPage) {
            RepoPage.DASHBOARD -> DashboardScreen(controller, repo)
            RepoPage.ISSUES -> IssuesScreen(controller)
            RepoPage.PROMPT -> PromptScreen(controller)
            RepoPage.FIX -> FixPreviewScreen(controller)
            RepoPage.BUILD -> BuildScreen(controller)
            RepoPage.GIT -> GitScreen(controller)
            RepoPage.HISTORY -> HistoryScreen(controller)
            RepoPage.LOGS -> LogsScreen(controller)
            RepoPage.LIST -> Unit
        }
    }
}

@Composable
private fun RepoContextHeader(repo: RepoRecord, page: RepoPage, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, "Repositories", tint = RpColors.Text)
        }
        Column(Modifier.weight(1f)) {
            Text(repo.name, color = RpColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pageTitle(page), color = RpColors.TextMuted, fontSize = 11.sp)
        }
        StatusPill(repo.branch, RpColors.Info, Icons.Rounded.GitHub)
    }
}

private fun pageTitle(page: RepoPage): String = when (page) {
    RepoPage.LIST -> "Repositories"
    RepoPage.DASHBOARD -> "Dashboard"
    RepoPage.ISSUES -> "Issues"
    RepoPage.PROMPT -> "AI prompt"
    RepoPage.FIX -> "Fix preview"
    RepoPage.BUILD -> "Build & test"
    RepoPage.GIT -> "Git changes"
    RepoPage.HISTORY -> "History"
    RepoPage.LOGS -> "Terminal logs"
}

@Composable
private fun DashboardScreen(controller: AppController, repo: RepoRecord) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Repository dashboard", style = MaterialTheme.typography.headlineSmall, color = RpColors.Text)
            Spacer(Modifier.height(5.dp))
            Text(repo.path, color = RpColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStatusCard("Branch", repo.branch, RpColors.Info, Modifier.weight(1f))
                SmallStatusCard("Last scan", controller.formattedLastScan(repo), RpColors.Success, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Scan repo", "Find build and Git problems", Icons.Rounded.Shield, Modifier.weight(1f), controller::scanRepo)
                ActionTile("Build APK", "Run assembleDebug safely", Icons.Rounded.Android, Modifier.weight(1f), controller::buildRepo)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Pull latest", "Fast-forward only", Icons.Rounded.Refresh, Modifier.weight(1f), controller::pullRepo)
                ActionTile("Git status", "Branch and working tree", Icons.Rounded.GitHub, Modifier.weight(1f), controller::gitStatus)
            }
        }
        item { SectionTitle("WORKSPACE") }
        item {
            MenuRow("Issues", "${controller.issues.size} findings", Icons.Rounded.Build) { controller.openRepoPage(RepoPage.ISSUES) }
            MenuRow("Build & Test", "APK and test output", Icons.Rounded.PlayArrow) { controller.openRepoPage(RepoPage.BUILD) }
            MenuRow("Git Changes", "Review, commit, push", Icons.Rounded.Code) { controller.openRepoPage(RepoPage.GIT) }
            MenuRow("History", "Fix and operation history", Icons.Rounded.History) { controller.openRepoPage(RepoPage.HISTORY) }
            MenuRow("Terminal Logs", "Last bounded output", Icons.Rounded.Terminal) { controller.openRepoPage(RepoPage.LOGS) }
        }
        item {
            PremiumCard {
                Text("Safety gates", color = RpColors.Text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Preview fix → approve → build/test → commit → separate push. RepoPilot blocks force-push and direct main/master commits.",
                    color = RpColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun IssuesScreen(controller: AppController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Issues", style = MaterialTheme.typography.headlineSmall, color = RpColors.Text)
                    Text("${controller.issues.size} findings from the latest scan", color = RpColors.TextMuted, fontSize = 12.sp)
                }
                CompactOutlinedButton("Scan", Icons.Rounded.Refresh, controller::scanRepo)
            }
        }
        if (controller.issues.isEmpty()) {
            item { EmptyCard("No scan results yet", "Run Scan Repo to detect Git, Gradle and Android problems.") }
        } else {
            items(controller.issues, key = { it.id }) { issue -> IssueCard(issue, controller) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun IssueCard(issue: DoctorIssue, controller: AppController) {
    val severityColor = when (issue.severity) {
        Severity.ERROR -> RpColors.Error
        Severity.WARNING -> RpColors.Warning
        Severity.INFO -> RpColors.Info
    }
    PremiumCard(borderColor = severityColor.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(issue.severity.name, severityColor, Icons.Rounded.Shield)
            Spacer(Modifier.width(8.dp))
            Text(issue.category, color = RpColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        Text(issue.title, color = RpColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(issue.explanation, color = RpColors.TextMuted, fontSize = 12.sp)
        issue.path?.let {
            Spacer(Modifier.height(7.dp))
            Text("$it${issue.line?.let { n -> ":$n" } ?: ""}", color = RpColors.Info, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactPrimaryButton(
                label = if (issue.autoFix) "Fix safely" else "Fix It",
                icon = Icons.Rounded.Bolt,
                modifier = Modifier.weight(1f),
                enabled = issue.autoFix || issue.aiFix,
            ) { controller.beginFix(issue) }
            CompactOutlinedButton(
                label = "AI Prompt",
                icon = Icons.Rounded.ContentCopy,
                modifier = Modifier.weight(1f),
            ) { controller.showPrompt(issue) }
        }
    }
}

@Composable
private fun PromptScreen(controller: AppController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("AI prompt preview", style = MaterialTheme.typography.headlineSmall, color = RpColors.Text)
            Text("Secrets are redacted before copy or cloud requests.", color = RpColors.TextMuted, fontSize = 12.sp)
        }
        item { CodeBlock(controller.promptPreview.ifBlank { "No prompt prepared." }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactOutlinedButton("Copy prompt", Icons.Rounded.ContentCopy, Modifier.weight(1f), onClick = controller::copyPrompt)
                CompactPrimaryButton("Use configured AI", Icons.Rounded.AutoAwesome, Modifier.weight(1f), onClick = controller::useConfiguredAiForPrompt)
            }
        }
    }
}

@Composable
private fun FixPreviewScreen(controller: AppController) {
    var manualPatch by remember { mutableStateOf("") }
    val patch = controller.pendingPatch
    val validation = if (patch.isBlank()) null else PatchValidator.validate(patch)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Fix preview", style = MaterialTheme.typography.headlineSmall, color = RpColors.Text)
            Text("Nothing changes until you approve this exact patch.", color = RpColors.TextMuted, fontSize = 12.sp)
        }
        if (patch.isBlank()) {
            item {
                PremiumCard {
                    FieldLabel("PASTE UNIFIED DIFF")
                    RpTextField(
                        value = manualPatch,
                        onValueChange = { manualPatch = it },
                        placeholder = "diff --git a/... b/...",
                        minLines = 8,
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Preview patch", Icons.Rounded.RemoveRedEye, enabled = manualPatch.isNotBlank()) {
                        controller.setPendingPatch(manualPatch)
                    }
                }
            }
        } else {
            item {
                SmallStatusCard(
                    "Patch validation",
                    if (validation?.ok == true) "SAFE TO APPLY" else validation?.reason.orEmpty(),
                    if (validation?.ok == true) RpColors.Success else RpColors.Error,
                    Modifier.fillMaxWidth(),
                )
            }
            item { CodeBlock(patch) }
            item {
                PrimaryButton("Approve Fix", Icons.Rounded.Check, enabled = validation?.ok == true, onClick = controller::approvePatch)
                Spacer(Modifier.height(8.dp))
                OutlinedActionButton("Cancel proposal", Icons.Rounded.DeleteOutline, controller::cancelPatch)
            }
        }
    }
}

@Composable
private fun BuildScreen(controller: AppController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Build & test", style = MaterialTheme.typography.headlineSmall, color = RpColors.Text)
            Text("Uses the repository Gradle wrapper in Termux with low-RAM defaults.", color = RpColors.TextMuted, fontSize = 12.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactPrimaryButton("Build APK", Icons.Rounded.Android, Modifier.weight(1f), onClick = controller::buildRepo)
                CompactOutlinedButton("Run tests", Icons.Rounded.PlayArrow, Modifier.weight(1f), onClick = controller::testRepo)
            }
        }
        item { CodeBlock(controller.lastLog.ifBlank { "No build or test output yet." }) }
        item {
            CompactOutlinedButton("Open full logs", Icons.Rounded.Terminal) { controller.openRepoPage(RepoPage.LOGS) }
        }
    }
}

@Composable
private fun GitScreen(controller: AppController) {
    var commitMessage by rememberSaveable { mutableStateOf("fix: resolve RepoPilot finding") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Git changes", style = MaterialTheme.typography.headlineSmall, color = RpColors.Text)
                    Text("Commit and push are always separate actions.", color = RpColors.TextMuted, fontSize = 12.sp)
                }
                CompactOutlinedButton("Refresh", Icons.Rounded.Refresh, controller::refreshDiff)
            }
        }
        item { CodeBlock(controller.lastLog.ifBlank { "Tap Refresh to inspect the current diff." }) }
        item {
            FieldLabel("COMMIT MESSAGE")
            RpTextField(commitMessage, { commitMessage = it }, "fix: describe the change")
        }
        item {
            PrimaryButton("Commit changes", Icons.Rounded.Save) { controller.commitChanges(commitMessage) }
            Spacer(Modifier.height(8.dp))
            OutlinedActionButton("Push fix branch to GitHub", Icons.Rounded.CloudDone, controller::pushFixBranch)
        }
        item {
            Text(
                "RepoPilot refuses direct commits or pushes from main/master and only pushes fix/* branches.",
                color = RpColors.Warning,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun HistoryScreen(controller: AppController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("History & recovery", style = MaterialTheme.typography.headlineSmall, color = RpColors.Text)
            Text("Operations are recorded locally. Recovery never discards unrelated work.", color = RpColors.TextMuted, fontSize = 12.sp)
        }
        item { OutlinedActionButton("Undo last uncommitted patch", Icons.Rounded.History, controller::undoLastPatch) }
        if (controller.history.isEmpty()) {
            item { EmptyCard("No history yet", "Repo operations will appear here.") }
        } else {
            items(controller.history, key = { it.id }) { item -> HistoryCard(item) }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryRecord) {
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.kind.uppercase(), color = if (item.kind == "failed") RpColors.Error else RpColors.Info, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("#${item.id}", color = RpColors.TextDim, fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(item.message, color = RpColors.TextMuted, fontSize = 11.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LogsScreen(controller: AppController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Terminal logs", style = MaterialTheme.typography.headlineSmall, color = RpColors.Text)
            Text("UI output is bounded and known secret patterns are redacted.", color = RpColors.TextMuted, fontSize = 12.sp)
        }
        item { CodeBlock(controller.lastLog.ifBlank { "Waiting for an operation…" }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactOutlinedButton("Copy log", Icons.Rounded.ContentCopy, Modifier.weight(1f), onClick = controller::copyLog)
                CompactOutlinedButton("AI debug prompt", Icons.Rounded.AutoAwesome, Modifier.weight(1f), onClick = controller::copyDebugPrompt)
            }
        }
    }
}

@Composable
private fun AiScreen(controller: AppController) {
    var apiKey by remember(controller.aiProvider) { mutableStateOf("") }
    var keyVisible by remember(controller.aiProvider) { mutableStateOf(false) }
    val selectedSpec = AiProviderCatalog.byId(controller.aiProvider)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("AI providers", style = MaterialTheme.typography.displaySmall, color = RpColors.Text)
            Spacer(Modifier.height(6.dp))
            Text("Bring your own cloud API key. Keys are encrypted with Android Keystore and never shown again.", color = RpColors.TextMuted, fontSize = 13.sp)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(AiProviderCatalog.all, key = { it.id }) { spec ->
                    ProviderChip(spec.label, controller.aiProvider == spec.id) { controller.setAiProvider(spec.id) }
                }
            }
        }
        item {
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, null, tint = RpColors.Text, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${selectedSpec.label} connection", color = RpColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (controller.aiKeySaved) "API key saved securely" else "No API key saved",
                            color = if (controller.aiKeySaved) RpColors.Success else RpColors.Warning,
                            fontSize = 11.sp,
                        )
                    }
                    if (controller.aiKeySaved) Icon(Icons.Rounded.Check, null, tint = RpColors.Success)
                }
                Spacer(Modifier.height(14.dp))
                FieldLabel("MODEL ID")
                RpTextField(controller.aiModel, controller::setAiModel, "Provider model ID")
                ModelQuickPicks(controller.aiProvider, controller.aiModel, controller::setAiModel)
                if (selectedSpec.requiresEndpoint) {
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("OPENAI-COMPATIBLE ENDPOINT")
                    RpTextField(controller.aiEndpoint, controller::setAiEndpoint, "https://example.com/v1/chat/completions")
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel(if (controller.aiKeySaved) "API KEY • SAVED (PASTE TO REPLACE)" else "API KEY")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(if (controller.aiKeySaved) "Saved securely" else "Paste API key", color = RpColors.TextDim) },
                    leadingIcon = { Icon(Icons.Rounded.Key, null, tint = RpColors.TextMuted) },
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.RemoveRedEye, null, tint = RpColors.TextMuted)
                        }
                    },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = repoPilotTextFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactOutlinedButton(
                        label = if (controller.aiTesting) "Testing…" else "Test connection",
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f),
                        enabled = !controller.aiTesting,
                    ) { controller.testAiConnection(apiKey) }
                    CompactPrimaryButton("Save", Icons.Rounded.Save, Modifier.weight(1f)) {
                        controller.saveAiSettings(apiKey)
                        apiKey = ""
                    }
                }
                controller.aiTestMessage?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        message,
                        color = if (message.startsWith("Connected")) RpColors.Success else if (message.startsWith("Testing")) RpColors.Info else RpColors.Error,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        if (controller.aiKeySaved) {
            item {
                OutlinedActionButton("Remove ${selectedSpec.label} API key", Icons.Rounded.DeleteOutline, controller::removeAiKey)
            }
        }
        item {
            PremiumCard {
                Text("How AI Fix works", color = RpColors.Text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "RepoPilot sends only the prepared issue prompt to your selected provider. The returned patch must pass local path and patch validation before you can approve it.",
                    color = RpColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ModelQuickPicks(provider: String, current: String, onPick: (String) -> Unit) {
    val suggestions = when (provider) {
        "gemini" -> listOf("gemini-2.5-flash", "gemini-2.5-pro")
        "groq" -> listOf("llama-3.3-70b-versatile")
        "openrouter" -> listOf("openai/gpt-oss-20b:free", "qwen/qwen3-coder:free")
        else -> emptyList()
    }
    if (suggestions.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(suggestions) { model ->
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = if (current == model) RpColors.Text else RpColors.SurfaceSoft,
                border = BorderStroke(1.dp, if (current == model) RpColors.Text else RpColors.Border),
                modifier = Modifier.clickable { onPick(model) },
            ) {
                Text(
                    model,
                    color = if (current == model) RpColors.Background else RpColors.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(controller: AppController) {
    var workspace by remember(controller.workspace) { mutableStateOf(controller.workspace) }
    val termuxInstalled = controller.isTermuxInstalled()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.displaySmall, color = RpColors.Text)
            Spacer(Modifier.height(6.dp))
            Text("Low-RAM defaults and secure local integrations.", color = RpColors.TextMuted, fontSize = 13.sp)
        }
        item {
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, null, tint = RpColors.Text, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Termux worker", color = RpColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(if (termuxInstalled) "Detected on this device" else "Not detected", color = if (termuxInstalled) RpColors.Success else RpColors.Error, fontSize = 11.sp)
                    }
                    StatusPill(if (termuxInstalled) "READY" else "SETUP", if (termuxInstalled) RpColors.Success else RpColors.Warning, Icons.Rounded.Terminal)
                }
                Spacer(Modifier.height(14.dp))
                SelectionContainer { Text(controller.termuxSetupCommand(), color = RpColors.TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactOutlinedButton("Copy setup", Icons.Rounded.ContentCopy, Modifier.weight(1f), onClick = controller::copyTermuxSetup)
                    CompactPrimaryButton("Check environment", Icons.Rounded.PlayArrow, Modifier.weight(1f), onClick = controller::checkEnvironment)
                }
            }
        }
        item {
            PremiumCard {
                FieldLabel("DEFAULT TERMUX WORKSPACE")
                RpTextField(workspace, { workspace = it }, "/data/data/com.termux/files/home/repopilot")
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Save workspace", Icons.Rounded.Save) { controller.saveWorkspace(workspace) }
            }
        }
        item { SectionTitle("PERFORMANCE & SAFETY") }
        item {
            SmallStatusCard("Background scanning", "OFF", RpColors.Success, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SmallStatusCard("Heavy job concurrency", "1", RpColors.Success, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SmallStatusCard("Live log buffer", "BOUNDED", RpColors.Success, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SmallStatusCard("Force push / repo delete", "BLOCKED", RpColors.Success, Modifier.fillMaxWidth())
        }
        if (controller.currentRepo != null) {
            item { OutlinedActionButton("Clean current repo Gradle cache", Icons.Rounded.Memory, controller::cleanGradleCache) }
        }
        item {
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = RpColors.Success)
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Local-first safety", color = RpColors.Text, fontWeight = FontWeight.SemiBold)
                        Text("GitHub tokens stay in Termux/gh. AI keys stay encrypted in Android Keystore.", color = RpColors.TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun RepoCard(repo: RepoRecord, lastScan: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, spring(dampingRatio = 0.72f), label = "repo card press")
    PremiumCard(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = RpColors.SurfaceSoft, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Folder, null, tint = RpColors.Text, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.name, color = RpColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(repo.url, color = RpColors.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("${repo.branch}  •  scan $lastScan", color = RpColors.TextDim, fontSize = 10.sp)
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = RpColors.TextMuted)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    PremiumCard(modifier = modifier) {
        Icon(icon, null, tint = RpColors.TextMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(12.dp))
        Text(value, color = RpColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
        Text(label, color = RpColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SmallStatusCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = RpColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RpColors.Border)) {
        Column(Modifier.padding(13.dp)) {
            Text(label.uppercase(), color = RpColors.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.62f), label = "action tile press")
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        color = RpColors.Surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, RpColors.Border),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = RpColors.Text, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, color = RpColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = RpColors.TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick),
        color = RpColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RpColors.Border),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RpColors.TextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = RpColors.Text, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(subtitle, color = RpColors.TextDim, fontSize = 10.sp)
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = RpColors.TextDim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) RpColors.Text else RpColors.Surface,
        border = BorderStroke(1.dp, if (selected) RpColors.Text else RpColors.Border),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = if (selected) RpColors.Background else RpColors.TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, color = if (selected) RpColors.Background else RpColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PremiumCard(
    modifier: Modifier = Modifier,
    borderColor: Color = RpColors.Border,
    content: @Composable Column.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = RpColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StatusPill(label: String, color: Color, icon: ImageVector) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(99.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.35f))) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    PremiumCard {
        Text(title, color = RpColors.Text, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = RpColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = RpColors.TextDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = RpColors.TextDim, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun RpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = minLines == 1,
        minLines = minLines,
        maxLines = if (minLines == 1) 1 else 14,
        placeholder = { Text(placeholder, color = RpColors.TextDim, fontSize = 12.sp) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (minLines > 1) FontFamily.Monospace else FontFamily.SansSerif),
        colors = repoPilotTextFieldColors(),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun repoPilotTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RpColors.Text,
    unfocusedTextColor = RpColors.Text,
    focusedBorderColor = RpColors.BorderStrong,
    unfocusedBorderColor = RpColors.Border,
    focusedContainerColor = RpColors.SurfaceRaised,
    unfocusedContainerColor = RpColors.SurfaceRaised,
    cursorColor = RpColors.Text,
)

@Composable
private fun CodeBlock(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF08090B),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RpColors.Border),
    ) {
        SelectionContainer {
            Text(
                text,
                color = RpColors.TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RpColors.Text,
            contentColor = RpColors.Background,
            disabledContainerColor = RpColors.SurfaceSoft,
            disabledContentColor = RpColors.TextDim,
        ),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun CompactPrimaryButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RpColors.Text,
            contentColor = RpColors.Background,
            disabledContainerColor = RpColors.SurfaceSoft,
            disabledContentColor = RpColors.TextDim,
        ),
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CompactOutlinedButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        border = BorderStroke(1.dp, RpColors.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RpColors.Text),
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun OutlinedActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, RpColors.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RpColors.Text),
    ) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
