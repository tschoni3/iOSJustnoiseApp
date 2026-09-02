package com.justnoise.uiprototype

import com.justnoise.androidcontract.JustnoiseContract

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat

private val LaunchDark = JustnoiseContract.color("surface.zap.idle")

@Composable
fun LaunchFlowScreen(
    state: PrototypeFlowState,
    onAction: (PrototypeFlowAction) -> Unit,
) {
    val usesLightSurface = state.route == PrototypeRoute.Activation
    val activity = LocalActivity.current
    LaunchedEffect(state.route) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = usesLightSurface
                isAppearanceLightNavigationBars = usesLightSurface
            }
        }
    }

    when (state.route) {
        PrototypeRoute.Onboarding -> OnboardingScreen(
            page = state.onboardingPage,
            onShowPage = { onAction(PrototypeFlowAction.ShowOnboardingPage(it)) },
            onContinue = { onAction(PrototypeFlowAction.AdvanceOnboarding) },
        )
        PrototypeRoute.Activation -> ActivationScreen(
            isConnected = false,
            onBack = { onAction(PrototypeFlowAction.Back) },
            onActivatePreview = {
                onAction(PrototypeFlowAction.CompleteActivationPreview)
            },
        )
        PrototypeRoute.SignIn -> AuthScreen(
            isSignUp = false,
            onSwitchMode = { onAction(PrototypeFlowAction.OpenSignUp) },
            onContinuePreview = { onAction(PrototypeFlowAction.PreviewAuthenticated) },
        )
        PrototypeRoute.SignUp -> AuthScreen(
            isSignUp = true,
            onSwitchMode = { onAction(PrototypeFlowAction.OpenSignIn) },
            onContinuePreview = { onAction(PrototypeFlowAction.PreviewAuthenticated) },
        )
        PrototypeRoute.Zap,
        PrototypeRoute.Modes,
        PrototypeRoute.History,
        PrototypeRoute.Schedules,
        PrototypeRoute.Settings,
        -> Unit
    }
}

@Composable
private fun OnboardingScreen(
    page: Int,
    onShowPage: (Int) -> Unit,
    onContinue: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = page, pageCount = { 2 })

    LaunchedEffect(page) {
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != page) onShowPage(pagerState.currentPage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LaunchTitleBar(title = "How to Use", foreground = Color.White)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { pageIndex ->
            when (pageIndex) {
                0 -> OnboardingPage(
                    videoResource = R.raw.jn_video1,
                    isVideoActive = pageIndex == pagerState.currentPage &&
                        !pagerState.isScrollInProgress,
                    title = "Select Mode",
                    description = "Press the Customize Apps button to choose which apps stay unblocked.",
                )
                else -> OnboardingPage(
                    videoResource = R.raw.jn_video2,
                    isVideoActive = pageIndex == pagerState.currentPage &&
                        !pagerState.isScrollInProgress,
                    title = "Stay Focused",
                    description = "Get in the zone—distractions off, focus mode on.",
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(10.dp)
                        .background(
                            color = if (page == index) Color.White else Color.Gray.copy(alpha = 0.5f),
                            shape = CircleShape,
                        ),
                )
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .padding(bottom = 20.dp)
                .height(54.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        ) {
            Text(
                text = if (page == 0) "Get Started" else "Start Activating",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        PreviewMarker(foreground = Color.White)
    }
}

@Composable
private fun OnboardingPage(
    videoResource: Int,
    isVideoActive: Boolean,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(30.dp))
        OnboardingVideo(
            videoResource = videoResource,
            isActive = isVideoActive,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(250.dp)
                .shadow(5.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = description,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 17.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 30.dp),
        )
    }
}

@Composable
private fun OnboardingVideo(
    videoResource: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current || !isActive) {
        Box(
            modifier = modifier.background(Color(0xFF252525)),
            contentAlignment = Alignment.Center,
        ) {
            if (LocalInspectionMode.current) {
                Text(
                    text = "Onboarding video",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 14.sp,
                )
            }
        }
        return
    }

    var videoView by remember { mutableStateOf<LoopingMutedTextureView?>(null) }
    DisposableEffect(videoResource) {
        onDispose {
            videoView?.release()
            videoView = null
        }
    }
    AndroidView(
        factory = { viewContext ->
            LoopingMutedTextureView(viewContext, videoResource).apply {
                videoView = this
            }
        },
        modifier = modifier,
    )
}

@SuppressLint("ViewConstructor") // Created only by the Compose AndroidView factory.
private class LoopingMutedTextureView(
    context: Context,
    private val videoResource: Int,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var playbackSurface: Surface? = null
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        val surface = Surface(surfaceTexture)
        playbackSurface = surface
        val descriptor = resources.openRawResourceFd(videoResource)
        player = MediaPlayer().apply {
            setDataSource(
                descriptor.fileDescriptor,
                descriptor.startOffset,
                descriptor.length,
            )
            descriptor.close()
            setSurface(surface)
            isLooping = true
            setVolume(0f, 0f)
            setOnVideoSizeChangedListener { _, sourceWidth, sourceHeight ->
                this@LoopingMutedTextureView.videoWidth = sourceWidth
                this@LoopingMutedTextureView.videoHeight = sourceHeight
                updateAspectFitTransform()
            }
            setOnPreparedListener { it.start() }
            prepareAsync()
        }
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        updateAspectFitTransform()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateAspectFitTransform()
    }

    fun release() {
        player?.release()
        player = null
        playbackSurface?.release()
        playbackSurface = null
    }

    private fun updateAspectFitTransform() {
        if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return
        val viewAspect = width.toFloat() / height.toFloat()
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val scaleX = if (videoAspect < viewAspect) videoAspect / viewAspect else 1f
        val scaleY = if (videoAspect > viewAspect) viewAspect / videoAspect else 1f
        setTransform(
            Matrix().apply {
                setScale(scaleX, scaleY, width / 2f, height / 2f)
            },
        )
    }
}

@Composable
private fun ActivationScreen(
    isConnected: Boolean,
    onBack: () -> Unit,
    onActivatePreview: () -> Unit,
) {
    val background = if (isConnected) Color.Black else Color.White
    val foreground = if (isConnected) Color.White else Color.Black
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LaunchTitleBar(
            title = "Activate Zap",
            foreground = foreground,
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.zap_button),
                contentDescription = "Zap button",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(240.dp)
                    .padding(10.dp)
                    .clickable(enabled = !isConnected, onClick = onActivatePreview),
            )
            Spacer(modifier = Modifier.height(40.dp))
            if (!isConnected) {
                Text(
                    text = "Tap the button on your screen to start.",
                    color = foreground,
                    fontSize = 28.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Then hold your Zap near your phone to connect.",
                    color = foreground.copy(alpha = 0.72f),
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!isConnected) {
                Text(
                    text = "Don't have a Zap? Purchase one here.",
                    color = foreground,
                    fontSize = 14.sp,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "UI preview only · NFC is not running",
                    color = foreground.copy(alpha = 0.38f),
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthScreen(
    isSignUp: Boolean,
    onSwitchMode: () -> Unit,
    onContinuePreview: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var showForgotPassword by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunchDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Image(
                painter = painterResource(R.drawable.justnoise_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isSignUp) {
                    "Create a Justnoise account"
                } else {
                    "Log in to Justnoise"
                },
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isSignUp) {
                        "Already have an account?"
                    } else {
                        "Don't have an account?"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                )
                TextButton(onClick = onSwitchMode) {
                    Text(
                        text = if (isSignUp) "Log in." else "Sign up.",
                        color = Color(0xFF4A9EFF),
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            AuthTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "Email",
                keyboardType = KeyboardType.Email,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AuthTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                isPassword = true,
            )
            TextButton(onClick = { showForgotPassword = true }) {
                Text(
                    text = "Forgot password?",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        message = "Enter an email and password to preview."
                    } else {
                        onContinuePreview()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(49.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isSignUp) "Create account" else "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (message != null) {
                Text(
                    text = checkNotNull(message),
                    color = Color(0xFFFF7B73),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            AuthDivider()
            Surface(
                onClick = onContinuePreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                color = Color.White,
                contentColor = Color.Black,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "G", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = if (isSignUp) "Sign up with Google" else "Login with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "UI preview only · no authentication request is sent",
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(42.dp))
            Text(
                text = if (isSignUp) {
                    "By signing up, you agree to our terms of use, and privacy policy."
                } else {
                    "By signing in, you agree to our terms of use, and privacy policy."
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 34.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        if (showForgotPassword) {
            ForgotPasswordSheet(
                initialEmail = email,
                onDismiss = { showForgotPassword = false },
            )
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(text = placeholder, color = Color.White.copy(alpha = 0.48f))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
            cursorColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun AuthDivider() {
    Row(
        modifier = Modifier.padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.3f)),
        )
        Text(
            text = "or",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.3f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgotPasswordSheet(
    initialEmail: String,
    onDismiss: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F7F7),
        contentColor = Color.Black,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Black.copy(alpha = 0.22f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Reset your password",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(20.dp))
            TextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    status = if (email.isBlank()) {
                        "Please enter your email address."
                    } else {
                        "Preview only—no reset email was sent."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                ),
            ) {
                Text("Send Reset Email", fontWeight = FontWeight.SemiBold)
            }
            if (status != null) {
                Text(
                    text = checkNotNull(status),
                    color = Color.Black.copy(alpha = 0.58f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun LaunchTitleBar(
    title: String,
    foreground: Color,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp),
    ) {
        if (onBack != null) {
            Text(
                text = "‹",
                color = foreground,
                fontSize = 40.sp,
                lineHeight = 40.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp),
            )
        }
        Text(
            text = title,
            color = foreground,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun PreviewMarker(foreground: Color) {
    Text(
        text = "UI PREVIEW",
        color = foreground.copy(alpha = 0.28f),
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}

@Preview(name = "Onboarding · Select Mode", widthDp = 412, heightDp = 915)
@Composable
private fun OnboardingFirstPreview() {
    JustnoiseTheme {
        OnboardingScreen(page = 0, onShowPage = {}, onContinue = {})
    }
}

@Preview(name = "Onboarding · Stay Focused", widthDp = 412, heightDp = 915)
@Composable
private fun OnboardingSecondPreview() {
    JustnoiseTheme {
        OnboardingScreen(page = 1, onShowPage = {}, onContinue = {})
    }
}

@Preview(name = "Zap activation", widthDp = 412, heightDp = 915)
@Composable
private fun ActivationPreview() {
    JustnoiseTheme {
        ActivationScreen(
            isConnected = false,
            onBack = {},
            onActivatePreview = {},
        )
    }
}

@Preview(name = "Sign in", widthDp = 412, heightDp = 915)
@Composable
private fun SignInPreview() {
    JustnoiseTheme {
        AuthScreen(
            isSignUp = false,
            onSwitchMode = {},
            onContinuePreview = {},
        )
    }
}

@Preview(name = "Sign up", widthDp = 412, heightDp = 915)
@Composable
private fun SignUpPreview() {
    JustnoiseTheme {
        AuthScreen(
            isSignUp = true,
            onSwitchMode = {},
            onContinuePreview = {},
        )
    }
}
