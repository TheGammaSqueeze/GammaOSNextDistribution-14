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

package com.android.server.nearby.provider;

import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;

import static com.android.server.nearby.NearbyConfiguration.NEARBY_MAINLINE_NANO_APP_MIN_VERSION;
import static com.android.server.nearby.provider.ChreCommunication.INVALID_NANO_APP_VERSION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppMessage;
import android.hardware.location.NanoAppState;
import android.os.Build;
import android.provider.DeviceConfig;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.nearby.NearbyConfiguration;
import com.android.server.nearby.injector.Injector;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public class ChreCommunicationTest {
    private static final String NAMESPACE = NearbyConfiguration.getNamespace();
    private static final int APP_VERSION = 1;

    @Mock Injector mInjector;
    @Mock Context mContext;
    @Mock ContextHubManager mManager;
    @Mock ContextHubTransaction<List<NanoAppState>> mTransaction;
    @Mock ContextHubTransaction.Response<List<NanoAppState>> mTransactionResponse;
    @Mock ContextHubClient mClient;
    @Mock ChreCommunication.ContextHubCommsCallback mChreCallback;

    @Captor
    ArgumentCaptor<ChreCommunication.OnQueryCompleteListener> mOnQueryCompleteListenerCaptor;

    private ChreCommunication mChreCommunication;

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(WRITE_DEVICE_CONFIG, READ_DEVICE_CONFIG);
        DeviceConfig.setProperty(
                NAMESPACE, NEARBY_MAINLINE_NANO_APP_MIN_VERSION, "1", false);

        MockitoAnnotations.initMocks(this);
        when(mInjector.getContextHubManager()).thenReturn(mManager);
        when(mManager.getContextHubs()).thenReturn(Collections.singletonList(new ContextHubInfo()));
        when(mManager.queryNanoApps(any())).thenReturn(mTransaction);
        when(mManager.createClient(any(), any(), any(), any())).thenReturn(mClient);
        when(mTransactionResponse.getResult()).thenReturn(ContextHubTransaction.RESULT_SUCCESS);
        when(mTransactionResponse.getContents())
                .thenReturn(
                        Collections.singletonList(
                                new NanoAppState(
                                        ChreDiscoveryProvider.NANOAPP_ID,
                                        APP_VERSION,
                                        true)));

        mChreCommunication = new ChreCommunication(mInjector, mContext, new InlineExecutor());
    }

    @Test
    public void testStart() {
        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));

        verify(mTransaction).setOnCompleteListener(mOnQueryCompleteListenerCaptor.capture(), any());
        mOnQueryCompleteListenerCaptor.getValue().onComplete(mTransaction, mTransactionResponse);
        verify(mChreCallback).started(true);
    }

    @Test
    public void testStop() {
        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));

        verify(mTransaction).setOnCompleteListener(mOnQueryCompleteListenerCaptor.capture(), any());
        mOnQueryCompleteListenerCaptor.getValue().onComplete(mTransaction, mTransactionResponse);
        mChreCommunication.stop();
        verify(mClient).close();
    }

    @Test
    public void testNotReachMinVersion() {
        DeviceConfig.setProperty(NAMESPACE, NEARBY_MAINLINE_NANO_APP_MIN_VERSION, "3", false);
        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));
        verify(mTransaction).setOnCompleteListener(mOnQueryCompleteListenerCaptor.capture(), any());
        mOnQueryCompleteListenerCaptor.getValue().onComplete(mTransaction, mTransactionResponse);
        verify(mChreCallback).started(false);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void test_getNanoVersion() {
        assertThat(mChreCommunication.queryNanoAppVersion()).isEqualTo(INVALID_NANO_APP_VERSION);

        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));
        verify(mTransaction).setOnCompleteListener(mOnQueryCompleteListenerCaptor.capture(), any());
        mOnQueryCompleteListenerCaptor.getValue().onComplete(mTransaction, mTransactionResponse);

        assertThat(mChreCommunication.queryNanoAppVersion()).isEqualTo(APP_VERSION);
    }

    @Test
    public void testSendMessageToNanApp() {
        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));
        verify(mTransaction).setOnCompleteListener(mOnQueryCompleteListenerCaptor.capture(), any());
        mOnQueryCompleteListenerCaptor.getValue().onComplete(mTransaction, mTransactionResponse);
        NanoAppMessage message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER,
                        new byte[] {1, 2, 3});
        mChreCommunication.sendMessageToNanoApp(message);
        verify(mClient).sendMessageToNanoApp(eq(message));
    }

    @Test
    public void testOnMessageFromNanoApp() {
        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));
        NanoAppMessage message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER_RESULT,
                        new byte[] {1, 2, 3});
        mChreCommunication.onMessageFromNanoApp(mClient, message);
        verify(mChreCallback).onMessageFromNanoApp(eq(message));
    }

    @Test
    public void testContextHubTransactionResultToString() {
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_SUCCESS))
                .isEqualTo("RESULT_SUCCESS");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_FAILED_UNKNOWN))
                .isEqualTo("RESULT_FAILED_UNKNOWN");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_FAILED_BAD_PARAMS))
                .isEqualTo("RESULT_FAILED_BAD_PARAMS");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_FAILED_UNINITIALIZED))
                .isEqualTo("RESULT_FAILED_UNINITIALIZED");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_FAILED_BUSY))
                .isEqualTo("RESULT_FAILED_BUSY");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_FAILED_AT_HUB))
                .isEqualTo("RESULT_FAILED_AT_HUB");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_FAILED_TIMEOUT))
                .isEqualTo("RESULT_FAILED_TIMEOUT");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_FAILED_SERVICE_INTERNAL_FAILURE))
                .isEqualTo("RESULT_FAILED_SERVICE_INTERNAL_FAILURE");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(
                        ContextHubTransaction.RESULT_FAILED_HAL_UNAVAILABLE))
                .isEqualTo("RESULT_FAILED_HAL_UNAVAILABLE");
        assertThat(
                mChreCommunication.contextHubTransactionResultToString(9))
                .isEqualTo("UNKNOWN_RESULT value=9");
    }

    @Test
    public void testOnHubReset() {
        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));
        mChreCommunication.onHubReset(mClient);
        verify(mChreCallback).onHubReset();
    }

    @Test
    public void testOnNanoAppLoaded() {
        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));
        mChreCommunication.onNanoAppLoaded(mClient, ChreDiscoveryProvider.NANOAPP_ID);
        verify(mChreCallback).onNanoAppRestart(eq(ChreDiscoveryProvider.NANOAPP_ID));
    }

    private static class InlineExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
