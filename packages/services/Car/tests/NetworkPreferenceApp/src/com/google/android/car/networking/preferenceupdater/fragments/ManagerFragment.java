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
package com.google.android.car.networking.preferenceupdater.fragments;

import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkRequest;
import android.net.NetworkTemplate;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.car.networking.preferenceupdater.R;
import com.google.android.car.networking.preferenceupdater.components.CarDriverDistractionManagerAdapter;
import com.google.android.car.networking.preferenceupdater.components.MetricDisplay;
import com.google.android.car.networking.preferenceupdater.components.OemNetworkPreferencesAdapter;
import com.google.android.car.networking.preferenceupdater.components.PersonalStorage;
import com.google.android.car.networking.preferenceupdater.utils.Utils;

import java.util.ArrayList;
import java.util.Set;

public final class ManagerFragment extends Fragment {
    private static final String TAG = ManagerFragment.class.getSimpleName();

    public static final String METRIC_MSG_OEM_PREFERENCE_KEY = "oem_preference";
    public static final String METRIC_MSG_OEM_PREFERENCE_RX_KEY = "oem_preference_rx";
    public static final String METRIC_MSG_OEM_PREFERENCE_TX_KEY = "oem_preference_tx";

    private PersonalStorage mPersonalStorage;
    private OemNetworkPreferencesAdapter mOemNetworkPreferencesAdapter;
    private CarDriverDistractionManagerAdapter mCarDriverDistractionManagerAdapter;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    // Metric Display components
    private MetricDisplay mMetricDisplay;
    private TextView mOemPaidRxBytesTextView;
    private TextView mOemPaidTxBytesTextView;
    private TextView mOemPrivateRxBytesTextView;
    private TextView mOemPrivateTxBytesTextView;
    private TextView mOemTotalRxBytesTextView;
    private TextView mOemTotalTxBytesTextView;
    // Metric display handler that updates indicators
    public final Handler mMetricMessageHandler =
            new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    Bundle bundle = msg.getData();
                    int oem_preference = bundle.getInt(METRIC_MSG_OEM_PREFERENCE_KEY);
                    long rx = bundle.getLong(METRIC_MSG_OEM_PREFERENCE_RX_KEY);
                    long tx = bundle.getLong(METRIC_MSG_OEM_PREFERENCE_TX_KEY);
                    updateMetricIndicatorByType(oem_preference, rx, tx);
                }
            };

    private EditText mOEMPaidAppsEditText;
    private EditText mOEMPaidNoFallbackAppsEditText;
    private EditText mOEMPaidOnlyAppsEditText;
    private EditText mOEMPrivateOnlyAppsEditText;
    private TextView mCurrentPANSStatusTextView;
    private Switch mReapplyPANSOnBootSwitch;
    private Switch mReapplyWifiSuggestionsOnBootSwitch;
    private Button mApplyConfigurationBtn;
    private Button mResetNetworkPreferencesBtn;
    private Button mApplyWifiCapabilitiesBtn;
    private Button mResetWifiCapabilitiesBtn;
    private Switch mConnectToOemPaidWifiSwitch;
    private NetworkCallback mConnectToOemPaidWifiSwitchNC;
    private Switch mConnectToOemPrivateWifiSwitch;
    private NetworkCallback mConnectToOemPrivateWifiSwitchNC;

    // Wifi SSIDs
    private EditText mOEMPaidWifiSSIDsEditText;
    private EditText mOEMPrivateWifiSSIDsEditText;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.manager, container, false);
        Context context = getActivity();

        mPersonalStorage = new PersonalStorage(context);
        mOemNetworkPreferencesAdapter = new OemNetworkPreferencesAdapter(context);
        mCarDriverDistractionManagerAdapter = new CarDriverDistractionManagerAdapter(context);
        mMetricDisplay = new MetricDisplay(context, mMetricMessageHandler);

        defineViewsFromFragment(v);
        defineButtonActions();
        setDefaultValues();

        // Check if PANS reapply logic is enabled. If yes, most likely PANS is in effect
        updatePansPolicyInEffectStatus(mPersonalStorage.getReapplyPansOnBootCompleteState());

        // Let's start watching OEM traffic and updating indicators
        mMetricDisplay.startWatching();

        mWifiManager = context.getSystemService(WifiManager.class);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mMetricDisplay.stopWatching();
        mCarDriverDistractionManagerAdapter.destroy();
    }

    /** Finds all views on the fragments and stores them in instance variables */
    private void defineViewsFromFragment(View v) {
        mOEMPaidAppsEditText = v.findViewById(R.id.OEMPaidAppsEditText);
        mOEMPaidNoFallbackAppsEditText = v.findViewById(R.id.OEMPaidNoFallbackAppsEditText);
        mOEMPaidOnlyAppsEditText = v.findViewById(R.id.OEMPaidOnlyAppsEditText);
        mOEMPrivateOnlyAppsEditText = v.findViewById(R.id.OEMPrivateOnlyAppsEditText);
        mOEMPaidWifiSSIDsEditText = v.findViewById(R.id.OEMPaidWifiSSIDsEditText);
        mOEMPrivateWifiSSIDsEditText = v.findViewById(R.id.OEMPrivateWifiSSIDsEditText);
        mCurrentPANSStatusTextView = v.findViewById(R.id.currentPANSStatusTextView);
        mReapplyPANSOnBootSwitch = v.findViewById(R.id.reapplyPANSOnBootSwitch);
        mReapplyWifiSuggestionsOnBootSwitch = v.findViewById(
                R.id.reapplyWifiSuggestionsOnBootSwitch);
        mApplyConfigurationBtn = v.findViewById(R.id.applyConfigurationBtn);
        mResetNetworkPreferencesBtn = v.findViewById(R.id.resetNetworkPreferencesBtn);
        mApplyWifiCapabilitiesBtn = v.findViewById(R.id.applyWifiCapabilitiesButton);
        mResetWifiCapabilitiesBtn = v.findViewById(R.id.resetWifiCapabilitiesButton);
        mConnectToOemPaidWifiSwitch = v.findViewById(R.id.connectToOemPaidWifiSwitch);
        mConnectToOemPrivateWifiSwitch = v.findViewById(R.id.connectToOemPrivateWifiSwitch);
        // Since our Metric Display is going to be alive, we want to pass our TextView components
        // into MetricDisplay instance to simplify refresh logic.
        mOemPaidRxBytesTextView = v.findViewById(R.id.oemPaidRxBytesTextView);
        mOemPaidTxBytesTextView = v.findViewById(R.id.oemPaidTxBytesTextView);
        mOemPrivateRxBytesTextView = v.findViewById(R.id.oemPrivateRxBytesTextView);
        mOemPrivateTxBytesTextView = v.findViewById(R.id.oemPrivateTxBytesTextView);
        mOemTotalRxBytesTextView = v.findViewById(R.id.totalOemManagedRxBytesTextView);
        mOemTotalTxBytesTextView = v.findViewById(R.id.totalOemManagedTxBytesTextView);
        mMetricDisplay.setMainActivity(this);
    }

    private void updateMetricIndicatorByType(int type, long rx, long tx) {
        switch (type) {
            case NetworkTemplate.OEM_MANAGED_PAID:
                mOemPaidRxBytesTextView.setText("RX: " + rx);
                mOemPaidTxBytesTextView.setText("TX: " + tx);
                break;
            case NetworkTemplate.OEM_MANAGED_PRIVATE:
                mOemPrivateRxBytesTextView.setText("RX: " + rx);
                mOemPrivateTxBytesTextView.setText("TX: " + tx);
                break;
            case NetworkTemplate.OEM_MANAGED_YES:
                mOemTotalRxBytesTextView.setText("RX: " + rx);
                mOemTotalTxBytesTextView.setText("TX: " + tx);
                break;
            default:
                Log.e(TAG, "Unknown NetworkIdentity " + type);
        }
    }

    /** Defines actions of the buttons on the page */
    private void defineButtonActions() {
        mApplyConfigurationBtn.setOnClickListener(view -> onApplyConfigurationBtnClick());
        mApplyWifiCapabilitiesBtn.setOnClickListener(view -> onApplyWifiCapabilitiesBtnClick());
        mResetWifiCapabilitiesBtn.setOnClickListener(view -> onResetWifiCapabilitiesBtnClick());
        mReapplyPANSOnBootSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        mPersonalStorage.saveReapplyPansOnBootCompleteState(isChecked));
        mReapplyWifiSuggestionsOnBootSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        mPersonalStorage.saveReapplyWifiOnBootCompleteState(isChecked));
        mResetNetworkPreferencesBtn.setOnClickListener(view -> resetNetworkPreferences());

        mConnectToOemPaidWifiSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> updateNetworkRequestFor(true /*isOemPaid*/, isChecked));
        mConnectToOemPrivateWifiSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> updateNetworkRequestFor(false /*isOemPaid*/, isChecked));
    }

    private void updateNetworkRequestFor(boolean isOemPaid, boolean isChecked) {
        if (isChecked) {
            if (isOemPaid) {
                mConnectToOemPaidWifiSwitchNC = sendNetworkRequest(isOemPaid);
            } else {
                mConnectToOemPrivateWifiSwitchNC = sendNetworkRequest(isOemPaid);
            }
        } else {
            mConnectivityManager.unregisterNetworkCallback(
                    isOemPaid ? mConnectToOemPaidWifiSwitchNC : mConnectToOemPrivateWifiSwitchNC);
        }
    }

    private void onResetWifiCapabilitiesBtnClick() {
        mWifiManager.removeNetworkSuggestions(new ArrayList<>());
        mOEMPaidWifiSSIDsEditText.setText("");
        mOEMPrivateWifiSSIDsEditText.setText("");
        mPersonalStorage.storeWifi(null, null);
    }

    private NetworkCallback sendNetworkRequest(boolean isOemPaid) {
        NetworkCallback nc = new NetworkCallback();
        try {
            mConnectivityManager.requestNetwork(
                    new NetworkRequest.Builder()
                            .addTransportType(TRANSPORT_WIFI)
                            .addCapability(
                                    isOemPaid
                                            ? NET_CAPABILITY_OEM_PAID
                                            : NET_CAPABILITY_OEM_PRIVATE)
                            .build(),
                    nc);
            return nc;
        } catch (Exception ex) {
            Toast.makeText(getActivity(), ex.toString(), Toast.LENGTH_SHORT).show();
            String msg =
                    String.format(
                            "Attempt to connect to wifi with %s cabaility failed!",
                            isOemPaid ? "OEM_PAID" : "OEM_PRIVATE");
            Log.e(TAG, msg, ex);
        }
        return null;
    }

    private void resetNetworkPreferences() {
        mOemNetworkPreferencesAdapter.resetNetworkPreferences();
        mPersonalStorage.resetNetworkPreferences();
        setDefaultValues();
    }

    /** Sets default values of text fields */
    private void setDefaultValues() {
        mOEMPaidAppsEditText.setText(getFromStorage(OEM_NETWORK_PREFERENCE_OEM_PAID));
        mOEMPaidNoFallbackAppsEditText.setText(
                getFromStorage(OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK));
        mOEMPaidOnlyAppsEditText.setText(getFromStorage(OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY));
        mOEMPrivateOnlyAppsEditText.setText(
                getFromStorage(OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY));
        mReapplyPANSOnBootSwitch.setChecked(mPersonalStorage.getReapplyPansOnBootCompleteState());
        mOEMPaidWifiSSIDsEditText.setText(Utils.toString(mPersonalStorage.getOemPaidWifiSsids()));
        mOEMPrivateWifiSSIDsEditText.setText(
                Utils.toString(mPersonalStorage.getOemPrivateWifiSsids()));
    }

    private String getFromStorage(int type) {
        return Utils.toString(mPersonalStorage.get(type));
    }

    // TODO(178245727): This should be updated once ag/13587171 merged.
    private void updatePansPolicyInEffectStatus(boolean status) {
        // Whenever we apply PANS logic, we save it to the PersonalStorage. Meaning we can use
        // PersonalStorage as the check for having any policy set or not.
        mCurrentPANSStatusTextView.setText(status ? "Yes" : "No");
    }

    private void onApplyWifiCapabilitiesBtnClick() {
        Log.d(TAG, "Applying WiFi settings");
        Set<String> ssidsWithOemPaid = Utils.toSet(mOEMPaidWifiSSIDsEditText.getText().toString());
        Set<String> ssidsWithOemPrivate =
                Utils.toSet(mOEMPrivateWifiSSIDsEditText.getText().toString());
        try {
            ArrayList<WifiNetworkSuggestion> list = new ArrayList<>();
            for (String ssid : ssidsWithOemPaid) {
                list.add(Utils.buildWifiSuggestion(ssid, true));
            }

            for (String ssid : ssidsWithOemPrivate) {
                list.add(Utils.buildWifiSuggestion(ssid, false));
            }

            mWifiManager.removeNetworkSuggestions(new ArrayList<>());
            mWifiManager.addNetworkSuggestions(list);
        } catch (Exception e) {
            // Could not set Wifi capabilities, pop the toast and do nothing
            Log.e(TAG, "Failed to set Wifi capabilities", e);
            Toast.makeText(getActivity(), e.toString(), Toast.LENGTH_SHORT).show();
            return;
        }
        mPersonalStorage.storeWifi(ssidsWithOemPaid, ssidsWithOemPrivate);
    }

    private void onApplyConfigurationBtnClick() {
        Log.d(TAG, "Applying PANS");
        // First we want to make sure that we are allowed to change
        if (!mCarDriverDistractionManagerAdapter.allowedToBeDistracted()) {
            // We are not allowed to apply PANS changes. Do nothing.
            Log.w(TAG, "Network preferences will not be updated, due to Driver Distraction Mode");
            return;
        }
        SparseArray<Set<String>> preference = new SparseArray<>();
        preference.put(
                OEM_NETWORK_PREFERENCE_OEM_PAID,
                Utils.toSet(mOEMPaidAppsEditText.getText().toString()));
        preference.put(
                OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK,
                Utils.toSet(mOEMPaidNoFallbackAppsEditText.getText().toString()));
        preference.put(
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY,
                Utils.toSet(mOEMPaidOnlyAppsEditText.getText().toString()));
        preference.put(
                OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY,
                Utils.toSet(mOEMPrivateOnlyAppsEditText.getText().toString()));

        mOemNetworkPreferencesAdapter.applyPreference(preference);

        // Persist latest preference
        mPersonalStorage.store(preference);

        // Notify app that PANS policy is now in effect
        updatePansPolicyInEffectStatus(true);
    }
}
