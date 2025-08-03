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

package android.server.wm.activity.lifecycle;

import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_SKIP_TOP_RESUMED_STATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_ACTIVITY_RESULT;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_MULTI_WINDOW_MODE_CHANGED;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_NEW_INTENT;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_POST_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_TOP_POSITION_GAINED;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_TOP_POSITION_LOST;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

/**
 * Base activity that records callbacks in addition to main lifecycle transitions.
 */
public class CallbackTrackingActivity extends LifecycleTrackingActivity {

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mEventLogClient.onCallback(ON_ACTIVITY_RESULT);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mEventLogClient.onCallback(ON_POST_CREATE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mEventLogClient.onCallback(ON_NEW_INTENT);
        setIntent(intent);
    }

    @Override
    public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
        if (!getIntent().getBooleanExtra(EXTRA_SKIP_TOP_RESUMED_STATE, false)) {
            mEventLogClient.onCallback(
                    isTopResumedActivity ? ON_TOP_POSITION_GAINED : ON_TOP_POSITION_LOST);
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        mEventLogClient.onCallback(ON_MULTI_WINDOW_MODE_CHANGED);
    }
}
