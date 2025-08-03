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

package android.permission.cts

import android.content.ComponentName
import android.content.Context
import com.android.compatibility.common.util.SystemUtil
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Rule that enables and disables the CTS NotificationListenerService */
class CtsNotificationListenerHelperRule(context: Context) : TestRule {

    private val notificationListenerComponentName =
        ComponentName(context, CtsNotificationListenerService::class.java)

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    // Allow NLS used to verify notifications sent
                    SystemUtil.runShellCommand(
                        ALLOW_NLS_COMMAND + notificationListenerComponentName.flattenToString()
                    )

                    base.evaluate()
                } finally {
                    // Disallow NLS used to verify notifications sent
                    SystemUtil.runShellCommand(
                        DISALLOW_NLS_COMMAND + notificationListenerComponentName.flattenToString()
                    )
                }
            }
        }
    }

    companion object {
        private const val ALLOW_NLS_COMMAND = "cmd notification allow_listener "
        private const val DISALLOW_NLS_COMMAND = "cmd notification disallow_listener "
    }
}
