package com.android.healthconnect.controller.utils

import java.text.Normalizer
import java.util.Locale

/** Utility string normalizer. */
object NormalizeUtf8 {
    /** Normalizes via `Form.NFKC` and to lowercase. */
    fun normalizeForMatch(input: CharSequence?): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKC).lowercase(Locale.getDefault())
    }
}
