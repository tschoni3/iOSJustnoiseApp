package com.justnoise.app.auth

import org.junit.Assert.*
import org.junit.Test

class AccountEntryNavigationTest {
    private val finished = AccountScreenState(busy = false, page = AccountPage.ACCOUNT)
    @Test fun localImportChoiceIsShownBeforeAutomaticReturnButDeferredChoiceDoesNotBlock() {
        val offer = com.justnoise.gate0.data.LocalDataImportOffer(com.justnoise.gate0.data.LocalDataImportStatus.AVAILABLE)
        assertFalse(AccountEntryNavigation.returnToProduct(true, true, finished.copy(localImport = offer)))
        assertTrue(AccountEntryNavigation.returnToProduct(true, true, finished.copy(localImport = offer.copy(deferred = true))))
    }
    @Test fun successfulRequiredLoginReturnsToProduct() {
        assertTrue(AccountEntryNavigation.returnToProduct(true, true, finished))
    }
    @Test fun settingsVisitDoesNotImmediatelyDismissItself() {
        assertFalse(AccountEntryNavigation.returnToProduct(false, true, finished))
    }
    @Test fun hydrationAndUnfinishedOperationCannotReturn() {
        assertFalse(AccountEntryNavigation.returnToProduct(true, false, finished))
        assertFalse(AccountEntryNavigation.returnToProduct(true, true, finished.copy(busy = true)))
    }
    @Test fun recoveryRegistrationAndDeletionDoNotAutoDismiss() {
        for (page in AccountPage.entries.filter { it != AccountPage.ACCOUNT }) {
            assertFalse(page.name, AccountEntryNavigation.returnToProduct(true, true, finished.copy(page = page)))
        }
        assertFalse(AccountEntryNavigation.returnToProduct(true, true, finished.copy(deletionPending = true)))
    }
}
