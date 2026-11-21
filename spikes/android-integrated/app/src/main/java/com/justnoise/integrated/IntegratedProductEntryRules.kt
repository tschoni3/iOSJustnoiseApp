package com.justnoise.integrated

import com.justnoise.gate0.feature.Gate0Truth

/** Keep capability education and exceptional recovery outside product presentation. */
internal object IntegratedProductEntryRules {
    fun needsRecovery(truth: Gate0Truth, sessionRequested: Boolean,
        storageRecovery: Boolean, schedulesOpen: Boolean, schedulesSuspended: Boolean): Boolean =
        storageRecovery || truth == Gate0Truth.RECOVERY_REQUIRED ||
            (sessionRequested && truth != Gate0Truth.ACTIVE) || (schedulesOpen && schedulesSuspended)

    fun needsBlocking(truth: Gate0Truth, disclosureAccepted: Boolean, blockingEnabled: Boolean): Boolean =
        truth != Gate0Truth.ACTIVE &&
            (!disclosureAccepted || !blockingEnabled ||
                truth == Gate0Truth.CONSENT_REQUIRED || truth == Gate0Truth.PERMISSION_REQUIRED)
}
