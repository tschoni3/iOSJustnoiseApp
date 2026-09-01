package com.justnoise.gate0.platform.nfc

object DirectNfcDispatchRules {
    private const val ACTION_NDEF_DISCOVERED = "android.nfc.action.NDEF_DISCOVERED"
    private const val MIME_TEXT_PLAIN = "text/plain"

    fun accepts(action: String?, mimeType: String?, hasLiveTag: Boolean): Boolean =
        action == ACTION_NDEF_DISCOVERED &&
            mimeType == MIME_TEXT_PLAIN &&
            hasLiveTag
}
