/*
 * Copyright (C) 2023 The Android Open Source Project
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


package android.app.uiautomation.cts;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.Arrays;

/**
* Activity for testing the UiAutomation APIs.
*/
public class UiAutomationTestActivity extends Activity {
    private static final String TAG = UiAutomationTestActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate(): userId=" + getUserId() + ", displayId=" + getDisplayId());
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setContentView(R.layout.ui_automation_test);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        final String[] list = new String[100];
        Arrays.setAll(list, Integer::toString);
        ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, list);

        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(listAdapter);
    }
}
