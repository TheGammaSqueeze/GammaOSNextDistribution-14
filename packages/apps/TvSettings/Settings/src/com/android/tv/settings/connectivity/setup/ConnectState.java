/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.connectivity.setup;

import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_AUTHENTICATION_FAILURE;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_AUTHENTICATION_NO_CREDENTIALS;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_ENABLED;
import static com.android.wifitrackerlib.WifiEntry.ConnectCallback.CONNECT_STATUS_SUCCESS;

import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.ConnectivityListener;
import com.android.tv.settings.connectivity.security.WifiSecurityHelper;
import com.android.tv.settings.connectivity.util.State;
import com.android.tv.settings.connectivity.util.StateMachine;
import com.android.tv.settings.library.network.AccessPoint;
import com.android.wifitrackerlib.WifiEntry;

import java.lang.ref.WeakReference;

/**
 * State responsible for showing the connect page.
 */
public class ConnectState implements State {
    private final FragmentActivity mActivity;
    private Fragment mFragment;

    public ConnectState(FragmentActivity wifiSetupActivity) {
        this.mActivity = wifiSetupActivity;
    }

    @Override
    public void processForward() {
        FragmentChangeListener listener = (FragmentChangeListener) mActivity;
        mFragment = ConnectToWifiFragment.newInstance("", true);
        if (listener != null) {
            listener.onFragmentChange(mFragment, true);
        }
    }

    @Override
    public void processBackward() {
        StateMachine stateMachine = ViewModelProviders.of(mActivity).get(StateMachine.class);
        stateMachine.back();
    }

    @Override
    public Fragment getFragment() {
        return mFragment;
    }

    /**
     * Connects to the wifi network specified by the given configuration.
     */
    public static class ConnectToWifiFragment extends MessageFragment
            implements ConnectivityListener.WifiNetworkListener {

        @VisibleForTesting
        static final int MSG_TIMEOUT = 1;
        @VisibleForTesting
        static final int CONNECTION_TIMEOUT = 60000;
        private static final String TAG = "ConnectToWifiFragment";
        private static final boolean DEBUG = false;
        @VisibleForTesting
        StateMachine mStateMachine;
        @VisibleForTesting
        WifiConfiguration mWifiConfiguration;
        @VisibleForTesting
        WifiManager mWifiManager;
        @VisibleForTesting
        Handler mHandler;
        private ConnectivityListener mConnectivityListener;
        private ConnectivityManager mConnectivityManager;
        private UserChoiceInfo mUserChoiceInfo;
        private boolean mStartedConnect;
        private boolean mConnectSucceeded;
        private boolean mStartedSave;
        private boolean mSaveComplete;

        /**
         * Obtain a new instance of ConnectToWifiFragment.
         *
         * @param title                 title of fragment.
         * @param showProgressIndicator whether show progress indicator.
         * @return new instance.
         */
        public static ConnectToWifiFragment newInstance(String title,
                                                        boolean showProgressIndicator) {
            ConnectToWifiFragment fragment = new ConnectToWifiFragment();
            Bundle args = new Bundle();
            addArguments(args, title, showProgressIndicator);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mUserChoiceInfo = ViewModelProviders.of(getActivity()).get(UserChoiceInfo.class);
            mConnectivityListener = new ConnectivityListener(getActivity(), null,
                    getLifecycle());
            mConnectivityManager = (ConnectivityManager) getActivity().getSystemService(
                    Context.CONNECTIVITY_SERVICE);

            mUserChoiceInfo = ViewModelProviders.of(getActivity()).get(UserChoiceInfo.class);
            mWifiConfiguration = WifiSecurityHelper.getConfig(getActivity());

            mStateMachine = ViewModelProviders
                    .of(getActivity()).get(StateMachine.class);

            mWifiManager = ((WifiManager) getActivity().getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE));
            mHandler = new MessageHandler(this);
            mConnectivityListener.setWifiListener(this);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
            View view = super.onCreateView(inflater, container, icicle);
            ((TextView) view.findViewById(R.id.status_text)).setText(
                    getContext().getString(R.string.wifi_connecting,
                            WifiSecurityHelper.getSsid(getActivity())));
            mSaveComplete = !needsSave();
            proceedDependOnNetworkState();
            return view;
        }

        @Override
        public void onResume() {
            super.onResume();
            postTimeout();
        }

        @Override
        public void onPause() {
            super.onPause();
            mHandler.removeMessages(MSG_TIMEOUT);
        }

        @VisibleForTesting
        void proceedDependOnNetworkState() {
            if (mStartedConnect) {
                return;
            }

            int easyConnectNetworkId = mUserChoiceInfo.getEasyConnectNetworkId();
            if (easyConnectNetworkId != -1) {
                if (DEBUG) Log.d(TAG, "Starting to connect via EasyConnect");
                WifiEntry wifiEntry = getEntryForNetworkId(easyConnectNetworkId);
                if (wifiEntry != null) {
                    startConnect(wifiEntry);
                }
            } else if (!mSaveComplete) {
                startSave();
            } else {
                WifiEntry wifiEntry = getEntryForConfiguration();
                if (wifiEntry != null) {
                    startConnect(wifiEntry);
                }
            }
        }

        @Override
        public void onDestroy() {
            if (!mConnectSucceeded) {
                mWifiManager.disconnect();
            }

            super.onDestroy();
        }

        @Override
        public void onWifiListChanged() {
            proceedDependOnNetworkState();
        }

        @Nullable
        private WifiEntry getEntryForNetworkId(int networkId) {
            WifiEntry result = null;
            for (AccessPoint accessPoint : mConnectivityListener.getAvailableNetworks()) {
                if (accessPoint.getConfig() != null
                        && accessPoint.getConfig().networkId == networkId) {
                    result = accessPoint.getWifiEntry();
                } else if (accessPoint.getWifiEntry().canDisconnect()) {
                    accessPoint.getWifiEntry().disconnect(null);
                }
            }
            return result;
        }

        @Nullable
        private WifiEntry getEntryForConfiguration() {
            WifiEntry result = null;
            for (AccessPoint accessPoint : mConnectivityListener.getAvailableNetworks()) {
                if (AccessPoint.convertToQuotedString(accessPoint.getSsidStr())
                        .equals(mWifiConfiguration.SSID)
                        && accessPoint.getWifiEntry().getSecurityTypes()
                        .contains(WifiSecurityHelper.getSecurity(getActivity()))) {
                    result = accessPoint.getWifiEntry();
                } else if (accessPoint.getWifiEntry().canDisconnect()) {
                    accessPoint.getWifiEntry().disconnect(null);
                }
            }
            return result;
        }

        private void startConnect(WifiEntry wifiEntry) {
            mStartedConnect = true;

            if (wifiEntry.getConnectedState() == WifiEntry.CONNECTED_STATE_CONNECTED) {
                notifyListener(StateMachine.RESULT_SUCCESS);
                return;
            }

            wifiEntry.connect(status -> {
                if (status == CONNECT_STATUS_SUCCESS) {
                    mConnectSucceeded = true;
                    notifyListener(StateMachine.RESULT_SUCCESS);
                    return;
                }

                // Diagnose failure based on current WifiConfiguration.
                WifiConfiguration configuration = null;
                for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
                    if (config.networkId == wifiEntry.getWifiConfiguration().networkId) {
                        configuration = config;
                        break;
                    }
                }

                if (configuration == null) {
                    notifyListener(StateMachine.RESULT_UNKNOWN_ERROR);
                    return;
                }

                NetworkSelectionStatus networkSelectionStatus =
                        configuration.getNetworkSelectionStatus();
                if (networkSelectionStatus.getNetworkSelectionStatus() != NETWORK_SELECTION_ENABLED
                        || !networkSelectionStatus.hasEverConnected()) {
                    if (networkSelectionStatus.getDisableReasonCounter(
                            DISABLED_AUTHENTICATION_FAILURE) > 0
                            || networkSelectionStatus.getDisableReasonCounter(
                            DISABLED_BY_WRONG_PASSWORD) > 0
                            || networkSelectionStatus.getDisableReasonCounter(
                            DISABLED_AUTHENTICATION_NO_CREDENTIALS) > 0) {
                        mUserChoiceInfo.setConnectionFailedStatus(
                                UserChoiceInfo.ConnectionFailedStatus.AUTHENTICATION);
                        notifyListener(StateMachine.RESULT_FAILURE);
                        return;
                    }
                }

                switch (configuration.getNetworkSelectionStatus()
                        .getNetworkSelectionDisableReason()) {
                    case WifiConfiguration.NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION:
                        mUserChoiceInfo.setConnectionFailedStatus(
                                UserChoiceInfo.ConnectionFailedStatus.REJECTED);
                        break;
                    case WifiConfiguration.NetworkSelectionStatus.DISABLED_DHCP_FAILURE:
                        notifyListener(StateMachine.RESULT_UNKNOWN_ERROR);
                        break;
                    default:
                        mUserChoiceInfo.setConnectionFailedStatus(
                                UserChoiceInfo.ConnectionFailedStatus.UNKNOWN);
                }
                notifyListener(StateMachine.RESULT_FAILURE);
            });
        }

        private void startSave() {
            if (mStartedSave) {
                return;
            }
            mStartedSave = true;
            mWifiManager.save(mWifiConfiguration, new WifiManager.ActionListener() {
                @Override
                public void onSuccess() {
                    mSaveComplete = true;
                    proceedDependOnNetworkState();
                }

                @Override
                public void onFailure(int status) {
                    notifyListener(StateMachine.RESULT_UNKNOWN_ERROR);
                }
            });
        }


        private boolean needsSave() {
            WifiEntry wifiEntry = mUserChoiceInfo.getWifiEntry();
            return wifiEntry == null || wifiEntry.getWifiConfiguration() == null
                    || !TextUtils.isEmpty(mUserChoiceInfo.getPageSummary(UserChoiceInfo.PASSWORD));
        }

        private void notifyListener(int result) {
            if (mStateMachine.getCurrentState() instanceof ConnectState) {
                mStateMachine.getListener().onComplete(result);
            }
        }

        private boolean isNetworkConnected() {
            return mConnectSucceeded;
        }

        private void postTimeout() {
            mHandler.removeMessages(MSG_TIMEOUT);
            mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, CONNECTION_TIMEOUT);
        }

        private static class MessageHandler extends Handler {

            private final WeakReference<ConnectToWifiFragment> mFragmentRef;

            MessageHandler(ConnectToWifiFragment fragment) {
                mFragmentRef = new WeakReference<>(fragment);
            }

            @Override
            public void handleMessage(Message msg) {
                if (DEBUG) Log.d(TAG, "Timeout waiting on supplicant state change");

                final ConnectToWifiFragment fragment = mFragmentRef.get();
                if (fragment == null) {
                    return;
                }

                if (fragment.isNetworkConnected()) {
                    if (DEBUG) Log.d(TAG, "Fake timeout; we're actually connected");
                    fragment.notifyListener(StateMachine.RESULT_SUCCESS);
                } else {
                    if (DEBUG) Log.d(TAG, "Timeout is real; telling the listener");
                    UserChoiceInfo userChoiceInfo = ViewModelProviders
                            .of(fragment.getActivity()).get(UserChoiceInfo.class);
                    userChoiceInfo.setConnectionFailedStatus(
                            UserChoiceInfo.ConnectionFailedStatus.TIMEOUT);
                    fragment.notifyListener(StateMachine.RESULT_FAILURE);
                }
            }
        }
    }
}
