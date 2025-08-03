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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.statusicon.StatusIconView;
import com.android.systemui.car.statusicon.StatusIconViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.concurrent.Executor;

/**
 * A controller for the read-only icon that shows sensor info
 */
public class StatusBarSensorInfoController extends StatusIconViewController {
    private static final String TAG = StatusBarSensorInfoController.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final MutableLiveData<String> mSensorStringLiveData;
    private final MutableLiveData<Boolean> mSensorAvailabilityData;
    private final Observer<String> mSensorStringObserver = this::updateDrawable;
    private final Observer<Boolean> mSensorAvailabilityObserver = this::updateAvailability;
    private final StatusBarSensorInfoManager mStatusBarSensorInfoManager;

    private final String mSensorString;
    private final int mDrawableWidth;
    private final int mDrawableHeight;
    private Boolean mSensorAvailability;
    private TextDrawable mDrawable;

    @AssistedInject
    protected StatusBarSensorInfoController(
            @Assisted StatusIconView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Context context,
            @Main Resources resources,
            @UiBackground Executor executor,
            CarServiceProvider carServiceProvider) {
        super(view, disableController, stateController);
        mContext = context;
        mSensorString = resources.getString(R.string.default_sensor_string);
        mSensorAvailability = false;
        mSensorStringLiveData = new MutableLiveData<>(mSensorString);
        mSensorAvailabilityData = new MutableLiveData<>(mSensorAvailability);
        mStatusBarSensorInfoManager = new StatusBarSensorInfoManager(resources,
                carServiceProvider, executor,
                mSensorStringLiveData, mSensorAvailabilityData);
        mDrawableWidth = resources.getDimensionPixelSize(
                R.dimen.statusbar_sensor_text_width);
        mDrawableHeight = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
    }

    @AssistedFactory
    public interface Factory extends
            StatusIconViewController.Factory<StatusBarSensorInfoController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mStatusBarSensorInfoManager.onAttached();
        mSensorStringLiveData.observeForever(mSensorStringObserver);
        mSensorAvailabilityData.observeForever(mSensorAvailabilityObserver);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mStatusBarSensorInfoManager.onDetached();
        mSensorStringLiveData.removeObserver(mSensorStringObserver);
        mSensorAvailabilityData.removeObserver(mSensorAvailabilityObserver);
    }

    @Override
    protected void updateStatus() {
        setIconVisibility(mSensorAvailability);
        setIconDrawableToDisplay(mDrawable);
        onStatusUpdated();
    }

    private void updateAvailability(Boolean availability) {
        logIfDebug("Sensor availability = " + availability);
        mSensorAvailability = availability;
        updateStatus();
    }

    private void updateDrawable(String sensorString) {
        logIfDebug("Sensor value string = " + sensorString);
        mDrawable = new TextDrawable(mContext, R.layout.sensor_text, sensorString, mDrawableWidth,
                mDrawableHeight);
        updateStatus();
    }

    private void logIfDebug(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
