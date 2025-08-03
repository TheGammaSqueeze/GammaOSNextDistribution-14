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

package com.android.safetycenter.testing

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** A JUnit [TestRule] that performs setup and reset steps before and after Safety Center tests. */
class SafetyCenterTestRule(
    private val safetyCenterTestHelper: SafetyCenterTestHelper,
    private val withNotifications: Boolean = false
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                setup()
                try {
                    base.evaluate()
                } finally {
                    reset()
                }
            }
        }
    }

    private fun setup() {
        safetyCenterTestHelper.setup()
        if (withNotifications) {
            TestNotificationListener.setup(safetyCenterTestHelper.context)
        }
    }

    private fun reset() {
        safetyCenterTestHelper.reset()
        if (withNotifications) {
            // It is important to reset the notification listener last because it waits/ensures that
            // all notifications have been removed before returning.
            TestNotificationListener.reset(safetyCenterTestHelper.context)
        }
    }
}
