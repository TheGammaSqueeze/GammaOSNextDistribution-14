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
package android.telephony.cts;

import static android.telephony.cts.FakeCarrierMessagingService.FAKE_MESSAGE_REF;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.carrier.CarrierMessagingService;
import android.service.carrier.CarrierMessagingServiceWrapper;
import android.service.carrier.MessagePdu;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Build, install and run the tests by running the commands below:
 *  make cts -j64
 *  cts-tradefed run cts -m CtsTelephonyTestCases --test android.telephony.cts.
 *  CarrierMessagingServiceWrapperTest
 */
public class CarrierMessagingServiceWrapperTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private TelephonyManager mTelephonyManager;
    private int mTestSub;
    private Context mContext;
    private CarrierMessagingServiceWrapper mServiceWrapper;
    private CompletableFuture<Void> mServiceReadyFuture = new CompletableFuture<>();
    private Runnable mOnServiceReadyCallback = () -> mServiceReadyFuture.complete(null);
    private String mPdu = "07916164260220F0040B914151245584F600006060605130308A04D4F29C0E";
    private static final int TIMEOUT_IN_MS = 1000;
    @Mock
    private CarrierMessagingServiceWrapper.CarrierMessagingCallback mCallback;

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    @Before
    public void setUp() throws Exception {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(getContext().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_MESSAGING));
        } else {
            assumeTrue(getContext().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY));
        }

        MockitoAnnotations.initMocks(this);
        mContext = getContext();
        mTestSub = SubscriptionManager.getDefaultSubscriptionId();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mTestSub);
    }

    @After
    public void tearDown() throws Exception {
        if (mServiceWrapper != null) mServiceWrapper.disconnect();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Tests that the device properly connects to available CarrierMessagingServices.
     */
    @Test
    public void testConnectToMessagingServiceWrapper() {
        String packageName = "android.telephony.cts";
        mServiceWrapper = new CarrierMessagingServiceWrapper();

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.BIND_CARRIER_SERVICES");
        boolean bindResult = mServiceWrapper.bindToCarrierMessagingService(
                mContext, packageName, Runnable::run, mOnServiceReadyCallback);
        assertTrue(bindResult);

        waitForServiceReady("Service " + packageName + " should be ready.");
    }

    @Test
    public void testCloseWrapper() {
        testConnectToMessagingServiceWrapper();
        mServiceWrapper.disconnect();
        try {
            mServiceWrapper.close();
            fail("Should have throw an NPE on double-close");
        } catch (NullPointerException e) {
            // expected
        } finally {
            mServiceWrapper = null;
        }
    }

    /**
     * Tests that the device the all CarrierMessagingServices can receive sms and
     * triggers valid callback.
     */
    @Test
    public void testReceiveSms() {
        testConnectToMessagingServiceWrapper();

        Mockito.reset(mCallback);
        mServiceWrapper.receiveSms(new MessagePdu(Collections.singletonList(
                TelephonyUtils.hexStringToByteArray(mPdu))), SmsMessage.FORMAT_3GPP, -1,
                mTestSub, Runnable::run, mCallback);
        // Currently we just check if any result is returned. We don't test it being
        // successful.
        Mockito.verify(mCallback, Mockito.timeout(TIMEOUT_IN_MS)).onReceiveSmsComplete(
                CarrierMessagingService.RECEIVE_OPTIONS_DEFAULT);
    }

    /**
     * Tests that the device the all CarrierMessagingServices can send text sms and
     * triggers valid callback.
     */
    @Test
    public void testSendTextSms() {
        testConnectToMessagingServiceWrapper();

        String destAddress = getPhoneNumber();

        Mockito.reset(mCallback);
        mServiceWrapper.sendTextSms("Testing CarrierMessagingService#sendTextSms", mTestSub,
                destAddress, 0, Runnable::run, mCallback);
        // Currently we just check if any result is returned. We don't test it being
        // successful.
        Mockito.verify(mCallback, Mockito.timeout(TIMEOUT_IN_MS)).onSendSmsComplete(
                CarrierMessagingService.RECEIVE_OPTIONS_DEFAULT, FAKE_MESSAGE_REF);
    }

    /**
     * Tests that the device the all CarrierMessagingServices can send data sms and
     * triggers valid callback.
     */
    @Test
    public void testSendDataSms() {
        testConnectToMessagingServiceWrapper();

        String destAddress = getPhoneNumber();

        Mockito.reset(mCallback);
        mServiceWrapper.sendDataSms(TelephonyUtils.hexStringToByteArray(
                "0123abcABC"), mTestSub,
                destAddress, -1, 0,  Runnable::run, mCallback);
        // Currently we just check if any result is returned. We don't test it being
        // successful.
        Mockito.verify(mCallback, Mockito.timeout(TIMEOUT_IN_MS)).onSendSmsComplete(
                CarrierMessagingService.RECEIVE_OPTIONS_DEFAULT, FAKE_MESSAGE_REF);
    }

    /**
     * Tests that the device the all CarrierMessagingServices can send multipart sms and
     * triggers valid callback.
     */
    @Test
    public void testSendMultipartTextSms() {
        testConnectToMessagingServiceWrapper();

        String destAddress = getPhoneNumber();

        List<String> multipartTextSms = Arrays.asList(
                "Testing CarrierMessagingService#sendMultipartTextSms#part1",
                "Testing CarrierMessagingService#sendMultipartTextSms#part2");

        Mockito.reset(mCallback);
        mServiceWrapper.sendMultipartTextSms(multipartTextSms, mTestSub,
                destAddress, 0,  Runnable::run, mCallback);
        // Currently we just check if any result is returned. We don't test it being
        // successful.
        Mockito.verify(mCallback, Mockito.timeout(TIMEOUT_IN_MS)).onSendMultipartSmsComplete(
                eq(CarrierMessagingService.RECEIVE_OPTIONS_DEFAULT),
                eq(new int[] {FAKE_MESSAGE_REF}));
    }

    /**
     * Tests that the device the all CarrierMessagingServices can send data sms and
     * triggers valid callback.
     */
    @Test
    public void testDownloadMms() {
        testConnectToMessagingServiceWrapper();
        Uri fakeUri = Uri.parse("fakeUriString");

        Mockito.reset(mCallback);
        mServiceWrapper.downloadMms(fakeUri, mTestSub, fakeUri, Runnable::run, mCallback);
        // Currently we just check if any result is returned. We don't test it being
        // successful.
        Mockito.verify(mCallback, Mockito.timeout(TIMEOUT_IN_MS)).onDownloadMmsComplete(
                CarrierMessagingService.RECEIVE_OPTIONS_DEFAULT);
    }

    /**
     * Tests that the device the all CarrierMessagingServices can send mms and
     * triggers valid callback.
     */
    @Test
    public void testSendMms() {
        testConnectToMessagingServiceWrapper();
        Uri fakeUri = Uri.parse("fakeUriString");

        Mockito.reset(mCallback);
        mServiceWrapper.sendMms(fakeUri, mTestSub, fakeUri, Runnable::run, mCallback);
        // Currently we just check if any result is returned. We don't test it being
        // successful.
        Mockito.verify(mCallback, Mockito.timeout(TIMEOUT_IN_MS)).onSendMmsComplete(
                eq(CarrierMessagingService.RECEIVE_OPTIONS_DEFAULT), any());
    }

    private boolean isServiceReady() {
        return (mServiceReadyFuture != null && mServiceReadyFuture.isDone());
    }

    private String mPhoneNumber;

    private String getPhoneNumber() {
        if (mPhoneNumber == null) mPhoneNumber = mTelephonyManager.getLine1Number();
        return mPhoneNumber;
    }

    private void waitForServiceReady(String failMessage) {
        try {
            mServiceReadyFuture.get(CarrierMessagingServiceWrapperTest.TIMEOUT_IN_MS,
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(isServiceReady());
        } catch (TimeoutException e) {
            fail(failMessage + " within "
                    + CarrierMessagingServiceWrapperTest.TIMEOUT_IN_MS + " ms.");
        }
    }
}
