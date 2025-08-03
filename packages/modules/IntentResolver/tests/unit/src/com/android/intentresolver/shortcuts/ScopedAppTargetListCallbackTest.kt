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

package com.android.intentresolver.shortcuts

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScopedAppTargetListCallbackTest {

    @Test
    fun test_consumerInvocations_onlyInvokedWhileScopeIsActive() {
        val scope = TestScope(UnconfinedTestDispatcher())
        var counter = 0
        val testSubject = ScopedAppTargetListCallback(scope) { counter++ }.toConsumer()

        testSubject.accept(ArrayList())

        assertThat(counter).isEqualTo(1)

        scope.cancel()
        testSubject.accept(ArrayList())

        assertThat(counter).isEqualTo(1)
    }

    @Test
    fun test_appPredictorCallbackInvocations_onlyInvokedWhileScopeIsActive() {
        val scope = TestScope(UnconfinedTestDispatcher())
        var counter = 0
        val testSubject = ScopedAppTargetListCallback(scope) { counter++ }.toAppPredictorCallback()

        testSubject.onTargetsAvailable(ArrayList())

        assertThat(counter).isEqualTo(1)

        scope.cancel()
        testSubject.onTargetsAvailable(ArrayList())

        assertThat(counter).isEqualTo(1)
    }

    @Test
    fun test_createdWithClosedScope_noCallbackInvocations() {
        val scope = TestScope(UnconfinedTestDispatcher()).apply { cancel() }
        var counter = 0
        val testSubject = ScopedAppTargetListCallback(scope) { counter++ }.toConsumer()

        testSubject.accept(ArrayList())

        assertThat(counter).isEqualTo(0)
    }
}
