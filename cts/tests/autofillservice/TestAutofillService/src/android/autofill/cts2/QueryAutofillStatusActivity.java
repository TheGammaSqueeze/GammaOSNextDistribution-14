/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.autofill.cts2;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.autofill.AutofillManager;

/**
 * An activity that queries its AutofillService status when started and immediately terminates
 * itself.
 */
public class QueryAutofillStatusActivity extends Activity {

    private static final String TAG = "QueryAutofillServiceStatusActivity";

    // Autofill enable status, the value should be the same with AutofillManagerTest in
    // CtsAutofillServiceTestCases.
    private static final int AUTOFILL_ENABLE = 1;
    private static final int AUTOFILL_DISABLE = 2;

    private PendingIntent mPendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        mPendingIntent = intent.getParcelableExtra("finishBroadcast");
    }

    @Override
    protected void onResume() {
        super.onResume();
        hasEnabledAutofillServicesAndFinish();
    }

    private void hasEnabledAutofillServicesAndFinish() {
        // Check if the calling application provides a AutofillService that is enabled
        final AutofillManager afm = getSystemService(AutofillManager.class);
        final boolean enabled = afm.hasEnabledAutofillServices();
        Log.w(TAG, "hasEnabledAutofillServices()= " + enabled);

        if (mPendingIntent != null) {
            try {
                final int resultCode = enabled ? AUTOFILL_ENABLE : AUTOFILL_DISABLE;
                mPendingIntent.send(resultCode);
            } catch (CanceledException e) {
                Log.w(TAG, "Pending intent " + mPendingIntent + " canceled");
            }
        }
        finish();
    }
}
