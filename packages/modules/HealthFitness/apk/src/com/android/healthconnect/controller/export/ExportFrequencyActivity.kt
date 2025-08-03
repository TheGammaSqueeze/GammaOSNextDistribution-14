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

package com.android.healthconnect.controller.export


import android.app.Activity
import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import com.android.healthconnect.controller.R

/** Export frequency activity for Health Connect. */
// TODO: b/325917283 - Save the selected frequency preference.
@AndroidEntryPoint(FragmentActivity::class)
class ExportFrequencyActivity: Hilt_ExportFrequencyActivity() {

    // TODO: b/325917283 - Add proper logging for the export frequency activity.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.export_frequency_screen)

        // TODO: b/325917283 - Add the navigation to the next screen.
        val backButton = findViewById<Button>(R.id.export_back_button)

        backButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
