package app.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventFamilyClassifierTest {
    private val classifier = EventFamilyClassifier(
        listOf(
            EventFamilyRule(family = "shift", prefixes = listOf("shift_")),
            EventFamilyRule(family = "shot", prefixes = listOf("shot_"), exact = listOf("goal")),
            EventFamilyRule(family = "pass", prefixes = listOf("pass_"), exact = listOf("assist_primary")),
        ),
    )

    @Test
    fun classifiesKnownFamilies() {
        assertEquals("shift", classifier.classify("shift_F1"))
        assertEquals("shot", classifier.classify("goal"))
        assertEquals("shot", classifier.classify("shot_on_target"))
        assertEquals("pass", classifier.classify("assist_primary"))
        assertEquals(EventFamilyClassifier.OTHER_FAMILY, classifier.classify("unknown_event"))
    }

    @Test
    fun sharedPolicyKeepsIntersectionOnly() {
        val catalog = EventTypeCatalog(
            listOf(
                EventTypeStat("shot_on_target", "shot", 10, 4),
                EventTypeStat("receive", "pass", 8, 0),
                EventTypeStat("goal", "shot", 3, 1),
            ),
        )
        val policy = SharedEventPolicy(catalog)
        assertTrue(policy.isEligible("goal"))
        assertTrue(policy.isEligible("shot_on_target"))
        assertFalse(policy.isEligible("receive"))
        assertEquals(setOf("goal", "shot_on_target"), policy.eligibleTypes())
    }
}
