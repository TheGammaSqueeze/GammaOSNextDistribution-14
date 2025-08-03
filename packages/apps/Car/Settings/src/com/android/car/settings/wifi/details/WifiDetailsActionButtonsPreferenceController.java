/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.wifi.details;

import static com.android.car.settings.common.ActionButtonsPreference.ActionButtons;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.widget.Toast;

import com.android.car.settings.R;
import com.android.car.settings.common.ActionButtonsPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.wifi.WifiUtil;
import com.android.wifitrackerlib.WifiEntry;

/**
 * Shows Wifi details action buttons (forget and connect).
 */
public class WifiDetailsActionButtonsPreferenceController
        extends WifiDetailsBasePreferenceController<ActionButtonsPreference>
        implements WifiEntry.ConnectCallback {

    public WifiDetailsActionButtonsPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ActionButtonsPreference> getPreferenceType() {
        return ActionButtonsPreference.class;
    }

    @Override
    protected void updateState(ActionButtonsPreference preference) {
        getPreference()
                .getButton(ActionButtons.BUTTON1)
                .setText(R.string.forget)
                .setIcon(R.drawable.ic_delete)
                .setVisible(canForgetNetwork())
                .setOnClickListener(v -> {
                    getWifiEntry().forget(/* callback= */ null);
                    getFragmentController().goBack();
                });

        boolean canConnectOrDisconnect = getWifiEntry().canConnect()
                || getWifiEntry().canDisconnect();
        preference
                .getButton(ActionButtons.BUTTON2)
                .setVisible(canConnectOrDisconnect || getWifiEntry().getConnectedState()
                        == WifiEntry.CONNECTED_STATE_CONNECTING)
                .setEnabled(canConnectOrDisconnect)
                .setText(getConnectDisconnectButtonTextResource())
                .setIcon(getConnectDisconnectButtonIconResource())
                .setOnClickListener(v -> connectOrDisconnect());
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (!WifiUtil.isWifiAvailable(getContext())) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return AVAILABLE;
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
        refreshUi();
    }

    @Override
    public void onConnectResult(@WifiEntry.ConnectCallback.ConnectStatus int status) {
        if (getWifiEntry().getLevel() == WifiEntry.WIFI_LEVEL_UNREACHABLE) {
            Toast.makeText(getContext(),
                    R.string.wifi_not_in_range_message,
                    Toast.LENGTH_SHORT).show();
        } else if (status != WifiEntry.ConnectCallback.CONNECT_STATUS_SUCCESS) {
            Toast.makeText(getContext(),
                    R.string.wifi_failed_connect_message,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean canForgetNetwork() {
        return getWifiEntry().canForget()
                && !WifiUtil.isNetworkLockedDown(getContext(),
                        getWifiEntry().getWifiConfiguration());
    }

    private void connectOrDisconnect() {
        if (!WifiUtil.isWifiEntryConnectedOrConnecting(getWifiEntry())) {
            getWifiEntry().connect(/* callback= */ this);
        } else {
            getWifiEntry().disconnect(/* callback= */ null);
        }
    }

    private int getConnectDisconnectButtonTextResource() {
        switch (getWifiEntry().getConnectedState()) {
            case WifiEntry.CONNECTED_STATE_DISCONNECTED:
                return R.string.wifi_setup_connect;
            case WifiEntry.CONNECTED_STATE_CONNECTED:
                return R.string.disconnect;
            case WifiEntry.CONNECTED_STATE_CONNECTING:
                return R.string.wifi_connecting;
            default:
                throw new IllegalStateException("Invalid WifiEntry connected state");
        }
    }

    private int getConnectDisconnectButtonIconResource() {
        switch (getWifiEntry().getConnectedState()) {
            case WifiEntry.CONNECTED_STATE_DISCONNECTED:
            case WifiEntry.CONNECTED_STATE_CONNECTING:
                return R.drawable.ic_settings_wifi;
            case WifiEntry.CONNECTED_STATE_CONNECTED:
                return R.drawable.ic_close;
            default:
                throw new IllegalStateException("Invalid WifiEntry connected state");
        }
    }
}
