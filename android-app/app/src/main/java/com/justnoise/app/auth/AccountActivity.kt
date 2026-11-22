package com.justnoise.app.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.justnoise.gate0.R
import com.justnoise.gate0.data.LocalDataImportOffer
import com.justnoise.gate0.data.LocalDataImportStatus

/** No credentials in Intent extras, saved instance state, logs, or UI test tags. */
class AccountActivity : ComponentActivity() {
    private val runtime get() = (application as AccountApplication).accounts
    private var callbackDelivered = false
    private var displayedRecoveryId: String? = null
    private var requiredEntry = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fail closed until Compose has identified the visible account surface.
        setScreenshotProtection(true)
        enableEdgeToEdge()
        requiredEntry = savedInstanceState?.getBoolean("account_required_entry")
            ?: !runtime.access.value.productAllowed
        callbackDelivered = savedInstanceState?.getBoolean("account_callback_delivered") == true
        if (!callbackDelivered) receive(intent)
        setContent {
            val state by runtime.state.collectAsState()
            val access by runtime.access.collectAsState()
            SideEffect {
                displayedRecoveryId = state.recoveryFlowId
                if (!state.busy && access.ready && !access.signedIn) requiredEntry = true
            }
            LaunchedEffect(access.productAllowed, state.busy, state.page, state.deletionPending, state.localImport.shouldPrompt) {
                if (AccountEntryNavigation.returnToProduct(requiredEntry, access.productAllowed, state)) {
                    startActivity(Intent(this@AccountActivity, com.justnoise.integrated.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                }
            }
            LaunchedEffect(state.browserUrl) {
                state.browserUrl?.let { url ->
                    val opened = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
                    runtime.browserHandled(url, opened)
                }
            }
            AccountSurface(state, runtime::navigate, runtime::passwordSignIn, runtime::requestRecovery,
                runtime::google, runtime::updatePassword, runtime::signOut, runtime::deleteAccount,
                runtime::restore, runtime::setAnalytics, runtime::reauthenticate, runtime::retryPendingLink, ::finish,
                runtime::importLocalData, runtime::deferLocalImport, ::setScreenshotProtection, ::openPrivacyPolicy)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("account_callback_delivered", callbackDelivered)
        outState.putBoolean("account_required_entry", requiredEntry)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        if (isFinishing) {
            // Back can finish this Activity while the exchange is still in flight,
            // before Compose displays its recovery ID. Queue cleanup behind that
            // exchange using only the strictly parsed callback's flow ID. Rotation
            // preserves the form; another/newer flow cannot be cancelled here.
            val ownedFlow = AuthPendingFlow.recoveryCleanupId(
                intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString, displayedRecoveryId)
            ownedFlow?.let(runtime::abandonRecovery)
        }
        super.onDestroy()
    }
    /** Opens the public privacy policy in the browser; nothing about the account is attached. */
    private fun openPrivacyPolicy(): Boolean = runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.integrated_settings_privacy_policy_url))))
    }.isSuccess
    private fun setScreenshotProtection(protected: Boolean) {
        if (protected) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun receive(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) intent.dataString?.let {
            callbackDelivered = true
            runtime.callback(it)
        }
    }
}

@Composable
internal fun AccountSurface(
    state: AccountScreenState,
    onNavigate: (AccountPage) -> Unit,
    onSignIn: (String, String, Boolean) -> Unit,
    onRecovery: (String) -> Unit,
    onGoogle: () -> Unit,
    onUpdatePassword: (String) -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onAnalytics: (Boolean) -> Unit,
    onReauthenticate: (String) -> Unit,
    onRetryLink: () -> Unit,
    onDone: () -> Unit,
    onImportLocal: (LocalDataImportOffer) -> Unit,
    onDeferLocal: (LocalDataImportOffer) -> Unit,
    onScreenshotProtection: (Boolean) -> Unit,
    onPrivacyPolicy: () -> Boolean = { false },
) {
    // Deliberately not rememberSaveable: passwords/drafts never enter Android backups/Bundles.
    var email by remember(state.page) { mutableStateOf("") }
    var password by remember(state.page) { mutableStateOf("") }
    var confirmPassword by remember(state.page) { mutableStateOf("") }
    var confirmImport by remember { mutableStateOf<LocalDataImportOffer?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmAnalytics by remember { mutableStateOf(false) }
    var emailFormRequested by remember { mutableStateOf(false) }
    var browserUnavailable by remember { mutableStateOf(false) }
    val authForm = state.page != AccountPage.ACCOUNT
    val showEmailForm = AccountEntryPresentation.showEmailForm(state.page, emailFormRequested)
    val showPasswordFields = AccountEntryPresentation.showPasswordFields(state.page, emailFormRequested)
    // Apply before drawing the committed composition; never depend on field focus
    // or whether a masked password happens to be empty.
    SideEffect { onScreenshotProtection(showPasswordFields) }
    MaterialTheme(colorScheme = if (authForm) darkColorScheme(primary = Color.White, onPrimary = Color.Black,
        background = Color(0xFF151515), surface = Color(0xFF252525), onSurface = Color.White)
        else lightColorScheme(primary = Color.Black, onPrimary = Color.White,
            background = Color(0xFFF2F2F7), surface = Color.White)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState())
                .padding(20.dp).testTag("account-main"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!authForm || state.reauthenticating) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDone) { Text("Done") }
                }
                if (authForm) Image(painterResource(R.drawable.justnoise_icon), contentDescription = null,
                    modifier = Modifier.padding(top = 24.dp).size(84.dp).clip(RoundedCornerShape(16.dp))
                        .align(Alignment.CenterHorizontally))
                Text(AccountEntryPresentation.title(state.page, emailFormRequested),
                    modifier = Modifier.fillMaxWidth(), textAlign = if (authForm) TextAlign.Center else TextAlign.Start,
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                if (!authForm) Text("Justnoise", style = MaterialTheme.typography.titleMedium)
                state.message?.let { Text(it, modifier = Modifier.testTag("account-message")
                    .semantics { liveRegion = LiveRegionMode.Polite }) }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.page == AccountPage.ACCOUNT) {
                    if (state.localImport.status == LocalDataImportStatus.AVAILABLE) Surface(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Bring your existing data", style = MaterialTheme.typography.titleLarge)
                            Text("This phone has ${state.localImport.modeCount} local modes, ${state.localImport.scheduleCount} schedules and ${state.localImport.sessionCount} completed sessions from before sign-in.")
                            Text("Move them into this account on this phone. Existing account data stays. Imported schedules stay off until you review and enable them. No cloud sync.")
                            Button(onClick = { confirmImport = state.localImport }, enabled = !state.busy && !state.deletionPending,
                                modifier = Modifier.fillMaxWidth().testTag("import-local-data")) { Text("Import local data") }
                            TextButton(onClick = { onDeferLocal(state.localImport) }, enabled = !state.busy && !state.deletionPending) {
                                Text("Not now — keep local data")
                            }
                        }
                    }
                    if (state.localImport.status == LocalDataImportStatus.ERROR || state.localImport.status == LocalDataImportStatus.PENDING)
                        Text("Your data transfer is not finished. Tap Try again. Your existing data is kept.")
                    Surface(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(state.email)
                            TextButton(onClick = onSignOut, enabled = !state.busy && !state.deletionPending) { Text("Sign out") }
                            TextButton(onClick = { onNavigate(AccountPage.REAUTHENTICATE) }, enabled = !state.busy) { Text("Confirm sign-in again") }
                            TextButton(onClick = { confirmDelete = true }, enabled = !state.busy) {
                                Text(if (state.deletionPending) "Retry account deletion" else "Delete account", color = Color(0xFFB3261E))
                            }
                            TextButton(onClick = { browserUnavailable = !onPrivacyPolicy() },
                                modifier = Modifier.testTag("account-privacy-policy")) {
                                Text(stringResource(R.string.integrated_settings_privacy))
                            }
                        }
                    }
                } else {
                    if (AccountEntryPresentation.showGoogle(state.page)) {
                        GoogleAccountButton(enabled = !state.busy, onClick = {
                            password = ""; confirmPassword = ""
                            onGoogle()
                        })
                        if (showEmailForm) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text("or use email", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                    }
                    if (!showEmailForm) {
                        OutlinedButton(onClick = { emailFormRequested = true }, enabled = !state.busy,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("account-use-email")) {
                            Text("Continue with email")
                        }
                    } else {
                        if (state.page != AccountPage.UPDATE_PASSWORD) OutlinedTextField(
                            value = if (state.reauthenticating) state.email else email,
                            onValueChange = { email = it }, label = { Text("Email") },
                            singleLine = true, enabled = !state.busy, readOnly = state.reauthenticating, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        if (showPasswordFields) {
                            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                            if (state.page in setOf(AccountPage.REGISTER, AccountPage.UPDATE_PASSWORD)) {
                                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it },
                                    label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true, enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                        val passwordValid = password.isNotEmpty() &&
                            (state.page in setOf(AccountPage.SIGN_IN, AccountPage.REAUTHENTICATE) ||
                                (password.length >= 8 && password == confirmPassword))
                        val effectiveEmail = if (state.reauthenticating) state.email else email
                        Button(colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(28.dp), onClick = {
                            val submitted = password
                            password = ""; confirmPassword = ""
                            when (state.page) {
                                AccountPage.RECOVERY -> onRecovery(effectiveEmail)
                                AccountPage.UPDATE_PASSWORD -> onUpdatePassword(submitted)
                                AccountPage.REAUTHENTICATE -> onReauthenticate(submitted)
                                else -> onSignIn(email, submitted, state.page == AccountPage.REGISTER)
                            }
                        }, enabled = !state.busy && (state.page == AccountPage.RECOVERY && effectiveEmail.isNotBlank() ||
                            state.page != AccountPage.RECOVERY && passwordValid &&
                            (state.page == AccountPage.UPDATE_PASSWORD || effectiveEmail.isNotBlank())),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("account-email-submit")) {
                            Text(when (state.page) {
                                AccountPage.REGISTER -> "Create account"
                                AccountPage.RECOVERY -> "Send recovery link"
                                AccountPage.UPDATE_PASSWORD -> "Save new password"
                                AccountPage.REAUTHENTICATE -> "Confirm sign-in"
                                else -> "Sign in"
                            })
                        }
                        if (state.page in setOf(AccountPage.SIGN_IN, AccountPage.REAUTHENTICATE)) {
                            if (!state.reauthenticating) TextButton(onClick = { onNavigate(AccountPage.REGISTER) }, enabled = !state.busy,
                                modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("New here? Create an account") }
                            TextButton(onClick = { onNavigate(AccountPage.RECOVERY) }, enabled = !state.busy) { Text("Forgot password?") }
                        }
                        if (state.page != AccountPage.SIGN_IN) TextButton(onClick = {
                            onNavigate(if (state.reauthenticating) AccountPage.ACCOUNT else AccountPage.SIGN_IN)
                        }, enabled = !state.busy) { Text(if (state.reauthenticating) "Back to account" else "Back to sign in") }
                    }
                }
                if (state.page == AccountPage.ACCOUNT) Surface(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Optional analytics", fontWeight = FontWeight.SemiBold)
                        Text("Share only session-start and session-end events with PostHog to help improve Justnoise. No account data, app selections, mode names or NFC contents. Analytics turns off when Android restarts the app. Your Zap works without analytics.")
                        Switch(checked = state.analyticsEnabled, enabled = !state.busy && !state.deletionPending,
                            onCheckedChange = { if (it) confirmAnalytics = true else onAnalytics(false) },
                            modifier = Modifier.testTag("analytics-consent")
                                .semantics { contentDescription = "Share optional session analytics" })
                    }
                }
                if (state.canRetryLink) TextButton(onClick = onRetryLink, enabled = !state.busy) { Text("Retry received sign-in link") }
                if (!authForm &&
                    (!state.message.isNullOrBlank() || state.localImport.status in
                        setOf(LocalDataImportStatus.ERROR, LocalDataImportStatus.PENDING))) {
                    TextButton(onClick = onRetry, enabled = !state.busy) { Text("Try again") }
                }
                if (authForm) TextButton(onClick = { browserUnavailable = !onPrivacyPolicy() },
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("account-entry-privacy-policy")) {
                    Text(stringResource(R.string.integrated_settings_privacy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (browserUnavailable) Text(stringResource(R.string.integrated_settings_browser_unavailable) + " " +
                    stringResource(R.string.integrated_settings_privacy_policy_url),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (!authForm) TextButton(onClick = onDone) { Text("Back to Justnoise") }
            }
        }
        confirmImport?.let { offer -> AlertDialog(onDismissRequest = { confirmImport = null },
            title = { Text("Import into this account?") },
            text = { Text("Move the existing local modes, app selections, schedules and history to ${state.email}. Existing account data will not be overwritten. Imported schedules stay off. These data will belong only to this account, not other accounts on this phone.") },
            confirmButton = { TextButton(enabled = !state.busy && !state.deletionPending && offer.token == state.localImport.token,
                onClick = { confirmImport = null; onImportLocal(offer) }) { Text("Import data") } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancel") } }) }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("This deletes your account on the server and then removes this account's modes, schedules and history from this phone. Local data, other accounts and Zap pairing are kept. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete account") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
        if (confirmAnalytics) AlertDialog(onDismissRequest = { confirmAnalytics = false },
            title = { Text("Allow optional analytics?") },
            text = { Text("Justnoise sends focus_session_started and focus_session_ended to our existing PostHog service in the US, using a random ID and no account profile. The service receives your network IP address. Turn this off here at any time; account changes and app-process restarts also turn it off. You can choose to enable it again here. Already delivered events cannot be recalled by this switch.") },
            confirmButton = { TextButton(onClick = { confirmAnalytics = false; onAnalytics(true) }) { Text("Allow analytics") } },
            dismissButton = { TextButton(onClick = { confirmAnalytics = false }) { Text("Not now") } })
    }
}

/** Official Google light/pill asset, scaled without changing its logo, font or aspect ratio. */
@Composable
private fun GoogleAccountButton(enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.google_signin_light), contentDescription = "Sign in with Google",
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(4.5f)
                .heightIn(min = 48.dp).alpha(if (enabled) 1f else 0.45f)
                .clip(RoundedCornerShape(50)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag("account-google"))
    }
}
