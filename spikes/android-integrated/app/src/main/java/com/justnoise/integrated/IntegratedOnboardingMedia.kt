package com.justnoise.integrated

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.justnoise.gate0.R

/** Silent local media, never a timer or a representation of real session state. */
@Composable
internal fun IntegratedOnboardingMedia(
    videoResource: Int,
    isActive: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = (LocalActivity.current as? LifecycleOwner)?.lifecycle
    var resumed by remember(lifecycle) {
        mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true)
    }
    var animationsEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var pausedByUser by remember(videoResource) { mutableStateOf(false) }
    var unavailable by remember(videoResource) { mutableStateOf(false) }

    DisposableEffect(lifecycle, context) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME ||
                (event != Lifecycle.Event.ON_PAUSE &&
                    lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true)
            animationsEnabled = ValueAnimator.areAnimatorsEnabled()
        }
        val motionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animationsEnabled = ValueAnimator.areAnimatorsEnabled()
            }
        }
        lifecycle?.addObserver(lifecycleObserver)
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver,
        )
        onDispose {
            lifecycle?.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(motionObserver)
        }
    }

    val shouldPlay = isActive && resumed && animationsEnabled && !pausedByUser && !unavailable
    val playbackLabel = stringResource(
        if (pausedByUser) R.string.first_run_play_video else R.string.first_run_pause_video,
    )
    Box(
        modifier = modifier.background(Color.Black).semantics { contentDescription = description }
            .then(
                if (animationsEnabled && !unavailable) Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = playbackLabel,
                    onClick = { pausedByUser = !pausedByUser },
                ) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (unavailable) {
            Text(
                stringResource(R.string.first_run_video_unavailable),
                color = Color(0xFF98989D), fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            AndroidView(
                factory = { viewContext ->
                    FirstRunVideoView(viewContext, videoResource) { unavailable = true }
                },
                update = { it.setPlaybackRequested(shouldPlay) },
                onReset = null,
                onRelease = { it.release() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@SuppressLint("ViewConstructor") // Owned exclusively by the Compose AndroidView lifecycle.
private class FirstRunVideoView(
    context: Context,
    private val videoResource: Int,
    private val onUnavailable: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var playbackSurface: Surface? = null
    private var prepared = false
    private var playbackRequested = false
    private var sourceWidth = 0
    private var sourceHeight = 0

    init {
        isOpaque = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    fun setPlaybackRequested(play: Boolean) {
        playbackRequested = play
        synchronizePlayback()
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        release()
        runCatching {
            playbackSurface = Surface(texture)
            val newPlayer = MediaPlayer()
            player = newPlayer
            resources.openRawResourceFd(videoResource).use { descriptor ->
                requireNotNull(descriptor)
                newPlayer.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            newPlayer.apply {
                setSurface(playbackSurface)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, width, height ->
                    sourceWidth = width
                    sourceHeight = height
                    updateAspectFitTransform()
                }
                setOnPreparedListener {
                    if (player === it) {
                        prepared = true
                        // Render a still frame even when system motion is disabled.
                        it.seekTo(1L, MediaPlayer.SEEK_CLOSEST)
                        synchronizePlayback()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    failPlayback()
                    true // Keep setup usable; never show an uncancellable platform error dialog.
                }
                prepareAsync()
            }
        }.onFailure { failPlayback() }
    }

    private fun synchronizePlayback() {
        val current = player ?: return
        if (!prepared) return
        runCatching {
            if (playbackRequested && !current.isPlaying) current.start()
            else if (!playbackRequested && current.isPlaying) current.pause()
        }.onFailure { failPlayback() }
    }

    private fun failPlayback() {
        release()
        onUnavailable()
    }

    fun release() {
        prepared = false
        player?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            setOnVideoSizeChangedListener(null)
            release()
        }
        player = null
        playbackSurface?.release()
        playbackSurface = null
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        updateAspectFitTransform()
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateAspectFitTransform()
    }

    private fun updateAspectFitTransform() {
        if (width == 0 || height == 0 || sourceWidth == 0 || sourceHeight == 0) return
        val viewAspect = width.toFloat() / height
        val videoAspect = sourceWidth.toFloat() / sourceHeight
        setTransform(Matrix().apply {
            setScale(
                if (videoAspect < viewAspect) videoAspect / viewAspect else 1f,
                if (videoAspect > viewAspect) viewAspect / videoAspect else 1f,
                width / 2f, height / 2f,
            )
        })
    }
}
