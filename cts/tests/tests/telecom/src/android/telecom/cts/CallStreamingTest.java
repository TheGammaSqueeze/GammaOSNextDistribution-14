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

package android.telecom.cts;

import static android.telecom.CallAttributes.DIRECTION_OUTGOING;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.ParcelUuid;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallException;
import android.telecom.PhoneAccount;
import android.telecom.StreamingCall;
import android.telecom.cts.streamingtestapp.CtsCallStreamingService;
import android.telecom.cts.streamingtestapp.ICtsCallStreamingServiceControl;
import android.util.Log;

import com.android.server.telecom.flags.Flags;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CallStreamingTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = CallStreamingTest.class.getSimpleName();
    private static final String CALL_CHANNEL_ID = "test_calls_not";
    private static final int NOTIFICATION_ID = 1;
    private static final String TEL_CLEAN_STUCK_CALLS_CMD = "telecom cleanup-stuck-calls";
    private static final long ASYNC_TIMEOUT = 5000L;

    private static final String CALL_STREAMING_PACKAGE_NAME =
            "android.telecom.cts.streamingtestapp";
    private static final PhoneAccount ACCOUNT =
            PhoneAccount.builder(TestUtils.TEST_SELF_MANAGED_HANDLE_1, TestUtils.ACCOUNT_LABEL)
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                    ).build();
    private CtsRoleManagerAdapter mCtsRoleManagerAdapter;
    private String mPreviousRoleHolder;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) {
            return;
        }
        mCtsRoleManagerAdapter = new CtsRoleManagerAdapter(getInstrumentation());
        mPreviousRoleHolder = mCtsRoleManagerAdapter
                .getRoleHolder(RoleManager.ROLE_SYSTEM_CALL_STREAMING)
                .stream()
                .findFirst().orElse(null);
        mCtsRoleManagerAdapter.setByPassRoleQualification(true);
        mCtsRoleManagerAdapter.setRoleHolder(RoleManager.ROLE_SYSTEM_CALL_STREAMING,
                CALL_STREAMING_PACKAGE_NAME);
        NewOutgoingCallBroadcastReceiver.reset();
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        mTelecomManager.registerPhoneAccount(ACCOUNT);
        cleanup();
    }

    @Override
    public void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            cleanup();
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_1);
            if (mPreviousRoleHolder == null) {
                mCtsRoleManagerAdapter.removeRoleHolder(RoleManager.ROLE_SYSTEM_CALL_STREAMING,
                        CALL_STREAMING_PACKAGE_NAME);
            } else {
                mCtsRoleManagerAdapter.setRoleHolder(RoleManager.ROLE_SYSTEM_CALL_STREAMING,
                        mPreviousRoleHolder);
            }
            mCtsRoleManagerAdapter.setByPassRoleQualification(false);
        }
        super.tearDown();
    }

    public void testStartCallStreaming() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // Setup control binder to the test CallStreamingService.
        ICtsCallStreamingServiceControl control = getControlBinder();

        TelecomCtsVoipCall call = new TelecomCtsVoipCall("streaming_call");
        final CountDownLatch latch = new CountDownLatch(1);
        CallAttributes attributes = new CallAttributes.Builder(TestUtils.TEST_SELF_MANAGED_HANDLE_1,
                DIRECTION_OUTGOING, "testName", Uri.parse("tel:123-TEST"))
                .setCallType(CallAttributes.AUDIO_CALL)
                .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                .build();

        // Post call notification so we don't lose FGS priority.
        configureNotificationChannel();
        postCallNotification();

        // Tricksy way to get around the fact that this has to be final and assigned in the below
        // lambda
        final ParcelUuid[] callId = new ParcelUuid[1];
        mTelecomManager.addCall(attributes, Runnable::run, new OutcomeReceiver<>() {
            @Override
            public void onResult(CallControl callControl) {
                Log.i(TAG, "onResult: adding callControl to callObject");

                if (callControl == null) {
                    fail("Can't get call control");
                }

                call.onAddCallControl(callControl);
                callId[0] = callControl.getCallId();
                latch.countDown();
            }

            @Override
            public void onError(CallException exception) {
                Log.i(TAG, "testRegisterApp: onError");
            }
        }, call.mHandshakes, call.mEvents);

        assertOnResultWasReceived(latch);

        final android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver outcome =
                new android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver(latch);
        call.mCallControl.startCallStreaming(Runnable::run, outcome);
        assertOnResultWasReceived(outcome.mCountDownLatch);

        // Wait until the streaming service is bound and a streaming call is added to it.
        Bundle bundle = control.waitForCallAdded();

        assertTrue(!bundle.containsKey(CtsCallStreamingService.EXTRA_FAILED));
        assertTrue(bundle.containsKey(CtsCallStreamingService.EXTRA_CALL_EXTRAS));
        Bundle theExtras = bundle.getBundle(CtsCallStreamingService.EXTRA_CALL_EXTRAS);

        // Verify that the StreamingCall got the right call ID.
        if (Flags.callDetailsIdChanges()){
            assertEquals(callId[0].toString(),
                    theExtras.getString(StreamingCall.EXTRA_CALL_ID));
        } else {
            assertEquals(callId[0].toString(),
                    theExtras.getString("android.telecom.extra.CALL_ID"));
        }

        // confirm the audio mode is for comm redirect
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, AudioManager.MODE_COMMUNICATION_REDIRECT);
    }

    /**
     * Configure a notification channel for test calls.
     */
    private void configureNotificationChannel() {
        NotificationManager mgr = mContext.getSystemService(NotificationManager.class);
        NotificationChannel callsChannel = new NotificationChannel(
                CALL_CHANNEL_ID,
                "Calls",
                NotificationManager.IMPORTANCE_DEFAULT);
        mgr.createNotificationChannel(callsChannel);
    }

    /**
     * Post a call notification to satisfy FGS requirements.
     */
    private void postCallNotification() {
        Person person = new Person.Builder().setName("Max Powers").build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent("test"),
                PendingIntent.FLAG_IMMUTABLE);
        Notification callNot = new Notification.Builder(mContext, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone_24dp)
                .setStyle(Notification.CallStyle.forOngoingCall(person, pendingIntent))
                .setFullScreenIntent(pendingIntent, true)
                .build();
        NotificationManager mgr = mContext.getSystemService(NotificationManager.class);
        mgr.notify(NOTIFICATION_ID, callNot);
    }

    public void assertOnResultWasReceived(CountDownLatch latch) {
        Log.i(TAG, "assertOnResultWasReceived: waiting for latch");
        try {
            boolean success = latch.await(5000, TimeUnit.MILLISECONDS);
            if (!success) {
                fail("Outcome received but it's failed.");
            }

        } catch (InterruptedException ie) {
            fail("Failed when trying to receive outcome");
        }
    }

    private void cleanup() {
        Log.i(TAG, "cleanup: method running");
        try {
            if (mInCallCallbacks.getService() != null) {
                mInCallCallbacks.getService().disconnectAllCalls();
                mInCallCallbacks.getService().clearCallList();
            }
            TestUtils.executeShellCommand(getInstrumentation(), TEL_CLEAN_STUCK_CALLS_CMD);
        } catch (Exception e) {
            Log.i(TAG, "Failed when cleanup: " + e);
        }
    }

    private ICtsCallStreamingServiceControl getControlBinder()
            throws Exception {
        Intent bindIntent = new Intent(
                android.telecom.cts.streamingtestapp.CtsCallStreamingServiceControl
                        .CONTROL_INTERFACE_ACTION);
        bindIntent.setPackage(CALL_STREAMING_PACKAGE_NAME);
        CompletableFuture<ICtsCallStreamingServiceControl> future =
                new CompletableFuture<>();
        boolean success = mContext.bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                future.complete(android.telecom.cts.streamingtestapp
                        .ICtsCallStreamingServiceControl.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                future.complete(null);
            }
        }, Context.BIND_AUTO_CREATE);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        return future.get(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
    }
}
