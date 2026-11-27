package llm.slop.spazradio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryPolicyTest {
    @Test fun backsOffThenSettlesAtThirtySeconds() {
        assertEquals(listOf(2000L, 4000L, 8000L, 16000L, 30000L, 30000L), (0..5).map { RetryPolicy.delayMs(it) })
    }

    @Test fun givesUpAfterTheLimit() {
        assertEquals(30000L, RetryPolicy.delayMs(RetryPolicy.MAX_ATTEMPTS - 1))
        assertNull(RetryPolicy.delayMs(RetryPolicy.MAX_ATTEMPTS))
        assertNull(RetryPolicy.delayMs(-1))
    }
}
