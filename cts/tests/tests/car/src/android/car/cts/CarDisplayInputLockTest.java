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

package android.car.cts;

import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.settings.CarSettings;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.ContentResolver;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CarDisplayInputLockTest extends AbstractCarTestCase {
    private static final String TAG = CarDisplayInputLockTest.class.getSimpleName();
    private static final long INPUT_LOCK_UPDATE_WAIT_TIME_MS = 10_000L;
    private static final long ACTIVITY_WAIT_TIME_OUT_MS = 10_000L;
    private static final String EMPTY_SETTING_VALUE = "";

    private Instrumentation mInstrumentation;
    private ContentResolver mContentResolver;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private String mInitialSettingValue;
    private TestActivity mActivity;

    @Before
    public void setUp() throws Exception {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        assumeTrue("This test is enabled only in multi-user/multi-display devices",
                userManager.isVisibleBackgroundUsersSupported());

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContentResolver = mContext.getContentResolver();
        mCarOccupantZoneManager =
                (CarOccupantZoneManager) getCar().getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE);
        mInitialSettingValue = getDisplayInputLockSetting(mContentResolver);
        unlockInputForAllDisplays();
    }

    @After
    public void tearDown() {
        if (mContentResolver != null) {
            writeDisplayInputLockSetting(mContentResolver, mInitialSettingValue);
        }
    }

    @CddTest(requirements = {"TODO(b/262236403)"})
    @Test
    public void testDisplayInputLockForEachPassengerDisplay() throws Exception {
        Display[] displays = getPassengerMainDisplays();
        for (int i = 0; i < displays.length; i++) {
            Display display = displays[i];
            int displayId = display.getDisplayId();
            String displayUniqueId = display.getUniqueId();

            launchActivity(displayId);
            doTestDisplayInputLock(displayId, /* touchReceived= */ true);

            lockInputForDisplays(displayUniqueId);
            assertDisplayInputSinkCreated(displayId);
            doTestDisplayInputLock(displayId, /* touchReceived= */ false);

            unlockInputForAllDisplays();
            assertAllDisplayInputSinksRemoved();
            doTestDisplayInputLock(displayId, /* touchReceived= */ true);
        }
    }

    @CddTest(requirements = {"TODO(b/262236403)"})
    @Test
    public void testDisplayInputLockForTwoPassengerDisplaysAtOnce() throws Exception {
        Display[] displays = getPassengerMainDisplays();
        assumeTrue("Two passenger displays doesn't exist.", displays.length >= 2);

        // Pick the first two passenger displays.
        Display first = displays[0];
        Display second = displays[1];
        int firstDisplayId = first.getDisplayId();
        int secondDisplayId = second.getDisplayId();

        launchActivity(firstDisplayId);
        doTestDisplayInputLock(firstDisplayId, /* touchReceived= */ true);
        launchActivity(secondDisplayId);
        doTestDisplayInputLock(secondDisplayId, /* touchReceived= */ true);

        // Lock both displays at once.
        lockInputForDisplays(first.getUniqueId() + "," + second.getUniqueId());
        assertDisplayInputSinkCreated(firstDisplayId);
        assertDisplayInputSinkCreated(secondDisplayId);
        launchActivity(firstDisplayId);
        doTestDisplayInputLock(firstDisplayId, /* touchReceived= */ false);
        launchActivity(secondDisplayId);
        doTestDisplayInputLock(secondDisplayId, /* touchReceived= */ false);

        // Unlock both displays at once.
        unlockInputForAllDisplays();
        assertAllDisplayInputSinksRemoved();
        launchActivity(firstDisplayId);
        doTestDisplayInputLock(firstDisplayId, /* touchReceived= */ true);
        launchActivity(secondDisplayId);
        doTestDisplayInputLock(secondDisplayId, /* touchReceived= */ true);
    }

    @CddTest(requirements = {"TODO(b/262236403)"})
    @Test
    @EnsureHasPermission(Car.ACCESS_PRIVATE_DISPLAY_ID)
    public void testPassengerDisplayInputLockDoesNotAffectDriverDisplay() throws Exception {
        int driverDisplayId = mCarOccupantZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        Display driverDisplay = dm.getDisplay(driverDisplayId);
        assertThat(driverDisplay).isNotNull();
        Display[] passengerDisplays = getPassengerMainDisplays();
        assumeTrue("At least one passenger display should exist.", passengerDisplays.length >= 1);

        // Pick the first passenger display.
        Display passengerDisplay = passengerDisplays[0];
        int passengerDisplayId = passengerDisplay.getDisplayId();

        // Lock passengerDisplay, check driverDisplay.
        launchActivity(driverDisplayId);
        doTestDisplayInputLock(driverDisplayId, /* touchReceived= */ true);
        lockInputForDisplays(passengerDisplay.getUniqueId());
        assertDisplayInputSinkCreated(passengerDisplayId);
        doTestDisplayInputLock(driverDisplayId, /* touchReceived= */ true);

        launchActivity(passengerDisplayId);
        doTestDisplayInputLock(passengerDisplayId, /* touchReceived= */ false);
        unlockInputForAllDisplays();
        assertAllDisplayInputSinksRemoved();
    }

    private void doTestDisplayInputLock(int displayId, boolean touchReceived) {
        tapOnDisplay(displayId);
        PollingCheck.waitFor(() -> mActivity.mIsTouchesReceived == touchReceived);
        mActivity.resetTouchesReceived();

        mouseClickOnDisplay(displayId);
        PollingCheck.waitFor(() -> mActivity.mIsTouchesReceived == touchReceived);
        mActivity.resetTouchesReceived();
    }

    private void assertDisplayInputSinkCreated(int displayId) throws Exception {
        PollingCheck.waitFor(INPUT_LOCK_UPDATE_WAIT_TIME_MS, () -> {
            String cmdOut = runShellCommand("dumpsys input");
            return cmdOut.contains("DisplayInputSink-" + displayId);
        });
    }

    private void assertAllDisplayInputSinksRemoved() throws Exception {
        PollingCheck.waitFor(INPUT_LOCK_UPDATE_WAIT_TIME_MS, () -> {
            String cmdOut = runShellCommand("dumpsys input");
            return !cmdOut.contains("DisplayInputSink");
        });
    }

    @Nullable
    private String getDisplayInputLockSetting(@NonNull ContentResolver resolver) {
        return Settings.Global.getString(resolver,
                CarSettings.Global.DISPLAY_INPUT_LOCK);
    }

    private void lockInputForDisplays(String displayUniqueIds) throws Exception {
        writeDisplayInputLockSetting(mContentResolver, displayUniqueIds);
    }

    private void unlockInputForAllDisplays() throws Exception {
        writeDisplayInputLockSetting(mContentResolver, EMPTY_SETTING_VALUE);
    }

    private void writeDisplayInputLockSetting(@NonNull ContentResolver resolver,
            @NonNull String value) {
        Settings.Global.putString(resolver, CarSettings.Global.DISPLAY_INPUT_LOCK, value);
    }

    private void launchActivity(int displayId) {
        mActivity = null;
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        // Uses ShellPermisson to launch an Activitiy on the different displays.
        runWithShellPermissionIdentity(()-> {
            ActivityScenario<TestActivity> scenario = ActivityScenario
                    .launch(TestActivity.class, createLaunchActivityOptionsBundle(displayId));
            scenario.onActivity(activity -> {
                mActivity = activity;
                activityReferenceObtained.open();
            });
        });
        activityReferenceObtained.block(ACTIVITY_WAIT_TIME_OUT_MS);
        assertWithMessage("Failed to acquire activity reference.").that(mActivity).isNotNull();
    }

    private Display[] getPassengerMainDisplays() {
        List<CarOccupantZoneManager.OccupantZoneInfo> zonelist =
                mCarOccupantZoneManager.getAllOccupantZones();
        ArrayList<Display> displayList = new ArrayList<>();
        for (CarOccupantZoneManager.OccupantZoneInfo zone : zonelist) {
            if (zone.occupantType == OCCUPANT_TYPE_DRIVER) {
                continue;
            }
            Display display = mCarOccupantZoneManager.getDisplayForOccupant(zone,
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
            if (display != null) {
                displayList.add(display);
            }
        }
        return displayList.toArray(new Display[0]);
    }

    private void tapOnDisplay(int displayId) {
        injectMotionEvent(obtainMotionEvent(InputDevice.SOURCE_TOUCHSCREEN,
                mActivity.mView, MotionEvent.ACTION_DOWN, displayId));
        injectMotionEvent(obtainMotionEvent(InputDevice.SOURCE_TOUCHSCREEN,
                mActivity.mView, MotionEvent.ACTION_UP, displayId));
    }

    private void mouseClickOnDisplay(int displayId) {
        injectMotionEvent(obtainMouseEvent(
                mActivity.mView, MotionEvent.ACTION_DOWN, displayId));
        injectMotionEvent(obtainMouseEvent(
                mActivity.mView, MotionEvent.ACTION_BUTTON_PRESS, displayId));
        injectMotionEvent(obtainMouseEvent(
                mActivity.mView, MotionEvent.ACTION_BUTTON_RELEASE, displayId));
        injectMotionEvent(obtainMouseEvent(
                mActivity.mView, MotionEvent.ACTION_UP, displayId));
    }

    private void injectMotionEvent(MotionEvent event) {
        mInstrumentation.getUiAutomation().injectInputEvent(event,
                /* sync= */ true, /* waitAnimations= */ true);
    }

    private static MotionEvent obtainMouseEvent(View target, int action, int displayId) {
        return obtainMotionEvent(InputDevice.SOURCE_MOUSE, target, action, displayId);
    }

    private static MotionEvent obtainMotionEvent(int source, View target, int action,
            int displayId) {
        long eventTime = SystemClock.uptimeMillis();
        int[] xy = new int[2];
        target.getLocationOnScreen(xy);
        MotionEvent event = MotionEvent.obtain(eventTime, eventTime, action,
                xy[0] + target.getWidth() / 2, xy[1] + target.getHeight() / 2,
                /* metaState= */ 0);
        event.setSource(source);
        event.setDisplayId(displayId);
        return event;
    }

    private static Bundle createLaunchActivityOptionsBundle(int displayId) {
        return ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle();
    }

    public static class TestActivity extends Activity {
        public boolean mIsTouchesReceived;
        public TextView mView;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mView = new TextView(this);
            mView.setText("Display Input Lock");
            mView.setBackgroundColor(Color.GREEN);
            mView.setOnClickListener(this::onClick);

            setContentView(mView);
        }

        public void resetTouchesReceived() {
            mIsTouchesReceived = false;
        }

        private void onClick(View view) {
            mIsTouchesReceived = true;
        }
    }
}
