package com.justnoise.gate0.platform.restriction

/** Package-only app context; the framework window class is classified and discarded. */
object ForegroundWindowRules {
    const val SOFT_INPUT_WINDOW = "android.inputmethodservice.SoftInputWindow"

    fun observedPackage(
        previousPackage: String?,
        eventPackage: String?,
        eventClass: String?,
        currentInputMethodPackage: String?,
        eventPackageProtected: Boolean,
    ): String? {
        // Never ignore an entire keyboard package: its settings/activity windows
        // are real transitions. Safety surfaces also always replace the context.
        val keyboardWindow = !eventPackageProtected &&
            !eventPackage.isNullOrBlank() &&
            eventPackage == currentInputMethodPackage &&
            eventClass == SOFT_INPUT_WINDOW
        return if (keyboardWindow) previousPackage else eventPackage
    }
}
