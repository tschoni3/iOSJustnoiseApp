package com.justnoise.gate0.data

/** Application-scoped port. Only production requires an account; spikes stay offline. */
interface SessionStartAccessHost {
    val sessionStartAccess: SessionStartAccess
}

/** No disk, credentials, network or Android types. Accessed under the product state lock. */
class SessionStartAccess(val accountRequired: Boolean = false) {
    internal var authorizedOwner: LocalWorkspaceId? = null
    internal var resolved: Boolean = !accountRequired

    internal fun allows(workspace: LocalWorkspaceId): Boolean = !accountRequired ||
        (authorizedOwner != null && authorizedOwner != LocalWorkspaceId.LOCAL && authorizedOwner == workspace)
}
