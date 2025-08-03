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

package com.android.car.carlauncher.calmmode;

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.cluster.ClusterHomeManager;
import android.car.cluster.navigation.NavigationState;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.icu.util.MeasureUnit;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.apps.common.testutils.InstantTaskExecutorRule;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NavigationStateViewModelTest extends AbstractExtendedMockitoTestCase {
    @Rule
    public InstantTaskExecutorRule mInstantExecutorRule = new InstantTaskExecutorRule();
    @Mock
    private ClusterHomeManager mClusterHomeManager;
    @Mock
    private Car mCar;
    private NavigationStateViewModel mNavigationStateViewModel;

    private static NavigationState.NavigationStateProto buildNavStateProto(
            String distanceValue,
            NavigationState.Distance.Unit distanceUnit,
            String durationUntilArrival) {
        NavigationState.Distance distance =
                NavigationState.Distance.newBuilder()
                        .setDisplayValue(distanceValue)
                        .setDisplayUnits(distanceUnit)
                        .build();
        NavigationState.Destination destination =
                NavigationState.Destination.newBuilder()
                        .setDistance(distance)
                        .setFormattedDurationUntilArrival(durationUntilArrival)
                        .build();
        return NavigationState.NavigationStateProto.newBuilder()
                .addDestinations(destination)
                .build();
    }

    @Override
    protected void onSessionBuilder(@NonNull CustomMockitoSessionBuilder builder) {
        builder.spyStatic(Car.class);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        ExtendedMockito.doAnswer(invocation -> mCar).when(() -> Car.createCar(any()));
        when(mCar.getCarManager(eq(Car.CLUSTER_HOME_SERVICE))).thenReturn(mClusterHomeManager);

        mNavigationStateViewModel =
                new NavigationStateViewModel(ApplicationProvider.getApplicationContext());
        mNavigationStateViewModel = spy(mNavigationStateViewModel);
    }

    @Test
    public void constructor_viewModelCreateSuccess() {
        assertNotNull(mNavigationStateViewModel);
        verify(mCar).getCarManager(eq(Car.CLUSTER_HOME_SERVICE));
        verify(mClusterHomeManager).registerClusterNavigationStateListener(any(), any());
    }

    @Test
    public void constructor_carIsNull_viewModelCreateSuccess() {
        ExtendedMockito.doAnswer(invocation -> null).when(() -> Car.createCar(any()));
        NavigationStateViewModel navStateVM =
                new NavigationStateViewModel(ApplicationProvider.getApplicationContext());
        assertNotNull(navStateVM);
    }

    @Test
    public void constructor_clusterHomeManagerIsNull_viewModelCreateSuccess() {
        Car car = mock(Car.class);
        ExtendedMockito.doAnswer(invocation -> car).when(() -> Car.createCar(any()));
        when(car.getCarManager(eq(Car.CLUSTER_HOME_SERVICE))).thenReturn(null);
        NavigationStateViewModel navStateVM =
                new NavigationStateViewModel(ApplicationProvider.getApplicationContext());
        assertNotNull(navStateVM);
        verify(car).getCarManager(eq(Car.CLUSTER_HOME_SERVICE));
    }

    @Test
    public void onNavigationState_noDestination_navStateSetToNull() {

        NavigationState.NavigationStateProto navStateProto =
                NavigationState.NavigationStateProto.newBuilder().build();
        mNavigationStateViewModel.onNavigationStateChanged(navStateProto.toByteArray());

        NavigationStateData navStateData =
                mNavigationStateViewModel.getNavigationState().getValue();
        assertNull(navStateData);
    }

    @Test
    public void onNavigationState_protoExceptionWhileParsing_navStateIsSetToNull() {
        mNavigationStateViewModel.onNavigationStateChanged(new byte[] {});
        NavigationStateData navStateData =
                mNavigationStateViewModel.getNavigationState().getValue();
        assertNull(navStateData);
    }

    @Test
    public void onNavigationState_destinationImperial_navStateIsImperial() {
        final String timeToDest = "1hr 30min";
        final String distanceToDest = "100";

        NavigationState.NavigationStateProto navStateProto =
                buildNavStateProto(distanceToDest, NavigationState.Distance.Unit.MILES, timeToDest);
        mNavigationStateViewModel.onNavigationStateChanged(navStateProto.toByteArray());

        NavigationStateData navStateData =
                mNavigationStateViewModel.getNavigationState().getValue();
        assertNotNull(navStateData);
        assertEquals(timeToDest, navStateData.getTimeToDestination());
        assertEquals(100, navStateData.getDistanceToDestination(), 0);
        assertEquals(MeasureUnit.MILE, navStateData.getDistanceUnit());
    }

    @Test
    public void onNavigationState_destinationMetric_navStateIsMetric() {
        final String timeToDest = "30min";
        final String distanceToDest = "20";
        NavigationState.NavigationStateProto navStateProto =
                buildNavStateProto(
                        distanceToDest, NavigationState.Distance.Unit.KILOMETERS, timeToDest);
        mNavigationStateViewModel.onNavigationStateChanged(navStateProto.toByteArray());

        NavigationStateData navStateData =
                mNavigationStateViewModel.getNavigationState().getValue();
        assertNotNull(navStateData);
        assertEquals(timeToDest, navStateData.getTimeToDestination());
        assertEquals(20, navStateData.getDistanceToDestination(), 0);
        assertEquals(MeasureUnit.KILOMETER, navStateData.getDistanceUnit());
    }

    @Test
    public void onNavigationState_destinationFeet_navStateIsFeet() {
        final String timeToDest = "3min";
        final String distanceToDest = "1000";
        NavigationState.NavigationStateProto navStateProto =
                buildNavStateProto(distanceToDest, NavigationState.Distance.Unit.FEET, timeToDest);
        mNavigationStateViewModel.onNavigationStateChanged(navStateProto.toByteArray());

        NavigationStateData navStateData =
                mNavigationStateViewModel.getNavigationState().getValue();
        assertNotNull(navStateData);
        assertEquals(timeToDest, navStateData.getTimeToDestination());
        assertEquals(1000, navStateData.getDistanceToDestination(), 0);
        assertEquals(MeasureUnit.FOOT, navStateData.getDistanceUnit());
    }

    @Test
    public void onCleared_testCarDisconnects() {
        mNavigationStateViewModel.onCleared();
        verify(mCar).disconnect();
        verify(mClusterHomeManager).unregisterClusterNavigationStateListener(any());
    }
}
