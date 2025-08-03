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

package android.server.wm.activity.lifecycle;

import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_ACTIVITY_ON_USER_LEAVE_HINT;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_PAUSE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_START;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_STOP;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_START_ACTIVITY_IN_ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_START_ACTIVITY_WHEN_IDLE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_DESTROY;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_PAUSE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESTART;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_START;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_STOP;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_USER_LEAVE_HINT;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;

/** Base activity that only tracks fundamental activity lifecycle states. */
public class LifecycleTrackingActivity extends Activity {
    EventLog.EventLogClient mEventLogClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEventLogClient = EventLog.EventLogClient.create(this.getClass().getCanonicalName(), this,
                Uri.parse("content://android.server.wm.lifecycle.logprovider"));
        mEventLogClient.onCallback(ON_CREATE);

        final Intent intent = getIntent();
        final Intent startOnCreate =
                intent.getParcelableExtra(EXTRA_START_ACTIVITY_IN_ON_CREATE);
        if (startOnCreate != null) {
            startActivity(startOnCreate);
        }

        final Intent startOnIdle = intent.getParcelableExtra(EXTRA_START_ACTIVITY_WHEN_IDLE);
        if (startOnIdle != null) {
            Looper.getMainLooper().getQueue().addIdleHandler(() -> {
                startActivity(startOnIdle);
                return false;
            });
        }

        if (intent.getBooleanExtra(EXTRA_FINISH_IN_ON_CREATE, false)) {
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mEventLogClient.onCallback(ON_START);

        if (getIntent().getBooleanExtra(EXTRA_FINISH_IN_ON_START, false)) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEventLogClient.onCallback(ON_RESUME);

        final Intent intent = getIntent();
        if (intent.getBooleanExtra(EXTRA_FINISH_IN_ON_RESUME, false)) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEventLogClient.onCallback(ON_PAUSE);

        if (getIntent().getBooleanExtra(EXTRA_FINISH_IN_ON_PAUSE, false)) {
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mEventLogClient.onCallback(ON_STOP);

        if (getIntent().getBooleanExtra(EXTRA_FINISH_IN_ON_STOP, false)) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mEventLogClient.onCallback(ON_DESTROY);
        mEventLogClient.close();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mEventLogClient.onCallback(ON_RESTART);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        if (getIntent().getBooleanExtra(EXTRA_ACTIVITY_ON_USER_LEAVE_HINT, false)) {
            mEventLogClient.onCallback(ON_USER_LEAVE_HINT);
        }
    }
}
