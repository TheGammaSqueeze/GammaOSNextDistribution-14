/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.wm.backgroundactivity.appa;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.server.wm.backgroundactivity.common.TestService;
import android.util.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Foreground activity that makes AppA as foreground.
 */
public class ForegroundActivity extends Activity {
    private static final String TAG = ForegroundActivity.class.getName();
    private Components mA;

    private int mActivityId = -1;
    private boolean mRelaunch = false;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int activityId = intent.getIntExtra(mA.FOREGROUND_ACTIVITY_EXTRA.ACTIVITY_ID,
                    mActivityId);
            if (activityId != mActivityId) {
                return;
            }

            if (mA.FOREGROUND_ACTIVITY_ACTIONS.FINISH_ACTIVITY.equals(action)
                    || intent.getBooleanExtra(mA.FOREGROUND_ACTIVITY_EXTRA.FINISH_FIRST, false)) {
                finish();
            }

            if (mA.FOREGROUND_ACTIVITY_ACTIONS.LAUNCH_BACKGROUND_ACTIVITIES.equals(action)) {
                if (intent.hasExtra(mA.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_INTENTS)) {
                    // Need to copy as a new array instead of just casting to Intent[] since a new
                    // array of type Parcelable[] is created when deserializing.
                    Intent[] intents = intent.getParcelableArrayExtra(
                            mA.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_INTENTS, Intent.class);
                    Log.d(TAG, mA + ":start " + Arrays.asList(intents));
                    startActivities(intents);
                }
                if (intent.hasExtra(mA.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_PENDING_INTENTS)) {
                    // Need to copy as a new array instead of just casting to Intent[] since a new
                    // array of type Parcelable[] is created when deserializing.
                    PendingIntent[] pendingIntents = intent.getParcelableArrayExtra(
                            mA.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_PENDING_INTENTS,
                            PendingIntent.class);
                    Log.d(TAG, mA + ":start " + Arrays.asList(pendingIntents));
                    if (intent.getBooleanExtra(
                            mA.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_FOR_RESULT_AND_FINISH, false)) {
                        // launch and then finish the activity
                        int nextRequestCode = 123;
                        List<Integer> requestCodesUsed = new ArrayList<>();
                        for (PendingIntent pi : pendingIntents) {
                            try {
                                int requestCode = nextRequestCode++;
                                startIntentSenderForResult(pi.getIntentSender(), requestCode, null,
                                        0, 0, 0);
                                requestCodesUsed.add(requestCode);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        SystemClock.sleep(Duration.ofSeconds(1).toMillis());
                        for (int requestCode : requestCodesUsed) {
                            finishActivity(requestCode);
                        }
                    } else {
                        for (PendingIntent pi : pendingIntents) {
                            try {
                                startIntentSender(pi.getIntentSender(), null, 0, 0, 0);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        TestService.sLatestForegroundActivity = this;
        mA = Components.get(getApplicationContext());

        Intent intent = getIntent();
        mRelaunch = intent.getBooleanExtra(
                mA.FOREGROUND_ACTIVITY_EXTRA.RELAUNCH_FOREGROUND_ACTIVITY_EXTRA, false);
        mActivityId = intent.getIntExtra(mA.FOREGROUND_ACTIVITY_EXTRA.ACTIVITY_ID, -1);
        setAllowCrossUidActivitySwitchFromBelow(
                intent.getBooleanExtra(mA.FOREGROUND_ACTIVITY_EXTRA.ALLOW_CROSS_UID, false));

        IntentFilter filter = new IntentFilter();
        filter.addAction(mA.FOREGROUND_ACTIVITY_ACTIONS.LAUNCH_BACKGROUND_ACTIVITIES);
        filter.addAction(mA.FOREGROUND_ACTIVITY_ACTIONS.FINISH_ACTIVITY);
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mRelaunch) {
            mRelaunch = false;
            SystemClock.sleep(50);
            startActivity(getIntent());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}
