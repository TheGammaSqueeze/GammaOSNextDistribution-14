/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.permissionui.cts

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.util.Log
import java.util.concurrent.CompletableFuture

class StartForFutureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            Log.w(TAG, "Activity was recreated. (Perhaps due to a configuration change?)")
        }
    }

    fun startActivityForFuture(
        intent: Intent,
        future: CompletableFuture<Instrumentation.ActivityResult>
    ) {
        if (StartForFutureActivity.future != null) {
            throw RuntimeException(
                "StartForFutureActivity only supports launching one " +
                    "concurrent activity, but more than one was attempted."
            )
        }

        startActivityForResult(intent, 1)
        StartForFutureActivity.future = future
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        future!!.complete(Instrumentation.ActivityResult(resultCode, data))
        future = null
        finish()
    }

    companion object {
        private var future: CompletableFuture<Instrumentation.ActivityResult>? = null
        private val TAG = StartForFutureActivity::class.simpleName
    }
}
