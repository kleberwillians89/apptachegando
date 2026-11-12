package org.example.project.core.domain.route
import kotlin.test.*
import org.example.project.features.demo.demonstrationAvailable
class DemonstrationPolicyTest {
    @Test fun existingLocalDemoCanBeUsedForDevelopmentAndQaWithoutRemoteIdentity() {
        assertTrue(demonstrationAvailable("development"))
        assertTrue(demonstrationAvailable("homologation"))
    }
    @Test fun productionAndUnknownEnvironmentsNeverExposeSimulation() {
        assertFalse(demonstrationAvailable("production"))
        assertFalse(demonstrationAvailable(""))
        assertFalse(demonstrationAvailable("Production"))
    }
}
