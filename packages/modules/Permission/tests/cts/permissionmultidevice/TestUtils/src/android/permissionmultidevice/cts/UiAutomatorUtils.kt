package android.permissionmultidevice.cts

import android.os.SystemClock
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.google.common.truth.Truth

object UiAutomatorUtils {
    fun waitFindObject(selector: BySelector): UiObject2 {
        return findObjectWithRetry({ t -> UiAutomatorUtils2.waitFindObject(selector, t) })!!
    }

    fun waitFindObject(selector: BySelector, timeoutMillis: Long): UiObject2 {
        return findObjectWithRetry(
            { t -> UiAutomatorUtils2.waitFindObject(selector, t) },
            timeoutMillis
        )!!
    }

    fun click(selector: BySelector, timeoutMillis: Long = 20_000) {
        waitFindObject(selector, timeoutMillis).click()
    }

    fun findTextForView(selector: BySelector): String {
        val timeoutMs = 10000L

        var exception: Exception? = null
        var view: UiObject2? = null
        try {
            view = waitFindObject(selector, timeoutMs)
        } catch (e: Exception) {
            exception = e
        }
        Truth.assertThat(exception).isNull()
        Truth.assertThat(view).isNotNull()
        return view!!.text
    }

    private fun findObjectWithRetry(
        automatorMethod: (timeoutMillis: Long) -> UiObject2?,
        timeoutMillis: Long = 20_000L
    ): UiObject2? {
        val startTime = SystemClock.elapsedRealtime()
        return try {
            automatorMethod(timeoutMillis)
        } catch (e: StaleObjectException) {
            val remainingTime = timeoutMillis - (SystemClock.elapsedRealtime() - startTime)
            if (remainingTime <= 0) {
                throw e
            }
            automatorMethod(remainingTime)
        }
    }
}
