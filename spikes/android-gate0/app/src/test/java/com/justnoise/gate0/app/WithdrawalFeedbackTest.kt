package com.justnoise.gate0.app

import com.justnoise.gate0.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WithdrawalFeedbackTest {
    @Test
    fun `confirmed cleanup uses the success message`() {
        assertEquals(R.string.status_withdrawn, withdrawalStatusMessage(cleared = true))
    }

    @Test
    fun `unconfirmed cleanup never reports successful deletion`() {
        val message = withdrawalStatusMessage(cleared = false)

        assertEquals(R.string.status_withdrawal_failed, message)
        assertNotEquals(R.string.status_withdrawn, message)
    }
}
