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

package com.android.server.telecom.transactionalVoipApp;

import static android.telecom.CallAttributes.DIRECTION_INCOMING;
import static android.telecom.CallAttributes.DIRECTION_OUTGOING;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallException;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.View;
import android.widget.ToggleButton;

public class VoipAppMainActivity extends Activity {
    private static final String TAG = "VoipAppMainActivity";
    private static final String ACT_STATE_TAG = "VoipActivityState";
    private static TelecomManager mTelecomManager;
    NotificationManager mNotificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, ACT_STATE_TAG + "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mTelecomManager = getSystemService(TelecomManager.class);
        mNotificationManager = getSystemService(NotificationManager.class);
        // create a notification channel
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    Utils.CHANNEL_ID, "new call channel",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }

        // register account
        findViewById(R.id.registerButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTelecomManager.registerPhoneAccount(Utils.PHONE_ACCOUNT);
            }
        });

        // Start a foreground service that will post a notification within 10 seconds.
        // This is helpful for debugging scenarios where the app is in the background and posting
        // an incoming call notification.
        findViewById(R.id.startForegroundService).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startForegroundService = new Intent(getApplicationContext(),
                        BackgroundIncomingCallService.class);
                getApplicationContext().startForegroundService(startForegroundService);
            }
        });


        // post a new call notification and start an InCall activity
        findViewById(R.id.startOutgoingCall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startInCallActivity(DIRECTION_OUTGOING);
            }
        });

        // post a new call notification and start an InCall activity
        findViewById(R.id.startIncomingCall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startInCallActivity(DIRECTION_INCOMING);
            }
        });

    }

    private void startInCallActivity(int direction) {
        Bundle extras = new Bundle();
        extras.putInt(Utils.sCALL_DIRECTION_KEY, direction);
        Intent intent = new Intent(getApplicationContext(), InCallActivity.class);
        intent.putExtra(Utils.sEXTRAS_KEY, extras);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, ACT_STATE_TAG + " onResume: When the activity enters the Resumed state,"
                + " it comes to the foreground");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, ACT_STATE_TAG + " onPause: The system calls this method as the first"
                + " indication that the user is leaving your activity.  It indicates that the"
                + " activity is no longer in the foreground, but it is still visible if the user"
                + " is in multi-window mode");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, ACT_STATE_TAG + "onStop: When your activity is no longer visible to"
                + " the user, it enters the Stopped state,");
        super.onStop();
    }

    @Override
    protected void onRestart() {
        Log.i(TAG, ACT_STATE_TAG + " onRestart: onStop has called onRestart and the "
                + "activity comes back to interact with the user");
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, ACT_STATE_TAG + " onDestroy: is called before the activity is"
                + " destroyed. ");
        Utils.clearNotification(getApplicationContext());
        super.onDestroy();
    }
}
