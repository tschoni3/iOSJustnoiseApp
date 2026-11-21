package com.justnoise.app.auth

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Presentation guard; authentication and consent behavior remain separately tested. */
class AccountPresentationCleanupTest {
    @Test fun screenshotProtectionTracksTheSameRuleAsVisiblePasswordFields() {
        val source = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "android-app/app/src/main/java/com/justnoise/app/auth/AccountActivity.kt") }
            .first { it.isFile }.readText()
        assertTrue(source.contains("setScreenshotProtection(true)"))
        assertTrue(source.contains("runtime::deferLocalImport, ::setScreenshotProtection"))
        assertTrue(source.contains("SideEffect { onScreenshotProtection(showPasswordFields) }"))
        assertTrue(source.contains("if (showPasswordFields)"))
        assertTrue(source.contains("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)"))
        assertTrue(source.contains("window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)"))
    }

    @Test fun retryIsNotAPermanentDiagnosticAction() {
        val source = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "android-app/app/src/main/java/com/justnoise/app/auth/AccountActivity.kt") }
            .first { it.isFile }.readText()
        assertFalse(source.contains("Reload account state"))
        val runtime = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "android-app/app/src/main/java/com/justnoise/app/auth/AccountRuntime.kt") }
            .first { it.isFile }.readText()
        assertFalse(runtime.contains("Reload account state"))
        assertTrue(source.contains("!state.message.isNullOrBlank() || state.localImport.status in"))
        assertTrue(source.contains("setOf(LocalDataImportStatus.ERROR, LocalDataImportStatus.PENDING)"))
        assertTrue(source.contains("Text(\"Try again\")"))
        assertTrue(source.contains("analytics-consent"))
    }

    @Test fun authenticationFormsDoNotShowGenericRetryOrClose() {
        val source = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "android-app/app/src/main/java/com/justnoise/app/auth/AccountActivity.kt") }
            .first { it.isFile }.readText()
        assertFalse(source.contains("\"Close\""))
        assertTrue(source.contains("if (!authForm &&\n                    (!state.message.isNullOrBlank()"))
        assertTrue(source.contains("if (!authForm) TextButton(onClick = onDone)"))
        assertTrue(source.contains("onSignIn(email, submitted, state.page == AccountPage.REGISTER)"))
        assertTrue(source.contains("contentDescription = \"Sign in with Google\""))
        assertTrue(source.contains("Text(\"Forgot password?\")"))
    }

    @Test fun googlePrecedesTheEmailFieldsAndBusyActionsStayDisabled() {
        val source = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "android-app/app/src/main/java/com/justnoise/app/auth/AccountActivity.kt") }
            .first { it.isFile }.readText()
        assertTrue(source.indexOf("GoogleAccountButton(enabled = !state.busy") < source.indexOf("OutlinedTextField("))
        assertTrue(source.contains("if (!showEmailForm)"))
        assertTrue(source.contains("account-use-email"))
        assertTrue(source.contains("account-email-submit"))
        assertTrue(source.contains("password = \"\"; confirmPassword = \"\"\n                            onGoogle()"))
        assertFalse(source.contains("0xFF0066D6"))
    }
}
