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
 * limitations under the License.
 */
package android.contentcaptureservice.cts;

import static android.contentcaptureservice.cts.Helper.EXTRA_VERIFY_RESULT;
import static android.contentcaptureservice.cts.Helper.sContext;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.view.contentcapture.ContentCaptureManager;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

/**
 * Activity that is running out of the CTS test process.
 *
 * <p>As such, it uses {@link BlockingBroadcastReceiver} to notify lifecycle events.
 */
public class OutOfProcessActivity extends Activity {

    private static final String TAG = OutOfProcessActivity.class.getSimpleName();
    static final String EXTRA_ACTION = "action";
    static final String EXTRA_FINISH_BROADCAST = "finishBroadcast";
    static final String EXTRA_ON_START_BROADCAST = "onStartBroadcast";

    public static final ComponentName COMPONENT_NAME = new ComponentName(Helper.MY_PACKAGE,
            OutOfProcessActivity.class.getName());

    public static final int ACTION_NONE = 0;
    public static final int ACTION_CHECK_MANAGER_AND_FINISH = 1;
    public static final int ACTION_KILL_APP_TEST = 2;

    public static final String NO_MANAGER = "NoMgr";
    public static final String HAS_MANAGER = "Mgr:";

    private Intent mIntent;

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart()");
        super.onStart();

        mIntent = getIntent();
        int startAction = mIntent.getIntExtra(EXTRA_ACTION, ACTION_NONE);
        if (startAction == ACTION_CHECK_MANAGER_AND_FINISH) {
            finish();
        } else if (startAction == ACTION_KILL_APP_TEST) {
            final PendingIntent pendingIntent =
                    mIntent.getParcelableExtra(EXTRA_ON_START_BROADCAST);
            if (pendingIntent != null) {
                try {
                    pendingIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    Log.w(TAG, "Pending intent " + pendingIntent + " canceled");
                }
            }
        } else {
            Log.e(TAG, "Unexpected start action.");
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop()");
        super.onStop();

        notifyContentCaptureStatus();
    }

    private void notifyContentCaptureStatus() {
        final int action = mIntent.getIntExtra(EXTRA_ACTION, ACTION_NONE);
        final PendingIntent pendingIntent = mIntent.getParcelableExtra(EXTRA_FINISH_BROADCAST);
        if (action == ACTION_CHECK_MANAGER_AND_FINISH) {
            final ContentCaptureManager ccm = getSystemService(ContentCaptureManager.class);
            final String checkResult = ccm == null ? NO_MANAGER : HAS_MANAGER + ccm;
            if (pendingIntent != null) {
                try {
                    final Intent result = new Intent();
                    result.putExtra(EXTRA_VERIFY_RESULT, checkResult);
                    pendingIntent.send(this, 0, result);
                } catch (PendingIntent.CanceledException e) {
                    Log.w(TAG, "Pending intent " + pendingIntent + " canceled");
                }
                Log.v(TAG, "So Long, and Thanks for All the Fish!");
            }
        }
    }

    static void launchOutOfProcessActivity(PendingIntent pendingIntent, int startAction,
            String extraPendingIntent) {
        final Intent startIntent = new Intent(sContext, OutOfProcessActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startIntent.putExtra(extraPendingIntent, pendingIntent);
        startIntent.putExtra(EXTRA_ACTION, startAction);
        sContext.startActivity(startIntent);
    }

    static void killOutOfProcessActivity() {
        runShellCommand("am broadcast --receiver-foreground "
                + "-n android.contentcaptureservice.cts/.SelfDestructReceiver");
    }
}
