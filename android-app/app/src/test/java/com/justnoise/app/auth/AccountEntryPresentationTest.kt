package com.justnoise.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountEntryPresentationTest {
    @Test fun screenshotsAreAllowedWithoutPasswordFields() {
        assertFalse(AccountEntryPresentation.showPasswordFields(AccountPage.SIGN_IN, false))
        for (page in listOf(AccountPage.ACCOUNT, AccountPage.RECOVERY)) {
            for (emailRequested in listOf(false, true)) {
                assertFalse(AccountEntryPresentation.showPasswordFields(page, emailRequested))
            }
        }
    }

    @Test fun everyPasswordFormIsProtectedRegardlessOfEmailChoice() {
        assertTrue(AccountEntryPresentation.showPasswordFields(AccountPage.SIGN_IN, true))
        for (page in listOf(AccountPage.REGISTER, AccountPage.UPDATE_PASSWORD, AccountPage.REAUTHENTICATE)) {
            for (emailRequested in listOf(false, true)) {
                assertTrue(AccountEntryPresentation.showPasswordFields(page, emailRequested))
            }
        }
    }

    @Test fun freshEntryStartsWithProviderChoiceNotPasswordFields() {
        assertTrue(AccountEntryPresentation.showGoogle(AccountPage.SIGN_IN))
        assertFalse(AccountEntryPresentation.showEmailForm(AccountPage.SIGN_IN, false))
        assertEquals("Welcome to Justnoise", AccountEntryPresentation.title(AccountPage.SIGN_IN, false))
    }

    @Test fun emailChoiceRevealsTheExistingSignInForm() {
        assertTrue(AccountEntryPresentation.showEmailForm(AccountPage.SIGN_IN, true))
        assertEquals("Sign in", AccountEntryPresentation.title(AccountPage.SIGN_IN, true))
    }

    @Test fun registrationSupportsGoogleAndEmailWithoutAnotherChoiceStep() {
        assertTrue(AccountEntryPresentation.showGoogle(AccountPage.REGISTER))
        assertTrue(AccountEntryPresentation.showEmailForm(AccountPage.REGISTER, false))
        assertEquals("Create your account", AccountEntryPresentation.title(AccountPage.REGISTER, false))
    }

    @Test fun recoveryAndPasswordUpdateStayImmediatelyAccessible() {
        for (page in listOf(AccountPage.RECOVERY, AccountPage.UPDATE_PASSWORD)) {
            assertTrue(AccountEntryPresentation.showEmailForm(page, false))
            assertFalse(AccountEntryPresentation.showGoogle(page))
        }
    }

    @Test fun sameAccountReauthenticationRetainsBothMethods() {
        assertTrue(AccountEntryPresentation.showGoogle(AccountPage.REAUTHENTICATE))
        assertTrue(AccountEntryPresentation.showEmailForm(AccountPage.REAUTHENTICATE, false))
        assertEquals("Confirm your account", AccountEntryPresentation.title(AccountPage.REAUTHENTICATE, false))
    }

    @Test fun signedInAccountSettingsDoNotBecomeASignupScreen() {
        assertFalse(AccountEntryPresentation.showGoogle(AccountPage.ACCOUNT))
        assertEquals("Your account", AccountEntryPresentation.title(AccountPage.ACCOUNT, false))
    }
}
