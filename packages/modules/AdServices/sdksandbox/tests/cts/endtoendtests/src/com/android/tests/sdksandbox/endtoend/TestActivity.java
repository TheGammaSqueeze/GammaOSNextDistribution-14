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

package com.android.tests.sdksandbox.endtoend;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.ctssdkprovider.IActivityStarter;

public class TestActivity extends Activity {

    private static final String ACTIVITY_STARTER_KEY = "ACTIVITY_STARTER_KEY";

    // Text to check against to ensure that the activity is shown
    public static final String DEFAULT_SHOW_TEXT = "DEFAULT_SHOW_TEXT";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final LinearLayout layout = new LinearLayout(this);
        layout.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.setOrientation(LinearLayout.HORIZONTAL);
        final TextView tv1 = new TextView(this);
        final Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getString("TEXT_KEY") != null) {
            tv1.setText(extras.getString("TEXT_KEY"));
        } else {
            tv1.setText(DEFAULT_SHOW_TEXT);
        }
        layout.addView(tv1);

        this.setContentView(layout);
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.containsKey(ACTIVITY_STARTER_KEY)) {
            IActivityStarter activityStarter =
                    (IActivityStarter) extras.getBinder(ACTIVITY_STARTER_KEY);
            try {
                activityStarter.onActivityResumed();
            } catch (RemoteException e) {
                throw new IllegalStateException("Failed to notify activityStarter onResume.");
            }
        }
    }
}
