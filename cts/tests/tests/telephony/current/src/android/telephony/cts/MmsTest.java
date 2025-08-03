/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.telephony.cts;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.DefaultSmsAppHelper;
import android.text.TextUtils;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.SendConf;
import com.google.android.mms.pdu.SendReq;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test sending MMS using {@link android.telephony.SmsManager}.
 */
public class MmsTest {
    private static final String TAG = "MmsTest";

    private static final String ACTION_MMS_SENT = "CTS_MMS_SENT_ACTION";
    private static final String ACTION_MMS_DOWNLOAD = "CTS_MMS_DOWNLOAD_ACTION";
    public static final String ACTION_WAP_PUSH_DELIVER_DEFAULT_APP =
            "CTS_WAP_PUSH_DELIVER_DEFAULT_APP_ACTION";
    private static final long DEFAULT_EXPIRY_TIME = 7 * 24 * 60 * 60;
    private static final int DEFAULT_PRIORITY = PduHeaders.PRIORITY_NORMAL;
    private static final long MESSAGE_ID = 912412L;

    private static final String SUBJECT = "CTS MMS Test";
    private static final String MESSAGE_BODY = "CTS MMS test message body";
    private static final String TEXT_PART_FILENAME = "text_0.txt";
    private static final String sSmilText =
            "<smil>" +
                    "<head>" +
                        "<layout>" +
                            "<root-layout/>" +
                            "<region height=\"100%%\" id=\"Text\" left=\"0%%\" top=\"0%%\" width=\"100%%\"/>" +
                        "</layout>" +
                    "</head>" +
                    "<body>" +
                        "<par dur=\"8000ms\">" +
                            "<text src=\"%s\" region=\"Text\"/>" +
                        "</par>" +
                    "</body>" +
            "</smil>";

    private static final long SENT_TIMEOUT = 1000 * 60 * 5; // 5 minutes
    private static final long NO_CALLS_TIMEOUT = 1000; // 1 second

    private static final String PROVIDER_AUTHORITY = "telephonyctstest";

    private Random mRandom;
    private SentReceiver mSentReceiver;
    private SentReceiver mDeliveryReceiver;
    private TelephonyManager mTelephonyManager;
    @Nullable private String mOriginalDefaultSmsApp;
    private static CarrierConfigReceiver sCarrierConfigReceiver;

    private static class SentReceiver extends BroadcastReceiver {
        private final Object mLock;
        private boolean mSuccess;
        private boolean mDone;
        private int mExpectedErrorResultCode;
        private String mAction;

        SentReceiver(int expectedErrorResultCode, String action) {
            mLock = new Object();
            mSuccess = false;
            mDone = false;
            mExpectedErrorResultCode = expectedErrorResultCode;
            mAction = action;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive Action " + intent.getAction() + ", mAction " + mAction);

            switch (intent.getAction()) {
                case ACTION_MMS_SENT:
                    final int resultCode = getResultCode();
                    if (resultCode == Activity.RESULT_OK) {
                        final byte[] response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA);
                        if (response != null) {
                            final GenericPdu pdu = new PduParser(
                                    response, shouldParseContentDisposition()).parse();
                            if (pdu != null && pdu instanceof SendConf) {
                                final SendConf sendConf = (SendConf) pdu;
                                if (sendConf.getResponseStatus() == PduHeaders.RESPONSE_STATUS_OK) {
                                    mSuccess = true;
                                } else {
                                    Log.e(TAG,
                                            "SendConf response status="
                                                    + sendConf.getResponseStatus());
                                }
                            } else {
                                Log.e(TAG, "Not a SendConf: " + (pdu != null
                                        ? pdu.getClass().getCanonicalName() : "NULL"));
                            }
                        } else {
                            Log.e(TAG, "Empty response");
                        }
                    } else {
                        Log.e(TAG, "Failure result=" + resultCode);
                        if (resultCode == mExpectedErrorResultCode) {
                            mSuccess = true;
                        }
                        if (resultCode == SmsManager.MMS_ERROR_HTTP_FAILURE) {
                            final int httpError = intent.getIntExtra(
                                    SmsManager.EXTRA_MMS_HTTP_STATUS,
                                    0);
                            Log.e(TAG, "HTTP failure=" + httpError);
                        }
                    }
                    break;
                case ACTION_WAP_PUSH_DELIVER_DEFAULT_APP:
                    mSuccess = true;
                    break;
            }

            if (intent.getAction().equals(mAction)) {
                synchronized (mLock) {
                    mDone = true;
                    mLock.notify();
                }
            }
        }

        public boolean waitForSuccess(long timeout) {
            synchronized(mLock) {
                final long startTime = SystemClock.elapsedRealtime();
                long waitTime = timeout;
                while (!mDone && waitTime > 0) {
                    try {
                        mLock.wait(waitTime);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    waitTime = timeout - (SystemClock.elapsedRealtime() - startTime);
                }
                Log.i(TAG, "Wait for sent: done=" + mDone + ", success=" + mSuccess);
                return mDone && mSuccess;
            }
        }

        public boolean verifyNoCalls(long timeout) {
            synchronized (mLock) {
                try {
                    mLock.wait(timeout);
                } catch (InterruptedException e) {
                    // Ignore
                }
                return (!mDone && !mSuccess);
            }
        }
    }

    /**
     * Setup before all tests.
     */
    @BeforeClass
    public static void beforeAllTests() {
        Log.i(TAG, "beforeAllTests");
        sCarrierConfigReceiver = new CarrierConfigReceiver(
                SmsManager.getDefaultSmsSubscriptionId());
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        getInstrumentation().getContext().registerReceiver(sCarrierConfigReceiver, filter);
    }

    /**
     * Clean up resources after all tests.
     */
    @AfterClass
    public static void afterAllTests() {
        Log.i(TAG, "afterAllTests");

        // Ensure there are no CarrierConfig overrides.
        overrideCarrierConfig(SmsManager.getDefaultSmsSubscriptionId(), null);
        if (sCarrierConfigReceiver != null) {
            getInstrumentation().getContext().unregisterReceiver(sCarrierConfigReceiver);
            sCarrierConfigReceiver = null;
        }
    }

    @Before
    public void setUp() throws Exception {
        mRandom = new Random();
        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING));
        mOriginalDefaultSmsApp = DefaultSmsAppHelper.getDefaultSmsApp(getContext());
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @After
    public void tearDown() throws Exception {
        if (!TextUtils.isEmpty(mOriginalDefaultSmsApp)) {
            assertTrue(DefaultSmsAppHelper.setDefaultSmsApp(getContext(), mOriginalDefaultSmsApp));
        }
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendMultimediaMessage")
    public void testSendMmsMessage() {
        Log.i("MmsTest", "testSendMmsMessage");

        // Test non-default SMS app
        sendMmsMessage(0L /* messageId */, Activity.RESULT_OK, SmsManager.getDefault(), false);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMmsMessage(0L /* messageId */, Activity.RESULT_OK, SmsManager.getDefault(), true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendMultimediaMessage")
    public void testSendMmsMessageWithInactiveSubscriptionId() {
        int inactiveSubId = 127;

        // Test non-default SMS app
        sendMmsMessage(0L /* messageId */, SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION,
                SmsManager.getSmsManagerForSubscriptionId(inactiveSubId), false);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMmsMessage(0L /* messageId */, SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION,
                SmsManager.getSmsManagerForSubscriptionId(inactiveSubId), true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendMultimediaMessage")
    public void testSendMmsMessageWithMmsDisabled() {
        if (!Flags.mmsDisabledError()) {
            Log.i(TAG, "testSendMmsMessageWithMmsDisabled: mmsDisabledError is not enabled");
            return;
        }
        Log.i(TAG, "testSendMmsMessageWithMmsDisabled");

        // Disable MMS carrier config
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, false);
        assertTrue(overrideCarrierConfig(SmsManager.getDefaultSmsSubscriptionId(), bundle));
        assertFalse(doesSupportMMS());

        // It takes some time for the new carrier config loaded to MmsConfigManager
        waitFor(TimeUnit.SECONDS.toMillis(1));

        // Test non-default SMS app
        sendMmsMessage(0L /* messageId */, SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER,
                SmsManager.getDefault(), false);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMmsMessage(0L /* messageId */, SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER,
                SmsManager.getDefault(), true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();

        // Restore MMS config
        if (doesSupportMMS()) {
            bundle.putBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true);
            assertTrue(overrideCarrierConfig(SmsManager.getDefaultSmsSubscriptionId(), bundle));
        }
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendMultimediaMessage")
    public void testSendMmsMessageWithMessageId() {
        // Test non-default SMS app
        sendMmsMessage(MESSAGE_ID, Activity.RESULT_OK, SmsManager.getDefault(), false);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMmsMessage(MESSAGE_ID, Activity.RESULT_OK, SmsManager.getDefault(), true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    private void sendMmsMessage(long messageId, int expectedErrorResultCode,
            SmsManager smsManager, boolean defaultSmsApp) {
        if (!doesSupportMMS()
                && expectedErrorResultCode != SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER) {
            Log.i(TAG, "testSendMmsMessage skipped: no telephony available or MMS not supported");
            return;
        }

        String selfNumber;
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        try {
            int subId = mTelephonyManager.getSubscriptionId();
            SubscriptionManager subscriptionManager = getContext()
                    .getSystemService(SubscriptionManager.class);
            selfNumber = subscriptionManager.getPhoneNumber(subId);
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
        assertFalse("[RERUN] SIM card does not provide phone number. Use a suitable SIM Card.",
                TextUtils.isEmpty(selfNumber));

        Log.i(TAG, "testSendMmsMessage");

        final Context context = getContext();
        // Register sent receiver
        mSentReceiver = new SentReceiver(expectedErrorResultCode, ACTION_MMS_SENT);
        context.registerReceiver(mSentReceiver, new IntentFilter(ACTION_MMS_SENT),
                Context.RECEIVER_EXPORTED);

        mDeliveryReceiver = new SentReceiver(expectedErrorResultCode,
                ACTION_WAP_PUSH_DELIVER_DEFAULT_APP);
        context.registerReceiver(mDeliveryReceiver,
                new IntentFilter(ACTION_WAP_PUSH_DELIVER_DEFAULT_APP), Context.RECEIVER_EXPORTED);

        // Create local provider file for sending PDU
        final String fileName = "send." + String.valueOf(Math.abs(mRandom.nextLong())) + ".dat";
        final File sendFile = new File(context.getCacheDir(), fileName);
        final byte[] pdu = buildPdu(context, selfNumber, SUBJECT, MESSAGE_BODY);
        assertNotNull(pdu);
        assertTrue(writePdu(sendFile, pdu));
        final Uri contentUri = (new Uri.Builder())
                .authority(PROVIDER_AUTHORITY)
                .path(fileName)
                .scheme(ContentResolver.SCHEME_CONTENT)
                .build();
        // Send
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_MMS_SENT).setPackage(context.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        if (messageId == 0L) {
            smsManager.sendMultimediaMessage(context,
                    contentUri, null/*locationUrl*/, null/*configOverrides*/, pendingIntent);
        } else {
            smsManager.sendMultimediaMessage(context,
                    contentUri, null/*locationUrl*/, null/*configOverrides*/, pendingIntent,
                    messageId);
        }
        assertTrue(mSentReceiver.waitForSuccess(SENT_TIMEOUT));
        assertEquals(expectedErrorResultCode, mSentReceiver.getResultCode());

        if (expectedErrorResultCode == Activity.RESULT_OK) {
            int carrierId = mTelephonyManager.getSimCarrierId();
            assertFalse("[RERUN] Carrier [carrier-id: " + carrierId + "] does not support "
                            + "loop back messages. Use another carrier.",
                    CarrierCapability.UNSUPPORT_LOOP_BACK_MESSAGES.contains(carrierId));
        }

        if (defaultSmsApp && expectedErrorResultCode == Activity.RESULT_OK) {
            // Default SMS App should receive android.provider.Telephony.WAP_PUSH_DELIVER
            assertTrue(mDeliveryReceiver.waitForSuccess(SENT_TIMEOUT));
        } else {
            // Non-default SMS App should not receive android.provider.Telephony.WAP_PUSH_DELIVER.
            // Default SMS App will not receive android.provider.Telephony.WAP_PUSH_DELIVER in case
            // of fail to send a message.
            assertTrue(mDeliveryReceiver.verifyNoCalls(NO_CALLS_TIMEOUT));
        }
        sendFile.delete();
    }

    private static boolean writePdu(File file, byte[] pdu) {
        FileOutputStream writer = null;
        try {
            writer = new FileOutputStream(file);
            writer.write(pdu);
            return true;
        } catch (final IOException e) {
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private byte[] buildPdu(Context context, String selfNumber, String subject, String text) {
        final SendReq req = new SendReq();
        // From, per spec
        req.setFrom(new EncodedStringValue(selfNumber));
        // To
        final String[] recipients = new String[1];
        recipients[0] = selfNumber;
        final EncodedStringValue[] encodedNumbers = EncodedStringValue.encodeStrings(recipients);
        if (encodedNumbers != null) {
            req.setTo(encodedNumbers);
        }
        // Subject
        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject));
        }
        // Date
        req.setDate(System.currentTimeMillis() / 1000);
        // Body
        final PduBody body = new PduBody();
        // Add text part. Always add a smil part for compatibility, without it there
        // may be issues on some carriers/client apps
        final int size = addTextPart(body, text, true/* add text smil */);
        req.setBody(body);
        // Message size
        req.setMessageSize(size);
        // Message class
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        // Expiry
        req.setExpiry(DEFAULT_EXPIRY_TIME);
        // The following set methods throw InvalidHeaderValueException
        try {
            // Priority
            req.setPriority(DEFAULT_PRIORITY);
            // Delivery report
            req.setDeliveryReport(PduHeaders.VALUE_NO);
            // Read report
            req.setReadReport(PduHeaders.VALUE_NO);
        } catch (InvalidHeaderValueException e) {
            return null;
        }

        return new PduComposer(context, req).make();
    }

    private static int addTextPart(PduBody pb, String message, boolean addTextSmil) {
        final PduPart part = new PduPart();
        // Set Charset if it's a text media.
        part.setCharset(CharacterSets.UTF_8);
        // Set Content-Type.
        part.setContentType(ContentType.TEXT_PLAIN.getBytes());
        // Set Content-Location.
        part.setContentLocation(TEXT_PART_FILENAME.getBytes());
        int index = TEXT_PART_FILENAME.lastIndexOf(".");
        String contentId = (index == -1) ? TEXT_PART_FILENAME
                : TEXT_PART_FILENAME.substring(0, index);
        part.setContentId(contentId.getBytes());
        part.setData(message.getBytes());
        pb.addPart(part);
        if (addTextSmil) {
            final String smil = String.format(sSmilText, TEXT_PART_FILENAME);
            addSmilPart(pb, smil);
        }
        return part.getData().length;
    }

    private static void addSmilPart(PduBody pb, String smil) {
        final PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(smil.getBytes());
        pb.addPart(0, smilPart);
    }

    private static boolean shouldParseContentDisposition() {
        return SmsManager
                .getDefault()
                .getCarrierConfigValues()
                .getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION, true);
    }

    private static boolean doesSupportMMS() {
        return SmsManager
                .getDefault()
                .getCarrierConfigValues()
                .getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true);
    }

    @Test
    public void testDownloadMultimediaMessage() {
        downloadMultimediaMessage(0L /* messageId */);
    }

    @Test
    public void testDownloadMultimediaMessageWithMessageId() {
        downloadMultimediaMessage(MESSAGE_ID);
    }

    private void downloadMultimediaMessage(long messageId) {
        if (!doesSupportMMS()) {
            Log.i(TAG, "testSendMmsMessage skipped: no telephony available or MMS not supported");
            return;
        }

        Log.i(TAG, "testSendMmsMessage");
        // Prime the MmsService so that MMS config is loaded
        final SmsManager smsManager = SmsManager.getDefault();
        smsManager.getCarrierConfigValues();
        // MMS config is loaded asynchronously. Wait a bit so it will be loaded.
        waitFor(TimeUnit.SECONDS.toMillis(1));

        final Context context = getContext();
        // Create local provider file
        final String fileName = "download." + String.valueOf(Math.abs(mRandom.nextLong())) + ".dat";
        final File sendFile = new File(context.getCacheDir(), fileName);
        final Uri contentUri = (new Uri.Builder())
                .authority(PROVIDER_AUTHORITY)
                .path(fileName)
                .scheme(ContentResolver.SCHEME_CONTENT)
                .build();

        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_MMS_DOWNLOAD).setPackage(context.getPackageName()),
                PendingIntent.FLAG_MUTABLE);

        if (messageId == 0L) {
            // Verify the downloadMultimediaMessage function without messageId exists. This test
            // doesn't actually verify downloading is successful, just that the function to
            // initiate the downloading has been implemented.
            smsManager.downloadMultimediaMessage(context, "foo/fake", contentUri,
                    null /* configOverrides */, pendingIntent);
        } else {
            // Verify the downloadMultimediaMessage function with messageId exists. This test
            // doesn't actually verify downloading is successful, just that the function to
            // initiate the downloading has been implemented.
            smsManager.downloadMultimediaMessage(context, "foo/fake", contentUri,
                    null /* configOverrides */, pendingIntent, MESSAGE_ID);
        }
    }

    private abstract static class BaseReceiver extends BroadcastReceiver {
        protected CountDownLatch mLatch = new CountDownLatch(1);

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        boolean waitForChanged() throws Exception {
            return mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    private static class CarrierConfigReceiver extends BaseReceiver {
        private final int mSubId;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                Log.d(TAG, "Carrier config changed for subId=" + subId
                        + ", mSubId=" + mSubId);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }
    }

    private static boolean overrideCarrierConfig(int subId, PersistableBundle bundle) {
        try {
            CarrierConfigManager carrierConfigManager = getInstrumentation()
                    .getContext().getSystemService(CarrierConfigManager.class);
            sCarrierConfigReceiver.clearQueue();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                    (m) -> m.overrideConfig(subId, bundle));
            return sCarrierConfigReceiver.waitForChanged();
        } catch (Exception ex) {
            Log.e(TAG, "overrideCarrierConfig(), ex=" + ex);
            return false;
        }
    }

    private static void waitFor(long timeoutMillis) {
        Object delayTimeout = new Object();
        synchronized (delayTimeout) {
            try {
                delayTimeout.wait(timeoutMillis);
            } catch (InterruptedException ex) {
                // Ignore the exception
                Log.d(TAG, "waitFor: delayTimeout ex=" + ex);
            }
        }
    }
}
