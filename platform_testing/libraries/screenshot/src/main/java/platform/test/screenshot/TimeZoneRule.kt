/*
 * Copyright 2022 The Android Open Source Project
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

package platform.test.screenshot

import java.util.TimeZone
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A rule to change the system colors using the given [seedColorHex].
 *
 * This is especially useful to change the colors before starting an activity using an
 * [ActivityScenarioRule] or any other rule.
 */
class TimeZoneRule(private val tz: String = "GMT") : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                lateinit var previousTZ: TimeZone
                try {
                    // Save the current value of tz, to be restored later
                    previousTZ = TimeZone.getDefault()
                    TimeZone.setDefault(TimeZone.getTimeZone(tz))
                    base.evaluate()
                } finally {
                    // Restore the previous timezone.
                    TimeZone.setDefault(previousTZ)
                }
            }
        }
    }
}
