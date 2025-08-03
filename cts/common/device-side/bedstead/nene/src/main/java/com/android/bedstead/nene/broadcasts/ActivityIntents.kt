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

package com.android.bedstead.nene.broadcasts

import android.os.Build.VERSION.SDK_INT
import android.util.Log
import com.android.bedstead.nene.annotations.Experimental
import com.android.bedstead.nene.exceptions.AdbParseException
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.Versions
import java.io.IOException

/**
 * Test Apis related to Activity Intents.
 */
object ActivityIntents {
    private const val LOG_TAG = "BedsteadActivityIntents"
    private const val MAX_WAIT_UNTIL_ATTEMPTS = 60
    private const val WAIT_UNTIL_DELAY_MILLIS = 1000L

    @Experimental
    /**
     * Runs dumpsys and parses its contents in a loop. Blocks for [timeoutMs] until desired activity
     * is identified, returning the [intent] on success, and NULL when fails to intercept activity
     *
     * @param timeoutMs the period it waits for dumpsys results
     * @property action string of action to intercepted, such as "android.intent.action.VIEW"
     * @property pkg string of package to be intercepted, such as "com.google.android.gms"
     * @return AdbDumpsysActivities.Intent on intercepted activity success, NULL when times out
     */
    fun waitForActivity(timeoutMs: Int, action: String, pkg: String): AdbDumpsysActivities.Intent? {
        Versions.requireMinimumVersion(Versions.T)
        try {
            val parser = AdbActivitiesParser.get(SDK_INT)
            var intentFound: AdbDumpsysActivities.Intent? = null

            Log.i(LOG_TAG, "Calling dumpsys")
            ShellCommand.builder("dumpsys")
                .addOperand("activity")
                .addOperand("activities")
                .validate { commandOutput ->
                    val intents = parser.parseDumpsysActivities(commandOutput)
                    intents.forEach { intent ->
                        if ((intent.action == action) && (intent.pkg == pkg)) {
                            Log.d(
                                LOG_TAG, "Intent captured. " +
                                        "Action:${intent.action} " +
                                        "Data:${intent.data} " +
                                        "Flags:${intent.flags} " +
                                        "Package:${intent.pkg} " +
                                        "Cmp:${intent.cmp}"
                            )
                            intentFound = intent
                        }
                    }
                    intentFound != null
                }
                .executeUntilValid(MAX_WAIT_UNTIL_ATTEMPTS, WAIT_UNTIL_DELAY_MILLIS);

            return intentFound
        } catch (e: AdbParseException) {
            Log.e(LOG_TAG, "Could not parse activity intent: ${e.message}")
        } catch (e: IOException) {
            // Handle potential IO errors during command execution
            Log.e(LOG_TAG, "Error executing command: ${e.message}")
        }

        return null
    }
}