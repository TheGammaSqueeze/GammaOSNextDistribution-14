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
package com.android.wallpaper.picker;

import static com.android.wallpaper.util.ActivityUtils.startActivityForResultSafely;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.wallpaper.module.LargeScreenMultiPanesChecker;
import com.android.wallpaper.module.MultiPanesChecker;

/**
 *  Activity to relinquish task identity for multi-pane and
 *  prevent launching incorrect base intent in non multi-pane.
 */
public class TrampolinePickerActivity extends FragmentActivity {

    private static final String TAG = "TrampolinePickerActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trampolineForFormFactors();
    }

    private void trampolineForFormFactors() {
        final MultiPanesChecker multiPanesChecker = new LargeScreenMultiPanesChecker();
        Bundle bundle = getIntent().getExtras();
        bundle = (bundle == null) ? new Bundle() : bundle;
        if (multiPanesChecker.isMultiPanesEnabled(this)) {
            startActivityForResultSafely(this,
                    new Intent(this, CustomizationPickerActivity.class).putExtras(
                            bundle), /* requestCode= */ 0);
        } else {
            startActivityForResultSafely(this,
                    new Intent(this, PassThroughCustomizationPickerActivity.class).putExtras(
                            bundle), /* requestCode= */ 0);
        }
        finish();
    }
}

