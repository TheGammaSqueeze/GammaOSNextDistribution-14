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
package android.storageaccess.cts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
import java.util.concurrent.CompletableFuture


class ClientActivity : Activity() {
    private var pendingActivityResult: Boolean = false
    private var activityResultFuture: CompletableFuture<Result>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(FLAG_KEEP_SCREEN_ON or FLAG_TURN_SCREEN_ON or FLAG_DISMISS_KEYGUARD)
    }

    fun startActivityForFutureResult(
            intent: Intent,
            requestCode: Int,
            future: CompletableFuture<Result>
    ) {
        log("ClientActivity.startActivityFor_Future_Result() " +
                "requestCode=$requestCode intent=$intent")
        super.startActivityForResult(intent, requestCode)
        activityResultFuture = future
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int, options: Bundle?) {
        if (pendingActivityResult) {
            // To avoid silly mistakes let's make sure we do not try to start multiple activities
            // for result at the same time.
            error("Cannot startActivityForResult(): already waiting for another result.")
        }
        pendingActivityResult = true
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = Result(requestCode, resultCode, data)
        log("ClientActivity.onActivityResult(): $result")

        pendingActivityResult = false

        activityResultFuture?.apply { complete(result) }
        activityResultFuture = null
    }

    data class Result(val requestCode: Int, val resultCode: Int, val data: Intent?)
}