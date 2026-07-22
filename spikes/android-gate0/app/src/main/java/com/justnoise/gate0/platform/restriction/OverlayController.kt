package com.justnoise.gate0.platform.restriction

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.justnoise.gate0.R
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.domain.ShieldMessageRenderer

class OverlayController(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlay: View? = null

    val isShowing: Boolean
        get() = overlay != null

    fun show(selectedAppDisplayName: String?) {
        if (overlay != null) return
        val view = LayoutInflater.from(service).inflate(R.layout.overlay_blocked, null)
        view.findViewById<TextView>(R.id.overlayMessage).text = ShieldMessageRenderer.render(
            displayName = selectedAppDisplayName,
            fallbackName = ShieldMessageRenderer.APPLICATION_FALLBACK_NAME,
        )
        wireEscapeRoutes(view)
        val parameters = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Justnoise Gate 0 blocked surface"
        }

        try {
            windowManager.addView(view, parameters)
            overlay = view
        } catch (_: Exception) {
            overlay = null
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
