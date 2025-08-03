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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.wifi.EasyConnectStatusCallback;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.util.QrCodeGenerator;
import com.android.tv.settings.connectivity.util.State;
import com.android.tv.settings.connectivity.util.StateMachine;

/**
 * State responsible for showing the connect page.
 */
public class EasyConnectQRState implements State {
    private final FragmentActivity mActivity;
    private Fragment mFragment;
    private final StateMachine mStateMachine;

    public EasyConnectQRState(FragmentActivity wifiSetupActivity) {
        this.mActivity = wifiSetupActivity;
        mStateMachine = ViewModelProviders.of(mActivity).get(StateMachine.class);
    }

    @Override
    public void processForward() {
        mFragment = new QRCodeFragment();

        FragmentChangeListener listener = (FragmentChangeListener) mActivity;
        listener.onFragmentChange(mFragment, true);
    }

    @Override
    public void processBackward() {
        mFragment = new QRCodeFragment();
        mStateMachine.back();
    }

    @Override
    public Fragment getFragment() {
        return mFragment;
    }

    /**
     * Connects to the wifi network specified by the given configuration.
     */
    public static class QRCodeFragment extends Fragment {
        private static final String TAG = "QRCodeFragment";
        private static final boolean DEBUG = false;

        private UserChoiceInfo mUserChoiceInfo;
        private StateMachine mStateMachine;

        private WifiManager mWifiManager;
        private ImageView mQrCodeView;
        private View mProgressView;

        @Override
        public void onCreate(Bundle bundle) {
            super.onCreate(bundle);

            mUserChoiceInfo = ViewModelProviders.of(getActivity()).get(UserChoiceInfo.class);
            mStateMachine = ViewModelProviders.of(getActivity()).get(StateMachine.class);
            mWifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(
                    Context.WIFI_SERVICE);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
            final View view = inflater.inflate(R.layout.setup_qr_code, container, false);

            mQrCodeView = view.findViewById(R.id.setup_qrcode_view);
            mProgressView = view.findViewById(R.id.setup_qrcode_progress);

            return view;
        }

        @Override
        public void onResume() {
            super.onResume();

            mProgressView.setVisibility(View.VISIBLE);
            mQrCodeView.setVisibility(View.INVISIBLE);

            mWifiManager.startEasyConnectAsEnrolleeResponder(
                    getSanitizedDeviceName(),
                    WifiManager.EASY_CONNECT_CRYPTOGRAPHY_CURVE_PRIME256V1,
                    getActivity().getApplication().getMainExecutor(),
                    new EasyConnectDelegateCallback());
        }

        @Override
        public void onPause() {
            mWifiManager.stopEasyConnectSession();

            super.onPause();
        }

        /*
         * Allowed Range of ASCII characters in deviceInfo - %x20-7E.
         * semicolon and space are not allowed.
         * Length is limited to
         * {@link #WifiManager.getEasyConnectMaxAllowedResponderDeviceInfoLength()}
         * characters.
         */
        private String getSanitizedDeviceName() {
            String deviceName = Settings.Global.getString(getActivity().getContentResolver(),
                    "device_name");
            if (deviceName == null) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            for (char c : deviceName.toCharArray()) {
                if (c >= 0x20 && c <= 0x7E && c != ' ' && c != ';') {
                    sb.append(c);
                }
                if (sb.length()
                        >= WifiManager.getEasyConnectMaxAllowedResponderDeviceInfoLength()) {
                    return sb.toString();
                }
            }

            return sb.toString();
        }

        private class EasyConnectDelegateCallback extends EasyConnectStatusCallback {
            @Override
            public void onEnrolleeSuccess(int newNetworkId) {
                if (DEBUG) Log.d(TAG, "onEnrolleeSuccess: newNetworkId = " + newNetworkId);

                mUserChoiceInfo.setEasyConnectNetworkId(newNetworkId);

                for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
                    if (config.networkId == newNetworkId) {
                        mUserChoiceInfo.put(UserChoiceInfo.SSID, config.getPrintableSsid());
                    }
                }

                mStateMachine.getListener().onComplete(StateMachine.CONNECT);
            }

            @Override
            public void onFailure(int code) {
                if (DEBUG) Log.d(TAG, "onFailure: code = " + code);
                mUserChoiceInfo.setConnectionFailedStatus(
                        UserChoiceInfo.ConnectionFailedStatus.EASY_CONNECT_FAILURE);
                mStateMachine.getListener().onComplete(StateMachine.RESULT_FAILURE);
            }

            @Override
            public void onBootstrapUriGenerated(@NonNull Uri dppUri) {
                if (DEBUG) Log.d(TAG, "onBootstrapUriGenerated: uri = " + dppUri.toString());

                try {
                    final Bitmap bmp = QrCodeGenerator.encodeQrCode(dppUri.toString(), 512);
                    mQrCodeView.setImageBitmap(bmp);

                    // TODO: Fade animations here?
                    mProgressView.setVisibility(View.INVISIBLE);
                    mQrCodeView.setVisibility(View.VISIBLE);

                } catch (Exception e) {
                    if (DEBUG) Log.d(TAG, "encodeQrCode error: " + e.getMessage());
                    // TODO: Set error code?
                    mStateMachine.getListener().onComplete(StateMachine.RESULT_FAILURE);
                }
            }

            @Override
            public void onConfiguratorSuccess(int code) {
                // Do nothing
            }

            @Override
            public void onProgress(int code) {
                if (DEBUG) Log.d(TAG, "onProgress: code = " + code);
                // Do nothing
            }
        }
    }
}
