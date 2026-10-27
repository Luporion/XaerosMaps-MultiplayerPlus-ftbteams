package dev.alinco8.xmmp.client.io

import org.junit.jupiter.api.Test

/** Run the existing standalone regression suite through real Gradle test discovery. */
class PersonalExplorationJournalGradleTest {
    @Test
    fun preservesNativeVisitsAndReceivedFlagsAfterRestart() {
        main() // invokes 14 existing assertions; exceptions fail JUnit and CI.
    }
}
