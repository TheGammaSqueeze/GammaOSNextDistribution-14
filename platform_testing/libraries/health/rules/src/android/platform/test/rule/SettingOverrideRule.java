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

package android.platform.test.rule;


import android.content.ContentResolver;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.runner.Description;

/** This rule will override secure setting and revert it after the test. */
public class SettingOverrideRule extends TestWatcher {

    private final ContentResolver contentResolver = InstrumentationRegistry.getInstrumentation()
            .getContext().getContentResolver();

    private final String mSettingName;
    private final String mOverrideValue;

    // Use strings to store values as all settings stored as strings internally
    private String mOriginalValue;

    public SettingOverrideRule(String name, String value) {
        mSettingName = name;
        mOverrideValue = value;
    }

    @Override
    protected void starting(Description description) {
        // This will return null if the setting hasn't been ever set
        mOriginalValue = Settings.Secure.getString(contentResolver, mSettingName);

        if (!Settings.Secure.putString(contentResolver, mSettingName, mOverrideValue)) {
            throw new RuntimeException("Could not update setting " + mSettingName);
        }
    }

    @Override
    protected void finished(Description description) {
        Settings.Secure.putString(contentResolver, mSettingName, mOriginalValue);
    }
}
