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

package com.android.cellbroadcastreceiver.unit;

import static android.provider.Telephony.CellBroadcasts.CID;
import static android.provider.Telephony.CellBroadcasts.CMAS_CATEGORY;
import static android.provider.Telephony.CellBroadcasts.CMAS_CERTAINTY;
import static android.provider.Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS;
import static android.provider.Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE;
import static android.provider.Telephony.CellBroadcasts.CMAS_SEVERITY;
import static android.provider.Telephony.CellBroadcasts.CMAS_URGENCY;
import static android.provider.Telephony.CellBroadcasts.DATA_CODING_SCHEME;
import static android.provider.Telephony.CellBroadcasts.DELIVERY_TIME;
import static android.provider.Telephony.CellBroadcasts.ETWS_WARNING_TYPE;
import static android.provider.Telephony.CellBroadcasts.LAC;
import static android.provider.Telephony.CellBroadcasts.LOCATION_CHECK_TIME;
import static android.provider.Telephony.CellBroadcasts.MAXIMUM_WAIT_TIME;
import static android.provider.Telephony.CellBroadcasts.PLMN;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.cellbroadcastreceiver.CellBroadcastListActivity.CursorLoaderListFragment.KEY_LOADER_ID;
import static com.android.cellbroadcastreceiver.CellBroadcastListActivity.CursorLoaderListFragment.LOADER_HISTORY_FROM_CBS;
import static com.android.cellbroadcastreceiver.CellBroadcastListActivity.CursorLoaderListFragment.MENU_DELETE;
import static com.android.cellbroadcastreceiver.CellBroadcastListActivity.CursorLoaderListFragment.MENU_DELETE_ALL;
import static com.android.cellbroadcastreceiver.CellBroadcastListActivity.CursorLoaderListFragment.MENU_SHOW_ALL_MESSAGES;
import static com.android.cellbroadcastreceiver.CellBroadcastListActivity.CursorLoaderListFragment.MENU_SHOW_REGULAR_MESSAGES;
import static com.android.cellbroadcastreceiver.CellBroadcastListActivity.CursorLoaderListFragment.MENU_VIEW_DETAILS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Telephony;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckedTextView;
import android.widget.ListView;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cellbroadcastreceiver.CellBroadcastCursorAdapter;
import com.android.cellbroadcastreceiver.CellBroadcastListActivity;
import com.android.cellbroadcastreceiver.CellBroadcastListItem;
import com.android.cellbroadcastreceiver.R;
import com.android.internal.view.menu.ContextMenuBuilder;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.List;

public class CellBroadcastListActivityTest extends
        CellBroadcastActivityTestCase<CellBroadcastListActivity> {
    private static final int TEST_TIMEOUT_MILLIS = 1000;

    @Mock
    private NotificationManager mMockNotificationManager;

    @Mock
    private UserManager mMockUserManager;


    @Captor
    private ArgumentCaptor<String> mColumnCaptor;

    private void setWatchFeatureEnabled(boolean enabled) {
        PackageManager mockPackageManager = mock(PackageManager.class);
        doReturn(enabled).when(mockPackageManager).hasSystemFeature(PackageManager.FEATURE_WATCH);
        mContext.injectPackageManager(mockPackageManager);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        injectSystemService(NotificationManager.class, mMockNotificationManager);
        injectSystemService(UserManager.class, mMockUserManager);

        doReturn(true).when(mMockUserManager).isAdminUser();

        SubscriptionManager mockSubscriptionManager = mock(SubscriptionManager.class);
        injectSystemService(SubscriptionManager.class, mockSubscriptionManager);
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubscriptionManager).getActiveSubscriptionInfo(anyInt());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        try {
            stopActivity();
        } catch (Throwable e) {
            // Some tests don't need to call #stopActivity.
        }
    }

    public CellBroadcastListActivityTest() {
        super(CellBroadcastListActivity.class);
    }

    public void testOnCreate() throws Throwable {
        Resources spyRes = mContext.getResources();
        doReturn(false).when(spyRes).getBoolean(R.bool.disable_capture_alert_dialog);
        CellBroadcastListActivity activity = startActivity();
        int flags = activity.getWindow().getAttributes().flags;
        assertEquals((flags & WindowManager.LayoutParams.FLAG_SECURE), 0);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testOnCreateForWatch() throws Throwable {
        setWatchFeatureEnabled(true);

        CellBroadcastListActivity activity = startActivity();

        Field customizeLayoutResIdField =
                CollapsingToolbarBaseActivity.class.getDeclaredField("mCustomizeLayoutResId");
        customizeLayoutResIdField.setAccessible(true);
        assertTrue(customizeLayoutResIdField.getInt(activity) != 0);

        assertNotNull(activity.findViewById(R.id.content_frame));
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testContextMenuForWatch() throws Throwable {
        setWatchFeatureEnabled(true);
        CellBroadcastListActivity activity = startActivity();
        ContextMenuBuilder contextMenu = new ContextMenuBuilder(mContext);

        activity.mListFragment.getListView().createContextMenu(contextMenu);

        assertNotNull(contextMenu.findItem(MENU_DELETE));
    }

    public void testOnCreateWithCaptureRestriction() throws Throwable {
        Resources spyRes = mContext.getResources();
        doReturn(true).when(spyRes).getBoolean(R.bool.disable_capture_alert_dialog);
        CellBroadcastListActivity activity = startActivity();
        int flags = activity.getWindow().getAttributes().flags;
        assertEquals((flags & WindowManager.LayoutParams.FLAG_SECURE),
                WindowManager.LayoutParams.FLAG_SECURE);
    }

    public void testOnListItemClick() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);
        // onListItemClick only checks the view
        View view = new CellBroadcastListItem(mContext, null);
        boolean startActivityWasCalled = false;
        try {
            activity.mListFragment.onListItemClick(null, view, 0, 0);
        } catch (NullPointerException e) {
            // NullPointerException is thrown in startActivity because we have no application thread
            startActivityWasCalled = true;
        }
        assertTrue("onListItemClick should call startActivity", startActivityWasCalled);
    }

    public void testOnActivityCreatedLoaderHistoryFromCbs() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // Override the loader ID to use history from CellBroadcastService
        int testLoaderId = LOADER_HISTORY_FROM_CBS;
        Bundle savedInstanceState = new Bundle();
        savedInstanceState.putInt(KEY_LOADER_ID, testLoaderId);

        // Looper.prepare must be called for the CursorLoader to query an outside ContentProvider
        Looper.prepare();
        activity.mListFragment.onActivityCreated(savedInstanceState);
        assertNotNull(activity.mListFragment.getLoaderManager());
    }

    private static MatrixCursor makeTestCursor() {
        MatrixCursor data =
                new MatrixCursor(CellBroadcastListActivity.CursorLoaderListFragment.QUERY_COLUMNS);
        data.addRow(new Object[] {
                0, //Telephony.CellBroadcasts._ID,
                0, //Telephony.CellBroadcasts.SLOT_INDEX,
                1, //Telephony.CellBroadcasts.SUBSCRIPTION_ID,
                -1, //Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE,
                "", //Telephony.CellBroadcasts.PLMN,
                0, //Telephony.CellBroadcasts.LAC,
                0, //Telephony.CellBroadcasts.CID,
                0, //Telephony.CellBroadcasts.SERIAL_NUMBER,
                0, //Telephony.CellBroadcasts.SERVICE_CATEGORY,
                "", //Telephony.CellBroadcasts.LANGUAGE_CODE,
                0, //Telephony.CellBroadcasts.DATA_CODING_SCHEME,
                "testAlert", //Telephony.CellBroadcasts.MESSAGE_BODY,
                0, //Telephony.CellBroadcasts.MESSAGE_FORMAT,
                0, //Telephony.CellBroadcasts.MESSAGE_PRIORITY,
                0, //Telephony.CellBroadcasts.ETWS_WARNING_TYPE,
                0, //Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS,
                0, //Telephony.CellBroadcasts.CMAS_CATEGORY,
                0, //Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE,
                0, //Telephony.CellBroadcasts.CMAS_SEVERITY,
                0, //Telephony.CellBroadcasts.CMAS_URGENCY,
                0, //Telephony.CellBroadcasts.CMAS_CERTAINTY,
                0, //Telephony.CellBroadcasts.RECEIVED_TIME,
                0, //Telephony.CellBroadcasts.LOCATION_CHECK_TIME,
                false, //Telephony.CellBroadcasts.MESSAGE_BROADCASTED,
                true, //Telephony.CellBroadcasts.MESSAGE_DISPLAYED,
                "", //Telephony.CellBroadcasts.GEOMETRIES,
                0 //Telephony.CellBroadcasts.MAXIMUM_WAIT_TIME
        });
        return data;
    }

    public void testOnLoadFinishedWithData() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // create data with one entry so that the "no alert" text view is invisible
        activity.mListFragment.onLoadFinished(null, makeTestCursor());
        assertEquals(View.INVISIBLE, activity.findViewById(R.id.empty).getVisibility());
        assertTrue(activity.findViewById(android.R.id.list).isLongClickable());
    }

    public void testOnLoadFinishedEmptyData() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // create empty data so that the "no alert" text view becomes visible
        Cursor data =
                new MatrixCursor(CellBroadcastListActivity.CursorLoaderListFragment.QUERY_COLUMNS);
        activity.mListFragment.onLoadFinished(null, data);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.empty).getVisibility());
        assertFalse(activity.findViewById(android.R.id.list).isLongClickable());
    }

    public void testOnLoadFinishedEmptyToExistData() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // When the history changes from empty to exist,
        // the ListView's LongClickable is from false to true.
        Cursor data =
                new MatrixCursor(CellBroadcastListActivity.CursorLoaderListFragment.QUERY_COLUMNS);
        activity.mListFragment.onLoadFinished(null, data);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.empty).getVisibility());
        assertFalse(activity.findViewById(android.R.id.list).isLongClickable());

        activity.mListFragment.onLoadFinished(null, makeTestCursor());
        assertEquals(View.INVISIBLE, activity.findViewById(R.id.empty).getVisibility());
        assertTrue(activity.findViewById(android.R.id.list).isLongClickable());
    }

    public void testOnLoaderReset() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        activity.mListFragment.onLoaderReset(null);
        assertNull("mAdapter.getCursor() should be null after reset",
                activity.mListFragment.mAdapter.getCursor());
    }

    public void testOnContextItemSelectedDelete() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // Mock out the adapter cursor
        Cursor mockCursor = getMockCursor(activity, 0, 0L);

        // create mock delete menu item
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(MENU_DELETE).when(mockMenuItem).getItemId();
        activity.mListFragment.getListView().setItemChecked(0, true);

        // must call looper.prepare to create alertdialog
        Looper.prepare();
        activity.mListFragment.onContextItemSelected(mockMenuItem);
        waitForHandlerAction(Handler.getMain(), TEST_TIMEOUT_MILLIS);

        assertNotNull("onContextItemSelected - MENU_DELETE_ALL should create alert dialog",
                activity.mListFragment.getFragmentManager().findFragmentByTag(
                        CellBroadcastListActivity.CursorLoaderListFragment.KEY_DELETE_DIALOG));

        verify(mockCursor, atLeastOnce()).getColumnIndex(eq(Telephony.CellBroadcasts._ID));
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testOnContextItemSelectedDeleteForWatch() throws Throwable {
        setWatchFeatureEnabled(true);
        CellBroadcastListActivity activity = startActivity();
        Cursor mockCursor = mock(Cursor.class);
        doReturn(0).when(mockCursor).getPosition();
        doReturn(10L).when(mockCursor).getLong(anyInt());
        activity.mListFragment.mAdapter.swapCursor(mockCursor);
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(MENU_DELETE).when(mockMenuItem).getItemId();
        activity.mListFragment.getListView().setSelection(1);

        activity.mListFragment.onContextItemSelected(mockMenuItem);
        waitForHandlerAction(Handler.getMain(), TEST_TIMEOUT_MILLIS);


        Fragment frag = activity.mListFragment.getFragmentManager().findFragmentByTag(
                CellBroadcastListActivity.CursorLoaderListFragment.KEY_DELETE_DIALOG);
        assertNotNull("onContextItemSelected - MENU_DELETE should create alert dialog",
                frag);
        long[] rowId = frag.getArguments().getLongArray(
                CellBroadcastListActivity.CursorLoaderListFragment.DeleteDialogFragment.ROW_ID);
        long[] expectedResult = {10L};
        assertTrue(Arrays.equals(expectedResult, rowId));
    }

    public void testOnActionItemClickedDelete() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // Mock out the adapter cursor
        Cursor mockCursor = getMockCursor(activity, 0, 0L);

        // create mock delete menu item
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(R.id.action_delete).when(mockMenuItem).getItemId();
        activity.mListFragment.getListView().setItemChecked(0, true);

        // must call looper.prepare to create alertdialog
        Looper.prepare();
        ActionMode mode = mock(ActionMode.class);
        activity.mListFragment.getMultiChoiceModeListener().onActionItemClicked(mode, mockMenuItem);
        waitForHandlerAction(Handler.getMain(), TEST_TIMEOUT_MILLIS);

        assertNotNull("onContextItemSelected - MENU_DELETE_ALL should create alert dialog",
                activity.mListFragment.getFragmentManager().findFragmentByTag(
                        CellBroadcastListActivity.CursorLoaderListFragment.KEY_DELETE_DIALOG));

        verify(mockCursor, atLeastOnce()).getColumnIndex(eq(Telephony.CellBroadcasts._ID));
    }

    public void testOnActionTitleOnMultiSelect() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        activity.mListFragment.getListView().setItemChecked(0, true);
        activity.mListFragment.getListView().setItemChecked(1, true);

        ActionMode mode = mock(ActionMode.class);
        activity.mListFragment.getMultiChoiceModeListener().onItemCheckedStateChanged(
                mode, 0, 0, true);
        ArgumentCaptor<CharSequence> title = ArgumentCaptor.forClass(CharSequence.class);
        verify(mode, atLeastOnce()).setTitle(title.capture());
        assertEquals("title should be the number of selected items",
                title.getValue(), "2");

        activity.mListFragment.getListView().setItemChecked(1, false);
        activity.mListFragment.getMultiChoiceModeListener().onItemCheckedStateChanged(
                mode, 0, 0, true);
        verify(mode, atLeastOnce()).setTitle(title.capture());
        assertEquals("title should be the number of selected items",
                title.getValue(), "1");

        activity.mListFragment.getListView().setItemChecked(1, true);
        Menu menu = mock(Menu.class);
        MenuInflater inflator = mock(MenuInflater.class);
        doReturn(inflator).when(mode).getMenuInflater();
        activity.mListFragment.getMultiChoiceModeListener().onCreateActionMode(mode, menu);
        verify(mode, atLeastOnce()).setTitle(title.capture());
        assertEquals("title should be the number of selected items",
                title.getValue(), "2");
    }

    public void testOnActionItemClickedDeleteOnMultiSelect() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        long rowId1 = 20;
        long rowId2 = 30;
        long rowId3 = 40;
        MatrixCursor data =
                new MatrixCursor(CellBroadcastListActivity.CursorLoaderListFragment.QUERY_COLUMNS);
        data.addRow(new Object[] {
                rowId1, //Telephony.CellBroadcasts._ID,
                0, //Telephony.CellBroadcasts.SLOT_INDEX,
                1, //Telephony.CellBroadcasts.SUBSCRIPTION_ID,
                -1, //Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE,
                "", //Telephony.CellBroadcasts.PLMN,
                0, //Telephony.CellBroadcasts.LAC,
                0, //Telephony.CellBroadcasts.CID,
                "", //Telephony.CellBroadcasts.SERIAL_NUMBER,
                0, //Telephony.CellBroadcasts.SERVICE_CATEGORY,
                "", //Telephony.CellBroadcasts.LANGUAGE_CODE,
                0, //Telephony.CellBroadcasts.DATA_CODING_SCHEME,
                "", //Telephony.CellBroadcasts.MESSAGE_BODY,
                0, //Telephony.CellBroadcasts.MESSAGE_FORMAT,
                0, //Telephony.CellBroadcasts.MESSAGE_PRIORITY,
                0, //Telephony.CellBroadcasts.ETWS_WARNING_TYPE,
                0, //Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS,
                0, //Telephony.CellBroadcasts.CMAS_CATEGORY,
                0, //Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE,
                0, //Telephony.CellBroadcasts.CMAS_SEVERITY,
                0, //Telephony.CellBroadcasts.CMAS_URGENCY,
                0, //Telephony.CellBroadcasts.CMAS_CERTAINTY,
                0, //Telephony.CellBroadcasts.RECEIVED_TIME,
                0, //Telephony.CellBroadcasts.LOCATION_CHECK_TIME,
                false, //Telephony.CellBroadcasts.MESSAGE_BROADCASTED,
                true, //Telephony.CellBroadcasts.MESSAGE_DISPLAYED,
                "", //Telephony.CellBroadcasts.GEOMETRIES,
                0 //Telephony.CellBroadcasts.MAXIMUM_WAIT_TIME
        });
        data.addRow(new Object[] {
                rowId2, //Telephony.CellBroadcasts._ID,
                0, //Telephony.CellBroadcasts.SLOT_INDEX,
                1, //Telephony.CellBroadcasts.SUBSCRIPTION_ID,
                -1, //Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE,
                "", //Telephony.CellBroadcasts.PLMN,
                0, //Telephony.CellBroadcasts.LAC,
                0, //Telephony.CellBroadcasts.CID,
                "", //Telephony.CellBroadcasts.SERIAL_NUMBER,
                0, //Telephony.CellBroadcasts.SERVICE_CATEGORY,
                "", //Telephony.CellBroadcasts.LANGUAGE_CODE,
                0, //Telephony.CellBroadcasts.DATA_CODING_SCHEME,
                "", //Telephony.CellBroadcasts.MESSAGE_BODY,
                0, //Telephony.CellBroadcasts.MESSAGE_FORMAT,
                0, //Telephony.CellBroadcasts.MESSAGE_PRIORITY,
                0, //Telephony.CellBroadcasts.ETWS_WARNING_TYPE,
                0, //Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS,
                0, //Telephony.CellBroadcasts.CMAS_CATEGORY,
                0, //Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE,
                0, //Telephony.CellBroadcasts.CMAS_SEVERITY,
                0, //Telephony.CellBroadcasts.CMAS_URGENCY,
                0, //Telephony.CellBroadcasts.CMAS_CERTAINTY,
                0, //Telephony.CellBroadcasts.RECEIVED_TIME,
                0, //Telephony.CellBroadcasts.LOCATION_CHECK_TIME,
                false, //Telephony.CellBroadcasts.MESSAGE_BROADCASTED,
                true, //Telephony.CellBroadcasts.MESSAGE_DISPLAYED,
                "", //Telephony.CellBroadcasts.GEOMETRIES,
                0 //Telephony.CellBroadcasts.MAXIMUM_WAIT_TIME
        });
        data.addRow(new Object[] {
                rowId3, //Telephony.CellBroadcasts._ID,
                0, //Telephony.CellBroadcasts.SLOT_INDEX,
                1, //Telephony.CellBroadcasts.SUBSCRIPTION_ID,
                -1, //Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE,
                "", //Telephony.CellBroadcasts.PLMN,
                0, //Telephony.CellBroadcasts.LAC,
                0, //Telephony.CellBroadcasts.CID,
                "", //Telephony.CellBroadcasts.SERIAL_NUMBER,
                0, //Telephony.CellBroadcasts.SERVICE_CATEGORY,
                "", //Telephony.CellBroadcasts.LANGUAGE_CODE,
                0, //Telephony.CellBroadcasts.DATA_CODING_SCHEME,
                "", //Telephony.CellBroadcasts.MESSAGE_BODY,
                0, //Telephony.CellBroadcasts.MESSAGE_FORMAT,
                0, //Telephony.CellBroadcasts.MESSAGE_PRIORITY,
                0, //Telephony.CellBroadcasts.ETWS_WARNING_TYPE,
                0, //Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS,
                0, //Telephony.CellBroadcasts.CMAS_CATEGORY,
                0, //Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE,
                0, //Telephony.CellBroadcasts.CMAS_SEVERITY,
                0, //Telephony.CellBroadcasts.CMAS_URGENCY,
                0, //Telephony.CellBroadcasts.CMAS_CERTAINTY,
                0, //Telephony.CellBroadcasts.RECEIVED_TIME,
                0, //Telephony.CellBroadcasts.LOCATION_CHECK_TIME,
                false, //Telephony.CellBroadcasts.MESSAGE_BROADCASTED,
                true, //Telephony.CellBroadcasts.MESSAGE_DISPLAYED,
                "", //Telephony.CellBroadcasts.GEOMETRIES,
                0 //Telephony.CellBroadcasts.MAXIMUM_WAIT_TIME
        });
        activity.mListFragment.mAdapter.swapCursor(data);

        // create mock delete menu item
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(R.id.action_delete).when(mockMenuItem).getItemId();
        activity.mListFragment.getListView().setItemChecked(0, true);
        activity.mListFragment.getListView().setItemChecked(2, true);

        // must call looper.prepare to create alertdialog
        Looper.prepare();
        ActionMode mode = mock(ActionMode.class);
        activity.mListFragment.getMultiChoiceModeListener().onActionItemClicked(mode, mockMenuItem);
        waitForHandlerAction(Handler.getMain(), TEST_TIMEOUT_MILLIS);

        assertNotNull("onContextItemSelected - MENU_DELETE_ALL should create alert dialog",
                activity.mListFragment.getFragmentManager().findFragmentByTag(
                        CellBroadcastListActivity.CursorLoaderListFragment.KEY_DELETE_DIALOG));

        Fragment frag = activity.mListFragment.getFragmentManager().findFragmentByTag(
                CellBroadcastListActivity.CursorLoaderListFragment.KEY_DELETE_DIALOG);
        long[] rowId = frag.getArguments().getLongArray(
                CellBroadcastListActivity.CursorLoaderListFragment.DeleteDialogFragment.ROW_ID);
        long[] expectedResult = {rowId1, rowId3};
        assertTrue(Arrays.equals(expectedResult, expectedResult));
    }

    public void testOnActionItemClickedViewDetail() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // mock out the AlertDialog.Builder
        AlertDialog.Builder mockAlertDialogBuilder = getMockAlertDialogBuilder(activity);

        // create mock delete menu item
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(R.id.action_detail_info).when(mockMenuItem).getItemId();
        activity.mListFragment.getListView().setItemChecked(0, true);

        // must call looper.prepare to create alertdialog
        Looper.prepare();

        // when cursor is null, verify do nothing for showing an alert dialog
        ActionMode mode = mock(ActionMode.class);
        activity.mListFragment.mAdapter.swapCursor(null);
        activity.mListFragment.getMultiChoiceModeListener().onActionItemClicked(mode, mockMenuItem);

        verify(mode, times(1)).finish();
        verify(mockAlertDialogBuilder, never()).show();

        // mock out the adapter cursor
        Cursor mockCursor = getMockCursor(activity, 1, 0L);

        // verify the showing the alert dialog
        activity.mListFragment.getMultiChoiceModeListener().onActionItemClicked(mode, mockMenuItem);

        verify(mode, times(2)).finish();
        verify(mockAlertDialogBuilder).show();

        // getColumnIndex is called 13 times within CellBroadcastCursorAdapter.createFromCursor
        verify(mockCursor, times(13)).getColumnIndex(mColumnCaptor.capture());
        List<String> columns = mColumnCaptor.getAllValues();
        assertTrue(contains(columns, PLMN));
        assertTrue(contains(columns, LAC));
        assertTrue(contains(columns, CID));
        assertTrue(contains(columns, ETWS_WARNING_TYPE));
        assertTrue(contains(columns, CMAS_MESSAGE_CLASS));
        assertTrue(contains(columns, CMAS_CATEGORY));
        assertTrue(contains(columns, CMAS_RESPONSE_TYPE));
        assertTrue(contains(columns, CMAS_SEVERITY));
        assertTrue(contains(columns, CMAS_URGENCY));
        assertTrue(contains(columns, CMAS_CERTAINTY));
        assertTrue(contains(columns, DELIVERY_TIME));
        assertTrue(contains(columns, DATA_CODING_SCHEME));
        assertTrue(contains(columns, MAXIMUM_WAIT_TIME));
    }

    public void testOnContextItemSelectedViewDetails() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // mock out the adapter cursor and the AlertDialog.Builder
        Cursor mockCursor = getMockCursor(activity, 1, 0L);
        AlertDialog.Builder mockAlertDialogBuilder = getMockAlertDialogBuilder(activity);

        // create mock delete menu item
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(MENU_VIEW_DETAILS).when(mockMenuItem).getItemId();

        // must call looper.prepare to create alertdialog
        Looper.prepare();

        // verify the showing the alert dialog
        activity.mListFragment.onContextItemSelected(mockMenuItem);

        verify(mockAlertDialogBuilder).show();

        // getColumnIndex is called 13 times within CellBroadcastCursorAdapter.createFromCursor
        verify(mockCursor, times(13)).getColumnIndex(mColumnCaptor.capture());
        List<String> columns = mColumnCaptor.getAllValues();
        assertTrue(contains(columns, PLMN));
        assertTrue(contains(columns, LAC));
        assertTrue(contains(columns, CID));
        assertTrue(contains(columns, ETWS_WARNING_TYPE));
        assertTrue(contains(columns, CMAS_MESSAGE_CLASS));
        assertTrue(contains(columns, CMAS_CATEGORY));
        assertTrue(contains(columns, CMAS_RESPONSE_TYPE));
        assertTrue(contains(columns, CMAS_SEVERITY));
        assertTrue(contains(columns, CMAS_URGENCY));
        assertTrue(contains(columns, CMAS_CERTAINTY));
        assertTrue(contains(columns, DELIVERY_TIME));
        assertTrue(contains(columns, DATA_CODING_SCHEME));
        assertTrue(contains(columns, MAXIMUM_WAIT_TIME));
    }

    private boolean contains(List<String> columns, String column) {
        for (String c : columns) {
            if (c.equals(column)) {
                return true;
            }
        }
        return false;
    }

    public void testOnOptionsItemSelected() throws Throwable {
        CellBroadcastListActivity activity = startActivity();

        // create mock home menu item
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(android.R.id.home).when(mockMenuItem).getItemId();

        // the activity should hkandle home button press
        assertTrue(activity.onOptionsItemSelected(mockMenuItem));
    }

    public void testFragmentOnOptionsItemSelected() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // create mock delete all menu item
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(MENU_DELETE_ALL).when(mockMenuItem).getItemId();

        // must call looper.prepare to create alertdialog
        Looper.prepare();
        activity.mListFragment.onOptionsItemSelected(mockMenuItem);
        waitForHandlerAction(Handler.getMain(), TEST_TIMEOUT_MILLIS);

        assertNotNull("onContextItemSelected - MENU_DELETE_ALL should create alert dialog",
                activity.mListFragment.getFragmentManager().findFragmentByTag(
                        CellBroadcastListActivity.CursorLoaderListFragment.KEY_DELETE_DIALOG));

        // "show all messages" and "show regular messages" options return false to allow normal
        // menu  processing to continue
        doReturn(MENU_SHOW_ALL_MESSAGES).when(mockMenuItem).getItemId();
        assertFalse(activity.mListFragment.onOptionsItemSelected(mockMenuItem));
        doReturn(MENU_SHOW_REGULAR_MESSAGES).when(mockMenuItem).getItemId();
        assertFalse(activity.mListFragment.onOptionsItemSelected(mockMenuItem));
    }

    public void testFragmentOnCreateOptionsMenu() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // create mock menu
        Menu mockMenu = mock(Menu.class);
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(mockMenuItem).when(mockMenu).add(anyInt(), anyInt(), anyInt(), anyInt());

        activity.mListFragment.onCreateOptionsMenu(mockMenu, null);
        verify(mockMenu, times(4)).add(anyInt(), anyInt(), anyInt(), anyInt());
    }

    public void testFragmentOnPrepareOptionsMenu() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        // create mock menu
        Menu mockMenu = mock(Menu.class);
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(mockMenuItem).when(mockMenu).findItem(anyInt());

        activity.mListFragment.onPrepareOptionsMenu(mockMenu);
        verify(mockMenuItem, times(3)).setVisible(anyBoolean());
    }

    public void testFragmentOnSaveInstanceState() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        Bundle bundle = new Bundle();
        activity.mListFragment.onSaveInstanceState(bundle);
        assertTrue(bundle.containsKey(KEY_LOADER_ID));
    }

    public void testActionModeSate() {
        CellBroadcastCursorAdapter adapter = new CellBroadcastCursorAdapter(mock(Context.class),
                null);
        boolean actionMode = adapter.getIsActionMode();
        assertEquals(false, actionMode);

        adapter.setIsActionMode(true);
        actionMode = adapter.getIsActionMode();
        assertEquals(true, actionMode);

        CellBroadcastCursorAdapter adapter2 = new CellBroadcastCursorAdapter(mock(Context.class),
                null);
        actionMode = adapter2.getIsActionMode();
        assertEquals(false, actionMode);

        adapter2.setIsActionMode(true);
        actionMode = adapter2.getIsActionMode();
        assertEquals(true, actionMode);
    }

    public void testCursorAdaptorBindViewForWatch() {
        // Watch layout misses checkbox.
        // mockListItemView.findViewById(R.id.checkBox) returns null as default setting up this
        // usecase.
        CellBroadcastListItem mockListItemView = mock(CellBroadcastListItem.class);
        ListView mockListView = mock(ListView.class);
        MatrixCursor data = makeTestCursor();
        data.moveToFirst();
        CellBroadcastCursorAdapter adapter = new CellBroadcastCursorAdapter(mContext,
                mockListView);

        adapter.bindView(mockListItemView, mContext, data);

        ArgumentCaptor<SmsCbMessage> messageCaptor = ArgumentCaptor.forClass(SmsCbMessage.class);
        verify(mockListItemView).bind(messageCaptor.capture());
        assertEquals("testAlert", messageCaptor.getValue().getMessageBody());
    }

    public void testCursorAdaptorBindView() {
        CellBroadcastListItem mockListItemView = mock(CellBroadcastListItem.class);
        ListView mockListView = mock(ListView.class);
        CheckedTextView mockCheckbox = mock(CheckedTextView.class);
        doReturn(mockCheckbox).when(mockListItemView).findViewById(R.id.checkBox);
        MatrixCursor data = makeTestCursor();
        data.moveToFirst();
        CellBroadcastCursorAdapter adapter = new CellBroadcastCursorAdapter(mContext,
                mockListView);

        adapter.setIsActionMode(true);
        adapter.bindView(mockListItemView, mContext, data);
        verify(mockCheckbox).setVisibility(View.VISIBLE);

        adapter.setIsActionMode(false);
        adapter.bindView(mockListItemView, mContext, data);
        verify(mockCheckbox).setVisibility(View.GONE);
    }

    public void testGetLocationCheckTime() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        Cursor mockCursor = getMockCursor(activity, 0, 0L);
        doReturn("test").when(mockCursor).getString(anyInt());
        AlertDialog.Builder mockAlertDialogBuilder = getMockAlertDialogBuilder(activity);

        // set the LocationCheckTime
        Field fieldCurrentLoaderId =
                CellBroadcastListActivity.CursorLoaderListFragment.class.getDeclaredField(
                        "mCurrentLoaderId");
        fieldCurrentLoaderId.setAccessible(true);
        fieldCurrentLoaderId.setInt(activity.mListFragment, LOADER_HISTORY_FROM_CBS);
        final Long locationCheckTime = 10L;
        final int locationCheckTimeColumnIndex = 1111;
        doReturn(locationCheckTimeColumnIndex).when(mockCursor).getColumnIndex(
                eq(LOCATION_CHECK_TIME));
        doReturn(locationCheckTime).when(mockCursor).getLong(eq(locationCheckTimeColumnIndex));

        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(R.id.action_detail_info).when(mockMenuItem).getItemId();

        activity.mListFragment.getListView().setItemChecked(0, true);
        activity.mListFragment.getMultiChoiceModeListener()
                .onActionItemClicked(mock(ActionMode.class), mockMenuItem);

        // verify the locationCheckTime in dialog's message
        ArgumentCaptor<CharSequence> detailCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(mockAlertDialogBuilder).setMessage(detailCaptor.capture());
        assertTrue(detailCaptor.getValue().toString().contains(
                DateFormat.getDateTimeInstance().format(locationCheckTime)));
        verify(mockAlertDialogBuilder).show();
    }

    public void testOnResume() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);
        Looper.prepare();

        CellBroadcastListActivity.CursorLoaderListFragment mockFragment = spy(
                activity.mListFragment);
        LoaderManager mockLoaderManager = mock(LoaderManager.class);
        doReturn(mockLoaderManager).when(mockFragment).getLoaderManager();

        mockFragment.onResume();
        verify(mockLoaderManager).restartLoader(anyInt(), any(), any());
    }

    public void testOnStart() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        CellBroadcastListActivity mockActivity = spy(activity);
        final Window mockWindow = mock(Window.class);
        doReturn(mockWindow).when(mockActivity).getWindow();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mockActivity.onStart();
        });
        verify(mockWindow).addSystemFlags(eq(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS));
    }

    public void testOnDestroyActionMode() throws Throwable {
        CellBroadcastListActivity activity = startActivity();
        assertNotNull(activity.mListFragment);

        CellBroadcastCursorAdapter mockAdapter = spy(activity.mListFragment.mAdapter);
        activity.mListFragment.mAdapter = mockAdapter;
        ActionMode mode = mock(ActionMode.class);

        activity.mListFragment.getMultiChoiceModeListener().onDestroyActionMode(mode);
        verify(mockAdapter).setIsActionMode(eq(false));
        verify(mockAdapter).notifyDataSetChanged();
    }

    public void testOnActionItemClickedUnsupportedAction() throws Throwable {
        CellBroadcastListActivity activity = startActivity();

        // when click unsupported action, verify do nothing
        MenuItem mockMenuItem = mock(MenuItem.class);
        doReturn(-1).when(mockMenuItem).getItemId();
        activity.mListFragment.getListView().setItemChecked(0, true);

        ActionMode mode = mock(ActionMode.class);
        assertFalse(activity.mListFragment.getMultiChoiceModeListener()
                .onActionItemClicked(mode, mockMenuItem));
        verify(mode, never()).finish();
    }

    private Cursor getMockCursor(CellBroadcastListActivity activity, int position, Long value) {
        Cursor mockCursor = mock(Cursor.class);
        doReturn(position).when(mockCursor).getPosition();
        doReturn(value).when(mockCursor).getLong(anyInt());
        activity.mListFragment.mAdapter.swapCursor(mockCursor);
        return mockCursor;
    }

    private AlertDialog.Builder getMockAlertDialogBuilder(CellBroadcastListActivity activity) {
        AlertDialog.Builder mockAlertDialogBuilder = mock(AlertDialog.Builder.class);
        doReturn(mockAlertDialogBuilder).when(mockAlertDialogBuilder).setTitle(anyInt());
        doReturn(mockAlertDialogBuilder).when(mockAlertDialogBuilder).setMessage(any());
        doReturn(mockAlertDialogBuilder).when(mockAlertDialogBuilder).setCancelable(anyBoolean());
        activity.mListFragment.mInjectAlertDialogBuilder = mockAlertDialogBuilder;
        return mockAlertDialogBuilder;
    }
}
