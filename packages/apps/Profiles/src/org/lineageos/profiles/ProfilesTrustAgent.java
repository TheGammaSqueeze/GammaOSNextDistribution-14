/*
* Copyright (C) 2015 The CyanogenMod Project
*               2020 The LineageOS Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.lineageos.profiles;

import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.service.trust.TrustAgentService;
import android.util.ArraySet;
import android.util.Log;
import lineageos.app.Profile;
import lineageos.app.ProfileManager;
import lineageos.providers.LineageSettings;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;

/**
 * Profiles Trust Agent
 *
 * Watches for changes in the current {@link Profile} and grants or revokes trust (whether
 * lock screen security is enforced).
 */
public class ProfilesTrustAgent extends TrustAgentService {

    private static final String TAG = ProfilesTrustAgent.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int GRANT_DURATION_MS = 1000 * 60 * 5; // 5 minutes

    private static final int MSG_UPDATE_STATE = 100;
    private static final int MSG_ON_AGENT_CREATED = 101;
    private static final int MSG_ON_TRIGGER_STATE_CHANGED = 102;
    private static final int MSG_ON_USER_SWITCH = 103;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ProfileManager.INTENT_ACTION_PROFILE_TRIGGER_STATE_CHANGED.equals(action)) {
                mHandler.obtainMessage(MSG_ON_TRIGGER_STATE_CHANGED,
                        intent.getExtras()).sendToTarget();
            } else if (Intent.ACTION_USER_FOREGROUND.equals(action)) {
                mHandler.sendEmptyMessage(MSG_ON_USER_SWITCH);
            } else {
                mHandler.sendEmptyMessage(MSG_UPDATE_STATE);
            }
        }
    };

    private ProfileManager mProfileManager;
    private ProfileHandler mHandler;
    private SystemProfilesSettingsObserver mObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        if (UserHandle.myUserId() == UserHandle.USER_OWNER) {
            mProfileManager = ProfileManager.getInstance(this);
            mHandler = new ProfileHandler(ProfilesTrustAgent.this);

            IntentFilter filter = new IntentFilter();
            filter.addAction(ProfileManager.INTENT_ACTION_PROFILE_SELECTED);
            filter.addAction(ProfileManager.INTENT_ACTION_PROFILE_UPDATED);
            filter.addAction(ProfileManager.INTENT_ACTION_PROFILE_TRIGGER_STATE_CHANGED);
            filter.addAction(Intent.ACTION_USER_FOREGROUND);

            registerReceiver(mReceiver, filter);
            setManagingTrust(true);
            mObserver = new SystemProfilesSettingsObserver(mHandler);
            getContentResolver().registerContentObserver(
                    LineageSettings.System.getUriFor(LineageSettings.System.SYSTEM_PROFILES_ENABLED),
                    false, mObserver);
            mHandler.sendEmptyMessage(MSG_ON_AGENT_CREATED);
        }
    }

    @Override
    public void onDestroy() {
        if (UserHandle.myUserId() == UserHandle.USER_OWNER) {
            mHandler = null;
            mProfileManager = null;
            setManagingTrust(false);
            unregisterReceiver(mReceiver);
            getContentResolver().unregisterContentObserver(mObserver);
        }
        super.onDestroy();
    }

    @Override
    public void onTrustTimeout() {
        if (UserHandle.myUserId() == UserHandle.USER_OWNER) {
            mHandler.sendEmptyMessage(MSG_UPDATE_STATE);
        }
    }

    private void handleApplyCurrentProfileState() {
        final DevicePolicyManager devicePolicyManager =
                getSystemService(DevicePolicyManager.class);
        if (devicePolicyManager != null && devicePolicyManager.requireSecureKeyguard()) {
            revokeTrust();
            return;
        }

        Profile p = mProfileManager.getActiveProfile();
        int lockscreenState = p != null ? p.getScreenLockMode().getValue()
                : Profile.LockMode.DEFAULT;
        switch (lockscreenState) {
            case Profile.LockMode.DISABLE:
            case Profile.LockMode.DEFAULT:
                if (DEBUG) Log.w(TAG, "revoking trust.");
                revokeTrust();
                break;
            case Profile.LockMode.INSECURE:
                if (DEBUG) Log.w(TAG, "granting trust for profile " + p.getName());
                grantTrust(getString(R.string.trust_by_profile), GRANT_DURATION_MS, false);
                break;
        }
    }

    private boolean shouldGrantTrustOnTriggerStateChanged(String triggerId, int triggerType,
            int triggerState) {
        final Profile activeProfile = mProfileManager.getActiveProfile();
        if (activeProfile != null) {
            final int lockMode = activeProfile.getScreenLockMode().getValue();
            if (lockMode == Profile.LockMode.INSECURE) {
                final List<Profile.ProfileTrigger> wifiTriggers
                        = activeProfile.getTriggersFromType(Profile.TriggerType.WIFI);
                final List<Profile.ProfileTrigger> btTriggers
                        = activeProfile.getTriggersFromType(Profile.TriggerType.BLUETOOTH);
                Set<String> onWiFiConnect = new ArraySet<>();
                Set<String> onBTConnect = new ArraySet<>();

                for (Profile.ProfileTrigger trigger : wifiTriggers) {
                    if (trigger.getState() == Profile.TriggerState.ON_CONNECT) {
                        onWiFiConnect.add(trigger.getId());
                    }
                }
                for (Profile.ProfileTrigger trigger : btTriggers) {
                    if (trigger.getState() == Profile.TriggerState.ON_CONNECT) {
                        onBTConnect.add(trigger.getId());
                    }
                }

                if (triggerState == Profile.TriggerState.ON_DISCONNECT) {
                    if (triggerType == Profile.TriggerType.BLUETOOTH
                            && onWiFiConnect.contains(getActiveSSID())) {
                        return true;
                    }

                    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                    Set<String> connectedBTDevices = new ArraySet<>();
                    for (BluetoothDevice device : pairedDevices) {
                        if (device.isConnected()) connectedBTDevices.add(device.getAddress());
                    }
                    for (Profile.ProfileTrigger btTrigger : btTriggers) {
                        if (connectedBTDevices.contains(btTrigger.getId())
                                && btTrigger.getState() == Profile.TriggerState.ON_CONNECT) {
                            return true;
                        }
                    }
                } else if (triggerState == Profile.TriggerState.ON_CONNECT
                        && (onWiFiConnect.contains(triggerId) || onBTConnect.contains(triggerId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldGrantTrustOnCreate() {
        final Profile activeProfile = mProfileManager.getActiveProfile();
        if (activeProfile != null) {
            final int lockMode = activeProfile.getScreenLockMode().getValue();
            if (lockMode == Profile.LockMode.INSECURE) {
                final String ssid = getActiveSSID();
                for (Profile.ProfileTrigger trigger
                        : activeProfile.getTriggersFromType(Profile.TriggerType.WIFI)) {
                    if (trigger.getId().equals(ssid)
                            && trigger.getState() == Profile.TriggerState.ON_CONNECT) {
                        return true;
                    }
                }
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                Set<String> connectedBTDevices = new ArraySet<>();
                for (BluetoothDevice device : pairedDevices) {
                    if (device.isConnected()) connectedBTDevices.add(device.getAddress());
                }
                for (Profile.ProfileTrigger trigger
                        : activeProfile.getTriggersFromType(Profile.TriggerType.BLUETOOTH)) {
                    if (connectedBTDevices.contains(trigger.getId())
                            && trigger.getState() == Profile.TriggerState.ON_CONNECT) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String removeDoubleQuotes(String string) {
        final int length = string.length();
        if (length >= 2) {
            if (string.startsWith("\"") && string.endsWith("\"")) {
                return string.substring(1, length - 1);
            }
        }
        return string;
    }

    private String getActiveSSID() {
        final WifiManager wifiManager = getSystemService(WifiManager.class);
        WifiInfo wifiinfo = wifiManager.getConnectionInfo();
        if (wifiinfo == null) {
            return null;
        }
        return removeDoubleQuotes(wifiinfo.getSSID());
    }

    private void onTrustAgentCreated() {
        // Check if we connected to a tracking WiFi network/BT device before this agent was created.
        // The agent is created AFTER the user authenticates at boot or when a new lock screen
        // (PIN, pattern, etc) is set
        final Profile p = mProfileManager.getActiveProfile();
        if (p != null && shouldGrantTrustOnCreate()) {
            if (DEBUG) Log.w(TAG, "granting trust for profile " + p.getName());
            grantTrust(getString(R.string.trust_by_profile), GRANT_DURATION_MS, false);
        } else {
            if (DEBUG) Log.w(TAG, "revoking trust.");
            revokeTrust();
        }
    }

    private void onTriggerStateChanged(String triggerId, int triggerType, int triggerState) {
        final Profile p = mProfileManager.getActiveProfile();
        if (p != null
                && shouldGrantTrustOnTriggerStateChanged(triggerId, triggerType, triggerState)) {
            if (DEBUG) Log.w(TAG, "granting trust for profile " + p.getName());
            grantTrust(getString(R.string.trust_by_profile), GRANT_DURATION_MS, false);
        } else {
            if (DEBUG) Log.w(TAG, "revoking trust.");
            revokeTrust();
        }
    }

    private static class ProfileHandler extends Handler {
        private final WeakReference<ProfilesTrustAgent> mService;

        private ProfileHandler(ProfilesTrustAgent service) {
            this.mService = new WeakReference<ProfilesTrustAgent>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_STATE: {
                    ProfilesTrustAgent service = mService.get();
                    if (service != null) {
                        service.handleApplyCurrentProfileState();
                    }
                    break;
                }
                case MSG_ON_USER_SWITCH:
                case MSG_ON_AGENT_CREATED: {
                    ProfilesTrustAgent service = mService.get();
                    if (service != null) {
                        service.onTrustAgentCreated();
                    }
                    break;
                }
                case MSG_ON_TRIGGER_STATE_CHANGED: {
                    ProfilesTrustAgent service = mService.get();
                    if (service != null && msg.obj != null) {
                        Bundle bundle = (Bundle) msg.obj;
                        final String id = bundle.getString(ProfileManager.EXTRA_TRIGGER_ID);
                        final int type = bundle.getInt(ProfileManager.EXTRA_TRIGGER_TYPE);
                        final int state = bundle.getInt(ProfileManager.EXTRA_TRIGGER_STATE);
                        service.onTriggerStateChanged(id, type, state);
                    }
                    break;
                }
            }
        }
    }

    private class SystemProfilesSettingsObserver extends ContentObserver {

        public SystemProfilesSettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (LineageSettings.System.getUriFor(LineageSettings.System.SYSTEM_PROFILES_ENABLED)
                    .compareTo(uri) == 0) {
                mHandler.sendEmptyMessage(MSG_UPDATE_STATE);
            }
        }
    }
}
