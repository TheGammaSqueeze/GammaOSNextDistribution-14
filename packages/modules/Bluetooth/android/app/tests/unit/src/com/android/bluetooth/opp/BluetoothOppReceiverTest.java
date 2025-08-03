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

import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.flags.Flags;

import com.google.common.base.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BluetoothOppReceiverTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    Context mContext;

    @Mock
    BluetoothMethodProxy mBluetoothMethodProxy;
    BluetoothOppReceiver mReceiver;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));

        // mock instance so query/insert/update/etc. will not be executed
        BluetoothMethodProxy.setInstanceForTesting(mBluetoothMethodProxy);

        mReceiver = new BluetoothOppReceiver();

        Intents.init();
        TestUtils.setUpUiTest();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.tearDownUiTest();
        BluetoothMethodProxy.setInstanceForTesting(null);

        Intents.release();
    }

    @Ignore("b/262201478")
    @Test
    public void onReceive_withActionDeviceSelected_callsStartTransfer() {
        BluetoothOppManager bluetoothOppManager = spy(BluetoothOppManager.getInstance(mContext));
        BluetoothOppManager.setInstance(bluetoothOppManager);
        String address = "AA:BB:CC:DD:EE:FF";
        BluetoothDevice device = mContext.getSystemService(BluetoothManager.class)
                .getAdapter().getRemoteDevice(address);
        Intent intent = new Intent();
        intent.setAction(BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);

        try {
            BluetoothOppTestUtils.enableActivity(
                    BluetoothOppBtEnableActivity.class, true, mContext);
            ActivityScenario<BluetoothOppBtEnableActivity> activityScenario =
                    ActivityScenario.launch(BluetoothOppBtEnableActivity.class);
            activityScenario.onActivity(
                    activity -> {
                        mReceiver.onReceive(mContext, intent);
                    });
            doNothing().when(bluetoothOppManager).startTransfer(eq(device));
            verify(bluetoothOppManager).startTransfer(eq(device));
            BluetoothOppManager.setInstance(null);
        } finally {
            BluetoothOppTestUtils.enableActivity(
                    BluetoothOppBtEnableActivity.class, false, mContext);
        }
    }

    @Test
    public void onReceive_withActionIncomingFileConfirm_startsIncomingFileConfirmActivity() {
        mSetFlagsRule.disableFlags(Flags.FLAG_OPP_START_ACTIVITY_DIRECTLY_FROM_NOTIFICATION);
        try {
            BluetoothOppTestUtils.enableActivity(
                    BluetoothOppIncomingFileConfirmActivity.class, true, mContext);

            Intent intent = new Intent();
            intent.setAction(Constants.ACTION_INCOMING_FILE_CONFIRM);
            intent.setData(Uri.parse("content:///not/important"));
            mReceiver.onReceive(mContext, intent);
            intended(hasComponent(BluetoothOppIncomingFileConfirmActivity.class.getName()));
        } finally {
            BluetoothOppTestUtils.enableActivity(
                    BluetoothOppIncomingFileConfirmActivity.class, false, mContext);
        }
    }

    @Test
    public void onReceive_withActionAccept_updatesContents() {
        Uri uri = Uri.parse("content:///important");
        Intent intent = new Intent();
        intent.setAction(Constants.ACTION_ACCEPT);
        intent.setData(uri);
        mReceiver.onReceive(mContext, intent);
        verify(mBluetoothMethodProxy).contentResolverUpdate(any(), eq(uri), argThat(arg ->
                Objects.equal(BluetoothShare.USER_CONFIRMATION_CONFIRMED,
                        arg.get(BluetoothShare.USER_CONFIRMATION))), any(), any());
    }

    @Test
    public void onReceive_withActionDecline_updatesContents() {
        Uri uri = Uri.parse("content:///important");
        Intent intent = new Intent();
        intent.setAction(Constants.ACTION_DECLINE);
        intent.setData(uri);
        mReceiver.onReceive(mContext, intent);
        verify(mBluetoothMethodProxy).contentResolverUpdate(any(), eq(uri), argThat(arg ->
                Objects.equal(BluetoothShare.USER_CONFIRMATION_DENIED,
                        arg.get(BluetoothShare.USER_CONFIRMATION))), any(), any());
    }

    @Test
    public void onReceive_withActionOutboundTransfer_startsTransferHistoryActivity() {
        mSetFlagsRule.disableFlags(Flags.FLAG_OPP_START_ACTIVITY_DIRECTLY_FROM_NOTIFICATION);
        try {
            BluetoothOppTestUtils.enableActivity(BluetoothOppTransferHistory.class, true, mContext);

            Intent intent = new Intent();
            intent.setAction(Constants.ACTION_OPEN_OUTBOUND_TRANSFER);
            intent.setData(Uri.parse("content:///not/important"));
            intending(anyIntent())
                    .respondWith(
                            new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent()));

            mReceiver.onReceive(mContext, intent);
            intended(hasComponent(BluetoothOppTransferHistory.class.getName()));
            intended(hasExtra(Constants.EXTRA_DIRECTION, BluetoothShare.DIRECTION_OUTBOUND));
        } finally {
            BluetoothOppTestUtils.enableActivity(
                    BluetoothOppTransferHistory.class, false, mContext);
        }
    }

    @Test
    public void onReceive_withActionInboundTransfer_startsTransferHistoryActivity() {
        mSetFlagsRule.disableFlags(Flags.FLAG_OPP_START_ACTIVITY_DIRECTLY_FROM_NOTIFICATION);
        try {
            BluetoothOppTestUtils.enableActivity(BluetoothOppTransferHistory.class, true, mContext);

            Intent intent = new Intent();
            intent.setAction(Constants.ACTION_OPEN_INBOUND_TRANSFER);
            intent.setData(Uri.parse("content:///not/important"));
            intending(anyIntent())
                    .respondWith(
                            new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent()));
            mReceiver.onReceive(mContext, intent);
            intended(hasComponent(BluetoothOppTransferHistory.class.getName()));
            intended(hasExtra(Constants.EXTRA_DIRECTION, BluetoothShare.DIRECTION_INBOUND));
        } finally {
            BluetoothOppTestUtils.enableActivity(
                    BluetoothOppTransferHistory.class, false, mContext);
        }
    }

    @Test
    public void onReceive_withActionHide_contentUpdate() {
        List<BluetoothOppTestUtils.CursorMockData> cursorMockDataList;
        Cursor cursor = mock(Cursor.class);
        cursorMockDataList = new ArrayList<>(List.of(
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.VISIBILITY, 0,
                        BluetoothShare.VISIBILITY_VISIBLE),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.USER_CONFIRMATION, 1,
                        BluetoothShare.USER_CONFIRMATION_PENDING)
        ));

        BluetoothOppTestUtils.setUpMockCursor(cursor, cursorMockDataList);

        doReturn(cursor).when(mBluetoothMethodProxy).contentResolverQuery(any(), any(), any(),
                any(), any(), any());
        doReturn(true).when(cursor).moveToFirst();

        Intent intent = new Intent();
        intent.setAction(Constants.ACTION_HIDE);
        mReceiver.onReceive(mContext, intent);

        verify(mBluetoothMethodProxy).contentResolverUpdate(any(), any(),
                argThat(arg -> Objects.equal(BluetoothShare.VISIBILITY_HIDDEN,
                        arg.get(BluetoothShare.VISIBILITY))), any(), any());
    }

    @Test
    public void onReceive_withActionCompleteHide_makeAllVisibilityHidden() {
        mSetFlagsRule.disableFlags(Flags.FLAG_OPP_FIX_MULTIPLE_NOTIFICATIONS_ISSUES);
        Intent intent = new Intent();
        intent.setAction(Constants.ACTION_COMPLETE_HIDE);
        mReceiver.onReceive(mContext, intent);
        verify(mBluetoothMethodProxy).contentResolverUpdate(any(), eq(BluetoothShare.CONTENT_URI),
                argThat(arg -> Objects.equal(BluetoothShare.VISIBILITY_HIDDEN,
                        arg.get(BluetoothShare.VISIBILITY))), any(), any());
    }

    @Test
    public void onReceive_withActionHideCompletedInboundTransfer_makesInboundVisibilityHidden() {
        mSetFlagsRule.enableFlags(Flags.FLAG_OPP_FIX_MULTIPLE_NOTIFICATIONS_ISSUES);
        Intent intent = new Intent();
        intent.setAction(Constants.ACTION_HIDE_COMPLETED_INBOUND_TRANSFER);
        mReceiver.onReceive(mContext, intent);
        verify(mBluetoothMethodProxy)
                .contentResolverUpdate(
                        any(),
                        eq(BluetoothShare.CONTENT_URI),
                        argThat(
                                arg ->
                                        Objects.equal(
                                                BluetoothShare.VISIBILITY_HIDDEN,
                                                arg.get(BluetoothShare.VISIBILITY))),
                        eq(BluetoothOppNotification.WHERE_COMPLETED_INBOUND),
                        any());
    }

    @Test
    public void onReceive_withActionHideCompletedOutboundTransfer_makesOutboundVisibilityHidden() {
        mSetFlagsRule.enableFlags(Flags.FLAG_OPP_FIX_MULTIPLE_NOTIFICATIONS_ISSUES);
        Intent intent = new Intent();
        intent.setAction(Constants.ACTION_HIDE_COMPLETED_OUTBOUND_TRANSFER);
        mReceiver.onReceive(mContext, intent);
        verify(mBluetoothMethodProxy)
                .contentResolverUpdate(
                        any(),
                        eq(BluetoothShare.CONTENT_URI),
                        argThat(
                                arg ->
                                        Objects.equal(
                                                BluetoothShare.VISIBILITY_HIDDEN,
                                                arg.get(BluetoothShare.VISIBILITY))),
                        eq(BluetoothOppNotification.WHERE_COMPLETED_OUTBOUND),
                        any());
    }

    @Test
    public void onReceive_withActionTransferCompletedAndHandoverInitiated_contextSendBroadcast() {
        List<BluetoothOppTestUtils.CursorMockData> cursorMockDataList;
        Cursor cursor = mock(Cursor.class);
        int idValue = 1234;
        Long timestampValue = 123456789L;
        String destinationValue = "AA:BB:CC:00:11:22";
        String fileTypeValue = "text/plain";

        cursorMockDataList = new ArrayList<>(List.of(
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare._ID, 0, idValue),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.STATUS, 1,
                        BluetoothShare.STATUS_SUCCESS),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.DIRECTION, 2,
                        BluetoothShare.DIRECTION_OUTBOUND),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.TOTAL_BYTES, 3, 100),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.CURRENT_BYTES, 4, 100),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.MIMETYPE, 5, fileTypeValue),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.TIMESTAMP, 6,
                        timestampValue),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.DESTINATION, 7,
                        destinationValue),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare._DATA, 8, null),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.FILENAME_HINT, 9, null),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.URI, 10,
                        "content://textfile.txt"),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.USER_CONFIRMATION, 11,
                        BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED)
        ));

        BluetoothOppTestUtils.setUpMockCursor(cursor, cursorMockDataList);

        doReturn(cursor).when(mBluetoothMethodProxy).contentResolverQuery(any(), any(), any(),
                any(), any(), any());
        doReturn(true).when(cursor).moveToFirst();

        Intent intent = new Intent();
        intent.setAction(BluetoothShare.TRANSFER_COMPLETED_ACTION);
        mReceiver.onReceive(mContext, intent);
        verify(mContext).sendBroadcast(any(), eq(Constants.HANDOVER_STATUS_PERMISSION), any());
    }

    @Test
    public void onReceive_withActionTransferComplete_noBroadcastSent() throws Exception {
        List<BluetoothOppTestUtils.CursorMockData> cursorMockDataList;
        Cursor cursor = mock(Cursor.class);
        int idValue = 1234;
        Long timestampValue = 123456789L;
        String destinationValue = "AA:BB:CC:00:11:22";
        String fileTypeValue = "text/plain";

        cursorMockDataList = new ArrayList<>(List.of(
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare._ID, 0, idValue),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.STATUS, 1,
                        BluetoothShare.STATUS_SUCCESS),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.DIRECTION, 2,
                        BluetoothShare.DIRECTION_OUTBOUND),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.TOTAL_BYTES, 3, 100),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.CURRENT_BYTES, 4, 100),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.MIMETYPE, 5, fileTypeValue),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.TIMESTAMP, 6,
                        timestampValue),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.DESTINATION, 7,
                        destinationValue),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare._DATA, 8, null),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.FILENAME_HINT, 9, null),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.URI, 10,
                        "content://textfile.txt"),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.USER_CONFIRMATION, 11,
                        BluetoothShare.USER_CONFIRMATION_CONFIRMED)
        ));

        BluetoothOppTestUtils.setUpMockCursor(cursor, cursorMockDataList);

        doReturn(cursor).when(mBluetoothMethodProxy).contentResolverQuery(any(), any(), any(),
                any(), any(), any());
        doReturn(true).when(cursor).moveToFirst();

        Intent intent = new Intent();
        intent.setAction(BluetoothShare.TRANSFER_COMPLETED_ACTION);

        ActivityScenario<BluetoothOppBtEnableActivity> activityScenario
                = ActivityScenario.launch(BluetoothOppBtEnableActivity.class);

        activityScenario.onActivity(activity -> {
            mReceiver.onReceive(mContext, intent);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // check Toast with Espresso seems not to work on Android 11+. Check not send broadcast
        // context instead
        verify(mContext, never()).sendBroadcast(any(), eq(Constants.HANDOVER_STATUS_PERMISSION),
                any());
    }
}
