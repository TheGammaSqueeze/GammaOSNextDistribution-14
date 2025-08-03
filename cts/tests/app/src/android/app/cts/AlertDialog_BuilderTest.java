/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.stubs.R;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

@SmallTest
@RunWith(JUnit4.class)
public final class AlertDialog_BuilderTest extends AlertDialog_BuilderTestBase {

    private Builder mBuilder;
    private final CharSequence mTitle = "title";
    private Drawable mDrawable;
    private AlertDialog mDialog;
    private Button mButton;
    private CharSequence mSelectedItem;

    private View mView;
    private ListView mListView;

    private OnClickListener mOnClickListener = mock(OnClickListener.class);

    private OnCancelListener mOnCancelListener = mock(OnCancelListener.class);

    private OnDismissListener mOnDismissListener = mock(OnDismissListener.class);

    private OnKeyListener mOnKeyListener = mock(OnKeyListener.class);

    private OnItemSelectedListener mOnItemSelectedListener = mock(OnItemSelectedListener.class);

    private OnMultiChoiceClickListener mOnMultiChoiceClickListener =
            mock(OnMultiChoiceClickListener.class);

    @Test
    public void testConstructor() {
        new AlertDialog.Builder(mDialogActivity);
    }

    @Test
    public void testConstructorWithThemeId() {
        mBuilder = new AlertDialog.Builder(mDialogActivity, R.style.DialogTheme_Test);

        // Get the context from the builder and attempt to resolve a custom attribute
        // set on our theme. This way we verify that our theme has been applied to the
        // builder.
        final Context themedContext = mBuilder.getContext();
        int[] attrs = new int[]{R.attr.themeInteger};
        TypedArray ta = themedContext.obtainStyledAttributes(attrs);
        assertEquals(20, ta.getInt(0, 0));
    }

    @Test
    public void testSetIconWithParamInt() {
        mActivityRule.getScenario().onActivity(activity -> {
            mDrawable = activity.getResources().getDrawable(android.R.drawable.btn_default);
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setIcon(android.R.drawable.btn_default);
            mDialog = mBuilder.show();
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetIconWithParamDrawable() {
        mActivityRule.getScenario().onActivity(activity -> {
            mDrawable = activity.getResources().getDrawable(android.R.drawable.btn_default);
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setIcon(mDrawable);
            mDialog = mBuilder.show();
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetIconAttribute() {
        mActivityRule.getScenario().onActivity(activity -> {
            mDrawable = activity.getResources().getDrawable(android.R.drawable.btn_default);
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setIconAttribute(android.R.attr.alertDialogIcon);
            mDialog = mBuilder.show();
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetPositiveButtonWithParamInt() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setPositiveButton(android.R.string.ok, mOnClickListener);
            mBuilder.setOnDismissListener(mOnDismissListener);
            mDialog = mBuilder.show();
            mButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            mButton.performClick();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(mDialogActivity.getText(android.R.string.ok), mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_POSITIVE);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetPositiveButtonWithParamCharSequence() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setPositiveButton(android.R.string.ok, mOnClickListener);
            mBuilder.setOnDismissListener(mOnDismissListener);
            mDialog = mBuilder.show();
            mButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            mButton.performClick();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mDialogActivity.getText(android.R.string.ok), mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_POSITIVE);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetNegativeButtonWithParamCharSequence() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setNegativeButton(mTitle, mOnClickListener);
            mBuilder.setOnDismissListener(mOnDismissListener);
            mDialog = mBuilder.show();
            mButton = mDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            mButton.performClick();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTitle, mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_NEGATIVE);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetNegativeButtonWithParamInt() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setNegativeButton(R.string.notify, mOnClickListener);
            mBuilder.setOnDismissListener(mOnDismissListener);
            mDialog = mBuilder.show();
            mButton = mDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            mButton.performClick();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mDialogActivity.getText(R.string.notify), mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_NEGATIVE);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetNeutralButtonWithParamInt() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setNeutralButton(R.string.notify, mOnClickListener);
            mBuilder.setOnDismissListener(mOnDismissListener);
            mDialog = mBuilder.show();
            mButton = mDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            mButton.performClick();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mDialogActivity.getText(R.string.notify), mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_NEUTRAL);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetNeutralButtonWithParamCharSequence() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setNeutralButton(mTitle, mOnClickListener);
            mBuilder.setOnDismissListener(mOnDismissListener);
            mDialog = mBuilder.show();
            mButton = mDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            mButton.performClick();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTitle, mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_NEUTRAL);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    private void testCancelable(final boolean cancelable) {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setCancelable(cancelable);
            mDialog = mBuilder.show();

        });
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(mDialog::isShowing);
        sendKeySync(KeyEvent.KEYCODE_BACK);
        mInstrumentation.waitForIdleSync();
        new PollingCheck() {
            @Override
            protected boolean check() {
                boolean showing = mDialog.isShowing();
                if (cancelable) {
                    // if the dialog is cancelable, then pressing back
                    // should cancel it. Thus it should not be showing
                    return !showing;
                } else {
                    // if the dialog is not cancelable, pressing back
                    // should so nothing and it should still be showing
                    return showing;
                }
            }
        }.run();
    }

    @Test
    public void testSetCancelable() {
        testCancelable(true);
    }

    @Test
    public void testDisableCancelable() {
        testCancelable(false);
    }

    @Test
    public void testSetOnCancelListener() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setOnCancelListener(mOnCancelListener);
            mDialog = mBuilder.show();
            mDialog.cancel();
        });
        mInstrumentation.waitForIdleSync();
        verify(mOnCancelListener, times(1)).onCancel(mDialog);
        verifyNoMoreInteractions(mOnCancelListener);
    }

    @Test
    public void testSetOnDismissListener() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setOnDismissListener(mOnDismissListener);
            mDialog = mBuilder.show();
            mDialog.dismiss();
        });
        mInstrumentation.waitForIdleSync();
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetOnKeyListener() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setOnKeyListener(mOnKeyListener);
            mDialog = mBuilder.show();
        });
        mInstrumentation.waitForIdleSync();
        sendKeySync(KeyEvent.KEYCODE_0);
        sendKeySync(KeyEvent.KEYCODE_1);
        mInstrumentation.waitForIdleSync();
        // Use Mockito captures so that we can verify that each "sent" key code resulted
        // in one DOWN event and one UP event.
        ArgumentCaptor<KeyEvent> keyEvent0Captor = ArgumentCaptor.forClass(KeyEvent.class);
        ArgumentCaptor<KeyEvent> keyEvent1Captor = ArgumentCaptor.forClass(KeyEvent.class);
        verify(mOnKeyListener, times(2)).onKey(eq(mDialog), eq(KeyEvent.KEYCODE_0),
                keyEvent0Captor.capture());
        verify(mOnKeyListener, times(2)).onKey(eq(mDialog), eq(KeyEvent.KEYCODE_1),
                keyEvent1Captor.capture());
        verifyNoMoreInteractions(mOnKeyListener);
        assertEquals(KeyEvent.ACTION_DOWN, keyEvent0Captor.getAllValues().get(0).getAction());
        assertEquals(KeyEvent.ACTION_UP, keyEvent0Captor.getAllValues().get(1).getAction());
        assertEquals(KeyEvent.ACTION_DOWN, keyEvent1Captor.getAllValues().get(0).getAction());
        assertEquals(KeyEvent.ACTION_UP, keyEvent1Captor.getAllValues().get(1).getAction());
    }

    @Test
    public void testSetItemsWithParamInt() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setItems(R.array.difficultyLevel, mOnClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();
        });
        mInstrumentation.waitForIdleSync();

        final CharSequence[] levels = mDialogActivity.getResources().getTextArray(
                R.array.difficultyLevel);
        assertEquals(levels[0], mListView.getItemAtPosition(0));
    }

    @Test
    public void testSetItemsWithParamCharSequence() {
        final CharSequence[] expect = mDialogActivity.getResources().getTextArray(
                R.array.difficultyLevel);

        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setItems(expect, mOnClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(expect[0], mListView.getItemAtPosition(0));
    }

    @Test
    public void testSetAdapter() {
        final ListAdapter adapter = new AdapterTest();
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setAdapter(adapter, mOnClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(adapter, mListView.getAdapter());
    }

    @Test
    public void testSetMultiChoiceItemsWithParamInt() {
        final CharSequence[] items = mDialogActivity.getResources().getTextArray(
                R.array.difficultyLevel);

        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setMultiChoiceItems(R.array.difficultyLevel, null,
                    mOnMultiChoiceClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();
        });
        if (mListView.isInTouchMode()) {
            reAttachListViewAdapter(mListView);
        }
        mActivityRule.getScenario().onActivity(unused -> {
            mSelectedItem = (CharSequence) mListView.getSelectedItem();
            mListView.performItemClick(null, 0, 0);
            mListView.performItemClick(null, 1, 0);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        verify(mOnMultiChoiceClickListener, times(1)).onClick(mDialog, 0, true);
        verify(mOnMultiChoiceClickListener, times(1)).onClick(mDialog, 1, true);
        verifyNoMoreInteractions(mOnMultiChoiceClickListener);
        assertEquals(items[0], mListView.getItemAtPosition(0));
    }

    @Test
    public void testSetMultiChoiceItemsWithParamCharSequence() {
        final CharSequence[] items = mDialogActivity.getResources().getTextArray(
                R.array.difficultyLevel);

        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setMultiChoiceItems(items, null, mOnMultiChoiceClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();
        });
        if (mListView.isInTouchMode()) {
            reAttachListViewAdapter(mListView);
        }
        mActivityRule.getScenario().onActivity(unused -> {
            mSelectedItem = (CharSequence) mListView.getSelectedItem();
            mListView.performItemClick(null, 0, 0);
            mListView.performItemClick(null, 1, 0);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        verify(mOnMultiChoiceClickListener, times(1)).onClick(mDialog, 0, true);
        verify(mOnMultiChoiceClickListener, times(1)).onClick(mDialog, 1, true);
        verifyNoMoreInteractions(mOnMultiChoiceClickListener);
        assertEquals(items[0], mListView.getItemAtPosition(0));
    }

    @Test
    public void testSetSingleChoiceItemsWithParamInt() {
        final CharSequence[] items = mDialogActivity.getResources().getTextArray(
                R.array.difficultyLevel);

        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(mDialogActivity);
            mBuilder.setSingleChoiceItems(R.array.difficultyLevel, 0,
                    mOnClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();
        });
        if (mListView.isInTouchMode()) {
            reAttachListViewAdapter(mListView);
        }
        mActivityRule.getScenario().onActivity(unused -> {
            mSelectedItem = (CharSequence) mListView.getSelectedItem();
            mListView.performItemClick(null, 0, 0);
        });

        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        assertEquals(items[0], mListView.getItemAtPosition(0));
        verify(mOnClickListener, times(1)).onClick(mDialog, 0);
        verifyNoMoreInteractions(mOnClickListener);
    }

    @Test
    public void testSetSingleChoiceItemsWithParamCharSequence() {
        final CharSequence[] items = mDialogActivity.getResources().getTextArray(
                R.array.difficultyLevel);

        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(mDialogActivity);
            mBuilder.setSingleChoiceItems(items, 0, mOnClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();

        });
        if (mListView.isInTouchMode()) {
            reAttachListViewAdapter(mListView);
        }
        mActivityRule.getScenario().onActivity(unused -> {
            mSelectedItem = (CharSequence) mListView.getSelectedItem();
            mListView.performItemClick(null, 0, 0);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        assertEquals(items[0], mListView.getItemAtPosition(0));
        verify(mOnClickListener, times(1)).onClick(mDialog, 0);
        verifyNoMoreInteractions(mOnClickListener);
    }

    @Test
    public void testSetSingleChoiceItems() {
        final CharSequence[] items = mDialogActivity.getResources().getTextArray(
                R.array.difficultyLevel);

        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setSingleChoiceItems(new ArrayAdapter<>(activity,
                            android.R.layout.select_dialog_singlechoice, android.R.id.text1,
                            items), 0,
                    mOnClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();
        });
        if (mListView.isInTouchMode()) {
            reAttachListViewAdapter(mListView);
        }
        mActivityRule.getScenario().onActivity(unused -> {
            mSelectedItem = (CharSequence) mListView.getSelectedItem();
            mListView.performItemClick(null, 0, 0);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        assertEquals(items[0], mListView.getItemAtPosition(0));
        verify(mOnClickListener, times(1)).onClick(mDialog, 0);
        verifyNoMoreInteractions(mOnClickListener);
    }

    @Test
    public void testSetOnItemSelectedListener() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setOnItemSelectedListener(mOnItemSelectedListener);
            mBuilder.setItems(R.array.difficultyLevel, mOnClickListener);
            mDialog = mBuilder.show();
            mListView = mDialog.getListView();
            mListView.pointToPosition(0, 0);
        });
        mInstrumentation.waitForIdleSync();
        verify(mOnItemSelectedListener, times(1)).onItemSelected(eq(mListView), any(View.class),
                eq(0), any(Long.class));
        verifyNoMoreInteractions(mOnItemSelectedListener);
    }

    @Test
    public void testSetView() {
        final View view = new View(mDialogActivity);
        view.setId(100);
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setView(view);
            mDialog = mBuilder.show();
            mView = mDialog.getWindow().findViewById(100);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(view, mView);
    }

    @Test
    public void testSetViewFromInflater() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setView(LayoutInflater.from(mBuilder.getContext()).inflate(
                    R.layout.alert_dialog_text_entry_2, null, false));
            mDialog = mBuilder.show();
            mView = mDialog.getWindow().findViewById(R.id.username_form);
        });
        mInstrumentation.waitForIdleSync();
        assertNotNull(mView);
        assertNotNull(mView.findViewById(R.id.username_view));
        assertNotNull(mView.findViewById(R.id.username_edit));
    }

    @Test
    public void testSetViewById() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setView(R.layout.alert_dialog_text_entry_2);
            mDialog = mBuilder.show();
            mView = mDialog.getWindow().findViewById(R.id.username_form);
        });
        mInstrumentation.waitForIdleSync();
        assertNotNull(mView);
        assertNotNull(mView.findViewById(R.id.username_view));
        assertNotNull(mView.findViewById(R.id.username_edit));
    }

    @Test
    public void testSetCustomTitle() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setCustomTitle(LayoutInflater.from(mBuilder.getContext()).inflate(
                    R.layout.alertdialog_custom_title, null, false));
            mDialog = mBuilder.show();
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetInverseBackgroundForced() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mBuilder.setInverseBackgroundForced(true);
            mDialog = mBuilder.create();
            mDialog.show();
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testCreate() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mDialog = mBuilder.create();
            mDialog.show();
        });
        mInstrumentation.waitForIdleSync();
        assertNotNull(mDialog);
        assertTrue(mDialog.isShowing());
    }

    @Test
    public void testShow() {
        mActivityRule.getScenario().onActivity(activity -> {
            mBuilder = new AlertDialog.Builder(activity);
            mDialog = mBuilder.show();
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mDialog.isShowing());
    }

    private void sendKeySync(int keyCode) {
        final long downTime = SystemClock.uptimeMillis();
        final KeyEvent downEvent =
                new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0);
        mInstrumentation.getUiAutomation().injectInputEvent(downEvent, true /*sync*/);

        final KeyEvent upEvent =
                new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0);
        mInstrumentation.getUiAutomation().injectInputEvent(upEvent, true /*sync*/);
    }

    private static class AdapterTest implements android.widget.ListAdapter {
        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean isEnabled(int position) {
            return false;
        }

        public int getCount() {
            return 0;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return 0;
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public android.view.View getView(int position,
                android.view.View convertView,
                android.view.ViewGroup parent) {
            return null;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public boolean hasStableIds() {
            return false;
        }

        public boolean isEmpty() {
            return true;
        }

        public void registerDataSetObserver(
                android.database.DataSetObserver observer) {
        }

        public void unregisterDataSetObserver(
                android.database.DataSetObserver observer) {
        }
    }
}
