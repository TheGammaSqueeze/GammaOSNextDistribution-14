/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.example.sampleleanbacklauncher.apps.AppFragment;
import com.example.sampleleanbacklauncher.search.SearchFragment;

public class LauncherActivity extends Activity {
    private SystemUpdateChecker mSystemUpdateChecker;
    private static final int SYSTEM_UPDATE_CHECKER_REQUEST = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.launcher);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    // Search row
                    .add(R.id.launcher_container,
                            SearchFragment.newInstance())
                    // Apps row
                    .add(R.id.launcher_container,
                            AppFragment.newInstance(AppFragment.ROW_TYPE_APPS))
                    // Games row
                    .add(R.id.launcher_container,
                            AppFragment.newInstance(AppFragment.ROW_TYPE_GAMES))
                    // Settings row
                    .add(R.id.launcher_container,
                            AppFragment.newInstance(AppFragment.ROW_TYPE_SETTINGS))
                    .commit();
        }

        mSystemUpdateChecker = new SystemUpdateChecker(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        findViewById(R.id.launcher_container).animate().alpha(1f);

        Intent intent = mSystemUpdateChecker.getSystemUpdateCheckerIntent();
        if (intent != null) {
            startActivityForResult(intent, SYSTEM_UPDATE_CHECKER_REQUEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        findViewById(R.id.launcher_container).animate().alpha(0f);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SYSTEM_UPDATE_CHECKER_REQUEST && resultCode == RESULT_OK) {
            mSystemUpdateChecker.onSystemUpdateCheckerComplete();
        }
    }
}
