/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiDialogManager.DialogHandle;
import com.android.server.wifi.WifiDialogManager.P2pInvitationReceivedDialogCallback;
import com.android.server.wifi.WifiDialogManager.SimpleDialogCallback;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link WifiDialogManager}.
 */
@SmallTest
public class WifiDialogManagerTest extends WifiBaseTest {
    private static final int TIMEOUT_MILLIS = 30_000;
    private static final String TEST_TITLE = "Title";
    private static final String TEST_MESSAGE = "Message";
    private static final String TEST_POSITIVE_BUTTON_TEXT = "Yes";
    private static final String TEST_NEGATIVE_BUTTON_TEXT = "No";
    private static final String TEST_NEUTRAL_BUTTON_TEXT = "Maybe";
    private static final String TEST_DEVICE_NAME = "TEST_DEVICE_NAME";
    private static final String WIFI_DIALOG_APK_PKG_NAME = "WifiDialogApkPkgName";

    @Mock WifiContext mWifiContext;
    @Mock WifiThreadRunner mWifiThreadRunner;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock Resources mResources;
    @Mock ActivityManager mActivityManager;
    @Mock WifiInjector mWifiInjector;
    @Mock WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;
    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverArgumentCaptor;
    private WifiDialogManager mDialogManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mWifiContext.getWifiDialogApkPkgName()).thenReturn(WIFI_DIALOG_APK_PKG_NAME);
        when(mWifiContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        when(mWifiContext.getResources()).thenReturn(mResources);
        when(mWifiInjector.getWifiDeviceStateChangeManager())
                .thenReturn(mWifiDeviceStateChangeManager);
        doThrow(SecurityException.class).when(mWifiContext).startActivityAsUser(any(), any(),
                any());
        mDialogManager = new WifiDialogManager(mWifiContext, mWifiThreadRunner, mFrameworkFacade,
                mWifiInjector);
        mDialogManager.enableVerboseLogging(true);
        verify(mWifiContext).registerReceiver(mBroadcastReceiverArgumentCaptor.capture(), any(),
                eq(SdkLevel.isAtLeastT() ? Context.RECEIVER_EXPORTED : 0));
    }

    private void dispatchMockWifiThreadRunner(WifiThreadRunner wifiThreadRunner) {
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(wifiThreadRunner, atLeastOnce()).post(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
    }

    /**
     * Helper method to synchronously call {@link DialogHandle#launchDialog(long)}.
     * @param dialogHandle     Dialog handle to call on.
     * @param timeoutMs        Timeout for {@link DialogHandle#launchDialog(long)}.
     * @param wifiThreadRunner Main Wi-Fi thread runner of the WifiDialogManager.
     */
    private void launchDialogSynchronous(
            @NonNull DialogHandle dialogHandle,
            long timeoutMs,
            @NonNull WifiThreadRunner wifiThreadRunner) {
        dialogHandle.launchDialog(timeoutMs);
        ArgumentCaptor<Runnable> launchRunnableArgumentCaptor =
                ArgumentCaptor.forClass(Runnable.class);
        verify(wifiThreadRunner, atLeastOnce()).post(launchRunnableArgumentCaptor.capture());
        launchRunnableArgumentCaptor.getValue().run();
    }

    /**
     * Helper method to synchronously call {@link DialogHandle#dismissDialog()}.
     * @param dialogHandle     Dialog handle to call on.
     * @param wifiThreadRunner Main Wi-Fi thread runner of the WifiDialogManager.
     */
    private void dismissDialogSynchronous(
            @NonNull DialogHandle dialogHandle,
            @NonNull WifiThreadRunner wifiThreadRunner) {
        dialogHandle.dismissDialog();
        ArgumentCaptor<Runnable> dismissRunnableArgumentCaptor =
                ArgumentCaptor.forClass(Runnable.class);
        verify(wifiThreadRunner, atLeastOnce()).post(dismissRunnableArgumentCaptor.capture());
        dismissRunnableArgumentCaptor.getValue().run();
    }

    /**
     * Helper method to verify startActivityAsUser was called a given amount of times and return the
     * last Intent that was sent.
     */
    @NonNull
    private Intent verifyStartActivityAsUser(
            int times,
            @NonNull WifiContext wifiContext) {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(wifiContext, times(times))
                .startActivityAsUser(intentArgumentCaptor.capture(), eq(UserHandle.CURRENT));
        return intentArgumentCaptor.getValue();
    }

    /**
     * Helper method to verify display-specific startActivityAsUser was called a given amount of
     * times and return the last Intent that was sent.
     */
    @NonNull
    private Intent verifyStartActivityAsUser(
            int times,
            int displayId,
            @NonNull WifiContext wifiContext) {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(wifiContext, times(times)).startActivityAsUser(intentArgumentCaptor.capture(),
                bundleArgumentCaptor.capture(), eq(UserHandle.CURRENT));
        assertEquals(ActivityOptions.makeBasic().setLaunchDisplayId(
                        displayId).toBundle().toString(),
                bundleArgumentCaptor.getValue().toString()); // since can't compare Bundles
        return intentArgumentCaptor.getValue();
    }

    /**
     * Helper method to verify the contents of a dismiss Intent
     */
    private void verifyDismissIntent(@NonNull Intent dismissIntent) {
        assertThat(dismissIntent.getAction()).isEqualTo(WifiManager.ACTION_DISMISS_DIALOG);
        ComponentName component = dismissIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(dismissIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        int dialogId = dismissIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID,
                WifiManager.INVALID_DIALOG_ID);
        assertThat(dialogId).isNotEqualTo(WifiManager.INVALID_DIALOG_ID);
    }

    /**
     * Helper method to verify the contents of a launch Intent for a simple dialog.
     * @return dialog id of the Intent.
     */
    private int verifySimpleDialogLaunchIntent(
            @NonNull Intent launchIntent,
            @Nullable String expectedTitle,
            @Nullable String expectedMessage,
            @Nullable String expectedPositiveButtonText,
            @Nullable String expectedNegativeButtonText,
            @Nullable String expectedNeutralButtonText) {
        assertThat(launchIntent.getAction()).isEqualTo(WifiManager.ACTION_LAUNCH_DIALOG);
        ComponentName component = launchIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        int dialogId = launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID,
                WifiManager.INVALID_DIALOG_ID);
        assertThat(dialogId).isNotEqualTo(WifiManager.INVALID_DIALOG_ID);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_TYPE)).isTrue();
        assertThat(launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_TYPE,
                WifiManager.DIALOG_TYPE_UNKNOWN))
                .isEqualTo(WifiManager.DIALOG_TYPE_SIMPLE);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_TITLE)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_TITLE))
                .isEqualTo(expectedTitle);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_MESSAGE)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_MESSAGE))
                .isEqualTo(expectedMessage);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_POSITIVE_BUTTON_TEXT)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_POSITIVE_BUTTON_TEXT))
                .isEqualTo(expectedPositiveButtonText);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_NEGATIVE_BUTTON_TEXT)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_NEGATIVE_BUTTON_TEXT))
                .isEqualTo(expectedNegativeButtonText);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_NEUTRAL_BUTTON_TEXT)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_NEUTRAL_BUTTON_TEXT))
                .isEqualTo(expectedNeutralButtonText);
        return dialogId;
    }

    /**
     * Verifies that launching a simple dialog will result in the correct callback methods invoked
     * when a response is received.
     */
    @Test
    public void testSimpleDialog_launchAndResponse_notifiesCallback() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);

        // Positive
        DialogHandle dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        mDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(0)).onNegativeButtonClicked();
        verify(callback, times(0)).onNeutralButtonClicked();
        verify(callback, times(0)).onCancelled();

        // Positive again -- callback should be removed from callback list, so a second notification
        // should be ignored.
        mDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_POSITIVE);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(0)).onNegativeButtonClicked();
        verify(callback, times(0)).onNeutralButtonClicked();
        verify(callback, times(0)).onCancelled();

        // Negative
        dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        mDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_NEGATIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(1)).onNegativeButtonClicked();
        verify(callback, times(0)).onNeutralButtonClicked();
        verify(callback, times(0)).onCancelled();

        // Neutral
        dialogHandle = mDialogManager.createSimpleDialog(
                TEST_TITLE, TEST_MESSAGE, TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT,
                TEST_NEUTRAL_BUTTON_TEXT, callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(3, mWifiContext);
        dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        mDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_NEUTRAL);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(1)).onNegativeButtonClicked();
        verify(callback, times(1)).onNeutralButtonClicked();
        verify(callback, times(0)).onCancelled();

        // Cancelled
        dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(4, mWifiContext);
        dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        mDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_CANCELLED);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(1)).onNegativeButtonClicked();
        verify(callback, times(1)).onNeutralButtonClicked();
        verify(callback, times(1)).onCancelled();
    }

    /**
     * Verifies that launching a simple dialog and dismissing it will send a dismiss intent and
     * prevent future replies to the original dialog id from notifying the callback.
     */
    @Test
    public void testSimpleDialog_launchAndDismiss_dismissesDialog() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        // Launch and dismiss dialog.
        DialogHandle dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        verifyDismissIntent(intent);
        verify(mActivityManager).forceStopPackage(WIFI_DIALOG_APK_PKG_NAME);

        // A reply to the same dialog id should not trigger callback
        mDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_POSITIVE);
        verify(callbackThreadRunner, never()).post(any());
        verify(callback, times(0)).onPositiveButtonClicked();

        // Another call to dismiss should not send another dismiss intent.
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        verifyStartActivityAsUser(2, mWifiContext);

        // Launch dialog again
        dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(3, mWifiContext);
        dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);

        // Callback should receive replies to the corresponding dialogId now.
        mDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
    }

    /**
     * Verifies the right callback is notified for a response to a simple dialog.
     */
    @Test
    public void testSimpleDialog_multipleDialogs_responseMatchedToCorrectCallback() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        // Launch Dialog1
        SimpleDialogCallback callback1 = mock(SimpleDialogCallback.class);
        DialogHandle dialogHandle1 = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback1, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle1, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId1 = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);

        // Launch Dialog2
        SimpleDialogCallback callback2 = mock(SimpleDialogCallback.class);
        DialogHandle dialogHandle2 = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback2, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle2, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        int dialogId2 = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);

        // callback1 notified
        mDialogManager.replyToSimpleDialog(dialogId1, WifiManager.DIALOG_REPLY_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onPositiveButtonClicked();
        verify(callback2, times(0)).onPositiveButtonClicked();

        // callback2 notified
        mDialogManager.replyToSimpleDialog(dialogId2, WifiManager.DIALOG_REPLY_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onPositiveButtonClicked();
        verify(callback2, times(1)).onPositiveButtonClicked();
    }

    /**
     * Verifies that launching a simple dialog will result in the correct callback methods invoked
     * when a response is received.
     */
    @Test
    public void testSimpleDialog_launchAndResponse_notifiesCallback_preT() {
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        AlertDialog.Builder builder = mock(AlertDialog.Builder.class);
        AlertDialog dialog = mock(AlertDialog.class);
        when(builder.setTitle(any())).thenReturn(builder);
        when(builder.setMessage(any())).thenReturn(builder);
        when(builder.setPositiveButton(any(), any())).thenReturn(builder);
        when(builder.setNegativeButton(any(), any())).thenReturn(builder);
        when(builder.setNeutralButton(any(), any())).thenReturn(builder);
        when(builder.setOnCancelListener(any())).thenReturn(builder);
        when(builder.setOnDismissListener(any())).thenReturn(builder);
        when(builder.create()).thenReturn(dialog);
        Window window = mock(Window.class);
        WindowManager.LayoutParams layoutParams = mock(WindowManager.LayoutParams.class);
        when(window.getAttributes()).thenReturn(layoutParams);
        when(dialog.getWindow()).thenReturn(window);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(builder);
        DialogHandle dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);

        ArgumentCaptor<DialogInterface.OnClickListener> positiveButtonListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        ArgumentCaptor<DialogInterface.OnClickListener> negativeButtonListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        ArgumentCaptor<DialogInterface.OnClickListener> neutralButtonListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        ArgumentCaptor<DialogInterface.OnCancelListener> cancelListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnCancelListener.class);
        verify(builder).setTitle(TEST_TITLE);
        ArgumentCaptor<CharSequence> messageCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(builder).setMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().toString()).isEqualTo(TEST_MESSAGE);
        verify(builder).setPositiveButton(eq(TEST_POSITIVE_BUTTON_TEXT),
                positiveButtonListenerCaptor.capture());
        verify(builder).setNegativeButton(eq(TEST_NEGATIVE_BUTTON_TEXT),
                negativeButtonListenerCaptor.capture());
        verify(builder).setNeutralButton(eq(TEST_NEUTRAL_BUTTON_TEXT),
                neutralButtonListenerCaptor.capture());
        verify(builder).setOnCancelListener(cancelListenerCaptor.capture());
        verify(mWifiThreadRunner, never()).postDelayed(any(Runnable.class), anyInt());

        // Positive
        positiveButtonListenerCaptor.getValue().onClick(dialog, DialogInterface.BUTTON_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onPositiveButtonClicked();

        // Negative
        negativeButtonListenerCaptor.getValue().onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onNegativeButtonClicked();

        // Neutral
        neutralButtonListenerCaptor.getValue().onClick(dialog, DialogInterface.BUTTON_NEUTRAL);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onNeutralButtonClicked();

        // Cancel
        cancelListenerCaptor.getValue().onCancel(dialog);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onCancelled();
    }

    @Test
    public void testSimpleDialog_timeoutCancelsDialog_preT() {
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        AlertDialog.Builder builder = mock(AlertDialog.Builder.class);
        AlertDialog dialog = mock(AlertDialog.class);
        when(builder.setTitle(any())).thenReturn(builder);
        when(builder.setMessage(any())).thenReturn(builder);
        when(builder.setPositiveButton(any(), any())).thenReturn(builder);
        when(builder.setNegativeButton(any(), any())).thenReturn(builder);
        when(builder.setNeutralButton(any(), any())).thenReturn(builder);
        when(builder.setOnCancelListener(any())).thenReturn(builder);
        when(builder.setOnDismissListener(any())).thenReturn(builder);
        when(builder.create()).thenReturn(dialog);
        Window window = mock(Window.class);
        WindowManager.LayoutParams layoutParams = mock(WindowManager.LayoutParams.class);
        when(window.getAttributes()).thenReturn(layoutParams);
        when(dialog.getWindow()).thenReturn(window);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(builder);
        DialogHandle dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, TIMEOUT_MILLIS, mWifiThreadRunner);

        // Verify the timeout runnable was posted and run it.
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWifiThreadRunner, times(1))
                .postDelayed(runnableArgumentCaptor.capture(), eq((long) TIMEOUT_MILLIS));
        runnableArgumentCaptor.getValue().run();

        // Verify that the dialog was cancelled.
        verify(dialog).cancel();
    }

    @Test
    public void testSimpleDialog_dismissedBeforeTimeout_preT() {
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        AlertDialog.Builder builder = mock(AlertDialog.Builder.class);
        AlertDialog dialog = mock(AlertDialog.class);
        when(builder.setTitle(any())).thenReturn(builder);
        when(builder.setMessage(any())).thenReturn(builder);
        when(builder.setPositiveButton(any(), any())).thenReturn(builder);
        when(builder.setNegativeButton(any(), any())).thenReturn(builder);
        when(builder.setNeutralButton(any(), any())).thenReturn(builder);
        when(builder.setOnCancelListener(any())).thenReturn(builder);
        when(builder.setOnDismissListener(any())).thenReturn(builder);
        when(builder.create()).thenReturn(dialog);
        Window window = mock(Window.class);
        WindowManager.LayoutParams layoutParams = mock(WindowManager.LayoutParams.class);
        when(window.getAttributes()).thenReturn(layoutParams);
        when(dialog.getWindow()).thenReturn(window);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(builder);

        DialogHandle dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, TIMEOUT_MILLIS, mWifiThreadRunner);

        // Verify the timeout runnable was posted.
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWifiThreadRunner, times(1))
                .postDelayed(runnableArgumentCaptor.capture(), eq((long) TIMEOUT_MILLIS));
        runnableArgumentCaptor.getValue().run();

        // Dismiss the dialog before the timeout runnable executes.
        ArgumentCaptor<DialogInterface.OnDismissListener> dismissListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnDismissListener.class);
        verify(builder).setOnDismissListener(dismissListenerCaptor.capture());
        dismissListenerCaptor.getValue().onDismiss(dialog);
        dispatchMockWifiThreadRunner(mWifiThreadRunner);

        // Verify that the timeout runnable was removed.
        verify(mWifiThreadRunner).removeCallbacks(runnableArgumentCaptor.getValue());
    }

    @Test
    public void testSimpleDialog_nullWifiResourceApkName_doesNotLaunchDialog() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiContext.getWifiDialogApkPkgName()).thenReturn(null);
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        DialogHandle dialogHandle = mDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);

        verify(mWifiContext, never()).startActivityAsUser(any(), eq(UserHandle.CURRENT));
    }

    /**
     * Verifies that launching a simple dialog will result in the correct callback methods invoked
     * when a response is received.
     */
    @Test
    public void testLegacySimpleDialog_launchAndResponse_notifiesCallback() {
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        AlertDialog.Builder builder = mock(AlertDialog.Builder.class);
        AlertDialog dialog = mock(AlertDialog.class);
        when(builder.setTitle(any())).thenReturn(builder);
        when(builder.setMessage(any())).thenReturn(builder);
        when(builder.setPositiveButton(any(), any())).thenReturn(builder);
        when(builder.setNegativeButton(any(), any())).thenReturn(builder);
        when(builder.setNeutralButton(any(), any())).thenReturn(builder);
        when(builder.setOnCancelListener(any())).thenReturn(builder);
        when(builder.setOnDismissListener(any())).thenReturn(builder);
        when(builder.create()).thenReturn(dialog);
        Window window = mock(Window.class);
        WindowManager.LayoutParams layoutParams = mock(WindowManager.LayoutParams.class);
        when(window.getAttributes()).thenReturn(layoutParams);
        when(dialog.getWindow()).thenReturn(window);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(builder);
        DialogHandle dialogHandle = mDialogManager.createLegacySimpleDialog(TEST_TITLE,
                TEST_MESSAGE, TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT,
                TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);

        ArgumentCaptor<DialogInterface.OnClickListener> positiveButtonListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        ArgumentCaptor<DialogInterface.OnClickListener> negativeButtonListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        ArgumentCaptor<DialogInterface.OnClickListener> neutralButtonListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        ArgumentCaptor<DialogInterface.OnCancelListener> cancelListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnCancelListener.class);
        verify(builder).setTitle(TEST_TITLE);
        ArgumentCaptor<CharSequence> messageCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(builder).setMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().toString()).isEqualTo(TEST_MESSAGE);
        verify(builder).setPositiveButton(eq(TEST_POSITIVE_BUTTON_TEXT),
                positiveButtonListenerCaptor.capture());
        verify(builder).setNegativeButton(eq(TEST_NEGATIVE_BUTTON_TEXT),
                negativeButtonListenerCaptor.capture());
        verify(builder).setNeutralButton(eq(TEST_NEUTRAL_BUTTON_TEXT),
                neutralButtonListenerCaptor.capture());
        verify(builder).setOnCancelListener(cancelListenerCaptor.capture());
        verify(mWifiThreadRunner, never()).postDelayed(any(Runnable.class), anyInt());

        // Positive
        positiveButtonListenerCaptor.getValue().onClick(dialog, DialogInterface.BUTTON_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onPositiveButtonClicked();

        // Negative
        negativeButtonListenerCaptor.getValue().onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onNegativeButtonClicked();

        // Neutral
        neutralButtonListenerCaptor.getValue().onClick(dialog, DialogInterface.BUTTON_NEUTRAL);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onNeutralButtonClicked();

        // Cancel
        cancelListenerCaptor.getValue().onCancel(dialog);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onCancelled();
    }

    @Test
    public void testLegacySimpleDialog_timeoutCancelsDialog() {
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        AlertDialog.Builder builder = mock(AlertDialog.Builder.class);
        AlertDialog dialog = mock(AlertDialog.class);
        when(builder.setTitle(any())).thenReturn(builder);
        when(builder.setMessage(any())).thenReturn(builder);
        when(builder.setPositiveButton(any(), any())).thenReturn(builder);
        when(builder.setNegativeButton(any(), any())).thenReturn(builder);
        when(builder.setNeutralButton(any(), any())).thenReturn(builder);
        when(builder.setOnCancelListener(any())).thenReturn(builder);
        when(builder.setOnDismissListener(any())).thenReturn(builder);
        when(builder.create()).thenReturn(dialog);
        Window window = mock(Window.class);
        WindowManager.LayoutParams layoutParams = mock(WindowManager.LayoutParams.class);
        when(window.getAttributes()).thenReturn(layoutParams);
        when(dialog.getWindow()).thenReturn(window);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(builder);
        DialogHandle dialogHandle = mDialogManager.createLegacySimpleDialog(TEST_TITLE,
                TEST_MESSAGE, TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT,
                TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, TIMEOUT_MILLIS, mWifiThreadRunner);

        // Verify the timeout runnable was posted and run it.
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWifiThreadRunner, times(1))
                .postDelayed(runnableArgumentCaptor.capture(), eq((long) TIMEOUT_MILLIS));
        runnableArgumentCaptor.getValue().run();

        // Verify that the dialog was cancelled.
        verify(dialog).cancel();
    }

    @Test
    public void testLegacySimpleDialog_dismissedBeforeTimeout() {
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        AlertDialog.Builder builder = mock(AlertDialog.Builder.class);
        AlertDialog dialog = mock(AlertDialog.class);
        when(builder.setTitle(any())).thenReturn(builder);
        when(builder.setMessage(any())).thenReturn(builder);
        when(builder.setPositiveButton(any(), any())).thenReturn(builder);
        when(builder.setNegativeButton(any(), any())).thenReturn(builder);
        when(builder.setNeutralButton(any(), any())).thenReturn(builder);
        when(builder.setOnCancelListener(any())).thenReturn(builder);
        when(builder.setOnDismissListener(any())).thenReturn(builder);
        when(builder.create()).thenReturn(dialog);
        Window window = mock(Window.class);
        WindowManager.LayoutParams layoutParams = mock(WindowManager.LayoutParams.class);
        when(window.getAttributes()).thenReturn(layoutParams);
        when(dialog.getWindow()).thenReturn(window);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(builder);

        DialogHandle dialogHandle = mDialogManager.createLegacySimpleDialog(TEST_TITLE,
                TEST_MESSAGE, TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT,
                TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, TIMEOUT_MILLIS, mWifiThreadRunner);

        // Verify the timeout runnable was posted.
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWifiThreadRunner, times(1))
                .postDelayed(runnableArgumentCaptor.capture(), eq((long) TIMEOUT_MILLIS));
        runnableArgumentCaptor.getValue().run();

        // Dismiss the dialog before the timeout runnable executes.
        ArgumentCaptor<DialogInterface.OnDismissListener> dismissListenerCaptor =
                ArgumentCaptor.forClass(DialogInterface.OnDismissListener.class);
        verify(builder).setOnDismissListener(dismissListenerCaptor.capture());
        dismissListenerCaptor.getValue().onDismiss(dialog);
        dispatchMockWifiThreadRunner(mWifiThreadRunner);

        // Verify that the timeout runnable was removed.
        verify(mWifiThreadRunner).removeCallbacks(runnableArgumentCaptor.getValue());
    }

    @Test
    public void testLegacySimpleDialog_cancelledDueToActionCloseSystemDialogs() {
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        AlertDialog.Builder builder = mock(AlertDialog.Builder.class);
        AlertDialog dialog = mock(AlertDialog.class);
        when(builder.setTitle(any())).thenReturn(builder);
        when(builder.setMessage(any())).thenReturn(builder);
        when(builder.setPositiveButton(any(), any())).thenReturn(builder);
        when(builder.setNegativeButton(any(), any())).thenReturn(builder);
        when(builder.setNeutralButton(any(), any())).thenReturn(builder);
        when(builder.setOnCancelListener(any())).thenReturn(builder);
        when(builder.setOnDismissListener(any())).thenReturn(builder);
        when(builder.create()).thenReturn(dialog);
        Window window = mock(Window.class);
        WindowManager.LayoutParams layoutParams = mock(WindowManager.LayoutParams.class);
        when(window.getAttributes()).thenReturn(layoutParams);
        when(dialog.getWindow()).thenReturn(window);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(builder);

        DialogHandle dialogHandle = mDialogManager.createLegacySimpleDialog(TEST_TITLE,
                TEST_MESSAGE, TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT,
                TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, TIMEOUT_MILLIS, mWifiThreadRunner);

        // ACTION_CLOSE_SYSTEM_DIALOGS with EXTRA_CLOSE_SYSTEM_DIALOGS_EXCEPT_WIFI should be
        // ignored.
        mBroadcastReceiverArgumentCaptor.getValue().onReceive(mWifiContext,
                new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                        .putExtra(WifiManager.EXTRA_CLOSE_SYSTEM_DIALOGS_EXCEPT_WIFI, true));
        dispatchMockWifiThreadRunner(mWifiThreadRunner);

        verify(dialog, never()).cancel();

        // ACTION_CLOSE_SYSTEM_DIALOGS without the extra should cancel the dialog.
        mBroadcastReceiverArgumentCaptor
                .getValue()
                .onReceive(mWifiContext, new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        dispatchMockWifiThreadRunner(mWifiThreadRunner);

        verify(dialog).cancel();
    }

    @Test
    public void testLegacySimpleDialog_windowTypeChangedDueToScreenOff() {
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        AlertDialog.Builder builder = mock(AlertDialog.Builder.class);
        AlertDialog dialog = mock(AlertDialog.class);
        when(builder.setTitle(any())).thenReturn(builder);
        when(builder.setMessage(any())).thenReturn(builder);
        when(builder.setPositiveButton(any(), any())).thenReturn(builder);
        when(builder.setNegativeButton(any(), any())).thenReturn(builder);
        when(builder.setNeutralButton(any(), any())).thenReturn(builder);
        when(builder.setOnCancelListener(any())).thenReturn(builder);
        when(builder.setOnDismissListener(any())).thenReturn(builder);
        when(builder.create()).thenReturn(dialog);
        Window window = mock(Window.class);
        WindowManager.LayoutParams layoutParams = mock(WindowManager.LayoutParams.class);
        when(window.getAttributes()).thenReturn(layoutParams);
        when(dialog.getWindow()).thenReturn(window);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(builder);

        DialogHandle dialogHandle = mDialogManager.createLegacySimpleDialog(TEST_TITLE,
                TEST_MESSAGE, TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT,
                TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, TIMEOUT_MILLIS, mWifiThreadRunner);
        verify(window).setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);

        // Receive screen off event.
        when(dialog.isShowing()).thenReturn(true);
        ArgumentCaptor<WifiDeviceStateChangeManager.StateChangeCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(WifiDeviceStateChangeManager.StateChangeCallback.class);
        verify(mWifiDeviceStateChangeManager).registerStateChangeCallback(
                callbackArgumentCaptor.capture());
        callbackArgumentCaptor.getValue().onScreenStateChanged(false);

        // Verify dialog was dismissed and relaunched with window type TYPE_APPLICATION_OVERLAY.
        verify(dialog, never()).cancel();
        verify(dialog).dismiss();
        verify(window).setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        verify(dialog, times(2)).show();
    }

    /**
     * Helper method to verify the contents of a launch Intent for a P2P Invitation Received dialog.
     * @return dialog id of the Intent.
     */
    private int verifyP2pInvitationReceivedDialogLaunchIntent(
            @NonNull Intent launchIntent,
            String expectedDeviceName,
            boolean expectedIsPinRequested,
            @Nullable String expectedDisplayPin) {
        assertThat(launchIntent.getAction()).isEqualTo(WifiManager.ACTION_LAUNCH_DIALOG);
        ComponentName component = launchIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        int dialogId = launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID, -1);
        assertThat(dialogId).isNotEqualTo(-1);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_TYPE)).isTrue();
        assertThat(launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_TYPE,
                WifiManager.DIALOG_TYPE_UNKNOWN))
                .isEqualTo(WifiManager.DIALOG_TYPE_P2P_INVITATION_RECEIVED);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DEVICE_NAME)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_P2P_DEVICE_NAME))
                .isEqualTo(expectedDeviceName);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED)).isTrue();
        assertThat(launchIntent.getBooleanExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED, false))
                .isEqualTo(expectedIsPinRequested);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN))
                .isEqualTo(expectedDisplayPin);
        return dialogId;
    }

    /**
     * Verifies that launching a P2P Invitation Received dialog with a callback will result in the
     * correct callback methods invoked when a response is received.
     */
    @Test
    public void testP2pInvitationReceivedDialog_launchAndResponse_notifiesCallback() {
        P2pInvitationReceivedDialogCallback callback =
                mock(P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        // Accept without PIN
        DialogHandle dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onAccepted(null);

        // Callback should be removed from callback list, so a second notification should be ignored
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, "012345");
        verify(callback, times(0)).onAccepted("012345");

        // Accept with PIN
        dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, true, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, true, null);
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onAccepted("012345");

        // Accept with PIN but PIN was not requested
        dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, 123, callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        if (SdkLevel.isAtLeastT()) {
            verifyStartActivityAsUser(1, 123, mWifiContext);
        }
        intent = verifyStartActivityAsUser(3, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onAccepted("012345");

        // Accept without PIN but PIN was requested
        dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, true, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(4, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, true, null);
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onAccepted(null);

        // Decline without PIN
        dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(5, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, false, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onDeclined();

        // Decline with PIN
        dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, true, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(6, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, true, null);
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, false, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onDeclined();

        // Decline with PIN but PIN was not requested
        dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(7, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, false, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(3)).onDeclined();

        // Decline without PIN but PIN was requested
        dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, true, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(8, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, true, null);
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, false, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(4)).onDeclined();
    }

    /**
     * Verifies that launching a P2P invitation sent dialog and dismissing it will send a dismiss
     * intent and prevent future replies to the original dialog id from notifying the callback.
     */
    @Test
    public void testP2pInvitationReceivedDialog_launchAndDismiss_dismissesDialog() {
        P2pInvitationReceivedDialogCallback callback =
                mock(P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);


        // Launch and dismiss dialog.
        DialogHandle dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        verifyDismissIntent(intent);
        verify(mActivityManager).forceStopPackage(WIFI_DIALOG_APK_PKG_NAME);

        // A reply to the same dialog id should not trigger callback
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        verify(callbackThreadRunner, never()).post(any());
        verify(callback, times(0)).onAccepted(null);

        // Another call to dismiss should not send another dismiss intent.
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        verifyStartActivityAsUser(2, mWifiContext);

        // Launch dialog again
        dialogHandle = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(3, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);

        // Callback should receive replies to the corresponding dialogId now.
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onAccepted(null);
    }

    /**
     * Verifies the right callback is notified for a response to a P2P Invitation Received dialog.
     */
    @Test
    public void testP2pInvitationReceivedDialog_multipleDialogs_responseMatchedToCorrectCallback() {


        // Launch Dialog1
        P2pInvitationReceivedDialogCallback callback1 =
                mock(P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        DialogHandle dialogHandle1 = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback1, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle1, 0, mWifiThreadRunner);
        Intent intent1 = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId1 = verifyP2pInvitationReceivedDialogLaunchIntent(intent1,
                TEST_DEVICE_NAME, false, null);

        // Launch Dialog2
        P2pInvitationReceivedDialogCallback callback2 =
                mock(P2pInvitationReceivedDialogCallback.class);
        DialogHandle dialogHandle2 = mDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback2, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle2, 0, mWifiThreadRunner);
        Intent intent2 = verifyStartActivityAsUser(2, mWifiContext);
        int dialogId2 = verifyP2pInvitationReceivedDialogLaunchIntent(intent2,
                TEST_DEVICE_NAME, false, null);

        // callback1 notified
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId1, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onAccepted(null);
        verify(callback2, times(0)).onAccepted(null);

        // callback2 notified
        mDialogManager.replyToP2pInvitationReceivedDialog(dialogId2, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onAccepted(null);
        verify(callback2, times(1)).onAccepted(null);
    }

    /**
     * Helper method to verify the contents of a launch Intent for a P2P Invitation Received dialog.
     * @return dialog id of the Intent.
     */
    private int verifyP2pInvitationSentDialogLaunchIntent(
            @NonNull Intent launchIntent,
            String expectedDeviceName,
            @Nullable String expectedDisplayPin) {
        assertThat(launchIntent.getAction()).isEqualTo(WifiManager.ACTION_LAUNCH_DIALOG);
        ComponentName component = launchIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        int dialogId = launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID, -1);
        assertThat(dialogId).isNotEqualTo(-1);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_TYPE)).isTrue();
        assertThat(launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_TYPE,
                WifiManager.DIALOG_TYPE_UNKNOWN))
                .isEqualTo(WifiManager.DIALOG_TYPE_P2P_INVITATION_SENT);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DEVICE_NAME)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_P2P_DEVICE_NAME))
                .isEqualTo(expectedDeviceName);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN))
                .isEqualTo(expectedDisplayPin);
        return dialogId;
    }

    /**
     * Verifies that launching a P2P invitation sent dialog and dismissing it will send a dismiss
     * intent.
     */
    @Test
    public void testP2pInvitationSentDialog_launchAndDismiss_dismissesDialog() {


        // Launch and dismiss dialog.
        DialogHandle dialogHandle = mDialogManager.createP2pInvitationSentDialog(
                TEST_DEVICE_NAME, null, Display.DEFAULT_DISPLAY);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        verifyP2pInvitationSentDialogLaunchIntent(verifyStartActivityAsUser(1, mWifiContext),
                TEST_DEVICE_NAME, null);
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        verifyDismissIntent(verifyStartActivityAsUser(2, mWifiContext));
        verify(mActivityManager).forceStopPackage(WIFI_DIALOG_APK_PKG_NAME);

        // Another call to dismiss should not send another dismiss intent.
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        verifyStartActivityAsUser(2, mWifiContext);
    }
}
