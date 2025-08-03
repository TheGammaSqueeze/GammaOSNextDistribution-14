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

package com.android.cts.verifier.car;

import android.content.Intent;
import android.os.Bundle;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test Car Launcher Behavior with respect to Car Service actions.
 */
public class CarLauncherTestActivity extends PassFailButtons.Activity {

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(getLayoutInflater().inflate(R.layout.car_launcher_test_main, null));
        setPassFailButtonClickListeners();

        // Sets the text in the dialog
        setInfoResources(R.string.car_launcher_test,
                R.string.car_launcher_test_desc, -1);

        // Open the car launcher
        findViewById(R.id.car_launcher_test_button).setOnClickListener(v -> {
            this.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        });
    }
}
