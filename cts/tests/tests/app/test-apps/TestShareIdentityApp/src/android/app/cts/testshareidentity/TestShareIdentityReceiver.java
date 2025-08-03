/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.app.cts.testshareidentity;

import static android.app.cts.testshareidentity.TestShareIdentityActivity.DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER;
import static android.app.cts.testshareidentity.TestShareIdentityActivity.DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER;
import static android.app.cts.testshareidentity.TestShareIdentityActivity.EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER;
import static android.app.cts.testshareidentity.TestShareIdentityActivity.EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER;
import static android.app.cts.testshareidentity.TestShareIdentityActivity.EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER;
import static android.app.cts.testshareidentity.TestShareIdentityActivity.EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER;
import static android.app.cts.testshareidentity.TestShareIdentityActivity.TEST_CASE_KEY;
import static android.app.cts.testshareidentity.TestShareIdentityActivity.TEST_ID_KEY;
import static android.app.cts.testshareidentity.TestShareIdentityActivity.getShareIdentityActivity;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Receives broadcasts from new intent activities in {@link android.app.cts.ShareIdentityTest},
 * indicating that their first launch was successful and they are ready to be re-launched to go into
 * #onNewIntent method.
 */
public class TestShareIdentityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int testId = intent.getIntExtra(TEST_ID_KEY, -1);
        int testCase = intent.getIntExtra(TEST_CASE_KEY, -1);
        Intent newIntent = new Intent();
        newIntent.setComponent(getShareIdentityActivity(testCase));
        newIntent.putExtra(TEST_ID_KEY, testId);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle bundle;
        switch (testCase) {
            case DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER,
                    DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER ->
                    context.startActivity(newIntent);
            case EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER,
                    EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER -> {
                bundle = ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
                context.startActivity(newIntent, bundle);
            }
            case EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER,
                    EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER -> {
                bundle = ActivityOptions.makeBasic().setShareIdentityEnabled(false).toBundle();
                context.startActivity(newIntent, bundle);
            }
        }
    }
}
