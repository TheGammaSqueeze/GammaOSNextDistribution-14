/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.telephony.qns.wfc;

import static androidx.test.ext.truth.content.IntentSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.TestLooperManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link WfcActivationHelper} */
@RunWith(AndroidJUnit4.class)
public final class WfcActivationHelperTest {
    private static final int RANDOM_INT = 3721;
    private static final int RANDOM_MESSAGE_ID = 2000;
    private static final int TIMEOUT_MS = 1000;
    private static final int SUB_ID = 1;
    private static final ImsReasonInfo IMS_IKEV2_AUTH_FAILURE =
            new ImsReasonInfo(
                    ImsReasonInfo.CODE_EPDG_TUNNEL_ESTABLISH_FAILURE,
                    ImsReasonInfo.CODE_IKEV2_AUTH_FAILURE,
                    null);

    @Mock ConnectivityManager mockConnectivityManager;
    @Mock ImsMmTelManager mockImsMmTelManager;
    @Mock private WfcCarrierConfigManager mockCarrierConfigManager;
    @Mock NetworkInfo mockNetworkInfo;
    @Captor ArgumentCaptor<WfcActivationHelper.ImsCallback> imsCallbackCaptor;
    @Captor ArgumentCaptor<Intent> tryWfcConnectionIntentCaptor;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private HandlerThread mUiThread;
    private TestLooperManager mTestLooperManager;
    private Message mMessage;
    private Message[] mCaughtMessages;
    private WfcActivationHelper mTestHelper;
    private Handler mHandler;

    private final Application appContext = spy(ApplicationProvider.getApplicationContext());

    @Before
    public void setUp() {
        mUiThread = new HandlerThread("MockUiThread");
        mUiThread.start();

        mCaughtMessages = new Message[] {new Message()};
        mHandler =
                new Handler(
                        mUiThread.getLooper(),
                        (Message msg) -> {
                            if (msg.what == RANDOM_MESSAGE_ID) {
                                mCaughtMessages[0].copyFrom(msg);
                                return true;
                            }
                            return false;
                        });
        mMessage = Message.obtain(mHandler, RANDOM_MESSAGE_ID, RANDOM_INT, 0);
        mTestLooperManager =
                InstrumentationRegistry.getInstrumentation()
                        .acquireLooperManager(mUiThread.getLooper());

        mTestHelper =
                new WfcActivationHelper(
                        appContext,
                        SUB_ID,
                        mockConnectivityManager,
                        mockImsMmTelManager,
                        mockCarrierConfigManager,
                        Runnable::run);
    }

    @After
    public void tearDown() {
        mTestLooperManager.release();
        mUiThread.quit();
    }

    @Test
    public void checkWiFi_networkInfoTypeWiFi_wiFiConnectionSuccess() throws Exception {
        when(mockConnectivityManager.getActiveNetworkInfo()).thenReturn(mockNetworkInfo);
        when(mockNetworkInfo.isConnected()).thenReturn(true);
        when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);

        mTestHelper.checkWiFi(mMessage);

        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(mCaughtMessages[0].what).isEqualTo(RANDOM_MESSAGE_ID);
        assertThat(mCaughtMessages[0].arg1).isEqualTo(WfcActivationHelper.WIFI_CONNECTION_SUCCESS);
    }

    @Test
    public void checkWiFi_networkInfoTypeMobile_wiFiConnectionError() throws Exception {
        when(mockConnectivityManager.getActiveNetworkInfo()).thenReturn(mockNetworkInfo);
        when(mockNetworkInfo.isConnected()).thenReturn(true);
        when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_MOBILE);

        mTestHelper.checkWiFi(mMessage);

        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(mCaughtMessages[0].what).isEqualTo(RANDOM_MESSAGE_ID);
        assertThat(mCaughtMessages[0].arg1).isEqualTo(WfcActivationHelper.WIFI_CONNECTION_ERROR);
    }

    @Test
    public void tryEpdgConnectionOverWiFi_imsMmTelManagerNull_wiFiConnectionError()
            throws Exception {
        mTestHelper =
                new WfcActivationHelper(
                        appContext,
                        SUB_ID,
                        mockConnectivityManager,
                        null,
                        mockCarrierConfigManager,
                        Runnable::run);

        mTestHelper.tryEpdgConnectionOverWiFi(mMessage, TIMEOUT_MS);

        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(mCaughtMessages[0].what).isEqualTo(RANDOM_MESSAGE_ID);
        assertThat(mCaughtMessages[0].arg1).isEqualTo(WfcActivationHelper.WIFI_CONNECTION_ERROR);
    }

    @Test
    public void tryEpdgConnectionOverWiFi_showVowifiPortalFalse_resultSuccess() throws Exception {
        when(mockCarrierConfigManager.isShowVowifiPortalAfterTimeout()).thenReturn(false);

        mTestHelper.tryEpdgConnectionOverWiFi(mMessage, TIMEOUT_MS);

        moveLooperToEvent(WfcActivationHelper.EVENT_PRE_START_ATTEMPT);
        verify(mockImsMmTelManager)
                .registerImsRegistrationCallback(any(), imsCallbackCaptor.capture());

        moveLooperToEvent(WfcActivationHelper.EVENT_FINISH_ATTEMPT);
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(mCaughtMessages[0].what).isEqualTo(RANDOM_MESSAGE_ID);
        assertThat(mCaughtMessages[0].arg1).isEqualTo(WfcActivationHelper.EPDG_CONNECTION_SUCCESS);
    }

    @Test
    public void tryEpdgConnectionOverWiFi_showVowifiPortalTrue_resultError() throws Exception {
        when(mockCarrierConfigManager.isShowVowifiPortalAfterTimeout()).thenReturn(true);

        mTestHelper.tryEpdgConnectionOverWiFi(mMessage, TIMEOUT_MS);

        moveLooperToEvent(WfcActivationHelper.EVENT_PRE_START_ATTEMPT);
        verify(mockImsMmTelManager)
                .registerImsRegistrationCallback(any(), imsCallbackCaptor.capture());

        moveLooperToEvent(WfcActivationHelper.EVENT_FINISH_ATTEMPT);
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(mCaughtMessages[0].what).isEqualTo(RANDOM_MESSAGE_ID);
        assertThat(mCaughtMessages[0].arg1).isEqualTo(WfcActivationHelper.EPDG_CONNECTION_ERROR);
    }

    @Test
    public void tryEpdgConnectionOverWiFi_success() throws Exception {
        mTestHelper.tryEpdgConnectionOverWiFi(mMessage, TIMEOUT_MS);
        InOrder inOrderIntent = Mockito.inOrder(appContext);

        moveLooperToEvent(WfcActivationHelper.EVENT_PRE_START_ATTEMPT);
        verify(mockImsMmTelManager)
                .registerImsRegistrationCallback(any(), imsCallbackCaptor.capture());
        ImsMmTelManager.RegistrationCallback imsCallback = imsCallbackCaptor.getValue();

        // During the PRE_EPDG_CONNECTION_DELAY_MS, received registration failure: no op.
        imsCallback.onUnregistered(IMS_IKEV2_AUTH_FAILURE);

        moveLooperToEvent(WfcActivationHelper.EVENT_START_ATTEMPT);
        assertThat(mCaughtMessages[0].what).isNotEqualTo(RANDOM_MESSAGE_ID);
        // After PRE_EPDG_CONNECTION_DELAY_MS, start triggering ePDG connection
        verify(mockImsMmTelManager).setVoWiFiNonPersistent(true, /* WiFi preferred*/ 2);
        inOrderIntent.verify(appContext).sendBroadcast(tryWfcConnectionIntentCaptor.capture());
        verifyWfcIntent(tryWfcConnectionIntentCaptor.getValue(), WfcActivationHelper.STATUS_START);

        // Eeceived registration success: return success
        imsCallback.onRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        moveLooperToEvent(WfcActivationHelper.EVENT_FINISH_ATTEMPT);
        mTestLooperManager.execute(mTestLooperManager.next());
        verify(mockImsMmTelManager).unregisterImsRegistrationCallback(imsCallback);
        assertThat(mCaughtMessages[0].what).isEqualTo(RANDOM_MESSAGE_ID);
        assertThat(mCaughtMessages[0].arg1).isEqualTo(WfcActivationHelper.EPDG_CONNECTION_SUCCESS);
        verify(mockImsMmTelManager).setVoWiFiSettingEnabled(true);
        inOrderIntent.verify(appContext).sendBroadcast(tryWfcConnectionIntentCaptor.capture());
        verifyWfcIntent(tryWfcConnectionIntentCaptor.getValue(), WfcActivationHelper.STATUS_END);
    }

    @Test
    public void tryEpdgConnectionOverWiFi_failure() throws Exception {
        mTestHelper.tryEpdgConnectionOverWiFi(mMessage, TIMEOUT_MS);
        InOrder inOrderIntent = Mockito.inOrder(appContext);

        moveLooperToEvent(WfcActivationHelper.EVENT_PRE_START_ATTEMPT);
        verify(mockImsMmTelManager)
                .registerImsRegistrationCallback(any(), imsCallbackCaptor.capture());
        ImsMmTelManager.RegistrationCallback imsCallback = imsCallbackCaptor.getValue();

        // After PRE_EPDG_CONNECTION_DELAY_MS, start triggering ePDG connection
        moveLooperToEvent(WfcActivationHelper.EVENT_START_ATTEMPT);
        verify(mockImsMmTelManager).setVoWiFiNonPersistent(true, /* WiFi preferred */ 2);
        inOrderIntent.verify(appContext).sendBroadcast(tryWfcConnectionIntentCaptor.capture());
        verifyWfcIntent(tryWfcConnectionIntentCaptor.getValue(), WfcActivationHelper.STATUS_START);

        imsCallback.onTechnologyChangeFailed(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, IMS_IKEV2_AUTH_FAILURE);

        moveLooperToEvent(WfcActivationHelper.EVENT_FINISH_ATTEMPT);
        mTestLooperManager.execute(mTestLooperManager.next());
        verify(mockImsMmTelManager).setVoWiFiNonPersistent(false, /* mobile preferred */ 1);
        verify(mockImsMmTelManager).unregisterImsRegistrationCallback(imsCallback);
        assertThat(mCaughtMessages[0].what).isEqualTo(RANDOM_MESSAGE_ID);
        assertThat(mCaughtMessages[0].arg1).isEqualTo(WfcActivationHelper.EPDG_CONNECTION_ERROR);
        inOrderIntent.verify(appContext).sendBroadcast(tryWfcConnectionIntentCaptor.capture());
        verifyWfcIntent(tryWfcConnectionIntentCaptor.getValue(), WfcActivationHelper.STATUS_END);
        // Do not change WFC ON/OFF
        verify(mockImsMmTelManager, never()).setVoWiFiSettingEnabled(anyBoolean());
    }

    private static void verifyWfcIntent(Intent intent, int tryStatus) {
        assertThat(intent).hasAction(WfcActivationHelper.ACTION_TRY_WFC_CONNECTION);
        assertThat(intent).extras().integer(WfcActivationHelper.EXTRA_SUB_ID).isEqualTo(SUB_ID);
        assertThat(intent)
                .extras()
                .integer(WfcActivationHelper.EXTRA_TRY_STATUS)
                .isEqualTo(tryStatus);
    }

    void moveLooperToEvent(int event) {
        Message msg;
        do {
            msg = mTestLooperManager.next();
            mTestLooperManager.execute(msg);
        } while (msg.what != event);
    }
}
