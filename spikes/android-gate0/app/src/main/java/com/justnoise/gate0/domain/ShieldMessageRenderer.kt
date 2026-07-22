package com.justnoise.gate0.domain

object ShieldMessageRenderer {
    const val APPLICATION_FALLBACK_NAME = "This app"
    const val WEBSITE_FALLBACK_NAME = "This website"

    fun render(displayName: String?, fallbackName: String): String {
        val effectiveName = displayName?.trim().takeUnless { it.isNullOrEmpty() } ?: fallbackName
        return "$effectiveName is currently Zapped.\nTap your Zap to access it."
    }
}
