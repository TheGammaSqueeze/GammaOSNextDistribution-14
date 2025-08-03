/*
 * Copyright (C) 2020 Google Inc.
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

package com.android.car.carlauncher.homescreen.assistive;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.CarProjectionManager;
import android.car.projection.ProjectionStatus;
import android.content.Context;
import android.icu.text.MessageFormat;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Map;

@RunWith(JUnit4.class)
public class ProjectionModelTest {

    public Context mContext = ApplicationProvider.getApplicationContext();

    private static final String PROJECTING_DEVICE_NAME = "projecting device name";
    private static final String NONPROJECTING_DEVICE_NAME = "non-projecting device name";
    private static final ProjectionStatus.MobileDevice PROJECTING_DEVICE =
            ProjectionStatus.MobileDevice.builder(0, PROJECTING_DEVICE_NAME).setProjecting(
                    true).build();
    private static final ProjectionStatus.MobileDevice NONPROJECTING_DEVICE =
            ProjectionStatus.MobileDevice.builder(0, NONPROJECTING_DEVICE_NAME).setProjecting(
                    false).build();

    private final ProjectionStatus mInactiveProjectionStatus = ProjectionStatus.builder(
            mContext.getPackageName(), ProjectionStatus.PROJECTION_STATE_INACTIVE).build();
    private final ProjectionStatus mProjectingDeviceProjectionStatus = ProjectionStatus.builder(
            mContext.getPackageName(),
            ProjectionStatus.PROJECTION_STATE_READY_TO_PROJECT).addMobileDevice(
            PROJECTING_DEVICE).build();
    private final ProjectionStatus mNonProjectingDeviceProjectionStatus = ProjectionStatus.builder(
            mContext.getPackageName(), ProjectionStatus.PROJECTION_STATE_READY_TO_PROJECT)
            .addMobileDevice(NONPROJECTING_DEVICE).build();
    private final ProjectionStatus mProjectingAndNonProjectingDeviceProjectionStatus =
            ProjectionStatus.builder(
                    mContext.getPackageName(),
                    ProjectionStatus.PROJECTION_STATE_READY_TO_PROJECT).addMobileDevice(
                    PROJECTING_DEVICE).addMobileDevice(NONPROJECTING_DEVICE).build();
    private final ProjectionStatus mProjectingMultipleAndNonProjectingDeviceProjectionStatus =
            ProjectionStatus.builder(
                    mContext.getPackageName(),
                    ProjectionStatus.PROJECTION_STATE_READY_TO_PROJECT).addMobileDevice(
                    PROJECTING_DEVICE).addMobileDevice(PROJECTING_DEVICE).addMobileDevice(
                    NONPROJECTING_DEVICE).build();

    private ProjectionModel mModel;
    private MockitoSession mSession;

    @Mock
    private HomeCardInterface.Model.OnModelUpdateListener mOnModelUpdateListener;
    @Mock
    private Car mMockCar;
    @Mock
    private CarProjectionManager mProjectionManager;

    @Before
    public void setUp() {
        mSession = mockitoSession()
                .initMocks(this)
                .mockStatic(Car.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(mMockCar.getCarManager(CarProjectionManager.class)).thenReturn(mProjectionManager);
        when(Car.createCar(any(), any(), anyLong(), any())).thenReturn(mMockCar);
    }

    @After
    public void tearDown() {
        mModel.onDestroy(mContext);
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_carConnected_registerProjectionStatusListener() {
        ArgumentCaptor<Car.CarServiceLifecycleListener> carLifecycleCaptor =
                ArgumentCaptor.forClass(Car.CarServiceLifecycleListener.class);
        when(Car.createCar(any(), any(), anyLong(), carLifecycleCaptor.capture())).then(
                invocation -> {
                    Car.CarServiceLifecycleListener listener = carLifecycleCaptor.getValue();
                    listener.onLifecycleChanged(mMockCar, true);
                    return mMockCar;
                });

        createModel();

        verify(() -> Car.createCar(any(), any(), anyLong(), any()));
        verify(mProjectionManager).registerProjectionStatusListener(any());
    }

    @Test
    public void noChange_doesNotCallPresenter() {
        createModel();

        verify(mOnModelUpdateListener, never()).onModelUpdate(any());
        assertNull(mModel.getCardHeader());
        assertNull(mModel.getCardContent());
    }

    @Test
    public void changeProjectionStatusToProjectingDevice_callsPresenter() {
        createModel();
        sendProjectionStatus(mProjectingDeviceProjectionStatus);

        verify(mOnModelUpdateListener).onModelUpdate(mModel);
        DescriptiveTextView content = (DescriptiveTextView) mModel.getCardContent();
        assertEquals(PROJECTING_DEVICE_NAME, String.valueOf(content.getSubtitle()));
    }

    @Test
    public void changeProjectionStatusToNonProjectingDevice_callsPresenter() {
        createModel();
        sendProjectionStatus(mNonProjectingDeviceProjectionStatus);

        verify(mOnModelUpdateListener).onModelUpdate(mModel);
        DescriptiveTextView content = (DescriptiveTextView) mModel.getCardContent();
        assertEquals(NONPROJECTING_DEVICE_NAME, String.valueOf(content.getSubtitle()));
    }

    @Test
    public void changeProjectionStatusToSingleProjectingAndNonProjectingDevice_callsPresenter() {
        createModel();
        sendProjectionStatus(mProjectingAndNonProjectingDeviceProjectionStatus);

        verify(mOnModelUpdateListener).onModelUpdate(mModel);
        DescriptiveTextView content = (DescriptiveTextView) mModel.getCardContent();
        assertEquals(PROJECTING_DEVICE_NAME, String.valueOf(content.getSubtitle()));
    }

    @Test
    public void changeProjectionStatusToMultipleProjectingAndNonProjectingDevice_callsPresenter() {
        createModel();
        sendProjectionStatus(mProjectingMultipleAndNonProjectingDeviceProjectionStatus);

        verify(mOnModelUpdateListener).onModelUpdate(mModel);
        DescriptiveTextView content = (DescriptiveTextView) mModel.getCardContent();

        String formattedPluralString = MessageFormat.format(mContext.getString(
                        R.string.projection_devices),
                Map.of("count",
                        mProjectingMultipleAndNonProjectingDeviceProjectionStatus
                                .getConnectedMobileDevices().size()));
        assertEquals(formattedPluralString, String.valueOf(content.getSubtitle()));
    }

    @Test
    public void changeProjectionStatusToInactive_callsPresenter() {
        createModel();
        sendProjectionStatus(mProjectingDeviceProjectionStatus);
        reset(mOnModelUpdateListener);

        sendProjectionStatus(mInactiveProjectionStatus);

        verify(mOnModelUpdateListener).onModelUpdate(mModel);
        assertNull(mModel.getCardHeader());
        assertNull(mModel.getCardContent());
    }

    private void createModel() {
        mModel = new ProjectionModel();
        mModel.setOnModelUpdateListener(mOnModelUpdateListener);
        mModel.onCreate(mContext);
        reset(mOnModelUpdateListener);
    }

    private void sendProjectionStatus(ProjectionStatus status) {
        reset(mOnModelUpdateListener);
        mModel.onProjectionStatusChanged(
                status.getState(),
                status.getPackageName(),
                Collections.singletonList(status));
    }
}
