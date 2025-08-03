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

package android.tools.flicker.assertions

import android.tools.Tag
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.wm.WindowManagerStateSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import org.junit.Test

/** Tests for [AssertionDataFactoryTest] */
class AssertionDataFactoryTest : AssertionStateDataFactoryTest() {
    override val wmAssertionFactory: AssertionStateDataFactory
        get() =
            AssertionDataFactory(WindowManagerStateSubject::class, WindowManagerTraceSubject::class)
    override val layersAssertionFactory: AssertionStateDataFactory
        get() = AssertionDataFactory(LayerTraceEntrySubject::class, LayersTraceSubject::class)

    @Test
    fun checkBuildsTraceAssertion() {
        validate(
            (wmAssertionFactory as AssertionDataFactory).createTraceAssertion {}
                as AssertionDataImpl,
            WindowManagerTraceSubject::class,
            Tag.ALL
        )
        validate(
            (layersAssertionFactory as AssertionDataFactory).createTraceAssertion {}
                as AssertionDataImpl,
            LayersTraceSubject::class,
            Tag.ALL
        )
    }
}
