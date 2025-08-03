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

package com.android.tv.settings.system.development;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.debug.AdbManager;
import android.debug.FingerprintAndPairDevice;
import android.debug.IAdbManager;
import android.debug.PairDevice;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.GuidedActionsStylist;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Fragment shown when clicking in the "Wireless Debugging" preference in
 * the developer options.
 */
@Keep
public class WirelessDebuggingFragment extends SettingsPreferenceFragment {
    private static final String TAG = "WirelessDebuggingFrag";

    private static final String PREF_KEY_ADB_WIRELESS_SELECTION_OPTION =
            "adb_wireless_selection_option";
    private static final String PREF_KEY_ADB_WIRELESS_SELECTION_DISABLE =
            "adb_wireless_selection_disable";
    private static final String PREF_KEY_ADB_WIRELESS_SELECTION_ENABLE =
            "adb_wireless_selection_enable";
    private static final String PREF_KEY_ADB_CODE_PAIRING = "adb_pair_method_code_pref";
    private static final String PREF_KEY_ADB_DEVICE_NAME = "adb_device_name_pref";
    private static final String PREF_KEY_ADB_IP_ADDR = "adb_ip_addr_pref";
    private static final String PREF_KEY_PAIRED_DEVICES_CATEGORY = "adb_paired_devices_category";

    private IAdbManager mAdbManager;
    private ContentObserver mToggleContentObserver;
    private ConnectivityManager mConnectivityManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private IntentFilter mIntentFilter;

    private PreferenceCategory mAdbWirelessSelectionOption;
    private RadioPreference mAdbWirelessSelectionDisable;
    private RadioPreference mAdbWirelessSelectionEnable;
    private Preference mCodePairingPreference;
    private Preference mDeviceNamePreference;
    private Preference mIpAddrPreference;
    private PreferenceCategory mPairedDevicesCategory;

    private final WifiNetworkCallback mWifiNetworkCallback = new WifiNetworkCallback();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AdbManager.WIRELESS_DEBUG_PAIRED_DEVICES_ACTION.equals(action)) {
                Map<String, PairDevice> newPairedDevicesList =
                        (HashMap<String, PairDevice>) intent.getSerializableExtra(
                                AdbManager.WIRELESS_DEVICES_EXTRA);
                updatePairedDevicePreferences(newPairedDevicesList);
            } else if (AdbManager.WIRELESS_DEBUG_STATE_CHANGED_ACTION.equals(action)) {
                int status = intent.getIntExtra(AdbManager.WIRELESS_STATUS_EXTRA,
                        AdbManager.WIRELESS_STATUS_DISCONNECTED);
                if (status == AdbManager.WIRELESS_STATUS_CONNECTED
                        || status == AdbManager.WIRELESS_STATUS_DISCONNECTED) {
                    updateAdbIpAddressPreference();
                }
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(getPreferenceScreenResId(), null);

        addPreferences();
        showBlankPreferences();

        mAdbManager = IAdbManager.Stub.asInterface(ServiceManager.getService(
                Context.ADB_SERVICE));
        mToggleContentObserver = new ContentObserver(new Handler(Looper.myLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updatePreferenceState();
            }
        };

        mConnectivityManager = getContext().getSystemService(ConnectivityManager.class);

        mIntentFilter = new IntentFilter(AdbManager.WIRELESS_DEBUG_PAIRED_DEVICES_ACTION);
        mIntentFilter.addAction(AdbManager.WIRELESS_DEBUG_STATE_CHANGED_ACTION);

        initAdbWirelessSelectionOptionPreference();
    }

    @Override
    public void onResume() {
        super.onResume();
        getContext().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ADB_WIFI_ENABLED),
                false,
                mToggleContentObserver);
        mConnectivityManager.registerNetworkCallback(
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                        .build(),
                mWifiNetworkCallback);
        getActivity().registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        getContext().getContentResolver().unregisterContentObserver(mToggleContentObserver);
        mConnectivityManager.unregisterNetworkCallback(mWifiNetworkCallback);
        getActivity().unregisterReceiver(mReceiver);
    }

    private int getPreferenceScreenResId() {
        return R.xml.adb_wireless_settings;
    }

    private void addPreferences() {
        mAdbWirelessSelectionOption = findPreference(PREF_KEY_ADB_WIRELESS_SELECTION_OPTION);
        mAdbWirelessSelectionDisable = findPreference(PREF_KEY_ADB_WIRELESS_SELECTION_DISABLE);
        mAdbWirelessSelectionEnable = findPreference(PREF_KEY_ADB_WIRELESS_SELECTION_ENABLE);
        mCodePairingPreference = findPreference(PREF_KEY_ADB_CODE_PAIRING);
        mDeviceNamePreference = findPreference(PREF_KEY_ADB_DEVICE_NAME);
        mIpAddrPreference = findPreference(PREF_KEY_ADB_IP_ADDR);
        mPairedDevicesCategory = findPreference(PREF_KEY_PAIRED_DEVICES_CATEGORY);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()) {
            case PREF_KEY_ADB_WIRELESS_SELECTION_ENABLE:
                setWirelessDebuggingRadioButtonEnabled(true);
                Settings.Global.putInt(getContext().getContentResolver(),
                        Settings.Global.ADB_WIFI_ENABLED,
                        1);
                break;
            case PREF_KEY_ADB_WIRELESS_SELECTION_DISABLE:
                setWirelessDebuggingRadioButtonEnabled(false);
                Settings.Global.putInt(getContext().getContentResolver(),
                        Settings.Global.ADB_WIFI_ENABLED,
                        0);
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void initAdbWirelessSelectionOptionPreference() {
        boolean enabled = Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.ADB_WIFI_ENABLED, 1) != 0;
        setWirelessDebuggingRadioButtonEnabled(enabled);
    }

    private void updatePreferenceState() {
        if (!isNetworkConnected()) {
            showBlankPreferences();
        } else {
            boolean enabled = Settings.Global.getInt(getContext().getContentResolver(),
                    Settings.Global.ADB_WIFI_ENABLED, 1) != 0;
            if (enabled) {
                showDebuggingPreferences();
                try {
                    FingerprintAndPairDevice[] newList = mAdbManager.getPairedDevices();
                    Map<String, PairDevice> newMap = new HashMap<>();
                    for (FingerprintAndPairDevice pair : newList) {
                        newMap.put(pair.keyFingerprint, pair.device);
                    }
                    updatePairedDevicePreferences(newMap);
                    int connectionPort = mAdbManager.getAdbWirelessPort();
                    if (connectionPort > 0) {
                        Log.i(TAG, "onEnabled(): connect_port=" + connectionPort);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to request the paired list for Adb wireless");
                }
                updateAdbIpAddressPreference();
            } else {
                showOffMessage();
            }
        }
    }

    private void showBlankPreferences() {
        if (mAdbWirelessSelectionOption != null) {
            mAdbWirelessSelectionOption.setVisible(false);
        }
        if (mCodePairingPreference != null) {
            mCodePairingPreference.setVisible(false);
        }
        if (mDeviceNamePreference != null) {
            mDeviceNamePreference.setVisible(false);
        }
        if (mIpAddrPreference != null) {
            mIpAddrPreference.setVisible(false);
        }
        if (mPairedDevicesCategory != null) {
            mPairedDevicesCategory.setVisible(false);
        }
    }

    private void showOffMessage() {
        setWirelessDebuggingRadioButtonEnabled(false);
        if (mAdbWirelessSelectionOption != null) {
            mAdbWirelessSelectionOption.setVisible(true);
        }
        if (mCodePairingPreference != null) {
            mCodePairingPreference.setVisible(false);
        }
        if (mDeviceNamePreference != null) {
            mDeviceNamePreference.setVisible(false);
        }
        if (mIpAddrPreference != null) {
            mIpAddrPreference.setVisible(false);
        }
        if (mPairedDevicesCategory != null) {
            mPairedDevicesCategory.setVisible(false);
        }
    }

    private void showDebuggingPreferences() {
        setWirelessDebuggingRadioButtonEnabled(true);
        if (mAdbWirelessSelectionOption != null) {
            mAdbWirelessSelectionOption.setVisible(true);
        }
        if (mCodePairingPreference != null) {
            mCodePairingPreference.setVisible(true);
        }
        if (mDeviceNamePreference != null) {
            mDeviceNamePreference.setSummary(getDeviceName());
            mDeviceNamePreference.setVisible(true);
        }
        if (mIpAddrPreference != null) {
            mIpAddrPreference.setVisible(true);
        }
        if (mPairedDevicesCategory != null) {
            mPairedDevicesCategory.setVisible(true);
        }
    }

    private void setWirelessDebuggingRadioButtonEnabled(boolean enabled) {
        if (mAdbWirelessSelectionEnable != null) {
            mAdbWirelessSelectionEnable.setChecked(enabled);
        }
        if (mAdbWirelessSelectionDisable != null) {
            mAdbWirelessSelectionDisable.setChecked(!enabled);
        }
    }

    private void updatePairedDevicePreferences(Map<String, PairDevice> newList) {
        if (newList == null) {
            mPairedDevicesCategory.removeAll();
            return;
        }
        for (int i = 0; i < mPairedDevicesCategory.getPreferenceCount(); i++) {
            AdbPairedDevicePreference p =
                    (AdbPairedDevicePreference) mPairedDevicesCategory.getPreference(i);
            // Remove any devices no longer on the newList
            if (!newList.containsKey(p.getKey())) {
                mPairedDevicesCategory.removePreference(p);
            } else {
                // It is in the newList. Just update the PairDevice value
                p.setPairedDevice(newList.get(p.getKey()));
                p.refresh();
            }
        }
        // Add new devices if any.
        for (Map.Entry<String, PairDevice> entry :
                newList.entrySet()) {
            if (mPairedDevicesCategory.findPreference(entry.getKey()) == null) {
                AdbPairedDevicePreference p =
                        new AdbPairedDevicePreference(entry.getValue(),
                                mPairedDevicesCategory.getContext());
                p.setKey(entry.getKey());
                mPairedDevicesCategory.addPreference(p);
            }
        }
    }

    private void updateAdbIpAddressPreference() {
        if (mIpAddrPreference != null) {
            String ipAddress = getIpAddressPort();
            mIpAddrPreference.setSummary(ipAddress);
        }
    }

    private String getDeviceName() {
        String deviceName = Settings.Global.getString(getContext().getContentResolver(),
                Settings.Global.DEVICE_NAME);
        if (deviceName == null) {
            deviceName = Build.MODEL;
        }
        return deviceName;
    }

    private boolean isNetworkConnected() {
        NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private String getIpAddressPort() {
        String ipAddress = getWifiIpv4Address();
        if (ipAddress != null) {
            int port = getAdbWirelessPort();
            if (port <= 0) {
                return getString(R.string.status_unavailable);
            } else {
                ipAddress += ":" + port;
            }
            return ipAddress;
        } else {
            return getString(R.string.status_unavailable);
        }
    }

    private int getAdbWirelessPort() {
        try {
            return mAdbManager.getAdbWirelessPort();
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to get the adb wifi port");
        }
        return 0;
    }

    private String getWifiIpv4Address() {
        LinkProperties prop = mConnectivityManager.getLinkProperties(
                mConnectivityManager.getActiveNetwork());
        return formatIpAddresses(prop);
    }

    private static String formatIpAddresses(LinkProperties prop) {
        if (prop == null) {
            return null;
        }

        Iterator<LinkAddress> iter = prop.getAllLinkAddresses().iterator();
        if (!iter.hasNext()) {
            return null;
        }

        StringBuilder addresses = new StringBuilder();
        while (iter.hasNext()) {
            InetAddress addr = iter.next().getAddress();
            if (addr instanceof Inet4Address) {
                addresses.append(addr.getHostAddress());
                break;
            }
        }
        return addresses.toString();
    }

    private class WifiNetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            mHandler.post(() -> updatePreferenceState());
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            mHandler.post(() -> updatePreferenceState());
        }
    }

    /**
     * Fragment for showing the pairing code and IP address & port to pair the device
     */
    @Keep
    public static class PairingCodeFragment extends GuidedStepSupportFragment {
        private String mPairingCode;
        private String mIpAddressPort;

        private ImageView mIconView;
        private TextView mPairingCodeTitleTextView;
        private TextView mPairingCodeTextView;
        private TextView mIpAddressPortTitleTextView;
        private TextView mIpAddressPortTextView;

        private IAdbManager mAdbManager;
        private IntentFilter mIntentFilter;
        private ConnectivityManager mConnectivityManager;

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION.equals(action)) {
                    Integer res = intent.getIntExtra(
                            AdbManager.WIRELESS_STATUS_EXTRA,
                            AdbManager.WIRELESS_STATUS_FAIL);

                    if (res.equals(AdbManager.WIRELESS_STATUS_PAIRING_CODE)) {
                        String pairingCode = intent.getStringExtra(
                                AdbManager.WIRELESS_PAIRING_CODE_EXTRA);
                        Log.d(TAG, "Received 6 digit pairing code: " + pairingCode);
                        mPairingCode = pairingCode;
                        refresh(mPairingCode, mIpAddressPort);
                    } else if (res.equals(AdbManager.WIRELESS_STATUS_CONNECTED)) {
                        int port = intent.getIntExtra(AdbManager.WIRELESS_DEBUG_PORT_EXTRA, 0);
                        Log.i(TAG, "Got pairing code port=" + port);
                        String ipAddr = getWifiIpv4Address() + ":" + port;
                        mIpAddressPort = ipAddr;
                        refresh(mPairingCode, mIpAddressPort);
                    }
                }
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mConnectivityManager = getContext().getSystemService(ConnectivityManager.class);
            mIntentFilter = new IntentFilter(AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION);
            mAdbManager = IAdbManager.Stub.asInterface(ServiceManager.getService(
                    Context.ADB_SERVICE));
        }

        @Override
        public void onResume() {
            super.onResume();
            getActivity().registerReceiver(mReceiver, mIntentFilter);
            try {
                mAdbManager.enablePairingByPairingCode();
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to enable pairing");
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            getActivity().unregisterReceiver(mReceiver);
            try {
                mAdbManager.disablePairing();
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to disable pairing");
            }
        }

        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            Drawable networkIcon;
            if (isWifiNetwork()) {
                networkIcon = getContext().getDrawable(R.drawable.ic_adb_wifi_132dp);
            } else {
                networkIcon = getContext().getDrawable(R.drawable.ic_adb_ethernet_132dp);
            }
            return new GuidanceStylist.Guidance(
                    getString(R.string.adb_pairing_device_dialog_title),
                    null,
                    null,
                    networkIcon
            );
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .build());
        }

        @Override
        public GuidanceStylist onCreateGuidanceStylist() {
            return new GuidanceStylist() {
                @Override
                public View onCreateView(LayoutInflater inflater, ViewGroup container,
                        Guidance guidance) {
                    View view = super.onCreateView(inflater, container, guidance);
                    mIconView = getIconView();
                    return view;
                }
            };
        }

        @Override
        public GuidedActionsStylist onCreateActionsStylist() {
            return new GuidedActionsStylist() {
                @Override
                public int onProvideItemLayoutId() {
                    return R.layout.pairing_code_guided_action;
                }

                @Override
                public ViewHolder onCreateViewHolder(ViewGroup parent) {
                    ViewHolder viewHolder = super.onCreateViewHolder(parent);
                    mPairingCodeTitleTextView =
                            viewHolder.itemView.findViewById(R.id.pairing_code_title);
                    mPairingCodeTextView =
                            viewHolder.itemView.findViewById(R.id.pairing_code);
                    mIpAddressPortTitleTextView =
                            viewHolder.itemView.findViewById(R.id.ip_address_port_title);
                    mIpAddressPortTextView =
                            viewHolder.itemView.findViewById(R.id.ip_address_port);

                    if (mPairingCodeTitleTextView != null) {
                        mPairingCodeTitleTextView.setText(isWifiNetwork()
                                ? R.string.adb_pairing_device_dialog_pairing_code_label
                                : R.string.adb_pairing_device_dialog_ethernet_pairing_code_label);
                    }
                    if (mPairingCodeTextView != null) {
                        mPairingCodeTextView.setText(mPairingCode);
                    }
                    if (mIpAddressPortTitleTextView != null) {
                        mIpAddressPortTitleTextView.setText(
                                R.string.adb_wireless_ip_addr_preference_title);
                    }
                    if (mIpAddressPortTextView != null) {
                        mIpAddressPortTextView.setText(mIpAddressPort);
                    }

                    return viewHolder;
                }
            };
        }

        private void refresh(String pairingCode, String ipAddressPort) {
            mPairingCode = pairingCode;
            mIpAddressPort = ipAddressPort;
            boolean isWifiNetwork = isWifiNetwork();

            if (mIconView != null) {
                mIconView.setImageDrawable(isWifiNetwork
                        ? getContext().getDrawable(R.drawable.ic_adb_wifi_132dp)
                        : getContext().getDrawable(R.drawable.ic_adb_ethernet_132dp));
            }

            if (mPairingCodeTitleTextView != null) {
                mPairingCodeTitleTextView.setText(isWifiNetwork
                        ? R.string.adb_pairing_device_dialog_pairing_code_label
                        : R.string.adb_pairing_device_dialog_ethernet_pairing_code_label);
            }
            if (mPairingCodeTextView != null) {
                mPairingCodeTextView.setText(mPairingCode);
            }
            if (mIpAddressPortTextView != null) {
                mIpAddressPortTextView.setText(mIpAddressPort);
            }
        }

        private boolean isWifiNetwork() {
            NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected()
                    && ConnectivityManager.TYPE_ETHERNET != activeNetworkInfo.getType();
        }

        private String getWifiIpv4Address() {
            LinkProperties prop = mConnectivityManager.getLinkProperties(
                    mConnectivityManager.getActiveNetwork());
            return formatIpAddresses(prop);
        }

        private static String formatIpAddresses(LinkProperties prop) {
            if (prop == null) {
                return null;
            }

            Iterator<LinkAddress> iter = prop.getAllLinkAddresses().iterator();
            if (!iter.hasNext()) {
                return null;
            }

            StringBuilder addresses = new StringBuilder();
            while (iter.hasNext()) {
                InetAddress addr = iter.next().getAddress();
                if (addr instanceof Inet4Address) {
                    addresses.append(addr.getHostAddress());
                    break;
                }
            }
            return addresses.toString();
        }
    }
}
