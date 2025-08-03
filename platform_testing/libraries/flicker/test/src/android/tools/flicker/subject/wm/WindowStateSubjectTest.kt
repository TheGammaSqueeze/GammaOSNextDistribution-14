/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.tools.flicker.subject.wm

import android.tools.Cache
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TestComponents
import android.tools.utils.getWmTraceReaderFromAsset
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

class WindowStateSubjectTest {
    @Before
    fun before() {
        Cache.clear()
    }

    @Test
    fun exceptionContainsDebugInfoImaginary() {
        val reader = getWmTraceReaderFromAsset("wm_trace_openchrome.pb", legacyTrace = true)
        val trace = reader.readWmTrace() ?: error("Unable to read WM trace")
        val foundWindow =
            WindowManagerTraceSubject(trace, reader)
                .first()
                .windowState(TestComponents.IMAGINARY.className)
        Truth.assertWithMessage("${TestComponents.IMAGINARY.className} is not found")
            .that(foundWindow)
            .isNull()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
