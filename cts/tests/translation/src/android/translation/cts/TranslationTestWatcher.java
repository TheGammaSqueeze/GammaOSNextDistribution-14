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

package android.translation.cts;

import android.util.Log;

import com.android.compatibility.common.util.TestNameUtils;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Custom {@link TestWatcher} that  used for UiTranslationManagerTest.
 */
public final class TranslationTestWatcher extends TestWatcher {

    private static final String TAG = "TranslationTestWatcher";

    @Override
    protected void starting(Description description) {
        final String testName = description.getDisplayName();
        Log.i(TAG, "Starting " + testName);
        TestNameUtils.setCurrentTestName(testName);
    }

    @Override
    protected void finished(Description description) {
        final String testName = description.getDisplayName();
        Log.i(TAG, "Finished " + testName);
        TestNameUtils.setCurrentTestName(null);
    }
}
