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

package android.attributionsource.cts.blamed

import android.app.Activity
import android.content.AttributionSource
import android.content.ContextParams
import android.content.Intent
import android.os.Bundle
import android.os.RemoteCallback

class BringToForegroundActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callback = intent.getParcelableExtra<RemoteCallback>(REMOTE_CALLBACK)

        val attributionContext = createContext(
                ContextParams.Builder()
                        .setAttributionTag(RECEIVER_ATTRIBUTION_TAG)
                        .setNextAttributionSource(AttributionSource.Builder(
                                        packageManager.getPackageUid(RECEIVER2_PACKAGE_NAME, 0))
                                .setPackageName(RECEIVER2_PACKAGE_NAME)
                                .setAttributionTag(RECEIVER2_ATTRIBUTION_TAG)
                                .build())
                        .build())

        val result = Bundle()
        result.putParcelable(ATTRIBUTION_SOURCE, attributionContext.attributionSource)
        callback?.sendResult(result)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        finish()
    }

    companion object {
        val REMOTE_CALLBACK = "remote_callback"
        val ATTRIBUTION_SOURCE = "attribution_source"
        val RECEIVER2_PACKAGE_NAME = "android.attributionsource.cts.blamed2"
        val RECEIVER_ATTRIBUTION_TAG = "receiver_attribution_tag"
        val RECEIVER2_ATTRIBUTION_TAG = "receiver2_attribution_tag"
    }
}
