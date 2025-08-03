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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.os.Handler;
import android.os.Process;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ConnectionFailureNotifier}.
 */
@SmallTest
public class ConnectionFailureNotifierTest extends WifiBaseTest {
    @Mock private WifiContext mContext;
    @Mock private Resources mResources;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private WifiConfigManager mWifiConfigManager;
    @Mock private WifiConnectivityManager mWifiConnectivityManager;
    @Mock private WifiNotificationManager mWifiNotificationManager;
    @Mock private WifiDialogManager mWifiDialogManager;
    @Mock private ConnectionFailureNotificationBuilder mConnectionFailureNotificationBuilder;
    @Mock private Notification mNotification;
    @Mock private WifiDialogManager.DialogHandle mDialogHandle;

    private final ArgumentCaptor<BroadcastReceiver> mBroadCastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);
    private ConnectionFailureNotifier mConnectionFailureNotifier;
    private TestLooper mLooper;

    /** Initialize objects before each test run. */
    @Before
    public void setUp() throws Exception {
        // Ensure looper exists
        mLooper = new TestLooper();
        MockitoAnnotations.initMocks(this);
        when(mContext.getResources()).thenReturn(mResources);
        when(mConnectionFailureNotificationBuilder
                .buildNoMacRandomizationSupportNotification(any())).thenReturn(mNotification);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mDialogHandle);
        mConnectionFailureNotifier = new ConnectionFailureNotifier(
                mContext, mFrameworkFacade, mWifiConfigManager, mWifiConnectivityManager,
                new Handler(mLooper.getLooper()), mWifiNotificationManager,
                mConnectionFailureNotificationBuilder, mWifiDialogManager);
        when(mResources.getString(
                eq(R.string.wifi_disable_mac_randomization_dialog_message), anyString()))
                .thenAnswer(s -> "blah" + s.getArguments()[1]);

        verify(mContext).registerReceiver(mBroadCastReceiverCaptor.capture(), any());
    }

    private class DisableMacRandomizationMatcher implements ArgumentMatcher<WifiConfiguration> {
        @Override
        public boolean matches(WifiConfiguration config) {
            return config.macRandomizationSetting == WifiConfiguration.RANDOMIZATION_NONE;
        }
    }

    // Returns an intent that simulates the broadcast which is received when the user tap
    // on the notification to change MAC randomization settings.
    private Intent buildBroadcastForRandomizationSettingsDialog(WifiConfiguration config) {
        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(ConnectionFailureNotificationBuilder
                .ACTION_SHOW_SET_RANDOMIZATION_DETAILS);
        when(intent.getIntExtra(eq(ConnectionFailureNotificationBuilder
                .RANDOMIZATION_SETTINGS_NETWORK_ID), anyInt())).thenReturn(config.networkId);
        when(intent.getStringExtra(
                eq(ConnectionFailureNotificationBuilder.RANDOMIZATION_SETTINGS_NETWORK_SSID)))
                .thenReturn(config.getSsidAndSecurityTypeString());
        return intent;
    }

    /**
     * Verify that a notification is posted when a connection failure happens on a network
     * in the hotlist. Then verify that tapping on the notification launches an dialog, which
     * could be used to set the randomization setting for a network to "Trusted".
     */
    @Test
    public void testConnectionFailureSendRandomizationSettingsNotification() {
        // Verify that the network is using randomized MAC at the start.
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        when(mWifiConfigManager.getConfiguredNetwork(config.networkId)).thenReturn(config);
        assertEquals(WifiConfiguration.RANDOMIZATION_AUTO, config.macRandomizationSetting);

        mConnectionFailureNotifier.showFailedToConnectDueToNoRandomizedMacSupportNotification(
                config.networkId);
        // verify that a notification is sent
        verify(mWifiNotificationManager).notify(
                eq(SystemMessage.NOTE_NETWORK_NO_MAC_RANDOMIZATION_SUPPORT), eq(mNotification));

        // sets up the intent that simulates the user tapping on the notification.
        Intent intent = buildBroadcastForRandomizationSettingsDialog(config);

        // simulate the user tapping on the notification, then verify the dialog shows up, and
        // the appropriate callback is registered
        ArgumentCaptor<DialogInterface.OnClickListener>  onClickListenerArgumentCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        mBroadCastReceiverCaptor.getValue().onReceive(mContext, intent);
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mDialogHandle).launchDialog();
        verify(mWifiDialogManager).createSimpleDialog(
                any(), messageCaptor.capture(), any(), any(), any(),
                dialogCallbackCaptor.capture(), any());
        String message = messageCaptor.getValue();
        assertNotNull(message);
        assertTrue(message.contains(config.SSID));

        // simulate the user tapping on the option to reset MAC address to factory MAC
        dialogCallbackCaptor.getValue().onPositiveButtonClicked();

        // verify the WifiConfiguration is updated properly.
        verify(mWifiConfigManager).addOrUpdateNetwork(
                argThat(new DisableMacRandomizationMatcher()), eq(Process.SYSTEM_UID));
        // verify that we try to connect to the updated network.
        verify(mWifiConnectivityManager).forceConnectivityScan(any());
    }

    /**
     * Verify that if the WifiConfiguration if not found (may have been deleted by the timed the
     * notification is tapped), then the AlertDialog does not show up.
     */
    @Test
    public void testWifiConfigurationMismatch() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        when(mWifiConfigManager.getConfiguredNetwork(config.networkId)).thenReturn(config);
        mConnectionFailureNotifier.showFailedToConnectDueToNoRandomizedMacSupportNotification(
                config.networkId);
        // verify that a notification is sent
        verify(mWifiNotificationManager).notify(
                eq(SystemMessage.NOTE_NETWORK_NO_MAC_RANDOMIZATION_SUPPORT), any());

        // sets up the intent that simulates the user tapping on the notification.
        Intent intent = buildBroadcastForRandomizationSettingsDialog(config);

        // the WifiConfiguration that is found doesn't match with the one received from broadcast.
        when(mWifiConfigManager.getConfiguredNetwork(anyInt()))
                .thenReturn(WifiConfigurationTestUtil.createOpenNetwork());
        mBroadCastReceiverCaptor.getValue().onReceive(mContext, intent);

        // verify that the AlertDialog is not launched in this case
        verify(mWifiDialogManager, never())
                .createSimpleDialog(any(), any(), any(), any(), any(), any(), any());
        verify(mDialogHandle, never()).launchDialog();

        verify(mFrameworkFacade, never()).makeAlertDialogBuilder(any());
        // instead we are showings a toast due to failing to find the network
        verify(mFrameworkFacade).showToast(any(), any());
    }
}
