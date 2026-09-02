package com.justnoise.gate0.platform.restriction

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Insets
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.justnoise.gate0.R
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.domain.ShieldMessageRenderer

class OverlayController(
    private val service: AccessibilityService,
    private val onUnavailable: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlay: View? = null

    val isShowing: Boolean
        get() = overlay != null

    fun show(selectedAppDisplayName: String?) {
        if (overlay != null) {
            refreshBounds()
            return
        }
        val view = LayoutInflater.from(service).inflate(R.layout.overlay_blocked, null)
        view.findViewById<TextView>(R.id.overlayMessage).text = ShieldMessageRenderer.render(
            displayName = selectedAppDisplayName,
            fallbackName = ShieldMessageRenderer.APPLICATION_FALLBACK_NAME,
        )
        wireEscapeRoutes(view)
        view.setOnApplyWindowInsetsListener { _, insets ->
            refreshBounds()
            insets
        }

        try {
            val parameters = requireNotNull(layoutParameters())
            windowManager.addView(view, parameters)
            overlay = view
        } catch (_: Exception) {
            overlay = null
            onUnavailable()
        }
    }

    fun refreshBounds() {
        val current = overlay ?: return
        try {
            val parameters = requireNotNull(layoutParameters())
            val previous = current.layoutParams as WindowManager.LayoutParams
            if (previous.x == parameters.x && previous.y == parameters.y &&
                previous.width == parameters.width && previous.height == parameters.height
            ) return
            windowManager.updateViewLayout(current, parameters)
        } catch (_: Exception) {
            hide()
            onUnavailable()
        }
    }

    @SuppressLint("RtlHardcoded") // These are physical display coordinates, not text alignment.
    private fun layoutParameters(): WindowManager.LayoutParams? {
        // Accessibility overlays sit above navigation windows and may not receive their
        // insets. WindowMetrics uses display insets instead of this overlay's Z-order.
        val metrics = windowManager.currentWindowMetrics
        val insets = Insets.max(
            metrics.windowInsets.getInsets(WindowInsets.Type.systemBars()),
            metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()),
        )
        val bounds = metrics.bounds
        val frame = OverlayWindowBounds.calculate(
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            insets.left, insets.top, insets.right, insets.bottom,
        ) ?: return null
        return WindowManager.LayoutParams(
            frame.width,
            frame.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            // Pixel coordinates are physical, not locale-relative. Insets are already applied.
            gravity = Gravity.TOP or Gravity.LEFT
            x = frame.left
            y = frame.top
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Justnoise Gate 0 blocked surface"
        }
    }

    fun hide() {
        val current = overlay ?: return
        overlay = null
        try {
            windowManager.removeViewImmediate(current)
        } catch (_: Exception) {
            // A system-side removal can race with a foreground-package transition.
        }
    }

    private fun wireEscapeRoutes(view: View) {
        view.findViewById<Button>(R.id.openJustnoiseButton).setOnClickListener {
            openAfterHiding(Intent(service, MainActivity::class.java))
        }
        view.findViewById<Button>(R.id.disableServiceButton).setOnClickListener {
            openAfterHiding(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        view.findViewById<Button>(R.id.overlayAppInfoButton).setOnClickListener {
            openAfterHiding(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${service.packageName}"),
                ),
            )
        }
        view.findViewById<Button>(R.id.homeButton).setOnClickListener {
            openAfterHiding(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        }
    }

    private fun openAfterHiding(intent: Intent) {
        hide()
        try {
            service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // System Home and Settings remain available through hardware/system navigation.
        }
    }
}
