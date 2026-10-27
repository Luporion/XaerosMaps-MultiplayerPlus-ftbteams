package dev.alinco8.xmmp.core.sharing

import org.junit.jupiter.api.Test

/** Execute the original policy checks in both CLI and Gradle/JUnit CI. */
class SharingPolicyGradleTest {
    @Test
    fun enforcesPrivateTeamGlobalAndScopeAccessRules() {
        main() // all 51 existing policy checks are JUnit assertions.
    }
}
