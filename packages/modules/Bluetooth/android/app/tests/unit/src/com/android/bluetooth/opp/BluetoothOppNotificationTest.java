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

package com.android.bluetooth.opp;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.MatrixCursor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class BluetoothOppNotificationTest {
    static final int TIMEOUT_MS = 3000;
    static final int WORKAROUND_TIMEOUT = 3000;

    @Mock
    BluetoothMethodProxy mMethodProxy;

    Context mTargetContext;

    BluetoothOppNotification mOppNotification;

    ComponentName mReceiverName;
    int mPreviousState;

    // Activity tests can sometimes flaky because of external factors like system dialog, etc.
    // making the expected Espresso's root not focused or the activity doesn't show up.
    // Add retry rule to resolve this problem.
    @Rule public TestUtils.RetryTestRule mRetryTestRule = new TestUtils.RetryTestRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTargetContext = spy(new ContextWrapper(
                ApplicationProvider.getApplicationContext()));
        BluetoothMethodProxy.setInstanceForTesting(mMethodProxy);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mOppNotification = new BluetoothOppNotification(mTargetContext));

        Intents.init();
        TestUtils.setUpUiTest();
        // Go to notification screen
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).openNotification();

        // Enable BluetoothOppReceiver and then check for dismissed notification
        mReceiverName = new ComponentName(mTargetContext,
                com.android.bluetooth.opp.BluetoothOppReceiver.class);
        mPreviousState = mTargetContext.getPackageManager().getComponentEnabledSetting(
                mReceiverName);
        mTargetContext.getPackageManager().setComponentEnabledSetting(
                mReceiverName, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);

        // clear all OPP notifications before each test
        mOppNotification.cancelNotifications();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.tearDownUiTest();
        // Back to home screen
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome();

        BluetoothMethodProxy.setInstanceForTesting(null);
        Intents.release();

        mTargetContext.getPackageManager().setComponentEnabledSetting(
                mReceiverName, mPreviousState, DONT_KILL_APP);

        // clear all OPP notifications after each test
        mOppNotification.cancelNotifications();
    }

    @Ignore("b/288660228")
    @Test
    public void updateActiveNotification() throws InterruptedException {
        long timestamp = 10L;
        int dir = BluetoothShare.DIRECTION_INBOUND;
        int id = 0;
        long total = 200;
        long current = 100;
        int status = BluetoothShare.STATUS_RUNNING;
        int confirmation = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        int confirmationHandoverInitiated = BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED;
        String destination = "AA:BB:CC:DD:EE:FF";
        MatrixCursor cursor = new MatrixCursor(new String[]{
                BluetoothShare.TIMESTAMP, BluetoothShare.DIRECTION, BluetoothShare._ID,
                BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES, BluetoothShare._DATA,
                BluetoothShare.FILENAME_HINT, BluetoothShare.USER_CONFIRMATION,
                BluetoothShare.DESTINATION, BluetoothShare.STATUS
        });
        cursor.addRow(new Object[]{
                timestamp, dir, id, total, current, null, null, confirmation, destination, status
        });
        cursor.addRow(new Object[]{
                timestamp + 10L, dir, id, total, current, null, null, confirmationHandoverInitiated,
                destination, status
        });
        doReturn(cursor).when(mMethodProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI), any(), any(), any(), any());

        mOppNotification.updateActiveNotification();

        //confirm handover case does broadcast
        verify(mTargetContext).sendBroadcast(any(), eq(Constants.HANDOVER_STATUS_PERMISSION),
                any());

        final UiDevice device = UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());

        device.openNotification();

        String titleString = mTargetContext.getString(R.string.notification_receiving,
                mTargetContext.getString(R.string.unknown_file));
        device.wait(Until.hasObject(By.text(titleString)), TIMEOUT_MS);
        UiObject2 title = device.findObject(By.text(titleString));
        assertThat(title).isNotNull();

        mOppNotification.cancelNotifications();
    }

    @Test
    @Ignore("b/288660228")
    public void updateCompletedNotification_withOutBoundShare_showsNoti()
            throws InterruptedException {
        long timestamp = 10L;
        int status = com.android.bluetooth.opp.BluetoothShare.STATUS_SUCCESS;
        int statusError = BluetoothShare.STATUS_CONNECTION_ERROR;
        int dir = BluetoothShare.DIRECTION_OUTBOUND;
        int id = 0;
        long total = 200;
        long current = 100;
        int confirmation = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        String destination = "AA:BB:CC:DD:EE:FF";
        MatrixCursor cursor = new MatrixCursor(new String[]{
                BluetoothShare.TIMESTAMP, BluetoothShare.DIRECTION, BluetoothShare._ID,
                BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES, BluetoothShare._DATA,
                BluetoothShare.FILENAME_HINT, BluetoothShare.USER_CONFIRMATION,
                BluetoothShare.DESTINATION, BluetoothShare.STATUS
        });
        cursor.addRow(new Object[]{
                timestamp, dir, id, total, current, null, null, confirmation, destination, status
        });
        cursor.addRow(new Object[]{
                timestamp + 10L, dir, id, total, current, null, null, confirmation,
                destination, statusError
        });
        doReturn(cursor).when(mMethodProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI), any(), any(), any(), any());

        mOppNotification.updateCompletedNotification();

        final UiDevice device = UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());

        device.openNotification();

        String titleString = mTargetContext.getString(R.string.outbound_noti_title);
        device.wait(Until.hasObject(By.text(titleString)), TIMEOUT_MS);
        UiObject2 title = device.findObject(By.text(titleString));
        assertThat(title).isNotNull();

        // Work around for b/283784660
        // We need to wait at least 3 seconds after the notification appear
        Thread.sleep(WORKAROUND_TIMEOUT);
        title.getParent().swipe(Direction.LEFT, 1.0f);

        device.wait(Until.gone(By.text(titleString)), TIMEOUT_MS);
        assertThat(device.findObject(By.text(titleString))).isNull();
    }

    @Ignore("b/288660228")
    @Test
    public void updateCompletedNotification_withInBoundShare_showsNoti()
            throws InterruptedException {
        long timestamp = 10L;
        int status = BluetoothShare.STATUS_SUCCESS;
        int statusError = BluetoothShare.STATUS_CONNECTION_ERROR;
        int dir = BluetoothShare.DIRECTION_INBOUND;
        int id = 0;
        long total = 200;
        long current = 100;
        int confirmation = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        String destination = "AA:BB:CC:DD:EE:FF";
        MatrixCursor cursor = new MatrixCursor(new String[]{
                BluetoothShare.TIMESTAMP, BluetoothShare.DIRECTION, BluetoothShare._ID,
                BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES, BluetoothShare._DATA,
                BluetoothShare.FILENAME_HINT, BluetoothShare.USER_CONFIRMATION,
                BluetoothShare.DESTINATION, BluetoothShare.STATUS
        });
        cursor.addRow(new Object[]{
                timestamp, dir, id, total, current, null, null, confirmation, destination, status
        });
        cursor.addRow(new Object[]{
                timestamp + 10L, dir, id, total, current, null, null, confirmation,
                destination, statusError
        });
        doReturn(cursor).when(mMethodProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI), any(), any(), any(), any());

        mOppNotification.updateCompletedNotification();

        final UiDevice device = UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());

        device.openNotification();

        String titleString = mTargetContext.getString(R.string.inbound_noti_title);
        device.wait(Until.hasObject(By.text(titleString)), TIMEOUT_MS);
        UiObject2 title = device.findObject(By.text(titleString));
        assertThat(title).isNotNull();

        // Work around for b/283784660
        // We need to wait at least 3 seconds after the notification appear
        Thread.sleep(WORKAROUND_TIMEOUT);
        title.getParent().swipe(Direction.LEFT, 1.0f);
        device.wait(Until.gone(By.text(titleString)), TIMEOUT_MS);

        assertThat(device.findObject(By.text(titleString))).isNull();
    }

    @Ignore("b/288660228")
    @Test
    public void updateIncomingFileConfirmationNotification() throws InterruptedException {
        long timestamp = 10L;
        int dir = BluetoothShare.DIRECTION_INBOUND;
        int id = 0;
        long total = 200;
        long current = 100;
        int confirmation = BluetoothShare.USER_CONFIRMATION_PENDING;
        int status = BluetoothShare.STATUS_SUCCESS;
        String url = "content:///abc/xyz";
        String destination = "AA:BB:CC:DD:EE:FF";
        String mimeType = "text/plain";

        mOppNotification.mNotificationMgr = spy(mOppNotification.mNotificationMgr);

        MatrixCursor cursor = new MatrixCursor(new String[]{
                BluetoothShare.TIMESTAMP, BluetoothShare.DIRECTION, BluetoothShare._ID,
                BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES, BluetoothShare._DATA,
                BluetoothShare.FILENAME_HINT, BluetoothShare.USER_CONFIRMATION, BluetoothShare.URI,
                BluetoothShare.DESTINATION, BluetoothShare.STATUS, BluetoothShare.MIMETYPE
        });
        cursor.addRow(new Object[]{
                timestamp, dir, id, total, current, null, null, confirmation, url, destination,
                status, mimeType
        });
        doReturn(cursor).when(mMethodProxy).contentResolverQuery(any(),
                eq(com.android.bluetooth.opp.BluetoothShare.CONTENT_URI), any(), any(), any(),
                any());

        mOppNotification.updateIncomingFileConfirmNotification();

        final UiDevice device = UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());

        String titleString = mTargetContext.getString(
                R.string.incoming_file_confirm_Notification_title);

        String confirmString = mTargetContext.getString(
                R.string.incoming_file_confirm_ok);
        String declineString = mTargetContext.getString(
                R.string.incoming_file_confirm_cancel);

        device.wait(Until.hasObject(By.text(titleString)), TIMEOUT_MS);
        UiObject2 title = device.findObject(By.text(titleString));
        UiObject2 buttonOk = device.findObject(By.text(confirmString));
        // In AOSP, all actions' titles are converted into upper case
        if(buttonOk == null) {
            buttonOk = device.findObject(By.text(confirmString.toUpperCase()));
        }

        UiObject2 buttonDecline = device.findObject(By.text(declineString));
        // In AOSP, all actions' titles are converted into upper case
        if(buttonDecline == null) {
            buttonDecline = device.findObject(By.text(declineString.toUpperCase()));
        }

        assertThat(title).isNotNull();
        assertThat(buttonOk).isNotNull();
        assertThat(buttonDecline).isNotNull();

        buttonDecline.wait(Until.clickable(true), TIMEOUT_MS);

        // Work around for b/283784660
        // We need to wait at least 3 seconds after the notification appear
        Thread.sleep(WORKAROUND_TIMEOUT);
        buttonDecline.click();

        device.wait(Until.gone(By.text(titleString)), TIMEOUT_MS);

        assertThat(device.findObject(By.text(titleString))).isNull();
        assertThat(device.findObject(By.text(confirmString))).isNull();
        assertThat(device.findObject(By.text(confirmString.toUpperCase()))).isNull();
        assertThat(device.findObject(By.text(declineString))).isNull();
        assertThat(device.findObject(By.text(declineString.toUpperCase()))).isNull();
    }
}

