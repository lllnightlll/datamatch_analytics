package app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PositionTest {
    @Test
    fun infersGoalieFromLineupSlotWhenPositionEmpty() {
        assertEquals(Position.G, Position.infer(null, "G"))
        assertEquals(Position.D, Position.infer("", "LD"))
        assertEquals(Position.C, Position.infer("C", null))
        assertEquals(Position.UNKNOWN, Position.infer(null, null))
        assertTrue(Position.C.isSkater())
        assertFalse(Position.G.isSkater())
        assertFalse(Position.UNKNOWN.isSkater())
    }
}
