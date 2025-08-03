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

package com.android.bluetooth.opp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.view.MenuItem;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.flags.Flags;

import com.google.common.base.Objects;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;

/**
 * This class will also test BluetoothOppTransferAdapter
 */
@RunWith(AndroidJUnit4.class)
public class BluetoothOppTransferHistoryTest {
    @Mock
    Cursor mCursor;
    @Spy
    BluetoothMethodProxy mBluetoothMethodProxy;

    List<BluetoothOppTestUtils.CursorMockData> mCursorMockDataList;

    Intent mIntent;
    Context mTargetContext;

    // Activity tests can sometimes flaky because of external factors like system dialog, etc.
    // making the expected Espresso's root not focused or the activity doesn't show up.
    // Add retry rule to resolve this problem.
    @Rule public TestUtils.RetryTestRule mRetryTestRule = new TestUtils.RetryTestRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mBluetoothMethodProxy = Mockito.spy(BluetoothMethodProxy.getInstance());
        BluetoothMethodProxy.setInstanceForTesting(mBluetoothMethodProxy);

        Uri dataUrl = Uri.parse("content://com.android.bluetooth.opp.test/random");
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mIntent = new Intent();
        mIntent.setClass(mTargetContext, BluetoothOppTransferHistory.class);
        mIntent.setData(dataUrl);

        doReturn(mCursor).when(mBluetoothMethodProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI),
                any(), any(), any(), any());

        int idValue = 1234;
        Long timestampValue = 123456789L;
        String destinationValue = "AA:BB:CC:00:11:22";
        String fileTypeValue = "text/plain";

        mCursorMockDataList = new ArrayList<>(List.of(
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.STATUS, 1,
                        BluetoothShare.STATUS_SUCCESS),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.DIRECTION, 2,
                        BluetoothShare.DIRECTION_INBOUND),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.TOTAL_BYTES, 3, 100),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.CURRENT_BYTES, 4, 0),
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare._ID, 0, idValue),
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

        BluetoothOppTestUtils.enableActivity(
                BluetoothOppTransferHistory.class, true, mTargetContext);
        TestUtils.setUpUiTest();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.tearDownUiTest();
        BluetoothMethodProxy.setInstanceForTesting(null);
        BluetoothOppTestUtils.enableActivity(
                BluetoothOppTransferHistory.class, false, mTargetContext);
    }

    @Test
    public void onCreate_withDirectionInbound_displayInboundHistory() {
        Assume.assumeFalse(
                mTargetContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        BluetoothOppTestUtils.setUpMockCursor(mCursor, mCursorMockDataList);
        if (Flags.oppStartActivityDirectlyFromNotification()) {
            mIntent.setAction(Constants.ACTION_OPEN_INBOUND_TRANSFER);
        } else {
            mIntent.putExtra(Constants.EXTRA_DIRECTION, BluetoothShare.DIRECTION_INBOUND);
        }

        ActivityScenario<BluetoothOppTransferHistory> scenario = ActivityScenario.launch(mIntent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        onView(withText(mTargetContext.getText(R.string.inbound_history_title).toString())).check(
                matches(isDisplayed()));
    }

    @Test
    public void onCreate_withDirectionOutbound_displayOutboundHistory() {
        Assume.assumeFalse(
                mTargetContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        BluetoothOppTestUtils.setUpMockCursor(mCursor, mCursorMockDataList);
        mCursorMockDataList.set(1,
                new BluetoothOppTestUtils.CursorMockData(BluetoothShare.DIRECTION, 2,
                        BluetoothShare.DIRECTION_OUTBOUND));
        if (Flags.oppStartActivityDirectlyFromNotification()) {
            mIntent.setAction(Constants.ACTION_OPEN_OUTBOUND_TRANSFER);
        } else {
            mIntent.putExtra(Constants.EXTRA_DIRECTION, BluetoothShare.DIRECTION_OUTBOUND);
        }

        ActivityScenario<BluetoothOppTransferHistory> scenario = ActivityScenario.launch(mIntent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        onView(withText(mTargetContext.getText(R.string.outbound_history_title).toString())).check(
                matches(isDisplayed()));
    }

    // TODO: Check whether watch devices can pass this test
    @Ignore("b/268424815")
    @Test
    public void onOptionsItemSelected_clearAllSelected_promptWarning() {
        BluetoothOppTestUtils.setUpMockCursor(mCursor, mCursorMockDataList);
        mIntent.putExtra(Constants.EXTRA_DIRECTION, BluetoothShare.DIRECTION_INBOUND);

        ActivityScenario<BluetoothOppTransferHistory> scenario = ActivityScenario.launch(mIntent);


        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(R.id.transfer_menu_clear_all).when(mockMenuItem).getItemId();
        scenario.onActivity(activity -> {
            activity.onOptionsItemSelected(mockMenuItem);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Controlling clear all download
        doReturn(true, false).when(mCursor).moveToFirst();
        doReturn(false, true).when(mCursor).isAfterLast();
        doReturn(0).when(mBluetoothMethodProxy).contentResolverUpdate(any(), any(),
                argThat(arg -> Objects.equal(arg.get(BluetoothShare.VISIBILITY),
                        BluetoothShare.VISIBILITY_HIDDEN)), any(), any());

        onView(withText(mTargetContext.getText(R.string.transfer_clear_dlg_title).toString()))
                .inRoot(isDialog()).check(matches(isDisplayed()));

        // Click ok on the prompted dialog
        onView(withText(mTargetContext.getText(android.R.string.ok).toString())).inRoot(
                isDialog()).check(matches(isDisplayed())).perform(click());

        // Verify that item is hidden
        verify(mBluetoothMethodProxy).contentResolverUpdate(any(), any(),
                argThat(arg -> Objects.equal(arg.get(BluetoothShare.VISIBILITY),
                        BluetoothShare.VISIBILITY_HIDDEN)), any(), any());
    }
}
