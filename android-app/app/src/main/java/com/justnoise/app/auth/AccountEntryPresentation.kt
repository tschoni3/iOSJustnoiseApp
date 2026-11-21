package com.justnoise.app.auth

/** Presentation only: choosing email never starts authentication or grants product access. */
internal object AccountEntryPresentation {
    fun showEmailForm(page: AccountPage, emailRequested: Boolean): Boolean =
        page != AccountPage.SIGN_IN || emailRequested

    fun showPasswordFields(page: AccountPage, emailRequested: Boolean): Boolean =
        showEmailForm(page, emailRequested) && page in setOf(
            AccountPage.SIGN_IN, AccountPage.REGISTER, AccountPage.UPDATE_PASSWORD,
            AccountPage.REAUTHENTICATE,
        )

    fun showGoogle(page: AccountPage): Boolean = page in setOf(
        AccountPage.SIGN_IN, AccountPage.REGISTER, AccountPage.REAUTHENTICATE,
    )

    fun title(page: AccountPage, emailRequested: Boolean): String = when (page) {
        AccountPage.SIGN_IN -> if (emailRequested) "Sign in" else "Welcome to Justnoise"
        AccountPage.REGISTER -> "Create your account"
        AccountPage.RECOVERY -> "Reset password"
        AccountPage.UPDATE_PASSWORD -> "New password"
        AccountPage.ACCOUNT -> "Your account"
        AccountPage.REAUTHENTICATE -> "Confirm your account"
    }
}
