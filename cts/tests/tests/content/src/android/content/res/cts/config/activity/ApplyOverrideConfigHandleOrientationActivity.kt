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

package android.content.res.cts.config.activity

import android.content.Context
import android.content.res.Configuration

/**
 * Tests overriding configuration using the [applyOverrideConfiguration] API, which applies to the
 * entire Activity. Also declares handling orientation changes in the manifest, so that the
 * Activity itself isn't recreated.
 */
class ApplyOverrideConfigHandleOrientationActivity : OverrideConfigBaseActivity() {

    var newConfig: Configuration? = null

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        applyOverrideConfiguration(makeOverrideConfig())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        this.newConfig = newConfig
    }
}
