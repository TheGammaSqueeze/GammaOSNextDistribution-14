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

package android.server.wm.backnavigation;

import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
import static android.window.BackNavigationInfo.TYPE_CALLBACK;
import static android.window.BackNavigationInfo.TYPE_CROSS_ACTIVITY;
import static android.window.BackNavigationInfo.TYPE_CROSS_TASK;
import static android.window.BackNavigationInfo.TYPE_DIALOG_CLOSE;
import static android.window.BackNavigationInfo.TYPE_RETURN_TO_HOME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.ComponentNameUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for back navigation mode
 *
 *  <p>Build/Install/Run:
 *      atest CtsWindowManagerDeviceBackNavigation:BackGestureInvokedTest
 */
@Presubmit
@android.server.wm.annotation.Group1
public class BackGestureInvokedTest extends ActivityManagerTestBase {
    private TestActivitySession<BackInvokedActivity> mActivitySession;
    private BackInvokedActivity mActivity;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        enableAndAssumeGestureNavigationMode();

        mActivitySession = createManagedTestActivitySession();
        mActivitySession.launchTestActivityOnDisplaySync(
                BackInvokedActivity.class, DEFAULT_DISPLAY);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        mActivity = mActivitySession.getActivity();
        mWmState.waitForActivityState(mActivity.getComponentName(), STATE_RESUMED);
        mWmState.assertVisibility(mActivity.getComponentName(), true);
    }

    @Test
    public void popupWindowDismissedOnBackGesture() {
        mActivitySession.runOnMainSyncAndWait(mActivity::addPopupWindow);
        final int taskId = mWmState.getTaskByActivity(mActivity.getComponentName()).getTaskId();
        assertTrue("Popup window must show", mWmState.waitFor(state ->
                mWmState.getMatchingWindows(ws -> ws.getType() == TYPE_APPLICATION_PANEL
                                && ws.getStackId() == taskId && ws.isSurfaceShown())
                        .findAny().isPresent(), "popup window show"));
        triggerBackEventByGesture(DEFAULT_DISPLAY);

        assertTrue("Popup window must be removed", mWmState.waitFor(state ->
                        mWmState.getMatchingWindows(ws -> ws.getType() == TYPE_APPLICATION_PANEL
                                && ws.getStackId() == taskId).findAny().isEmpty(),
                "popup window removed"));

        // activity remain focused
        mWmState.assertFocusedActivity("Top activity must be focused",
                mActivity.getComponentName());
        final String windowName = ComponentNameUtils.getWindowName(mActivity.getComponentName());
        mWmState.assertFocusedWindow(
                "Top activity window must be focused window.", windowName);
    }

    @Test
    public void testBackToHome() {
        triggerBackEventByGesture(DEFAULT_DISPLAY);
        mWmState.waitAndAssertActivityRemoved(mActivity.getComponentName());

        assertEquals(TYPE_RETURN_TO_HOME, mWmState.getBackNavigationState().getLastBackType());
    }

    @Test
    public void testBackToTask() {
        final ComponentName
                componentName = new ComponentName(mContext, NewTaskActivity.class);
        launchActivityInNewTask(componentName);
        mWmState.waitForActivityState(componentName, STATE_RESUMED);
        mWmState.assertVisibility(componentName, true);
        mWmState.waitForFocusedActivity("Wait for launched activity to be in front.",
                componentName);
        triggerBackEventByGesture(DEFAULT_DISPLAY);
        mWmState.waitForActivityState(mActivity.getComponentName(), STATE_RESUMED);

        assertEquals(TYPE_CROSS_TASK, mWmState.getBackNavigationState().getLastBackType());
    }

    @Test
    public void testBackToActivity() {
        final ComponentName componentName = new ComponentName(mContext, SecondActivity.class);
        launchActivity(componentName);
        mWmState.waitForActivityState(componentName, STATE_RESUMED);
        mWmState.assertVisibility(componentName, true);
        mWmState.waitForFocusedActivity("Wait for launched activity to be in front.",
                componentName);
        triggerBackEventByGesture(DEFAULT_DISPLAY);
        mWmState.waitForActivityState(mActivity.getComponentName(), STATE_RESUMED);

        assertEquals(TYPE_CROSS_ACTIVITY, mWmState.getBackNavigationState().getLastBackType());
    }

    @Test
    public void testDialogDismissed() {
        mInstrumentation.runOnMainSync(() -> {
            new AlertDialog.Builder(mActivity)
                    .setTitle("Dialog")
                    .show();
        });
        mInstrumentation.waitForIdleSync();

        triggerBackEventByGesture(DEFAULT_DISPLAY);

        // activity remain focused
        mWmState.waitForFocusedActivity("top activity to be focused",
                mActivity.getComponentName());
        mWmState.assertFocusedActivity("Top activity must be focused",
                mActivity.getComponentName());

        assertEquals(TYPE_DIALOG_CLOSE, mWmState.getBackNavigationState().getLastBackType());
    }

    @Test
    public void testImeDismissed() {
        ComponentName componentName = new ComponentName(mContext, ImeTestActivity.class);
        launchActivity(componentName);
        mWmState.waitForActivityState(componentName, STATE_RESUMED);
        mWmState.assertVisibility(componentName, true);
        mWmState.waitAndAssertImeWindowShownOnDisplay(DEFAULT_DISPLAY);

        triggerBackEventByGesture(DEFAULT_DISPLAY);
        mWmState.waitAndAssertImeWindowHiddenOnDisplay(DEFAULT_DISPLAY);

        assertEquals(TYPE_CALLBACK, mWmState.getBackNavigationState().getLastBackType());
    }

    public static class BackInvokedActivity extends Activity {

        private FrameLayout mContentView;

        private FrameLayout getContentView() {
            return mContentView;
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContentView = new FrameLayout(this);
            setContentView(mContentView);
        }

        public void addPopupWindow() {
            FrameLayout contentView = new FrameLayout(this);
            contentView.setBackgroundColor(Color.RED);
            PopupWindow popup = new PopupWindow(contentView, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            // Ensure the window can get the focus by marking the views as focusable
            popup.setFocusable(true);
            contentView.setFocusable(true);
            popup.showAtLocation(getContentView(), Gravity.FILL, 0, 0);
        }
    }

    public static class NewTaskActivity extends Activity {
        private FrameLayout mContentView;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContentView = new FrameLayout(this);
            mContentView.setBackgroundColor(Color.GREEN);
            setContentView(mContentView);
        }
    }

    public static class SecondActivity extends Activity {
        private FrameLayout mContentView;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContentView = new FrameLayout(this);
            mContentView.setBackgroundColor(Color.BLUE);
            setContentView(mContentView);
        }
    }

    public static class ImeTestActivity extends Activity {
        EditText mEditText;

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new EditText(this);
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            if (mEditText.requestFocus()) {
                InputMethodManager imm = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
            }
            setContentView(layout);
        }
    }
}
