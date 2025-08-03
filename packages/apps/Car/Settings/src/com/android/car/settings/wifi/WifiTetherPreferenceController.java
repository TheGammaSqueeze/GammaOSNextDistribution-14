/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.wifi;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.TetheringManager;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiTwoActionSwitchPreference;

/**
 * Controls the availability of wifi tethering preference based on whether tethering is supported
 */
public class WifiTetherPreferenceController extends
        PreferenceController<CarUiTwoActionSwitchPreference>
        implements WifiTetheringHandler.WifiTetheringAvailabilityListener {

    private final TetheringManager mTetheringManager;
    private final CarWifiManager mCarWifiManager;
    private final Handler mHandler;
    private final WifiTetheringHandler mWifiTetheringHandler;
    private int mConnectedDevicesCount;
    private volatile boolean mIsTetheringSupported;
    private volatile boolean mReceivedTetheringEventCallback = false;

    private final TetheringManager.TetheringEventCallback mTetheringCallback =
            new TetheringManager.TetheringEventCallback() {
                @Override
                public void onTetheringSupported(boolean supported) {
                    mReceivedTetheringEventCallback = true;
                    if (mIsTetheringSupported != supported) {
                        mIsTetheringSupported = supported;
                    }
                    refreshUi();
                }
            };

    public WifiTetherPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                new CarWifiManager(context, fragmentController.getSettingsLifecycle()),
                context.getSystemService(TetheringManager.class));
    }

    @VisibleForTesting
    WifiTetherPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            CarWifiManager carWifiManager, TetheringManager tetheringManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarWifiManager = carWifiManager;
        mTetheringManager = tetheringManager;
        mHandler = new Handler(Looper.getMainLooper());
        mWifiTetheringHandler = new WifiTetheringHandler(context, mCarWifiManager,
                mTetheringManager, this, /* monitorRestarts= */ true);
    }

    @Override
    protected Class<CarUiTwoActionSwitchPreference> getPreferenceType() {
        return CarUiTwoActionSwitchPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        getPreference().setOnSecondaryActionClickListener(isChecked -> {
            mWifiTetheringHandler.updateWifiTetheringState(isChecked);
        });
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            WifiUtil.runClickableWhileDisabled(getContext(), getFragmentController());
        });
    }

    @Override
    protected void onStartInternal() {
        mTetheringManager.registerTetheringEventCallback(
                new HandlerExecutor(mHandler), mTetheringCallback);
        boolean tetheringEnabled = mWifiTetheringHandler.isWifiTetheringEnabled();
        updateSwitchPreference(tetheringEnabled);
        mWifiTetheringHandler.onStartInternal();
    }

    @Override
    protected void onStopInternal() {
        mTetheringManager.unregisterTetheringEventCallback(mTetheringCallback);
        mWifiTetheringHandler.onStopInternal();
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (!mReceivedTetheringEventCallback) {
            return AVAILABLE_FOR_VIEWING;
        }
        if (!mIsTetheringSupported) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (WifiUtil.isConfigWifiRestrictedByUm(getContext())
                || WifiUtil.isConfigWifiRestrictedByDpm(getContext())) {
            return AVAILABLE_FOR_VIEWING;
        }
        return AVAILABLE;
    }

    @Override
    public void onWifiTetheringAvailable() {
        updateSwitchPreference(true);
    }

    @Override
    public void onWifiTetheringUnavailable() {
        updateSwitchPreference(false);
    }

    @Override
    public void onConnectedClientsChanged(int clientCount) {
        mConnectedDevicesCount = clientCount;
        updateSummary(mWifiTetheringHandler.isWifiTetheringEnabled());
    }

    @Override
    public void enablePreference() {
        getPreference().setSecondaryActionEnabled(true);
    }

    @Override
    public void disablePreference() {
        getPreference().setSecondaryActionEnabled(false);
    }

    private void updateSwitchPreference(boolean switchOn) {
        updateSummary(switchOn);
        getPreference().setSecondaryActionChecked(switchOn);
    }

    private void updateSummary(boolean hotspotEnabled) {
        String subtitle = WifiTetherUtil.getHotspotSubtitle(getContext(),
                mCarWifiManager.getSoftApConfig(), hotspotEnabled, mConnectedDevicesCount);
        getPreference().setSummary(subtitle);
    }
}
