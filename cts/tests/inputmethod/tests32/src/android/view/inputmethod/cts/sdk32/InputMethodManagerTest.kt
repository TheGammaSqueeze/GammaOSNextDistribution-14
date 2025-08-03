/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view.inputmethod.cts.sdk32

import android.content.Context
import android.view.inputmethod.cts.util.EndToEndImeTestBase
import android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible
import android.view.inputmethod.cts.util.MockTestActivityUtil
import android.view.inputmethod.cts.util.MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS
import android.view.inputmethod.InputMethodManager
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher
import com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent
import com.android.cts.mockime.ImeSettings
import com.android.cts.mockime.MockImeSession
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.AssumptionViolatedException
import org.junit.runner.RunWith
import org.junit.Test

@MediumTest
@RunWith(AndroidJUnit4::class)
class InputMethodManagerTest : EndToEndImeTestBase() {

    val context: Context = InstrumentationRegistry.getInstrumentation().getContext()
    val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()

    @Test
    fun getInputMethodWindowVisibleHeight_returnsZeroIfNotFocused() {
        val marker = createUniqueMarker()
        val imm = context.getSystemService(InputMethodManager::class.java)!!
        MockImeSession.create(context, uiAutomation, ImeSettings.Builder()).use { session ->
            val stream = session.openEventStream()
            MockTestActivityUtil.launchSync(
                    context.getPackageManager().isInstantApp(), TIMEOUT,
                    mapOf(EXTRA_KEY_PRIVATE_IME_OPTIONS to marker)
            )
                .use {
                    expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT)
                    session.callRequestShowSelf(0)
                    expectImeVisible(TIMEOUT)
                    assertEquals(
                            "Only IME target UID may observe the visible height of the IME",
                            0,
                            imm.reflectivelyGetInputMethodWindowVisibleHeight()
                    )
                }
        }
    }

    fun InputMethodManager.reflectivelyGetInputMethodWindowVisibleHeight() =
        try {
            InputMethodManager::class.java
                    .getMethod("getInputMethodWindowVisibleHeight")
                    .invoke(this) as Int
        } catch (_: NoSuchMethodError) {
            throw AssumptionViolatedException("getInputMethodWindowVisibleHeight doesn't exist")
        }

    companion object {
        val TIMEOUT = TimeUnit.SECONDS.toMillis(5)
    }
}
