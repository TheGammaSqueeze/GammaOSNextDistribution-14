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

import android.app.Activity
import android.content.cts.R
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

abstract class OverrideConfigBaseActivity : Activity() {

    companion object {
        const val OVERRIDE_SMALLEST_WIDTH = 99999
    }

    lateinit var textOrientation: TextView
    lateinit var textSmallestWidth: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(layoutInflater().inflate(R.layout.override_config_test_layout, null))

        textOrientation = requireViewById(R.id.textOrientation)
        textSmallestWidth = requireViewById(R.id.textSmallestWidth)
    }

    open fun layoutInflater() = layoutInflater

    open fun configuration() = resources.configuration!!

    protected fun makeOverrideConfig() = Configuration().apply {
        smallestScreenWidthDp = OVERRIDE_SMALLEST_WIDTH
    }
}
