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

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import java.util.concurrent.CompletableFuture

fun launchClientActivity(block: (ActivityScenario<ClientActivity>) -> Unit) =
        ActivityScenario.launch(ClientActivity::class.java).use(block)

fun ActivityScenario<ClientActivity>.startActivityForFutureResult(
        intent: Intent,
        requestCode: Int
): CompletableFuture<ClientActivity.Result> {
    val futureResult = CompletableFuture<ClientActivity.Result>()
    onActivity { activity ->
        // Careful: UI thread!
        activity.startActivityForFutureResult(intent, requestCode, futureResult)
    }
    return futureResult
}