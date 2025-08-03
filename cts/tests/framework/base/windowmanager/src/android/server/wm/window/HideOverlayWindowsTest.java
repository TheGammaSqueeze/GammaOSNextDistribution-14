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
 * limitations under the License
 */

package android.server.wm.window;

import static android.server.wm.app.Components.HIDE_OVERLAY_WINDOWS_ACTIVITY;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.ACTION;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.MOTION_EVENT_EXTRA;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.PONG;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.REPORT_TOUCH;
import static android.view.Gravity.LEFT;
import static android.view.Gravity.TOP;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CliIntentExtra;
import android.server.wm.app.Components;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceWindow:HideOverlayWindowsTest
 */
@Presubmit
public class HideOverlayWindowsTest extends ActivityManagerTestBase {

    private final static String WINDOW_NAME_EXTRA = "window_name";
    private final static String SYSTEM_APPLICATION_OVERLAY_EXTRA = "system_application_overlay";
    private PongReceiver mPongReceiver;
    private TouchReceiver mTouchReceiver;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPongReceiver = new PongReceiver();
        mContext.registerReceiver(mPongReceiver, new IntentFilter(PONG), Context.RECEIVER_EXPORTED);
        mTouchReceiver = new TouchReceiver();
        mContext.registerReceiver(mTouchReceiver, new IntentFilter(REPORT_TOUCH),
                Context.RECEIVER_EXPORTED);
    }

    @After
    public void tearDown() throws Exception {
        mContext.unregisterReceiver(mPongReceiver);
        mContext.unregisterReceiver(mTouchReceiver);
    }

    @Test
    public void testApplicationOverlayHiddenWhenRequested() {
        String windowName = "SYSTEM_ALERT_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, SystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, false);

        setHideOverlayWindowsAndWaitForPong(false);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testSystemApplicationOverlayFlagNoEffectWithoutPermission() {
        String windowName = "SYSTEM_ALERT_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, SystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName),
                    CliIntentExtra.extraBool(SYSTEM_APPLICATION_OVERLAY_EXTRA, true));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, false);

        setHideOverlayWindowsAndWaitForPong(false);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testInternalSystemApplicationOverlaysNotHidden() {
        String windowName = "INTERNAL_SYSTEM_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, InternalSystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.INTERNAL_SYSTEM_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testSystemApplicationOverlaysNotHidden() {
        String windowName = "SYSTEM_APPLICATION_OVERLAY";
        ComponentName componentName = new ComponentName(
                mContext, SystemApplicationOverlayActivity.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName),
                    CliIntentExtra.extraBool(SYSTEM_APPLICATION_OVERLAY_EXTRA, true));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_APPLICATION_OVERLAY);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testSystemApplicationOverlayHiddenWithoutFlag() {
        String windowName = "SYSTEM_APPLICATION_OVERLAY";
        ComponentName componentName = new ComponentName(
                mContext, SystemApplicationOverlayActivity.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_APPLICATION_OVERLAY);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, false);
    }

    @Test
    public void testSystemApplicationOverlayAllowsTouchWithoutObscured() {
        String windowName = "SYSTEM_APPLICATION_OVERLAY";
        ComponentName componentName = new ComponentName(
                mContext, SystemApplicationOverlayActivity.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName),
                    CliIntentExtra.extraBool(SYSTEM_APPLICATION_OVERLAY_EXTRA, true));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_APPLICATION_OVERLAY);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        MotionEvent motionEvent = touchCenterOfDisplayAndWaitForMotionEvent();

        assertThat(
                motionEvent.getFlags() & MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED).isEqualTo(
                0);
    }

    @Test
    public void testApplicationOverlay_touchIsObscuredWithoutCorrectPermission() {
        String windowName = "SYSTEM_APPLICATION_OVERLAY";
        ComponentName componentName = new ComponentName(
                mContext, SystemWindowActivity.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName),
                    CliIntentExtra.extraBool(SYSTEM_APPLICATION_OVERLAY_EXTRA, true));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        launchActivityInFullscreen(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(false);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        MotionEvent motionEvent = touchCenterOfDisplayAndWaitForMotionEvent();
        assertThat(
                motionEvent.getFlags() & MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED).isEqualTo(
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED);
    }

    private MotionEvent touchCenterOfDisplayAndWaitForMotionEvent() {
        mTouchHelper.tapOnDisplayCenter(Display.DEFAULT_DISPLAY);
        return mTouchReceiver.getMotionEvent();
    }

    void setHideOverlayWindowsAndWaitForPong(boolean hide) {
        Intent intent = new Intent(ACTION);
        intent.putExtra(Components.HideOverlayWindowsActivity.SHOULD_HIDE, hide);
        mContext.sendBroadcast(intent);
        mPongReceiver.waitForPong();
    }

    public static class BaseSystemWindowActivity extends Activity {

        TextView mTextView;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            String windowName = getIntent().getStringExtra(WINDOW_NAME_EXTRA);

            final Point size = new Point();
            getDisplay().getRealSize(size);

            WindowManager.LayoutParams params =
                    new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            params.width = size.x / 3;
            params.height = size.y / 3;
            params.gravity = TOP | LEFT;
            params.setTitle(windowName);
            params.setSystemApplicationOverlay(
                    getIntent().getBooleanExtra(SYSTEM_APPLICATION_OVERLAY_EXTRA, false));

            mTextView = new TextView(this);
            mTextView.setText(windowName + "   type=" + TYPE_APPLICATION_OVERLAY);
            mTextView.setBackgroundColor(Color.GREEN);

            getWindowManager().addView(mTextView, params);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            getWindowManager().removeView(mTextView);
        }
    }

    // These activities are running the same code, but in different processes to ensure that they
    // each create their own WindowSession, using the correct permissions. If they are run in the
    // same process WindowSession is cached and might end up not matching the permissions set up
    // with adoptShellPermissions
    public static class InternalSystemWindowActivity extends BaseSystemWindowActivity {}
    public static class SystemApplicationOverlayActivity extends BaseSystemWindowActivity {}
    public static class SystemWindowActivity extends BaseSystemWindowActivity {}

    private static class PongReceiver extends BroadcastReceiver {

        volatile ConditionVariable mConditionVariable = new ConditionVariable();

        @Override
        public void onReceive(Context context, Intent intent) {
            mConditionVariable.open();
        }

        public void waitForPong() {
            assertThat(mConditionVariable.block(10000L)).isTrue();
            mConditionVariable = new ConditionVariable();
        }
    }

    private static class TouchReceiver extends BroadcastReceiver {

        volatile ConditionVariable mConditionVariable = new ConditionVariable();
        MotionEvent mMotionEvent;

        @Override
        public void onReceive(Context context, Intent intent) {
            mMotionEvent = intent.getParcelableExtra(MOTION_EVENT_EXTRA, MotionEvent.class);
            mConditionVariable.open();
        }

        public MotionEvent getMotionEvent() {
            assertThat(mConditionVariable.block(10000L)).isTrue();
            mConditionVariable = new ConditionVariable();
            return mMotionEvent;
        }
    }

}
