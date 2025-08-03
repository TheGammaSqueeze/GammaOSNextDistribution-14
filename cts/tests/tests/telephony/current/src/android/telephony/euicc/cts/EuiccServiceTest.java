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

package android.telephony.euicc.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.euicc.DownloadSubscriptionResult;
import android.service.euicc.EuiccService;
import android.service.euicc.GetDefaultDownloadableSubscriptionListResult;
import android.service.euicc.GetDownloadableSubscriptionMetadataResult;
import android.service.euicc.GetEuiccProfileInfoListResult;
import android.service.euicc.IDeleteSubscriptionCallback;
import android.service.euicc.IDownloadSubscriptionCallback;
import android.service.euicc.IEraseSubscriptionsCallback;
import android.service.euicc.IEuiccService;
import android.service.euicc.IEuiccServiceDumpResultCallback;
import android.service.euicc.IGetAvailableMemoryInBytesCallback;
import android.service.euicc.IGetDefaultDownloadableSubscriptionListCallback;
import android.service.euicc.IGetDownloadableSubscriptionMetadataCallback;
import android.service.euicc.IGetEidCallback;
import android.service.euicc.IGetEuiccInfoCallback;
import android.service.euicc.IGetEuiccProfileInfoListCallback;
import android.service.euicc.IGetOtaStatusCallback;
import android.service.euicc.IOtaStatusChangedCallback;
import android.service.euicc.IRetainSubscriptionsForFactoryResetCallback;
import android.service.euicc.ISwitchToSubscriptionCallback;
import android.service.euicc.IUpdateSubscriptionNicknameCallback;
import android.telephony.TelephonyManager;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccInfo;
import android.telephony.euicc.EuiccManager;
import android.telephony.euicc.cts.MockEuiccService.IMockEuiccServiceCallback;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class EuiccServiceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String ACTIVATION_CODE = "12345-ABCDE";

    private static final int CALLBACK_TIMEOUT_MILLIS = 2000 /* 2 sec */;

    private static final int MOCK_SLOT_ID = 1;
    static final int MOCK_SLOT_ID_EXCEPTION = 2;
    private static final int MOCK_PORT_ID = 0;
    private static final String MOCK_ICCID = "12345";
    private static final String MOCK_NICK_NAME = "nick name";

    private IEuiccService mEuiccServiceBinder;
    private IMockEuiccServiceCallback mCallback;
    private TestEuiccService mEuiccService;

    private CountDownLatch mCountDownLatch;

    @Rule public ServiceTestRule mServiceTestRule = new ServiceTestRule();

    private static class TestEuiccService extends EuiccService {
        @Override
        public String onGetEid(int slotId) {
            return null;
        }

        @Override
        public long onGetAvailableMemoryInBytes(int slotId) {
            return EuiccManager.EUICC_MEMORY_FIELD_UNAVAILABLE;
        }

        @Override
        public int onGetOtaStatus(int slotId) {
            return 0;
        }

        @Override
        public void onStartOtaIfNecessary(int slotId,
                OtaStatusChangedCallback statusChangedCallback) {
        }

        @Override
        public GetDownloadableSubscriptionMetadataResult onGetDownloadableSubscriptionMetadata(
                int slotId, DownloadableSubscription subscription, boolean forceDeactivateSim) {
            return null;
        }

        @Override
        public GetDownloadableSubscriptionMetadataResult onGetDownloadableSubscriptionMetadata(
                int slotId, int portIndex, DownloadableSubscription subscription,
                boolean forceDeactivateSim) {
            return null;
        }

        @Override
        public GetDefaultDownloadableSubscriptionListResult
                onGetDefaultDownloadableSubscriptionList(
                int slotId, boolean forceDeactivateSim) {
            return null;
        }

        @Override
        public GetEuiccProfileInfoListResult onGetEuiccProfileInfoList(int slotId) {
            return null;
        }

        @Override
        public EuiccInfo onGetEuiccInfo(int slotId) {
            return null;
        }

        @Override
        public int onDeleteSubscription(int slotId, String iccid) {
            return 0;
        }

        @Override
        public int onSwitchToSubscription(int slotId, String iccid, boolean forceDeactivateSim) {
            return 0;
        }

        @Override
        public int onSwitchToSubscriptionWithPort(int slotId, int portIndex, String iccid,
                boolean forceDeactivateSim) {
            return 0;
        }

        @Override
        public int onUpdateSubscriptionNickname(int slotId, String iccid, String nickname) {
            return 0;
        }

        @Override
        public int onEraseSubscriptions(int slotId) {
            return 0;
        }

        @Override
        public int onRetainSubscriptionsForFactoryReset(int slotId) {
            return 0;
        }
    }

    @Before
    public void setUp() throws Exception {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(EuiccUtil.hasEuiccFeature());
        }

        mCallback = new MockEuiccServiceCallback();
        MockEuiccService.setCallback(mCallback);

        mEuiccService = new TestEuiccService();

        Intent mockServiceIntent = new Intent(getContext(), MockEuiccService.class);
        IBinder binder = mServiceTestRule.bindService(mockServiceIntent);
        mEuiccServiceBinder = IEuiccService.Stub.asInterface(binder);
    }

    @After
    public void tearDown() throws Exception {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            if (!EuiccUtil.hasEuiccFeature()) {
                return;
            }
        }

        mServiceTestRule.unbindService();
        mCallback.reset();
    }

    static class MockEuiccServiceCallback implements IMockEuiccServiceCallback {
        private boolean mMethodCalled;

        @Override
        public void setMethodCalled() {
            mMethodCalled = true;
        }

        @Override
        public boolean isMethodCalled() {
            return mMethodCalled;
        }

        @Override
        public void reset() {
            mMethodCalled = false;
        }
    }

    private Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    @Test
    public void testOnGetEid() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getEid(
                MOCK_SLOT_ID,
                new IGetEidCallback.Stub() {
                    @Override
                    public void onSuccess(String eid) {
                        assertEquals(MockEuiccService.MOCK_EID, eid);
                        mCountDownLatch.countDown();
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ESIM_AVAILABLE_MEMORY)
    public void testOnGetAvailableMemoryInBytes_onSuccess() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getAvailableMemoryInBytes(
                MOCK_SLOT_ID,
                new IGetAvailableMemoryInBytesCallback.Stub() {
                    @Override
                    public void onSuccess(long availableMemoryInBytes) {
                        assertEquals(
                                MockEuiccService.MOCK_AVAILABLE_MEMORY, availableMemoryInBytes);
                        mCountDownLatch.countDown();
                    }

                    @Override
                    public void onUnsupportedOperationException(String message) {
                        fail("method should not be called");
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ESIM_AVAILABLE_MEMORY)
    public void testOnGetAvailableMemoryInBytes_onUnsupportedOperationException()
            throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getAvailableMemoryInBytes(
                MOCK_SLOT_ID_EXCEPTION,
                new IGetAvailableMemoryInBytesCallback.Stub() {
                    @Override
                    public void onSuccess(long availableMemoryInBytes) {
                        fail("method should not be called");
                    }

                    @Override
                    public void onUnsupportedOperationException(String message) {
                        assertTrue(message != null && !message.isEmpty());
                        mCountDownLatch.countDown();
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnGetOtaStatus() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getOtaStatus(
                MOCK_SLOT_ID,
                new IGetOtaStatusCallback.Stub() {
                    @Override
                    public void onSuccess(int status) {
                        assertEquals(EuiccManager.EUICC_OTA_SUCCEEDED, status);
                        mCountDownLatch.countDown();
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnGetDownloadableSubscriptionMetadata() throws Exception {
        DownloadableSubscription subscription =
                DownloadableSubscription.forActivationCode(ACTIVATION_CODE);

        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getDownloadableSubscriptionMetadata(
                MOCK_SLOT_ID,
                TelephonyManager.DEFAULT_PORT_INDEX,
                subscription,
                false /* switchAfterDownload */,
                true /*forceDeactivateSim*/,
                new IGetDownloadableSubscriptionMetadataCallback.Stub() {
                    @Override
                    public void onComplete(GetDownloadableSubscriptionMetadataResult result) {
                        assertNotNull(result);
                        assertEquals(EuiccService.RESULT_OK, result.getResult());
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnGetDownloadableSubscriptionMetadataWithPortIndex() throws Exception {
        DownloadableSubscription subscription =
                DownloadableSubscription.forActivationCode(ACTIVATION_CODE);

        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getDownloadableSubscriptionMetadata(
                MOCK_SLOT_ID,
                TelephonyManager.DEFAULT_PORT_INDEX,
                subscription,
                true /* switchAfterDownload */,
                true /*forceDeactivateSim*/,
                new IGetDownloadableSubscriptionMetadataCallback.Stub() {
                    @Override
                    public void onComplete(GetDownloadableSubscriptionMetadataResult result) {
                        assertNotNull(result);
                        assertEquals(EuiccService.RESULT_OK, result.getResult());
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnStartOtaIfNecessary() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.startOtaIfNecessary(
                MOCK_SLOT_ID,
                new IOtaStatusChangedCallback.Stub() {
                    @Override
                    public void onOtaStatusChanged(int status) {
                        assertEquals(EuiccManager.EUICC_OTA_SUCCEEDED, status);
                        mCountDownLatch.countDown();
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnGetDefaultDownloadableSubscriptionList() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getDefaultDownloadableSubscriptionList(
                MOCK_SLOT_ID,
                true /*forceDeactivateSim*/,
                new IGetDefaultDownloadableSubscriptionListCallback.Stub() {
                    @Override
                    public void onComplete(GetDefaultDownloadableSubscriptionListResult result) {
                        assertNotNull(result);
                        assertEquals(EuiccService.RESULT_RESOLVABLE_ERRORS, result.getResult());
                        mCountDownLatch.countDown();
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnDownloadSubscription() throws Exception {
        DownloadableSubscription subscription =
                DownloadableSubscription.forActivationCode(ACTIVATION_CODE);

        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.downloadSubscription(
                MOCK_SLOT_ID,
                MOCK_PORT_ID,
                subscription,
                true /*switchAfterDownload*/,
                true /*forceDeactivateSim*/,
                null /*resolvedBundle*/,
                new IDownloadSubscriptionCallback.Stub() {
                    @Override
                    public void onComplete(DownloadSubscriptionResult result) {
                        assertNotNull(result);
                        assertEquals(EuiccService.RESULT_OK, result.getResult());
                        mCountDownLatch.countDown();
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnGetEuiccProfileInfoList() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getEuiccProfileInfoList(
                MOCK_SLOT_ID,
                new IGetEuiccProfileInfoListCallback.Stub() {
                    @Override
                    public void onComplete(GetEuiccProfileInfoListResult result) {
                        assertNotNull(result);
                        assertEquals(EuiccService.RESULT_RESOLVABLE_ERRORS, result.getResult());
                        mCountDownLatch.countDown();
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnGetEuiccInfo() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.getEuiccInfo(
                MOCK_SLOT_ID,
                new IGetEuiccInfoCallback.Stub() {
                    @Override
                    public void onSuccess(EuiccInfo euiccInfo) {
                        assertNotNull(euiccInfo);
                        assertEquals(MockEuiccService.MOCK_OS_VERSION, euiccInfo.getOsVersion());
                        mCountDownLatch.countDown();
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnDeleteSubscription() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.deleteSubscription(
                MOCK_SLOT_ID,
                MOCK_ICCID,
                new IDeleteSubscriptionCallback.Stub() {
                    @Override
                    public void onComplete(int result) {
                        assertEquals(EuiccService.RESULT_OK, result);
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnSwitchToSubscription() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.switchToSubscription(
                MOCK_SLOT_ID,
                MOCK_PORT_ID,
                MOCK_ICCID,
                true /*forceDeactivateSim*/,
                new ISwitchToSubscriptionCallback.Stub() {
                    @Override
                    public void onComplete(int result) {
                        assertEquals(EuiccService.RESULT_OK, result);
                    }
                },
                false /* usePortIndex */);

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnSwitchToSubscriptionWithPort() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.switchToSubscription(
                MOCK_SLOT_ID,
                MOCK_PORT_ID,
                MOCK_ICCID,
                true /*forceDeactivateSim*/,
                new ISwitchToSubscriptionCallback.Stub() {
                    @Override
                    public void onComplete(int result) {
                        assertEquals(EuiccService.RESULT_OK, result);
                    }
                },
                true /* usePortIndex */);

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnUpdateSubscriptionNickname() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.updateSubscriptionNickname(
                MOCK_SLOT_ID,
                MOCK_ICCID,
                MOCK_NICK_NAME,
                new IUpdateSubscriptionNicknameCallback.Stub() {
                    @Override
                    public void onComplete(int result) {
                        assertEquals(EuiccService.RESULT_OK, result);
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnEraseSubscriptions() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.eraseSubscriptions(
                MOCK_SLOT_ID,
                new IEraseSubscriptionsCallback.Stub() {
                    @Override
                    public void onComplete(int result) {
                        assertEquals(EuiccService.RESULT_OK, result);
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testOnRetainSubscriptionsForFactoryReset() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.retainSubscriptionsForFactoryReset(
                MOCK_SLOT_ID,
                new IRetainSubscriptionsForFactoryResetCallback.Stub() {
                    @Override
                    public void onComplete(int result) {
                        assertEquals(EuiccService.RESULT_OK, result);
                    }
                });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testDump() throws Exception {
        mCountDownLatch = new CountDownLatch(1);

        mEuiccServiceBinder.dump(new IEuiccServiceDumpResultCallback.Stub() {
            @Override
            public void onComplete(String logs) {
                assertEquals(MockEuiccService.MOCK_DUMPED_LOG, logs);
                mCountDownLatch.countDown();
            }
        });

        try {
            mCountDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        assertTrue(mCallback.isMethodCalled());
    }

    @Test
    public void testEncodeSmdxSubjectAndReasonCode() {
        assertEquals(mEuiccService.encodeSmdxSubjectAndReasonCode("8.11.1", "5.1.1"), 0xA8B1511);
        assertEquals(mEuiccService.encodeSmdxSubjectAndReasonCode("1", "1"), 0xA001001);
        assertEquals(mEuiccService.encodeSmdxSubjectAndReasonCode("3.0.1", "5.1"), 0xA301051);
        assertEquals(mEuiccService.encodeSmdxSubjectAndReasonCode("8.1", "5"), 0xA081005);
    }
}
