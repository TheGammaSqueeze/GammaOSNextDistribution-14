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

package com.android.cts.verifier;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/** An activity to show tester instructions when integrating with CTSInteractive. */
public class CtsInteractiveActivity extends Activity {

    private static final String TAG = CtsInteractiveActivity.class.getSimpleName();

    // TODO: There is a bug in this logic - if i launch via launcher and then press back the state
    //  goes bad and it'll show a previous cts test rather than the "please wait" page

    @Override
    protected void onStart() {
        super.onStart();

        if (getIntent() != null) {
            String activityName = getIntent().getStringExtra("ACTIVITY_NAME");
            if (activityName != null) {
                String displayMode = getIntent().getStringExtra("DISPLAY_MODE");
                String requirements = getIntent().getStringExtra("REQUIREMENTS");
                Log.i(TAG, "activityName=" + activityName + ", displayMode=" + displayMode);
                if (requirements != null) {
                    if (!ManifestTestListAdapter.matchAllConfigs(this, requirements.split(","))) {
                        String testName =
                                TestListAdapter.setTestNameSuffix(
                                        displayMode, "com.android.cts.verifier" + activityName);
                        // Store assumption failed
                        TestResultsProvider.setTestResult(
                                this,
                                testName,
                                3 /* assumption failed */,
                                null,
                                /* reportLog= */ null,
                                /* historyCollection= */ null,
                                /* screenshotsMetadata= */ null);
                        return;
                    }
                }

                Editor editor = getSharedPreferences("CTSInteractive", Context.MODE_PRIVATE).edit();
                editor.putString("ACTIVITY_NAME", activityName);
                if (displayMode != null) {
                    editor.putString("DISPLAY_MODE", displayMode);
                    // Update the public share mode value to be accessed by the test activity.
                    TestListActivity.sCurrentDisplayMode = displayMode;
                } else {
                    // Reset the global display mode if no specific display mode is specified. In
                    // case the existing CTS-V APP on the device is set as FOLD mode (it will cause
                    // CtsVerifierWrappedTestCases hanging there for the result).
                    TestListActivity.sCurrentDisplayMode =
                            TestListActivity.DisplayMode.UNFOLDED.toString();
                }
                editor.apply();

                Intent intent = getTestIntent(activityName, displayMode);

                setIntent(null); // Remove the intent so we don't relaunch when it returns

                startActivityForResult(intent, 0);
            } else {
                relaunchTest();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ctsinteractive_activity);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Back button comes here - doesn't go above
        if (resultCode != Activity.RESULT_CANCELED) {
            // We have a result - no need to restart the test
            getSharedPreferences("CTSInteractive", Context.MODE_PRIVATE).edit().clear().commit();
        } else {
            relaunchTest();
        }
    }

    private void relaunchTest() {
        SharedPreferences preferences =
                getSharedPreferences("CTSInteractive", Context.MODE_PRIVATE);
        String activityName = preferences.getString("ACTIVITY_NAME", null);
        if (activityName == null) {
            Toast.makeText(this, "No current test", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            String displayMode = preferences.getString("DISPLAY_MODE", null);
            if (displayMode != null) {
                TestListActivity.sCurrentDisplayMode = displayMode;
            } else {
                TestListActivity.sCurrentDisplayMode =
                        TestListActivity.DisplayMode.UNFOLDED.toString();
            }
            startActivityForResult(getTestIntent(activityName, displayMode), 0);
        }
    }

    private Intent getTestIntent(String activityName, String displayMode) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(getPackageName(), getPackageName() + activityName));
        if (displayMode != null
                && (activityName.endsWith("NotificationListenerVerifierActivity")
                        || activityName.endsWith("USBRestrictRecordAActivity"))) {
            // Start some activities with extra display mode data, more background is b/255265824
            // and b/256545013.
            intent.putExtra("DISPLAY_MODE", displayMode);
        } else if (displayMode == null) {
            intent.putExtra("AVOID_RESTORE_DISPLAY_MODE", true);
        }
        return intent;
    }
}
