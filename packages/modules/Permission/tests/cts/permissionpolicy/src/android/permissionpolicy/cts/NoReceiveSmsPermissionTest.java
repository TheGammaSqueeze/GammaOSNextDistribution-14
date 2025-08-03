/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.permissionpolicy.cts;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.SystemUserOnly;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Verify Sms and Mms cannot be received without required permissions.
 * Uses {@link android.telephony.SmsManager}.
 */
@AppModeFull(reason = "Instant apps cannot get the SEND_SMS permission")
@SystemUserOnly(reason = "Secondary users have the DISALLOW_SMS user restriction")
public class NoReceiveSmsPermissionTest extends AndroidTestCase {

    private static final int SMS_DELIVERED_WAIT_TIME_MILLIS = 4000;
    private static final String TELEPHONY_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String MESSAGE_STATUS_RECEIVED_ACTION =
        "com.android.cts.permission.sms.MESSAGE_STATUS_RECEIVED_ACTION";
    private static final String MESSAGE_SENT_ACTION =
        "com.android.cts.permission.sms.MESSAGE_SENT";
    private static final String APP_SPECIFIC_SMS_RECEIVED_ACTION =
        "com.android.cts.permission.sms.APP_SPECIFIC_SMS_RECEIVED";


    private static final String LOG_TAG = "NoReceiveSmsPermissionTest";

    private Semaphore mSemaphore = new Semaphore(0);

    /**
     * Verify that SmsManager.sendTextMessage requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#SEND_SMS}.
     *
     * Note: this test requires that the device under test reports a valid phone number
     */
    public void testReceiveTextMessage() {
        PackageManager packageManager = mContext.getPackageManager();
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        // register our test receiver to receive SMSs. This won't throw a SecurityException,
        // so test needs to wait to determine if it actual receives an SMS
        // admittedly, this is a weak verification
        // this test should be used in conjunction with a test that verifies an SMS can be
        // received successfully using the same logic if all permissions are in place
        IllegalSmsReceiver receiver = new IllegalSmsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TELEPHONY_SMS_RECEIVED);
        filter.addAction(MESSAGE_SENT_ACTION);
        filter.addAction(MESSAGE_STATUS_RECEIVED_ACTION);

        getContext().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        sendSMSToSelf("test");

        waitForForEvents(mSemaphore, 1);
        assertTrue("[RERUN] Sms not sent successfully. Check signal.",
                receiver.isMessageSent());
        assertFalse("Sms received without proper permissions", receiver.isSmsReceived());
    }

    /**
     * Verify that without {@link android.Manifest.permission#RECEIVE_SMS} that an SMS sent
     * containing a nonce from {@link SmsManager#createAppSpecificSmsToken} is delivered
     * to the app.
     */
    public void testAppSpecificSmsToken() {
        PackageManager packageManager = mContext.getPackageManager();
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        AppSpecificSmsReceiver receiver = new AppSpecificSmsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TELEPHONY_SMS_RECEIVED);
        filter.addAction(MESSAGE_SENT_ACTION);
        filter.addAction(MESSAGE_STATUS_RECEIVED_ACTION);
        filter.addAction(APP_SPECIFIC_SMS_RECEIVED_ACTION);
        getContext().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);

        PendingIntent receivedIntent = PendingIntent.getBroadcast(getContext(), 0,
                new Intent(APP_SPECIFIC_SMS_RECEIVED_ACTION)
                        .setPackage(getContext().getPackageName()),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);

        String token = SmsManager.getDefault().createAppSpecificSmsToken(receivedIntent);
        String message = "test message, token=" + token;
        sendSMSToSelf(message);

        waitForForEvents(mSemaphore, 1);
        assertTrue("[RERUN] Sms not sent successfully. Check signal.",
                receiver.isMessageSent());
        assertFalse("Sms received without proper permissions", receiver.isSmsReceived());
        waitForForEvents(mSemaphore, 1);
        assertTrue("App specific SMS intent not triggered", receiver.isAppSpecificSmsReceived());
    }

    private boolean waitForForEvents(Semaphore semaphore, int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!semaphore.tryAcquire(SMS_DELIVERED_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS)) {
                    return false;
                }
            } catch (Exception ex) {
                return false;
            }
        }
        return true;
    }

    private void sendSMSToSelf(String message) {
        PendingIntent sentIntent = PendingIntent.getBroadcast(getContext(), 0,
                new Intent(MESSAGE_SENT_ACTION).setPackage(getContext().getPackageName()),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
        PendingIntent deliveryIntent = PendingIntent.getBroadcast(getContext(), 0,
                new Intent(MESSAGE_STATUS_RECEIVED_ACTION)
                        .setPackage(getContext().getPackageName()),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);

        SubscriptionManager subscription = (SubscriptionManager)
                 getContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        int subscriptionId = subscription.getActiveDataSubscriptionId();

        assertFalse("[RERUN] No active telephony subscription. Check there is one enabled.",
                subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        // get current phone number
        String currentNumber = subscription.getPhoneNumber(subscriptionId);

        // fallback to getActiveSubscriptionInfo if number is empty
        if (TextUtils.isEmpty(currentNumber)) {
            SubscriptionInfo subInfo = subscription.getActiveSubscriptionInfo(subscriptionId);

            assertTrue("[RERUN] No info for the active telephony subscription.",
                    subInfo != null);
            currentNumber = subInfo.getNumber();
        }
        assertFalse("[RERUN] SIM card does not provide phone number. Use a suitable SIM Card.",
                TextUtils.isEmpty(currentNumber));

        Log.i(LOG_TAG, String.format("Sending SMS to self: %s", currentNumber));
        sendSms(currentNumber, message, sentIntent, deliveryIntent);
    }

    protected void sendSms(String currentNumber, String text, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        SmsManager.getDefault().sendTextMessage(currentNumber, null, text, sentIntent,
                deliveryIntent);
    }

    /**
     * A receiver that tracks if message was sent and received
     */
    public class IllegalSmsReceiver extends BroadcastReceiver {

        private boolean mIsSmsReceived = false;
        private boolean mIsMessageSent = false;

        public void onReceive(Context context, Intent intent) {
            if (TELEPHONY_SMS_RECEIVED.equals(intent.getAction())) {
                // this is bad, received sms without having SMS permission
                setSmsReceived();
            } else if (MESSAGE_STATUS_RECEIVED_ACTION.equals(intent.getAction())) {
                handleResultCode(getResultCode(), "delivery");
            } else if (MESSAGE_SENT_ACTION.equals(intent.getAction())) {
                handleResultCode(getResultCode(), "sent");
            } else {
                Log.w(LOG_TAG, String.format("unknown intent received: %s", intent.getAction()));
            }

        }

        public boolean isSmsReceived() {
            return mIsSmsReceived;
        }

        private synchronized void setSmsReceived() {
            mIsSmsReceived = true;
            notify();
        }

        public boolean isMessageSent() {
            return mIsMessageSent;
        }

        private void handleResultCode(int resultCode, String action) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(LOG_TAG, String.format("message %1$s successful", action));
                setMessageSentSuccess();
            } else {
                setMessageSentFailure();
                String reason = getErrorReason(resultCode);
                Log.e(LOG_TAG, String.format("message %1$s failed: %2$s", action, reason));
            }
        }

        private synchronized void setMessageSentSuccess() {
            mIsMessageSent = true;
            // set this to true, but don't notify receiver since we don't know if message received
            // yet
        }

        private synchronized void setMessageSentFailure() {
            mIsMessageSent = false;
            // test environment failure, notify observer so it can stop listening
            // TODO: should test retry?
            notify();
        }

        private String getErrorReason(int resultCode) {
            switch (resultCode) {
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    return "generic failure";
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    return "no service";
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    return "null pdu";
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    return "Radio off";
            }
            return "unknown";
        }
    }

    public class AppSpecificSmsReceiver extends IllegalSmsReceiver {
        private boolean mAppSpecificSmsReceived = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (APP_SPECIFIC_SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                mAppSpecificSmsReceived = true;
            } else {
                super.onReceive(context, intent);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                Log.e(LOG_TAG, "mSemaphore: Got exception in releasing semaphore, ex=" + ex);
            }
        }

        public boolean isAppSpecificSmsReceived() {
            return mAppSpecificSmsReceived;
        }
    }
}
