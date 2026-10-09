package com.justnoise.gate0.platform.nfc

/** Pure bounded wait policy; it never authorizes a tag or a session mutation. */
object DirectNfcReadinessWait {
    fun shouldWait(claimActive: Boolean, accountRestoring: Boolean, serviceRecovering: Boolean,
        nowElapsed: Long, deadlineElapsed: Long): Boolean =
        claimActive && nowElapsed < deadlineElapsed && (accountRestoring || serviceRecovering)
}
