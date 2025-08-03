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

package com.android.car.settings.wifi;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.wifi.SoftApConfiguration;
import android.util.Log;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.wifi.preferences.WifiTetherQrCodePreference;
import com.android.settingslib.qrcode.QrCodeGenerator;

/**
 * Controls WiFi Hotspot QR code.
 */
public class WifiTetherQrCodePreferenceController extends
        WifiTetherBasePreferenceController<WifiTetherQrCodePreference> implements
        WifiTetheringHandler.WifiTetheringAvailabilityListener {
    private static final String TAG = WifiTetherPasswordPreferenceController.class.getSimpleName();
    private static final String QR_CODE_FORMAT = "WIFI:T:WPA;S:%s;P:%s;;";
    private static final String QR_CODE_FORMAT_NOPASS = "WIFI:T:nopass;S:%s;;";

    private WifiTetheringHandler mWifiTetheringHandler;

    public WifiTetherQrCodePreferenceController(Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mWifiTetheringHandler = new WifiTetheringHandler(context,
                fragmentController.getSettingsLifecycle(), this, /* monitorRestarts= */ false);
    }

    @Override
    protected Class<WifiTetherQrCodePreference> getPreferenceType() {
        return WifiTetherQrCodePreference.class;
    }

    @Override
    protected String getSummary() {
        return null;
    }

    @Override
    protected String getDefaultSummary() {
        return null;
    }

    @Override
    protected void onStartInternal() {
        super.onStartInternal();
        mWifiTetheringHandler.onStartInternal();
    }

    @Override
    protected void onStopInternal() {
        mWifiTetheringHandler.onStopInternal();
    }

    @Override
    public void onWifiTetheringAvailable() {
        refreshUi();
    }

    @Override
    public void onWifiTetheringUnavailable() {
        refreshUi();
    }

    @Override
    public void enablePreference() {
        // No op.
    }

    @Override
    public void disablePreference() {
        // No op.
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (mWifiTetheringHandler.isWifiTetheringEnabled()) {
            return AVAILABLE_FOR_VIEWING;
        }
        return CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    protected void updateState(WifiTetherQrCodePreference preference) {
        super.updateState(preference);
        updateQrCode(preference);
    }

    private void updateQrCode(WifiTetherQrCodePreference preference) {
        if (mWifiTetheringHandler.isWifiTetheringEnabled()) {
            Bitmap bmp = null;
            String name = getCarSoftApConfig().getSsid();
            String password = getCarSoftApConfig().getPassphrase();
            int securityType = getCarSoftApConfig().getSecurityType();
            String content;
            if (securityType == SoftApConfiguration.SECURITY_TYPE_OPEN) {
                content = String.format(QR_CODE_FORMAT_NOPASS, name);
            } else {
                content = String.format(QR_CODE_FORMAT, name, password);
            }
            try {
                int size = getContext().getResources().getDimensionPixelSize(
                        R.dimen.hotspot_qr_code_size);
                int margin = getContext().getResources().getDimensionPixelSize(
                        R.dimen.qr_code_margin);
                bmp = QrCodeGenerator.encodeQrCode(content, size, margin);
            } catch (Exception e) {
                Log.w(TAG, "Exception found: " + e);
            }
            preference.setPreferenceInfo(bmp, name, password);
        }
    }
}
