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

package android.safetycenter.hostside.rules

import android.cts.statsdatom.lib.DeviceUtils
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit rule for host side tests that installs a helper app with the given [apkName] and
 * [packageName].
 */
class HelperAppRule(
    private val hostTestClass: BaseHostJUnit4Test,
    private val apkName: String,
    private val packageName: String
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    DeviceUtils.installTestApp(
                        hostTestClass.device,
                        apkName,
                        packageName,
                        hostTestClass.build
                    )
                    base.evaluate()
                } finally {
                    DeviceUtils.uninstallTestApp(hostTestClass.device, packageName)
                }
            }
        }
    }

    /** Runs the specified test in the helper app. */
    fun runTest(testClassName: String, testMethodName: String) {
        DeviceUtils.runDeviceTests(hostTestClass.device, packageName, testClassName, testMethodName)
    }
}
