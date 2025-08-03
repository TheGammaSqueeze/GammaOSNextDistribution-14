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

import android.content.Context
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit [TestRule] for on-device tests that requires Safety Center to be supported. This rule does
 * not require Safety Center to be enabled.
 *
 * For tests which should only run on devices where Safety Center is not supported, instantiate with
 * [requireSupportIs] set to `false` to invert the condition.
 */
class SupportsSafetyCenterRule(private val context: Context, requireSupportIs: Boolean = true) :
    TestRule {

    private val shouldSupportSafetyCenter: Boolean = requireSupportIs

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val support = context.deviceSupportsSafetyCenter()
                if (shouldSupportSafetyCenter) {
                    assumeTrue("Test device does not support Safety Center", support)
                } else {
                    assumeFalse("Test device supports Safety Center", support)
                }
                base.evaluate()
            }
        }
    }
}
