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

import android.app.Application;
import android.car.Car;
import android.car.cluster.ClusterHomeManager;
import android.car.cluster.navigation.NavigationState;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * ViewModel class for showing trip status on Calm mode
 * This class exposes a {@link androidx.lifecycle.LiveData} for {@link NavigationStateData}
*/
public class NavigationStateViewModel extends AndroidViewModel
        implements ClusterHomeManager.ClusterNavigationStateListener {
    private static final boolean DEBUG = Build.isDebuggable();
    private static final String TAG = NavigationStateViewModel.class.getSimpleName();
    private final MutableLiveData<NavigationStateData> mNavStateData = new MutableLiveData<>();
    private ClusterHomeManager mClusterHomeManager;
    private Car mCar;

    public NavigationStateViewModel(@NonNull Application application) {
        super(application);
        mCar = Car.createCar(application);
        if (mCar == null) {
            if (DEBUG) {
                Log.w(TAG, "Car is null, unable to get ClusterHomeManager.");
            }
            return;
        }
        mClusterHomeManager = (ClusterHomeManager) mCar.getCarManager(Car.CLUSTER_HOME_SERVICE);
        if (mClusterHomeManager == null) {
            if (DEBUG) {
                Log.w(TAG, "ClusterHomeManager is null,"
                                + " unable to registerClusterNavigationStateListener.");
            }
            return;
        }
        mClusterHomeManager.registerClusterNavigationStateListener(
                application.getMainExecutor(), this);
    }


    @Override
    public void onNavigationStateChanged(byte[] navigationStateByteArr) {
        if (DEBUG) {
            Log.v(TAG, "ClusterNavigationStateListener onNavigationState");
        }
        NavigationState.NavigationStateProto navigationStateProto;
        try {
            navigationStateProto =
                    NavigationState.NavigationStateProto.parseFrom(navigationStateByteArr);
        } catch (InvalidProtocolBufferException e) {
            if (DEBUG) {
                Log.e(TAG, "Unable to parse navigation state proto. " + e);
            }
            mNavStateData.setValue(null);
            return;
        }
        if (DEBUG) {
            Log.v(TAG, "onNavigationState: navigationStateProto = " + navigationStateProto);
        }
        if (navigationStateProto == null || navigationStateProto.getDestinationsCount() == 0) {
            mNavStateData.setValue(null);
            return;
        }

        NavigationState.Destination nextDestination = navigationStateProto.getDestinations(0);

        if (!nextDestination.hasDistance()) {
            mNavStateData.setValue(null);
            return;
        }

        try {
            NavigationStateData.Builder navStateDataBuilder =
                    NavigationStateData.newBuilder()
                            .setTimeToDestination(
                                    nextDestination.getFormattedDurationUntilArrival())
                            .setDistanceToDestination(
                                    nextDestination.getDistance().getDisplayValue())
                            .setDistanceUnit(nextDestination.getDistance().getDisplayUnits());

            mNavStateData.setValue(navStateDataBuilder.build());
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "Unable to create NavigationStateData " + e);
            }
            mNavStateData.setValue(null);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (DEBUG) {
            Log.v(TAG, "onCleared()");
        }
        if (mCar != null) {
            mCar.disconnect();
        }
        if (mClusterHomeManager != null) {
            mClusterHomeManager.unregisterClusterNavigationStateListener(this);
        }
    }

    public LiveData<NavigationStateData> getNavigationState() {
        return mNavStateData;
    }
}
