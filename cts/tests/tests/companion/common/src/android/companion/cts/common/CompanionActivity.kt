/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.companion.cts.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * An [Activity] for launching confirmation UI via an [android.content.IntentSender.sendIntent] and
 * receiving result in [onActivityResult].
 */
class CompanionActivity : Activity() {
    private var result: Pair<Int, Intent?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "$this.onCreate()")
        super.onCreate(savedInstanceState)
        unsafeInstance = this
    }

    override fun onDestroy() {
        Log.d(TAG, "$this.onDestroy()")
        unsafeInstance = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult() code=${resultCode.codeToString()}, " +
                "data=${intent.extras}")
        result = resultCode to data
    }

    companion object {
        private var unsafeInstance: CompanionActivity? = null
        val instance: CompanionActivity
            get() = unsafeInstance ?: error("There is no CompanionActivity")

        fun launchAndWait(context: Context) {
            val intent = Intent(context, CompanionActivity::class.java)
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            waitForResult(timeout = 3.seconds, interval = 100.milliseconds) {
                unsafeInstance?.takeIf { it.isResumed }
            } ?: error("CompanionActivity has not appeared")
        }

        fun startIntentSender(intentSender: IntentSender) =
                instance.startIntentSenderForResult(intentSender, 0, null, 0, 0, 0)

        fun waitForActivityResult(): Pair<Int, Intent?> {
            val result = waitForResult(timeout = 1.seconds, interval = 100.milliseconds) {
                instance.result
            } ?: error("onActivityResult() has not been invoked")
            instance.result = null
            return result
        }

        fun finish() = instance.finish()

        fun safeFinish() = unsafeInstance?.finish()

        fun waitUntilGone() = waitFor { unsafeInstance == null }
    }
}

private fun Int.codeToString() = when (this) {
    Activity.RESULT_OK -> "RESULT_OK"
    Activity.RESULT_CANCELED -> "RESULT_CANCELED"
    else -> "Unknown"
} + "($this)"
