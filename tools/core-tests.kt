import com.repopilot.app.core.*

fun assertTrue(v: Boolean, msg: String) { if (!v) error(msg) }
fun assertEq(a: Any?, b: Any?, msg: String) { if (a != b) error("$msg: expected=$b actual=$a") }

fun main() {
  assertEq(AppConstants.APP_NAME, "RepoPilot", "app name")
  assertEq(GitHubUrlParser.parse("https://github.com/owner/repo.git")?.slug, "owner/repo", "https url")
  assertEq(GitHubUrlParser.parse("git@github.com:owner/repo.git")?.slug, "owner/repo", "ssh url")
  assertTrue(GitHubUrlParser.parse("https://example.com/a/b") == null, "reject non-github")
  assertEq(BranchNameGenerator.forIssue("Gradle wrapper broken", 42), "fix/gradle-wrapper-broken-42", "branch")
  assertTrue(PatchValidator.validate("diff --git a/../x b/../x\n").isFailure, "reject traversal")
  assertTrue(PatchValidator.validate("diff --git a/.git/config b/.git/config\n").isFailure, "reject .git")
  val redacted = SecretRedactor.redact("token=ghp_abcdefghijklmnopqrstuvwxyz1234567890 SECRET=abc")
  assertTrue(!redacted.contains("ghp_"), "redact github token")
  val b = BoundedLogBuffer(3)
  b.add("1"); b.add("2"); b.add("3"); b.add("4")
  assertEq(b.lines(), listOf("2","3","4"), "bounded buffer")
  println("CORE_TESTS_OK")
}
