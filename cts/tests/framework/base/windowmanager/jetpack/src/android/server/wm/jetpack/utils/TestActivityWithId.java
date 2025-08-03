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

package android.server.wm.jetpack.utils;

import static android.server.wm.jetpack.utils.TestActivityLauncher.KEY_ACTIVITY_ID;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

/**
 * Test activity that has a unique Id passed in from the launching context.
 */
public class TestActivityWithId extends TestActivity {

    private static final String DEFAULT_ID = "unknown";
    private String mId = DEFAULT_ID;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get ID
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(KEY_ACTIVITY_ID)) {
            mId = intent.getStringExtra(KEY_ACTIVITY_ID);
        }
    }

    public String getId() {
        return mId;
    }

    @Override
    public String toString() {
        return String.format("{" + this.getClass().getSimpleName() + ", id=%s}", mId);
    }

}
