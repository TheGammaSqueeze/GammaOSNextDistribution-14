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

package com.android.car.power;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.ICarOccupantZoneCallback;
import android.car.VehicleAreaSeat;
import android.car.settings.CarSettings;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.AbstractExtendedMockitoTestCase.CustomMockitoSessionBuilder;
import android.car.test.mocks.MockSettings;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.DisplayAddress;

import com.android.car.CarLocalServices;
import com.android.car.CarOccupantZoneService;
import com.android.car.OccupantZoneHelper;
import com.android.car.R;
import com.android.car.systeminterface.SystemInterface;
import com.android.server.testutils.OffsettableClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScreenOffHandlerUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = ScreenOffHandlerUnitTest.class.getSimpleName();
    private static final int SCREEN_OFF_TIMEOUT = 60000;

    @Mock
    private Context mContext;
    @Mock
    private CarOccupantZoneService mCarOccupantZoneService;
    @Mock
    private SystemInterface mSystemInterface;
    @Mock
    private ContentResolver mContentResolver;
    @Mock
    private Resources mResources;
    @Mock
    private DisplayManager mDisplayManager;

    private OffsettableClock mClock;
    private TestLooper mTestLooper;
    private OccupantZoneHelper mZoneHelper = new OccupantZoneHelper();
    private Runnable mRunnableAtBootComplete;

    private ScreenOffHandler mScreenOffHandler;

    // Not used directly, but sets proper mockStatic() expectations on Settings
    @SuppressWarnings("UnusedVariable")
    private MockSettings mMockSettings;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        mMockSettings = new MockSettings(session);
    }

    @Before
    public void setUp() throws Exception {
        mZoneHelper.setUpOccupantZones(mCarOccupantZoneService, /* hasDriver= */ true,
                /* hasFrontPassenger= */ false, /* numRearPassengers= */ 2);
        CarLocalServices.removeServiceForTest(CarOccupantZoneService.class);
        CarLocalServices.addService(CarOccupantZoneService.class, mCarOccupantZoneService);
        mClock = new OffsettableClock.Stopped();
        mTestLooper = new TestLooper(mClock::now);
        setService();
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarOccupantZoneService.class);
    }

    @Test
    public void testParseSetting_valid_returnNonNull() throws Exception {
        bootComplete();

        String expectedModeString = "10:0,11:0,21:1";
        DisplayPowerModeBuilder builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(/* displayId= */ 10, /* displayPort= */10),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 11, /* displayPort= */11),
                                /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 21, /* displayPort= */21),
                                /* mode= */ 1);

        String actualModeString = builder.build();
        assertThat(actualModeString).isEqualTo(expectedModeString);
        SparseIntArray result = mScreenOffHandler.parseModeAssignmentSettingValue(actualModeString);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);

        expectedModeString = "0:0,1:0,2:1,3:2,4:1,5:0";
        builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(/* displayId= */ 0, /* displayPort= */ 0),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 1, /* displayPort= */ 1),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 2, /* displayPort= */ 2),
                        /* mode= */ 1)
                .setDisplayMode(createMockDisplay(/* displayId= */ 3, /* displayPort= */ 3),
                        /* mode= */ 2)
                .setDisplayMode(createMockDisplay(/* displayId= */ 4, /* displayPort= */ 4),
                        /* mode= */ 1)
                .setDisplayMode(createMockDisplay(/* displayId= */ 5, /* displayPort= */ 5),
                        /* mode= */ 0);

        actualModeString = builder.build();
        assertThat(actualModeString).isEqualTo(expectedModeString);
        result = mScreenOffHandler.parseModeAssignmentSettingValue(actualModeString);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(6);

        expectedModeString = "0:0,1:0,2:1";
        builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(/* displayId= */ 99, /* displayPort= */ 0),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 2, /* displayPort= */ 1),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 5, /* displayPort= */ 2),
                        /* mode= */ 1);

        actualModeString = builder.build();
        assertThat(actualModeString).isEqualTo(expectedModeString);
        result = mScreenOffHandler.parseModeAssignmentSettingValue(actualModeString);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);

        expectedModeString = "11:0,52:2";
        builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(/* displayId= */ 22, /* displayPort= */ 11),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 420, /* displayPort= */ 52),
                        /* mode= */ 2);

        actualModeString = builder.build();
        assertThat(actualModeString).isEqualTo(expectedModeString);
        result = mScreenOffHandler.parseModeAssignmentSettingValue(actualModeString);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);

        expectedModeString = "99:0";
        builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(/* displayId= */ 99, /* displayPort= */ 99),
                /* mode= */ 0);

        actualModeString = builder.build();
        assertThat(actualModeString).isEqualTo(expectedModeString);
        result = mScreenOffHandler.parseModeAssignmentSettingValue(actualModeString);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
    }

    @Test
    public void testParseSetting_invalid_returnNull() throws Exception {
        bootComplete();

        // Duplicate displayId
        String expectedModeString = "0:0,0:1,2:1";
        DisplayPowerModeBuilder builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(/* displayId= */ 0, /* displayPort= */0),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 0, /* displayPort= */0),
                        /* mode= */ 1)
                .setDisplayMode(createMockDisplay(/* displayId= */ 2, /* displayPort= */2),
                        /* mode= */ 1);

        String actualModeString = builder.build();
        assertThat(actualModeString).isEqualTo(expectedModeString);
        SparseIntArray result = mScreenOffHandler.parseModeAssignmentSettingValue(actualModeString);
        assertThat(result).isNull();

        // Mode out of range
        expectedModeString = "0:0,1:0,2:1,3:2,4:1,5:11";
        builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(/* displayId= */ 0, /* displayPort= */0),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 1, /* displayPort= */1),
                        /* mode= */ 0)
                .setDisplayMode(createMockDisplay(/* displayId= */ 2, /* displayPort= */2),
                        /* mode= */ 1)
                .setDisplayMode(createMockDisplay(/* displayId= */ 3, /* displayPort= */3),
                        /* mode= */ 2)
                .setDisplayMode(createMockDisplay(/* displayId= */ 4, /* displayPort= */4),
                        /* mode= */ 1)
                .setDisplayMode(createMockDisplay(/* displayId= */ 5, /* displayPort= */5),
                        /* mode= */ 11);

        actualModeString = builder.build();
        assertThat(actualModeString).isEqualTo(expectedModeString);
        result = mScreenOffHandler.parseModeAssignmentSettingValue(actualModeString);
        assertThat(result).isNull();

        // "" - empty
        builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(/* displayId= */ 99, /* displayPort= */ 99),
                /* mode= */ 0)
                .build();

        result = mScreenOffHandler.parseModeAssignmentSettingValue("");
        assertThat(result).isNull();
    }

    @Test
    public void testUpdateUserActivity_noUserAssignedInModeOn_shouldTurnOffDisplay()
            throws Exception {
        bootComplete();
        OccupantZoneInfo zoneInfo = mCarOccupantZoneService.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
                VehicleAreaSeat.SEAT_ROW_2_LEFT);
        assertThat(zoneInfo).isNotNull();

        int displayId = mCarOccupantZoneService.getDisplayForOccupant(
                zoneInfo.zoneId, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        assertWithMessage("User for occupant zone(%s)", zoneInfo.zoneId)
                .that((mCarOccupantZoneService.getUserForOccupant(zoneInfo.zoneId)))
                .isEqualTo(CarOccupantZoneManager.INVALID_USER_ID);

        updateDisplayPowerModeSetting(displayId, ScreenOffHandler.DISPLAY_POWER_MODE_ON);
        mScreenOffHandler.updateUserActivity(displayId, mClock.now());

        advanceTime(SCREEN_OFF_TIMEOUT + 1);
        verify(mSystemInterface).setDisplayState(displayId, false);
    }

    @Test
    public void testUpdateUserActivity_userAssignedInModeOn_shouldKeepScreenOn() throws Exception {
        bootComplete();
        OccupantZoneInfo zoneInfo = mCarOccupantZoneService.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
                VehicleAreaSeat.SEAT_ROW_2_LEFT);
        assertThat(zoneInfo).isNotNull();

        int displayId = mCarOccupantZoneService.getDisplayForOccupant(
                zoneInfo.zoneId, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);

        int userId = 99;
        assertWithMessage("User assignment").that(
                mCarOccupantZoneService.assignVisibleUserToOccupantZone(
                zoneInfo.zoneId, UserHandle.of(userId))).isEqualTo(
                        CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK);

        ICarOccupantZoneCallback callback = getOccupantZoneCallback();
        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        updateDisplayPowerModeSetting(displayId, ScreenOffHandler.DISPLAY_POWER_MODE_ON);
        mScreenOffHandler.updateUserActivity(displayId, mClock.now());

        advanceTime(SCREEN_OFF_TIMEOUT + 1);
        verify(mSystemInterface, never()).setDisplayState(displayId, false);
    }

    @Test
    public void testUserActivity_bootIncomplete() throws Exception {
        OccupantZoneInfo zoneInfo = mCarOccupantZoneService.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
                VehicleAreaSeat.SEAT_ROW_2_LEFT);
        assertThat(zoneInfo).isNotNull();

        assertWithMessage("User for occupant zone(%s)", zoneInfo.zoneId)
                .that((mCarOccupantZoneService.getUserForOccupant(zoneInfo.zoneId)))
                .isEqualTo(CarOccupantZoneManager.INVALID_USER_ID);

        int displayId = mCarOccupantZoneService.getDisplayForOccupant(
                zoneInfo.zoneId, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);

        updateDisplayPowerModeSetting(displayId, ScreenOffHandler.DISPLAY_POWER_MODE_ON);
        mScreenOffHandler.updateUserActivity(displayId, mClock.now());

        advanceTime(SCREEN_OFF_TIMEOUT + 1);
        verify(mSystemInterface, never()).setDisplayState(displayId, false);
    }

    @Test
    public void testCanTurnOnDisplay() throws Exception {
        bootComplete();
        OccupantZoneInfo zoneInfo = mCarOccupantZoneService.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
                VehicleAreaSeat.SEAT_ROW_2_LEFT);
        assertThat(zoneInfo).isNotNull();

        int displayPort = 22;
        int displayId = mCarOccupantZoneService.getDisplayForOccupant(
                zoneInfo.zoneId, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);

        updateDisplayPowerModeSetting(displayId, displayPort,
                ScreenOffHandler.DISPLAY_POWER_MODE_OFF);
        assertWithMessage("Display off")
                .that(mScreenOffHandler.canTurnOnDisplay(displayId)).isFalse();

        updateDisplayPowerModeSetting(displayId, displayPort,
                ScreenOffHandler.DISPLAY_POWER_MODE_ON);
        assertWithMessage("Display on")
                .that(mScreenOffHandler.canTurnOnDisplay(displayId)).isTrue();
    }

    @Test
    public void testHandleDisplayStateChange_modeOn() throws Exception {
        bootComplete();
        OccupantZoneInfo zoneInfo = mCarOccupantZoneService.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
                VehicleAreaSeat.SEAT_ROW_2_LEFT);
        assertThat(zoneInfo).isNotNull();

        int displayId = mCarOccupantZoneService.getDisplayForOccupant(
                zoneInfo.zoneId, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        assertWithMessage("User for occupant zone(%s)", zoneInfo.zoneId)
                .that((mCarOccupantZoneService.getUserForOccupant(zoneInfo.zoneId)))
                .isEqualTo(CarOccupantZoneManager.INVALID_USER_ID);

        updateDisplayPowerModeSetting(displayId, ScreenOffHandler.DISPLAY_POWER_MODE_ON);
        mScreenOffHandler.handleDisplayStateChange(displayId, /* on= */ true);

        advanceTime(SCREEN_OFF_TIMEOUT + 1);
        verify(mSystemInterface).setDisplayState(displayId, false);
    }

    @Test
    public void testHandleDisplayStateChange_modeAlwaysOn() throws Exception {
        bootComplete();
        OccupantZoneInfo zoneInfo = mCarOccupantZoneService.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
                VehicleAreaSeat.SEAT_ROW_2_LEFT);
        assertThat(zoneInfo).isNotNull();

        int displayId = mCarOccupantZoneService.getDisplayForOccupant(
                zoneInfo.zoneId, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        assertWithMessage("User for occupant zone(%s)", zoneInfo.zoneId)
                .that((mCarOccupantZoneService.getUserForOccupant(zoneInfo.zoneId)))
                .isEqualTo(CarOccupantZoneManager.INVALID_USER_ID);

        updateDisplayPowerModeSetting(displayId, ScreenOffHandler.DISPLAY_POWER_MODE_ALWAYS_ON);
        mScreenOffHandler.handleDisplayStateChange(displayId, /* on= */ true);

        advanceTime(SCREEN_OFF_TIMEOUT + 1);
        verify(mSystemInterface, never()).setDisplayState(displayId, false);
    }

    @Test
    public void testHandleDisplayStateChange_modeOff() throws Exception {
        bootComplete();
        // TODO(b/279041525): Replace OccupantZoneHelper with mocking logics.
        OccupantZoneInfo zoneInfo = mCarOccupantZoneService.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
                VehicleAreaSeat.SEAT_ROW_2_LEFT);
        assertThat(zoneInfo).isNotNull();

        int displayId = mCarOccupantZoneService.getDisplayForOccupant(
                zoneInfo.zoneId, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        assertWithMessage("User for occupant zone(%s)", zoneInfo.zoneId)
                .that((mCarOccupantZoneService.getUserForOccupant(zoneInfo.zoneId)))
                .isEqualTo(CarOccupantZoneManager.INVALID_USER_ID);

        updateDisplayPowerModeSetting(displayId, ScreenOffHandler.DISPLAY_POWER_MODE_OFF);
        mScreenOffHandler.handleDisplayStateChange(displayId, /* on= */ true);

        advanceTime(SCREEN_OFF_TIMEOUT + 1);
        verify(mSystemInterface).setDisplayState(displayId, false);
    }

    private void setService() {
        when(mResources.getBoolean(R.bool.config_enablePassengerDisplayPowerSaving))
                .thenReturn(true);
        when(mResources.getInteger(R.integer.config_noUserScreenOffTimeout))
                .thenReturn(SCREEN_OFF_TIMEOUT);
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mSystemInterface.isDisplayEnabled(anyInt())).thenReturn(true);
        when(mContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);
        doAnswer((invocation) -> {
            Runnable r = invocation.getArgument(0);
            mRunnableAtBootComplete = r;
            return null;
        }).when(mSystemInterface).scheduleActionForBootCompleted(any(Runnable.class), any());

        mScreenOffHandler = new ScreenOffHandler(mContext, mSystemInterface,
                mTestLooper.getLooper(), mClock::now);
        mScreenOffHandler.init();
    }

    private void bootComplete() {
        if (mRunnableAtBootComplete == null) return;
        mRunnableAtBootComplete.run();
    }

    private Display createMockDisplay(int displayId) {
        int displayPort = displayId;
        return createMockDisplay(displayId, displayPort);
    }

    private Display createMockDisplay(int displayId, int displayPort) {
        Display display = mock(Display.class);
        DisplayAddress.Physical displayAddress = mock(DisplayAddress.Physical.class);
        when(displayAddress.getPort()).thenReturn(displayPort);
        when(display.getDisplayId()).thenReturn(displayId);
        when(display.getAddress()).thenReturn(displayAddress);
        return display;
    }

    private ICarOccupantZoneCallback getOccupantZoneCallback() {
        ArgumentCaptor<ICarOccupantZoneCallback> captor =
                ArgumentCaptor.forClass(ICarOccupantZoneCallback.class);
        verify(mCarOccupantZoneService).registerCallback(captor.capture());
        return captor.getValue();
    }

    private ContentObserver getSettingsObserver() {
        ArgumentCaptor<ContentObserver> captor =
                ArgumentCaptor.forClass(ContentObserver.class);
        verify(mContentResolver).registerContentObserver(
                any(),
                anyBoolean(),
                captor.capture());
        return captor.getValue();
    }

    private void updateDisplayPowerModeSetting(int displayId, int displayMode) {
        DisplayPowerModeBuilder builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(displayId), displayMode);

        Settings.Global.putString(mContentResolver, CarSettings.Global.DISPLAY_POWER_MODE,
                builder.build());
        ContentObserver osbserver = getSettingsObserver();
        osbserver.onChange(/* selfChange= */ false, /* uri= */ null);
    }

    private void updateDisplayPowerModeSetting(int displayId, int displayPort, int displayMode) {
        DisplayPowerModeBuilder builder = new DisplayPowerModeBuilder(mDisplayManager);
        builder.setDisplayMode(createMockDisplay(displayId, displayPort), displayMode);

        Settings.Global.putString(mContentResolver, CarSettings.Global.DISPLAY_POWER_MODE,
                builder.build());
        ContentObserver osbserver = getSettingsObserver();
        osbserver.onChange(/* selfChange= */ false, /* uri= */ null);
    }

    private void advanceTime(long timeMs) {
        mClock.fastForward(timeMs);
        mTestLooper.dispatchAll();
    }

    private static final class DisplayPowerModeBuilder {
        private final Map<Display, Integer> mDisplayPowerModeMap;
        private final List<Display> mDisplays;
        private final DisplayManager mDisplayManager;

        private DisplayPowerModeBuilder(DisplayManager displayManager) {
            mDisplayPowerModeMap = new ArrayMap<>();
            mDisplays = new ArrayList<>();
            mDisplayManager = displayManager;
        }

        private DisplayPowerModeBuilder setDisplayMode(Display display, int mode) {
            mDisplays.add(display);
            mDisplayPowerModeMap.put(display, mode);
            return this;
        }

        private String build() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mDisplays.size(); i++) {
                if (sb.length() != 0) {
                    sb.append(",");
                }
                Display display = mDisplays.get(i);
                int displayPort = getDisplayPort(display);
                int mode = mDisplayPowerModeMap.get(display);
                sb.append(displayPort).append(":").append(mode);
            }
            when(mDisplayManager.getDisplays())
                    .thenReturn(mDisplays.toArray(new Display[mDisplays.size()]));
            return sb.toString();
        }

        private int getDisplayPort(Display display) {
            DisplayAddress.Physical address = (DisplayAddress.Physical) display.getAddress();
            return address.getPort();
        }
    }
}
