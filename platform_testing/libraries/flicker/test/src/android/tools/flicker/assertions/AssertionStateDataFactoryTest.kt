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
import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.wm.WindowManagerStateSubject
import android.tools.utils.CleanFlickerEnvironmentRule
import com.google.common.truth.Truth
import kotlin.reflect.KClass
import org.junit.ClassRule
import org.junit.Test

/** Tests for [AssertionStateDataFactory] */
open class AssertionStateDataFactoryTest {
    protected open val wmAssertionFactory
        get() = AssertionStateDataFactory(WindowManagerStateSubject::class)
    protected open val layersAssertionFactory
        get() = AssertionStateDataFactory(LayerTraceEntrySubject::class)
    protected open val eventLogAssertionFactory
        get() = AssertionStateDataFactory(EventLogSubject::class)

    @Test
    open fun checkBuildsStartAssertion() {
        validate(
            wmAssertionFactory.createStartStateAssertion {},
            WindowManagerStateSubject::class,
            Tag.START
        )
        validate(
            layersAssertionFactory.createStartStateAssertion {},
            LayerTraceEntrySubject::class,
            Tag.START
        )
        validate(
            eventLogAssertionFactory.createStartStateAssertion {},
            EventLogSubject::class,
            Tag.START
        )
    }

    @Test
    open fun checkBuildsEndAssertion() {
        validate(
            wmAssertionFactory.createEndStateAssertion {},
            WindowManagerStateSubject::class,
            Tag.END
        )
        validate(
            layersAssertionFactory.createEndStateAssertion {},
            LayerTraceEntrySubject::class,
            Tag.END
        )
        validate(
            eventLogAssertionFactory.createEndStateAssertion {},
            EventLogSubject::class,
            Tag.END
        )
    }

    @Test
    open fun checkBuildsTagAssertion() {
        validate(
            wmAssertionFactory.createTagAssertion(TAG) {},
            WindowManagerStateSubject::class,
            TAG
        )
        validate(
            layersAssertionFactory.createTagAssertion(TAG) {},
            LayerTraceEntrySubject::class,
            TAG
        )
        validate(eventLogAssertionFactory.createTagAssertion(TAG) {}, EventLogSubject::class, TAG)
    }

    protected fun validate(
        assertionData: AssertionDataImpl,
        expectedSubject: KClass<*>,
        expectedTag: String
    ) {
        Truth.assertWithMessage("Subject")
            .that(assertionData.expectedSubjectClass)
            .isEqualTo(expectedSubject)
        Truth.assertWithMessage("Tag").that(assertionData.tag).isEqualTo(expectedTag)
    }

    companion object {
        internal const val TAG = "tag"
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
