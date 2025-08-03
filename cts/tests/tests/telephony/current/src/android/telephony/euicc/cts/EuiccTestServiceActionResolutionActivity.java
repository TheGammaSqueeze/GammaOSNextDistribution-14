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

package android.telephony.euicc.cts;

import android.app.Activity;
import android.content.Intent;
import android.service.euicc.EuiccService;
import android.util.Log;

/**
 * A mock activity which simulates a resolution of EuiccService actions.
 *
 */
public class EuiccTestServiceActionResolutionActivity extends Activity {

    // Extra to confirm which action is launched
    public static String EXTRA_RESULT_ACTION = "extra_result_action";
    @Override
    protected void onResume() {
        super.onResume();
        String testAction = getMatchingTestBroadcastReceiverAction(getIntent().getAction());
        Intent sendResultIntent = new Intent(testAction);
        sendResultIntent.putExtra(EXTRA_RESULT_ACTION, testAction);
        sendBroadcast(sendResultIntent);
        // Resolution activity has successfully started, return result & finish
        finish();
    }

    /**
     * Returns the matching test broad case receiver action.
     * @param action euicc service action.
     * @return the test brod cast receiver action.
     */
    private String getMatchingTestBroadcastReceiverAction(String action) {
        String matchingTestAction = null;
        switch(action) {
            case EuiccService
                    .ACTION_PROVISION_EMBEDDED_SUBSCRIPTION:
                matchingTestAction = EuiccManagerTest.ACTION_PROVISION_EMBEDDED_SUBSCRIPTION;
                break;
            case EuiccService
                    .ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS:
                matchingTestAction = EuiccManagerTest.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS;
                break;
            case EuiccService
                    .ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS:
                matchingTestAction = EuiccManagerTest.ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS;
                break;
            case EuiccService
                    .ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTION:
                matchingTestAction = EuiccManagerTest.ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTIONS;
                break;
        }
        return matchingTestAction;
    }
}

