/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.wifi.details.WifiDetailsFragment;
import com.android.wifitrackerlib.WifiEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a list of {@link WifiEntry} as a list of preferences.
 */
public class WifiEntryListPreferenceController extends
        WifiBasePreferenceController<PreferenceGroup> implements
        CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
    private static final Logger LOG = new Logger(WifiEntryListPreferenceController.class);
    private final WifiManager.ActionListener mConnectionListener =
            new WifiManager.ActionListener() {
                @Override
                public void onSuccess() {
                    LOG.d("connected to network");
                }

                @Override
                public void onFailure(int reason) {
                    LOG.d("Failed to connect to network. Failure code: " + reason);
                    Toast.makeText(getContext(), R.string.wifi_failed_connect_message,
                            Toast.LENGTH_SHORT).show();
                }
            };

    @VisibleForTesting
    final WifiPasswordDialog.WifiDialogListener mDialogListener =
            new WifiPasswordDialog.WifiDialogListener() {
                @Override
                public void onSubmit(WifiPasswordDialog dialog) {
                    WifiConfiguration config = dialog.getConfig();
                    WifiEntry wifiEntry = dialog.getWifiEntry();
                    if (config == null) {
                        wifiEntry.connect(
                                new WifiEntryConnectCallback(wifiEntry,
                                        /* editIfNoConfig= */ false));
                    } else {
                        getWifiManager().connect(config, mConnectionListener);
                    }
                }
            };

    private List<WifiEntry> mWifiEntries = new ArrayList<>();

    public WifiEntryListPreferenceController(@NonNull Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        if (getCarWifiManager() == null) {
            return;
        }
        mWifiEntries = fetchWifiEntries();

        LOG.d("showing wifiEntries: " + mWifiEntries.size());

        preferenceGroup.setVisible(!mWifiEntries.isEmpty());
        preferenceGroup.removeAll();

        List<WifiEntry> connectedWifiEntries = getCarWifiManager().getConnectedWifiEntries();
        for (int i = 0; i < mWifiEntries.size(); i++) {
            WifiEntry wifiEntry = mWifiEntries.get(i);

            // fetchWifiEntries() sort the connected networks to the front
            if (i < connectedWifiEntries.size()) {
                preferenceGroup.addPreference(
                        createWifiEntryPreference(wifiEntry, /* connected= */  true));
            } else {
                preferenceGroup.addPreference(
                        createWifiEntryPreference(wifiEntry, /* connected= */ false));
            }
        }
    }

    @Override
    protected void onApplyUxRestrictions(CarUxRestrictions uxRestrictions) {
        // Since the list dynamically changes based on the UX restrictions, we enable this fragment
        // regardless of the restriction. Intentional no-op.
    }

    @Override
    public void onWifiEntriesChanged() {
        refreshUi();
    }

    @Override
    public void onWifiStateChanged(int state) {
        if (state == WifiManager.WIFI_STATE_ENABLED) {
            refreshUi();
        }
    }

    /**
     * Get all {@link WifiEntry} that should be displayed as a list.
     * @return List of wifi entries that should be displayed
     */
    protected List<WifiEntry> fetchWifiEntries() {
        List<WifiEntry> wifiEntries;
        // Do not populate the entries when DISALLOW_CONFIG_WIFI restriction is set.
        Context context = getContext();
        if (WifiUtil.isConfigWifiRestrictedByUm(context)
                || WifiUtil.isConfigWifiRestrictedByDpm(context)) {
            wifiEntries = new ArrayList<>();
        } else if (shouldApplyUxRestrictions(getUxRestrictions())) {
            wifiEntries = getCarWifiManager().getSavedWifiEntries();
        } else {
            wifiEntries = getCarWifiManager().getAllWifiEntries();
        }

        List<WifiEntry> connectedWifiEntries = getCarWifiManager().getConnectedWifiEntries();
        // Insert current connected networks as first items, if available
        wifiEntries.addAll(0, connectedWifiEntries);

        return wifiEntries;
    }

    @VisibleForTesting
    WifiManager getWifiManager() {
        return getContext().getSystemService(WifiManager.class);
    }

    private WifiEntryPreference createWifiEntryPreference(WifiEntry wifiEntry, boolean connected) {
        LOG.d("Adding preference for " + WifiUtil.getKey(wifiEntry));
        WifiEntryPreference wifiEntryPreference = new WifiEntryPreference(getContext(), wifiEntry);

        // Make connected secondary networks unclickable
        if (!connected || wifiEntry.isPrimaryNetwork()) {
            wifiEntryPreference.setOnPreferenceClickListener(pref -> {
                if (connected) {
                    if (wifiEntry.canSignIn()) {
                        wifiEntry.signIn(/* callback= */ null);
                    } else {
                        getFragmentController().launchFragment(
                                WifiDetailsFragment.getInstance(wifiEntry));
                    }
                } else if (wifiEntry.shouldEditBeforeConnect()) {
                    getFragmentController().showDialog(
                            new WifiPasswordDialog(wifiEntry, mDialogListener),
                            WifiPasswordDialog.TAG);
                } else {
                    wifiEntry.connect(
                            new WifiEntryConnectCallback(wifiEntry, /* editIfNoConfig= */ true));
                }
                return true;
            });
        }

        if (wifiEntry.isSaved()) {
            wifiEntryPreference.setSecondaryActionIcon(R.drawable.ic_delete);
            wifiEntryPreference.setOnSecondaryActionClickListener(
                    () -> wifiEntry.forget(/* callback= */ null));
            wifiEntryPreference.setSecondaryActionVisible(true);
        }

        // Since this preference is dynamically created, it doesn't have the dpm behaviors set
        wifiEntryPreference.setEnabled(getPreference().isEnabled());
        wifiEntryPreference.setClickableWhileDisabled(true);
        wifiEntryPreference.setDisabledClickListener(p ->
                WifiUtil.runClickableWhileDisabled(getContext(), getFragmentController()));

        return wifiEntryPreference;
    }

    private class WifiEntryConnectCallback implements WifiEntry.ConnectCallback {
        final WifiEntry mConnectWifiEntry;
        final boolean mEditIfNoConfig;

        WifiEntryConnectCallback(WifiEntry connectWifiEntry, boolean editIfNoConfig) {
            mConnectWifiEntry = connectWifiEntry;
            mEditIfNoConfig = editIfNoConfig;
        }

        @Override
        public void onConnectResult(@ConnectStatus int status) {
            if (!isStarted()) {
                return;
            }

            if (status == WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_NO_CONFIG) {
                if (mEditIfNoConfig) {
                    getFragmentController().showDialog(
                            new WifiPasswordDialog(mConnectWifiEntry, mDialogListener),
                            WifiPasswordDialog.TAG);
                }
            } else if (status == CONNECT_STATUS_FAILURE_UNKNOWN) {
                Toast.makeText(getContext(), R.string.wifi_failed_connect_message,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
