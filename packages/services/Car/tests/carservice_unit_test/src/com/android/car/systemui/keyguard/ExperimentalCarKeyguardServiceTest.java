/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.systemui.keyguard;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_RIGHT;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_INVISIBLE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.CarOccupantZoneManager;
import android.car.IExperimentalCarKeyguardLockedStateListener;
import android.car.builtin.keyguard.KeyguardServiceDelegate;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.user.CarUserManager;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Display;

import com.android.car.CarOccupantZoneService;
import com.android.car.user.CarUserService;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Set;

/**
 * Unit tests for the {@link ExperimentalCarKeyguardService}.
 */
public final class ExperimentalCarKeyguardServiceTest extends AbstractExtendedMockitoTestCase {
    private static final int FOREGROUND_USER_ID = 1000;
    private static final int FOREGROUND_USER_DISPLAY_ID = 1000;
    private static final int FOREGROUND_USER_ZONE = 1000;

    private static final int SECONDARY_USER_ID = 1001;
    private static final int SECONDARY_USER_DISPLAY_ID = 1001;
    private static final int SECONDARY_USER_ZONE = 1000;

    private ExperimentalCarKeyguardService mExperimentalCarKeyguardService;

    @Mock
    private Context mMockContext;
    @Mock
    private DisplayManager mMockDisplayManager;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private CarUserService mMockCarUserService;
    @Mock
    private CarOccupantZoneService mMockCarOccupantZoneService;
    @Mock
    private Display mMainDisplay;
    @Mock
    private Display mSecondaryDisplay;

    public ExperimentalCarKeyguardServiceTest() {
        super(ExperimentalCarKeyguardService.TAG);
    }

    @Before
    public void setUp() {
        when(mMockContext.getSystemService(DisplayManager.class)).thenReturn(mMockDisplayManager);
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

        doReturn(FOREGROUND_USER_ID)
                .when(ActivityManager::getCurrentUser);

        setupUsers();
        mExperimentalCarKeyguardService = new ExperimentalCarKeyguardService(mMockContext,
                mMockCarUserService, mMockCarOccupantZoneService);
        ExtendedMockito.spyOn(mExperimentalCarKeyguardService);
        when(mExperimentalCarKeyguardService.createKeyguardServiceDelegate()).thenReturn(mock(
                KeyguardServiceDelegate.class));
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder.spyStatic(ActivityManager.class);
    }

    @Test
    public void onInit_registersListeners() {
        mExperimentalCarKeyguardService.init();

        // Verify user listener registered for visible and invisible events
        ArgumentCaptor<UserLifecycleEventFilter> filterCaptor = ArgumentCaptor.forClass(
                UserLifecycleEventFilter.class);
        verify(mMockCarUserService).addUserLifecycleListener(filterCaptor.capture(), any());
        int[] eventTypes = filterCaptor.getValue().getEventTypes();
        assertThat(Arrays.stream(eventTypes).anyMatch(
                event -> event == USER_LIFECYCLE_EVENT_TYPE_VISIBLE)).isTrue();
        assertThat(Arrays.stream(eventTypes).anyMatch(
                event -> event == USER_LIFECYCLE_EVENT_TYPE_INVISIBLE)).isTrue();

        // Verify display listener registered
        verify(mMockDisplayManager).registerDisplayListener(any(), any());

        // Verify occupant zone config listener registered
        verify(mMockCarOccupantZoneService).registerCallback(any());
    }

    @Test
    public void onInit_initializeCurrentPassengerUsers() {
        setCurrentUsers(/* passengerVisible= */ true);

        mExperimentalCarKeyguardService.init();

        // Only the secondary user should be initialized
        assertThat(mExperimentalCarKeyguardService.getKeyguardState().size()).isEqualTo(1);
        KeyguardServiceDelegate delegate = getKeyguardState(SECONDARY_USER_ID).mKeyguardDelegate;
        verify(delegate).bindService(any(), eq(UserHandle.of(SECONDARY_USER_ID)),
                eq(new int[]{SECONDARY_USER_DISPLAY_ID}));
    }

    @Test
    public void onSystemUserVisible_doNothing() {
        setCurrentUsers(/* passengerVisible= */ false);
        mExperimentalCarKeyguardService.init();
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                USER_LIFECYCLE_EVENT_TYPE_VISIBLE, UserHandle.USER_SYSTEM);

        mExperimentalCarKeyguardService.mUserLifecycleListener.onEvent(event);

        assertThat(mExperimentalCarKeyguardService.getKeyguardState().size()).isEqualTo(0);
    }

    @Test
    public void onForegroundUserVisible_doNothing() {
        setCurrentUsers(/* passengerVisible= */ false);
        mExperimentalCarKeyguardService.init();
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                USER_LIFECYCLE_EVENT_TYPE_VISIBLE, FOREGROUND_USER_ID);

        mExperimentalCarKeyguardService.mUserLifecycleListener.onEvent(event);

        assertThat(mExperimentalCarKeyguardService.getKeyguardState().size()).isEqualTo(0);
    }

    @Test
    public void onSecondaryUserVisible_initKeyguard() {
        setCurrentUsers(/* passengerVisible= */ false);
        mExperimentalCarKeyguardService.init();

        startKeyguardForSecondaryUser();

        assertThat(mExperimentalCarKeyguardService.getKeyguardState().size()).isEqualTo(1);
        KeyguardServiceDelegate delegate = getKeyguardState(SECONDARY_USER_ID).mKeyguardDelegate;
        verify(delegate).bindService(any(), eq(UserHandle.of(SECONDARY_USER_ID)),
                eq(new int[]{SECONDARY_USER_DISPLAY_ID}));
    }

    @Test
    public void onUserInvisible_removeKeyguard() {
        setCurrentUsers(/* passengerVisible= */ true);
        mExperimentalCarKeyguardService.init();
        KeyguardServiceDelegate delegate = getKeyguardState(SECONDARY_USER_ID).mKeyguardDelegate;

        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                USER_LIFECYCLE_EVENT_TYPE_INVISIBLE, SECONDARY_USER_ID);
        mExperimentalCarKeyguardService.mUserLifecycleListener.onEvent(event);

        assertThat(mExperimentalCarKeyguardService.getKeyguardState().size()).isEqualTo(0);
        verify(delegate).stop(any());
    }

    @Test
    public void onDisplayOn_notifiesDelegate() {
        when(mSecondaryDisplay.getState()).thenReturn(Display.STATE_OFF);
        setCurrentUsers(/* passengerVisible= */ true);
        mExperimentalCarKeyguardService.init();

        when(mSecondaryDisplay.getState()).thenReturn(Display.STATE_ON);
        mExperimentalCarKeyguardService.mDisplayListener.onDisplayChanged(
                SECONDARY_USER_DISPLAY_ID);

        KeyguardServiceDelegate delegate = getKeyguardState(SECONDARY_USER_ID).mKeyguardDelegate;
        verify(delegate).notifyDisplayOn();
    }

    @Test
    public void onDisplayOff_notifiesDelegate() {
        setCurrentUsers(/* passengerVisible= */ true);
        mExperimentalCarKeyguardService.init();

        when(mSecondaryDisplay.getState()).thenReturn(Display.STATE_OFF);
        mExperimentalCarKeyguardService.mDisplayListener.onDisplayChanged(
                SECONDARY_USER_DISPLAY_ID);

        KeyguardServiceDelegate delegate = getKeyguardState(SECONDARY_USER_ID).mKeyguardDelegate;
        verify(delegate).notifyDisplayOff();
    }

    @Test
    public void addLockedStateListener_registerToDelegateOnce() {
        IExperimentalCarKeyguardLockedStateListener listener1 =
                mock(IExperimentalCarKeyguardLockedStateListener.class);
        IExperimentalCarKeyguardLockedStateListener listener2 =
                mock(IExperimentalCarKeyguardLockedStateListener.class);
        setCurrentUsers(/* passengerVisible= */ true);
        mExperimentalCarKeyguardService.init();
        ExperimentalCarKeyguardService.KeyguardState state = getKeyguardState(SECONDARY_USER_ID);

        state.addKeyguardLockedStateListener(listener1);
        state.addKeyguardLockedStateListener(listener2);

        verify(state.mKeyguardDelegate).registerKeyguardLockedStateCallback(notNull());
    }

    @Test
    public void removeLastLockedStateListener_unregistersFromDelegate() {
        IExperimentalCarKeyguardLockedStateListener listener1 =
                mock(IExperimentalCarKeyguardLockedStateListener.class);
        IExperimentalCarKeyguardLockedStateListener listener2 =
                mock(IExperimentalCarKeyguardLockedStateListener.class);
        setCurrentUsers(/* passengerVisible= */ true);
        mExperimentalCarKeyguardService.init();
        ExperimentalCarKeyguardService.KeyguardState state = getKeyguardState(SECONDARY_USER_ID);
        state.addKeyguardLockedStateListener(listener1);
        state.addKeyguardLockedStateListener(listener2);

        state.removeKeyguardLockedStateListener(listener2);
        verify(state.mKeyguardDelegate, never()).unregisterKeyguardLockedStateCallback();
        state.removeKeyguardLockedStateListener(listener1);
        verify(state.mKeyguardDelegate).unregisterKeyguardLockedStateCallback();
    }

    @Test
    public void onLockedStateChanged_notifiesListener() throws RemoteException {
        IExperimentalCarKeyguardLockedStateListener listener =
                mock(IExperimentalCarKeyguardLockedStateListener.class);
        setCurrentUsers(/* passengerVisible= */ true);
        mExperimentalCarKeyguardService.init();
        ExperimentalCarKeyguardService.KeyguardState state = getKeyguardState(SECONDARY_USER_ID);
        state.addKeyguardLockedStateListener(listener);
        ArgumentCaptor<KeyguardServiceDelegate.KeyguardLockedStateCallback> callbackCaptor =
                ArgumentCaptor.forClass(KeyguardServiceDelegate.KeyguardLockedStateCallback.class);
        verify(state.mKeyguardDelegate).registerKeyguardLockedStateCallback(
                callbackCaptor.capture());

        callbackCaptor.getValue().onKeyguardLockedStateChanged(true);

        verify(listener).onKeyguardLockedStateChanged(true);
    }

    private void startKeyguardForSecondaryUser() {
        when(mMockUserManager.isUserRunning(UserHandle.of(SECONDARY_USER_ID))).thenReturn(true);
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                USER_LIFECYCLE_EVENT_TYPE_VISIBLE, SECONDARY_USER_ID);
        mExperimentalCarKeyguardService.mUserLifecycleListener.onEvent(event);
    }

    private void setupUsers() {
        // Set occupant zones and displays for system, foreground, and secondary users
        when(mMainDisplay.getDisplayId()).thenReturn(FOREGROUND_USER_DISPLAY_ID);
        when(mMainDisplay.getState()).thenReturn(Display.STATE_ON);
        when(mSecondaryDisplay.getDisplayId()).thenReturn(SECONDARY_USER_DISPLAY_ID);
        when(mSecondaryDisplay.getState()).thenReturn(Display.STATE_ON);
        CarOccupantZoneManager.OccupantZoneInfo primaryOccupantInfo =
                new CarOccupantZoneManager.OccupantZoneInfo(FOREGROUND_USER_ZONE,
                        OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);
        CarOccupantZoneManager.OccupantZoneInfo secondaryOccupantInfo =
                new CarOccupantZoneManager.OccupantZoneInfo(SECONDARY_USER_ZONE,
                        OCCUPANT_TYPE_FRONT_PASSENGER, SEAT_ROW_1_RIGHT);
        when(mMockCarOccupantZoneService.getOccupantZoneForUser(UserHandle.SYSTEM)).thenReturn(
                primaryOccupantInfo);
        when(mMockCarOccupantZoneService.getOccupantZoneForUser(
                UserHandle.of(FOREGROUND_USER_ID))).thenReturn(primaryOccupantInfo);
        when(mMockCarOccupantZoneService.getOccupantZoneForUser(
                UserHandle.of(SECONDARY_USER_ID))).thenReturn(secondaryOccupantInfo);
        when(mMockCarOccupantZoneService.getDisplayForOccupant(FOREGROUND_USER_ZONE,
                DISPLAY_TYPE_MAIN)).thenReturn(FOREGROUND_USER_DISPLAY_ID);
        when(mMockCarOccupantZoneService.getDisplayForOccupant(SECONDARY_USER_ZONE,
                DISPLAY_TYPE_MAIN)).thenReturn(SECONDARY_USER_DISPLAY_ID);
        when(mMockCarOccupantZoneService.getAllDisplaysForOccupantZone(FOREGROUND_USER_ZONE))
                .thenReturn(new int[]{FOREGROUND_USER_DISPLAY_ID});
        when(mMockCarOccupantZoneService.getAllDisplaysForOccupantZone(
                SECONDARY_USER_ZONE)).thenReturn(new int[]{SECONDARY_USER_DISPLAY_ID});
        when(mMockDisplayManager.getDisplay(FOREGROUND_USER_DISPLAY_ID)).thenReturn(mMainDisplay);
        when(mMockDisplayManager.getDisplay(SECONDARY_USER_DISPLAY_ID)).thenReturn(
                mSecondaryDisplay);
    }

    private void setCurrentUsers(boolean passengerVisible) {
        Set<UserHandle> visibleUsers;
        if (passengerVisible) {
            visibleUsers = Set.of(UserHandle.SYSTEM, UserHandle.of(FOREGROUND_USER_ID),
                    UserHandle.of(SECONDARY_USER_ID));
        } else {
            visibleUsers = Set.of(UserHandle.SYSTEM, UserHandle.of(FOREGROUND_USER_ID));
        }
        when(mMockUserManager.getVisibleUsers()).thenReturn(visibleUsers);
    }

    private ExperimentalCarKeyguardService.KeyguardState getKeyguardState(int userId) {
        ExperimentalCarKeyguardService.KeyguardState state =
                mExperimentalCarKeyguardService.getKeyguardState().get(userId);
        assertThat(state).isNotNull();
        return state;
    }
}
