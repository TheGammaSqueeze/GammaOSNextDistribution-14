/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.tools.flicker.junit

import android.app.Instrumentation
import android.tools.ScenarioBuilder
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

object TestUtils {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val VALID_ARGS_EMPTY = LegacyFlickerTest()

    var executionCount = 0

    class DummyTestClassValid(test: LegacyFlickerTest) {
        @FlickerBuilderProvider
        fun myMethod(): FlickerBuilder =
            FlickerBuilder(instrumentation).apply { transitions { executionCount++ } }

        @Test fun dummyExecute() {}

        companion object {
            val SCENARIO = ScenarioBuilder().forClass(DummyTestClassValid::class.java.name).build()
        }
    }

    class DummyTestClassEmpty

    class DummyTestClassMultipleProvider {
        @FlickerBuilderProvider fun myMethod(): FlickerBuilder = FlickerBuilder(instrumentation)

        @FlickerBuilderProvider
        fun mySecondMethod(): FlickerBuilder = FlickerBuilder(instrumentation)
    }

    class DummyTestClassProviderPrivateVoid {
        @FlickerBuilderProvider private fun myMethod() {}
    }

    class DummyTestClassProviderStatic {
        companion object {
            @FlickerBuilderProvider
            @JvmStatic
            fun myMethod(): FlickerBuilder = FlickerBuilder(instrumentation)
        }
    }
}
