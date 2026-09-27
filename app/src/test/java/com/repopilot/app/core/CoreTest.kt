package com.repopilot.app.core

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun appName() = assertEquals("RepoPilot", AppConstants.APP_NAME)
    @Test fun githubUrls() {
        assertEquals("owner/repo", GitHubUrlParser.parse("https://github.com/owner/repo.git")?.slug)
        assertEquals("owner/repo", GitHubUrlParser.parse("git@github.com:owner/repo.git")?.slug)
        assertNull(GitHubUrlParser.parse("https://example.com/owner/repo"))
    }
    @Test fun patchTraversalBlocked() {
        assertTrue(PatchValidator.validate("diff --git a/../x b/../x\n").isFailure)
        assertTrue(PatchValidator.validate("diff --git a/.git/config b/.git/config\n").isFailure)
    }
    @Test fun bufferBounded() {
        val b = BoundedLogBuffer(2); b.add("a"); b.add("b"); b.add("c")
        assertEquals(listOf("b","c"), b.lines())
    }
}
