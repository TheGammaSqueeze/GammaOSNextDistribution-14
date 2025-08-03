/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tv.settings.startup;

import android.app.Activity;

import androidx.annotation.NonNull;

/** Feature provider for TvSettings startup verification. */
public interface StartupVerificationFeatureProvider {

    /**
     * Start the Activity for TvSettings startup verification.
     *
     * The Activity is expected to :
     * - invoke setResult(RESULT_OK) and finish() immediately without showing any UI if TvSettings
     * should be launched directly (e.g., device in adult profile or has gone through successful
     * adult re-auth within 30s);
     * - show challenge/informative UI (e.g., adult re-auth) based on device's status and invoke
     * setResult(RESULT_OK) before finishing ONLY IF it has determined that TvSettings should be
     * launched normally.
     *
     * @param activity    the activity to invoke startActivityForResult() from
     * @param requestCode the requestCode expected by onActivityResult() to receive result of
     *                    startup verification activity
     * @return whether the Activity is found and started. The false result, aka the absence of the
     * Activity handling the specified Intent is suggesting the missing of features that require the
     * startup verification, in which case TvSettings should be started normally.
     */
    boolean startStartupVerificationActivityForResult(@NonNull Activity activity, int requestCode);
}
