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

package com.android.car.settings.testutils;

import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.settings.common.BaseCarSettingsActivity;

public class BaseCarSettingsTestActivity extends BaseCarSettingsActivity {

    private final TestEventListener<Intent> mStartActivityListener = new TestEventListener<>();
    private boolean mOnBackPressedFlag;

    @Nullable
    @Override
    protected Fragment getInitialFragment() {
        return null;
    }

    @Override
    public void onBackPressed() {
        mOnBackPressedFlag = true;
        super.onBackPressed();
    }

    @Override
    public void startActivity(Intent intent) {
        mStartActivityListener.accept(intent);
        super.startActivity(intent);
    }

    /**
     * Gets a boolean flag indicating whether onBackPressed has been called.
     *
     * @return {@code true} if onBackPressed called, {@code false} otherwise.
     */
    public boolean getOnBackPressedFlag() {
        return mOnBackPressedFlag;
    }

    /**
     * Clear the boolean flag for onBackPressed by setting it to false.
     */
    public void clearOnBackPressedFlag() {
        mOnBackPressedFlag = false;
    }

    /**
     * Gets an event listener for {@link #startActivity(Intent)}
     */
    public TestEventListener<Intent> getStartActivityListener() {
        return mStartActivityListener;
    }
}
