package android.platform.uiautomator_helpers

import android.os.Trace
import android.util.Log

/** Tracing utils until androidx tracing library is updated in the tree. */
@Deprecated("Use com.android.app.tracing utils instead.")
object TracingUtils {

    // from frameworks/base/core/java/android/os/Trace.java MAX_SECTION_NAME_LEN.
    private const val MAX_TRACE_NAME_LEN = 127
    private const val TAG = "TracingUtils"

    @Deprecated(
        "Use com.android.app.tracing.traceSection instead",
        replaceWith = ReplaceWith("com.android.app.tracing.traceSection(sectionName, block)")
    )
    inline fun <T> trace(sectionName: String, block: () -> T): T {
        Trace.beginSection(sectionName.shortenedIfNeeded())
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    /** Shortens the section name if it's too long. */
    fun beginSectionSafe(sectionName: String) {
        Trace.beginSection(sectionName.shortenedIfNeeded())
    }

    /** Shorten the length of a string to make it less than the limit for atraces. */
    fun String.shortenedIfNeeded(): String =
        if (length > MAX_TRACE_NAME_LEN) {
            Log.w(TAG, "Section name too long: \"$this\" (len=$length, max=$MAX_TRACE_NAME_LEN)")
            substring(0, MAX_TRACE_NAME_LEN)
        } else this
}
