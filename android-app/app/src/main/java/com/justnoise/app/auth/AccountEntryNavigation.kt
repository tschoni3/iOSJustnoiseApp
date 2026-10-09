package com.justnoise.app.auth

/** Return only after the real account operation and product handoff have completed. */
internal object AccountEntryNavigation {
    fun returnToProduct(requiredEntry: Boolean, productAllowed: Boolean,
        state: AccountScreenState): Boolean = requiredEntry && productAllowed &&
        !state.busy && state.page == AccountPage.ACCOUNT && !state.deletionPending && !state.localImport.shouldPrompt
}
