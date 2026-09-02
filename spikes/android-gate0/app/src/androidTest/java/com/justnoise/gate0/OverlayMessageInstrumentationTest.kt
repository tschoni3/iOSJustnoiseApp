package com.justnoise.gate0

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.domain.ShieldMessageRenderer
import com.justnoise.gate0.platform.restriction.OverlayController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayMessageInstrumentationTest {
    @Test
    fun selectedAppChangeUpdatesExistingMessageWithoutReplacingWindowOrRebindingSameText() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var unavailable = false
                val controller = OverlayController(TestAccessibilityService(activity)) {
                    unavailable = true
                }
                val view = LayoutInflater.from(activity).inflate(R.layout.overlay_blocked, null)
                val label = view.findViewById<TextView>(R.id.overlayMessage)
                label.text = messageFor("First app")

                // Exercise the existing-view path without enabling a service or attaching an
                // accessibility window. Matching bounds make refreshBounds a genuine no-op.
                val boundsMethod = OverlayController::class.java.getDeclaredMethod("layoutParameters")
                    .apply { isAccessible = true }
                view.layoutParams = boundsMethod.invoke(controller) as WindowManager.LayoutParams
                val overlayField = OverlayController::class.java.getDeclaredField("overlay")
                    .apply { isAccessible = true }
                overlayField.set(controller, view)

                var textChanges = 0
                label.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                        textChanges += 1
                    }
                    override fun afterTextChanged(text: Editable?) = Unit
                })

                controller.show("Second app")

                assertEquals(messageFor("Second app"), label.text.toString())
                assertEquals(1, textChanges)
                assertSame(view, overlayField.get(controller))
                assertTrue(controller.isShowing)
                assertFalse(view.isAttachedToWindow)
                assertFalse(unavailable)

                controller.show("Second app")

                assertEquals("Repeated events must not rebind unchanged text", 1, textChanges)
                assertSame(view, overlayField.get(controller))
                assertFalse(view.isAttachedToWindow)
                assertFalse(unavailable)
            }
        }
    }

    private fun messageFor(name: String) = ShieldMessageRenderer.render(
        displayName = name,
        fallbackName = ShieldMessageRenderer.APPLICATION_FALLBACK_NAME,
    )

    private class TestAccessibilityService(private val context: Context) : AccessibilityService() {
        init {
            attachBaseContext(context)
        }

        override fun getSystemService(name: String): Any? = context.getSystemService(name)

        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

        override fun onInterrupt() = Unit
    }
}
