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

package com.android.systemui.car.input;

import static android.view.DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.hardware.power.CarPowerManager;
import android.car.settings.CarSettings;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarServiceProvider.CarServiceOnConnectedListener;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DisplayInputSinkControllerTest extends SysuiTestCase {
    private static final String TAG = DisplayInputSinkControllerTest.class.getSimpleName();

    private static final String EMPTY_SETTING_VALUE = "";

    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private DisplayInputSink.OnInputEventListener mCallback;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private Car mCar;
    @Mock
    private CarPowerManager mCarPowerManager;
    @Mock
    private CarOccupantZoneManager mCarOccupantZoneManager;

    private MockitoSession mMockingSession;
    private DisplayInputSinkController mDisplayInputSinkController;
    private Handler mHandler;
    private ContentResolver mContentResolver;
    private final SparseArray<DisplayInputSink> mDisplayInputSinks = new SparseArray<>();
    private final ArraySet<Integer> mDisplayInputLockWindows = new ArraySet<>();
    private final ArraySet<String> mDisplayInputLockSetting = new ArraySet<>();
    private final SparseArray<Display> mPassengerDisplays = new SparseArray();
    @Mock
    private Display mPassengerDisplay1;
    private final int mPassengerDisplayId1 = 1001;
    private final String mPassengerDisplayUniqueId1 = "testUniqueId1001";
    @Mock
    private Display mPassengerDisplay2;
    private final int mPassengerDisplayId2 = 1002;
    private final String mPassengerDisplayUniqueId2 = "testUniqueId1002";

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .spyStatic(UserHandle.class)
                .spyStatic(UserManager.class)
                .strictness(Strictness.WARN)
                .startMocking();
        spyOn(mContext);
        mContentResolver = mContext.getContentResolver();
        spyOn(mContentResolver);
        mHandler = new Handler(Looper.getMainLooper());
        doReturn(mDisplayManager).when(mContext).getSystemService(DisplayManager.class);
        mDisplayInputSinkController =
                new DisplayInputSinkController(mContext, mHandler, mCarServiceProvider,
                        mDisplayInputSinks, mDisplayInputLockWindows, mDisplayInputLockSetting,
                        mPassengerDisplays);
        spyOn(mDisplayInputSinkController);
        writeDisplayInputLockSetting(mContentResolver, EMPTY_SETTING_VALUE);
        doAnswer(invocation -> {
            CarServiceOnConnectedListener listener = invocation.getArgument(0);
            listener.onConnected(mCar);
            return null;
        }).when(mCarServiceProvider).addListener(any(CarServiceOnConnectedListener.class));
        doReturn(mCarPowerManager).when(mCar).getCarManager(CarPowerManager.class);
        doReturn(mCarOccupantZoneManager).when(mCar).getCarManager(CarOccupantZoneManager.class);
        // Initialize two displays as passenger displays.
        setUpDisplay(mPassengerDisplay1, mPassengerDisplayId1, mPassengerDisplayUniqueId1);
        setUpDisplay(mPassengerDisplay2, mPassengerDisplayId2, mPassengerDisplayUniqueId2);
    }

    @After
    public void tearDown() {
        mMockingSession.finishMocking();
    }

    @Test
    public void start_nonSystemUser_controllerNotStarted() {
        doReturn(UserHandle.USER_NULL).when(() -> UserHandle.myUserId());
        doReturn(true).when(() -> UserManager.isHeadlessSystemUserMode());

        mDisplayInputSinkController.start();

        verify(mContentResolver, never())
                .registerContentObserver(any(Uri.class), anyBoolean(), any(ContentObserver.class));
        verify(mDisplayManager, never()).registerDisplayListener(
                any(DisplayManager.DisplayListener.class),
                any());
    }

    @Test
    public void start_systemUser_controllerStarted() {
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        doReturn(true).when(() -> UserManager.isHeadlessSystemUserMode());

        mDisplayInputSinkController.start();

        verify(mContentResolver, times(1))
                .registerContentObserver(any(Uri.class), anyBoolean(), any(ContentObserver.class));
        verify(mDisplayManager, times(1)).registerDisplayListener(
                any(DisplayManager.DisplayListener.class),
                any());
    }

    @Test
    public void mayStartDisplayInputLock_withValidDisplay_createsDisplayInputSink() {
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();

        mDisplayInputSinkController.mayStartDisplayInputLock(mPassengerDisplay1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
    }

    @Test
    public void mayStartDisplayInputLock_alreadyStarted_displayInputSinkRemainsSame() {
        addDisplayInputLock(mPassengerDisplay1);
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();

        mDisplayInputSinkController.mayStartDisplayInputLock(mPassengerDisplay1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
    }

    @Test
    public void mayStopDisplayInputLockLocked_inputLockStarted_removesDisplayInputSink() {
        addDisplayInputLock(mPassengerDisplay1);
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();

        mDisplayInputSinkController.mayStopDisplayInputLock(mPassengerDisplay1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
    }

    @Test
    public void mayStopDisplayInputLockLocked_inputLockNotStarted_doesNotStopDisplayInputLock() {
        addDisplayInputLock(mPassengerDisplay2);
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputLockStarted(mPassengerDisplayId2)).isTrue();

        mDisplayInputSinkController.mayStopDisplayInputLock(mPassengerDisplay1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputLockStarted(mPassengerDisplayId2)).isTrue();
    }

    @Test
    public void onDisplayAdded_withValidDisplay_callsStartDisplayInputLock() {
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        mDisplayInputSinkController.start();
        mDisplayInputLockSetting.add(mPassengerDisplayUniqueId2);

        mDisplayInputSinkController.mDisplayListener.onDisplayAdded(mPassengerDisplayId2);

        assertThat(isInputLockStarted(mPassengerDisplayId2)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId2)).isFalse();
    }

    @Test
    public void onDisplayAdded_withNonPassengerDisplay_doesNotCallStartDisplayInputLock() {
        int nonPassengerDisplayId = 999;
        String nonPassengerUniqueId = "testUniqueId999";
        mDisplayInputLockSetting.add(nonPassengerUniqueId);

        mDisplayInputSinkController.mDisplayListener.onDisplayAdded(nonPassengerDisplayId);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputLockStarted(mPassengerDisplayId2)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId2)).isFalse();
    }

    @Test
    public void onDisplayRemoved_inputLockStarted_callsStopDisplayInputLock() {
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        mDisplayInputSinkController.start();
        addDisplayInputLock(mPassengerDisplay1);
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();

        mDisplayInputSinkController.mDisplayListener.onDisplayRemoved(mPassengerDisplayId1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
    }

    @Test
    public void onDisplayChangedToTurnOff_onUnlockedDisplay_startsDisplayInputMonitor() {
        doReturn(Display.STATE_OFF).when(mPassengerDisplay1).getState();

        mDisplayInputSinkController.mDisplayListener.onDisplayChanged(mPassengerDisplayId1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isTrue();
    }

    @Test
    public void onDisplayChangedToTurnOff_onLockedDisplay_doesNotStartsDisplayInputLockOrMonitor() {
        addDisplayInputLock(mPassengerDisplay1);
        doReturn(Display.STATE_OFF).when(mPassengerDisplay1).getState();
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();

        mDisplayInputSinkController.mDisplayListener.onDisplayChanged(mPassengerDisplayId1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
        assertThat(mDisplayInputLockWindows.contains(mPassengerDisplayId1)).isTrue();
    }

    @Test
    public void onDisplayChangedToTurnOn_onUnlockedDisplay_stopsDisplayInputMonitor() {
        addDisplayInputMonitor(mPassengerDisplay1);
        doReturn(Display.STATE_ON).when(mPassengerDisplay1).getState();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isTrue();

        mDisplayInputSinkController.mDisplayListener.onDisplayChanged(mPassengerDisplayId1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
    }

    @Test
    public void onDisplayChangedToTurnOn_onLockedDisplay_doesNotStopDisplayInputLockOrMonitor() {
        addDisplayInputLock(mPassengerDisplay1);
        doReturn(Display.STATE_ON).when(mPassengerDisplay1).getState();
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();

        mDisplayInputSinkController.mDisplayListener.onDisplayChanged(mPassengerDisplayId1);

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
        assertThat(mDisplayInputLockWindows.contains(mPassengerDisplayId1)).isTrue();
    }

    @Test
    public void refreshDisplayInputLock_withValidSettingValue_callsStartDisplayInputLock() {
        writeDisplayInputLockSetting(mContentResolver, mPassengerDisplayUniqueId1);

        mDisplayInputSinkController.refreshDisplayInputLockSetting();

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
    }

    @Test
    public void refreshDisplayInputLock_withInvalidSettingValue_doesNotCallStartDisplayInputLock() {
        String settingUniqueId = "invalidUniqueId";
        writeDisplayInputLockSetting(mContentResolver, settingUniqueId);

        mDisplayInputSinkController.refreshDisplayInputLockSetting();

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputLockStarted(mPassengerDisplayId2)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId2)).isFalse();
    }

    @Test
    public void refreshDisplayInputLock_duplicateEntriesInSettingValue_onlyOneEntryIsValid() {
        writeDisplayInputLockSetting(mContentResolver,
                mPassengerDisplayUniqueId1 + "," + mPassengerDisplayUniqueId1);

        mDisplayInputSinkController.refreshDisplayInputLockSetting();

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
    }

    @Test
    public void refreshDisplayInputLock_inputLockAlreadyStarted_doesNotCallStartDisplayInputLock() {
        addDisplayInputLock(mPassengerDisplay1);
        writeDisplayInputLockSetting(mContentResolver, mPassengerDisplayUniqueId1);
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();

        mDisplayInputSinkController.refreshDisplayInputLockSetting();

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
    }

    @Test
    public void refreshDisplayInputLock_settingValueReplaced_stopsExistingLockAndStartsNewLock() {
        addDisplayInputLock(mPassengerDisplay1);
        writeDisplayInputLockSetting(mContentResolver, mPassengerDisplayUniqueId2);
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();

        mDisplayInputSinkController.refreshDisplayInputLockSetting();

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputLockStarted(mPassengerDisplayId2)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId2)).isFalse();
    }

    @Test
    public void refreshDisplayInputLock_multiEntriesInSettingValue_startsDisplayInputLockForEach() {
        writeDisplayInputLockSetting(mContentResolver,
                mPassengerDisplayUniqueId1 + "," + mPassengerDisplayUniqueId2);

        mDisplayInputSinkController.refreshDisplayInputLockSetting();

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputLockStarted(mPassengerDisplayId2)).isTrue();
        assertThat(isInputMonitorStarted(mPassengerDisplayId2)).isFalse();
    }

    @Test
    public void refreshDisplayInputLock_settingValueRemoved_stopsDisplayInputLockForEach() {
        addDisplayInputLock(mPassengerDisplay1);
        addDisplayInputLock(mPassengerDisplay2);
        writeDisplayInputLockSetting(mContentResolver, EMPTY_SETTING_VALUE);
        assertThat(isInputLockStarted(mPassengerDisplayId1)).isTrue();
        assertThat(isInputLockStarted(mPassengerDisplayId2)).isTrue();

        mDisplayInputSinkController.refreshDisplayInputLockSetting();

        assertThat(isInputLockStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId1)).isFalse();
        assertThat(isInputLockStarted(mPassengerDisplayId2)).isFalse();
        assertThat(isInputMonitorStarted(mPassengerDisplayId2)).isFalse();
    }

    @Test
    public void onInputEvent_inputEventReceived_callbackOnInputEvent() {
        DisplayInputSink displayInputSinks =
                new DisplayInputSink(mPassengerDisplay1, mCallback);

        displayInputSinks.mInputEventReceiver.onInputEvent(mock(InputEvent.class));

        verify(mCallback).onInputEvent(any(InputEvent.class));
    }

    @Test
    public void displayInputMonitorCallback_triggers_notifyUserActivity() {
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        mDisplayInputSinkController.start();  // To setup CarPowerManager

        doReturn(Display.STATE_OFF).when(mPassengerDisplay1).getState();
        mDisplayInputSinkController.mDisplayListener.onDisplayChanged(mPassengerDisplayId1);

        DisplayInputSink inputSink = mDisplayInputSinks.get(mPassengerDisplayId1);
        assertThat(inputSink).isNotNull();

        MotionEvent event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 0, 0, mPassengerDisplayId1);
        inputSink.mInputEventReceiver.onInputEvent(event);

        verify(mCarPowerManager, times(1)).notifyUserActivity(mPassengerDisplayId1);
    }

    @Test
    public void displayInputLockCallback_triggers_notifyUserActivity() {
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        writeDisplayInputLockSetting(mContentResolver, mPassengerDisplayUniqueId2);
        mDisplayInputSinkController.start();  // To setup CarPowerManager and start the input lock.

        assertThat(isInputLockStarted(mPassengerDisplayId2)).isTrue();
        DisplayInputSink inputSink = mDisplayInputSinks.get(mPassengerDisplayId2);
        assertThat(inputSink).isNotNull();

        MotionEvent event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 0, 0, mPassengerDisplayId2);
        inputSink.mInputEventReceiver.onInputEvent(event);

        verify(mCarPowerManager, times(1)).notifyUserActivity(mPassengerDisplayId2);
    }

    private MotionEvent obtainMotionEvent(int action, int x, int y, int displayId) {
        long eventTime = SystemClock.uptimeMillis();
        return MotionEvent.obtain(eventTime, eventTime, action, x, y,
                /* pressure= */ 1.0f, /* size= */ 1.0f, /* metaState= */ 0,
                /* xPrecision= */ 1.0f, /* yPrecision= */ 1.0f,
                /* deviceId= */ 0, /* edgeFlags= */ 0, InputDevice.SOURCE_CLASS_POINTER, displayId);
    }

    private void writeDisplayInputLockSetting(@NonNull ContentResolver resolver,
            @NonNull String value) {
        Settings.Global.putString(resolver, CarSettings.Global.DISPLAY_INPUT_LOCK, value);
    }


    private void setUpDisplay(Display display, int displayId, String uniqueId) {
        DisplayAdjustments daj = DEFAULT_DISPLAY_ADJUSTMENTS;
        doReturn(display).when(mDisplayManager).getDisplay(displayId);
        doReturn(uniqueId).when(display).getUniqueId();
        doReturn(displayId).when(display).getDisplayId();
        doReturn(daj).when(display).getDisplayAdjustments();
        mPassengerDisplays.put(displayId, display);
    }

    private boolean isInputLockStarted(int displayId) {
        if (!mDisplayInputLockWindows.contains(displayId)) {
            Log.d(TAG, "isInputLockStarted: No DisplayInputLockWindow for display#" + displayId);
            return false;
        }
        if (!mDisplayInputSinks.contains(displayId)) {
            Log.d(TAG, "isInputLockStarted: No DisplayInputSink for display#" + displayId);
            return false;
        }
        return true;
    }

    private boolean isInputMonitorStarted(int displayId) {
        if (mDisplayInputLockWindows.contains(displayId)) {
            Log.d(TAG, "isInputMonitorStarted: InputLock started for display#" + displayId);
            return false;
        }
        if (!mDisplayInputSinks.contains(displayId)) {
            Log.d(TAG, "isInputMonitorStarted: No DisplayInputSink for display#" + displayId);
            return false;
        }
        return true;
    }

    private void addDisplayInputLock(Display display) {
        mDisplayInputLockSetting.add(display.getUniqueId());
        int displayId = display.getDisplayId();
        mDisplayInputLockWindows.add(displayId);
        DisplayInputSink displayInputSinks = new DisplayInputSink(display, mCallback);
        mDisplayInputSinks.put(displayId, displayInputSinks);
    }

    private void addDisplayInputMonitor(Display display) {
        int displayId = display.getDisplayId();
        DisplayInputSink displayInputSinks = new DisplayInputSink(display, mCallback);
        mDisplayInputSinks.put(displayId, displayInputSinks);
    }
}
