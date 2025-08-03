/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.telephony.satellite.cts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

/**
 * A mock pointing UI activity, which will be started by
 * {@link com.android.internal.telephony.satellite.PointingAppController#startPointingUI(boolean)}.
 */
public class MockPointingUiActivity extends Activity {
    /** An intent of this action will be broadcasted when MockPointingUiActivity is launched. */
    public static final String ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED =
            "android.telephony.satellite.cts.MOCK_POINTING_UI_ACTIVITY_STARTED";

    private static final String TAG = "MockPointingUiActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        Intent intent = new Intent();
        intent.setAction(ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED);
        getContext().sendBroadcast(intent);
        finish();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }
}
