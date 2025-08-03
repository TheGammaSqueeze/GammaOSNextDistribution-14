/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.phone.testapps.gbatestapp.ui.main;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.telephony.gba.UaSecurityProtocolIdentifier;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.phone.testapps.gbatestapp.R;
import com.android.phone.testapps.gbatestapp.Settings;

/** main fragent to update settings and run the test */
public class MainFragment extends Fragment {

    private static final String TAG = "GBATestApp";
    private static final String TAG_CARRIER = "carrier";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_CLIENT = "client";

    private static MainFragment sInstance;

    private Settings mSettings;
    private TelephonyManager mTelephonyManager;

    /** Get the instance of MainFragment*/
    public static MainFragment newInstance() {
        if (sInstance == null) {
            sInstance = new MainFragment();
        }
        return sInstance;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View viewHierarchy = inflater.inflate(R.layout.main_fragment, container, false);

        Button buttonCarrier = viewHierarchy.findViewById(R.id.carrier_config_change_button);
        buttonCarrier.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Fragment carrierFrag = getChildFragmentManager()
                                .findFragmentByTag(TAG_CARRIER);
                        if (carrierFrag == null) {
                            carrierFrag = CarrierConfigFragment.newInstance();
                        }
                        getChildFragmentManager()
                                .beginTransaction()
                                .replace(R.id.main, carrierFrag, TAG_CARRIER)
                                .commitNow();
                    }
                }
        );

        Button buttonService = viewHierarchy.findViewById(R.id.service_config);
        buttonService.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Fragment serviceFrag = getChildFragmentManager()
                                .findFragmentByTag(TAG_SERVICE);
                        if (serviceFrag == null) {
                            serviceFrag = ServiceConfigFragment.newInstance();
                        }
                        getChildFragmentManager()
                                .beginTransaction()
                                .replace(R.id.main, serviceFrag, TAG_SERVICE)
                                .commitNow();
                    }
                }
        );

        Button buttonClient = viewHierarchy.findViewById(R.id.client_config);
        buttonClient.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Fragment testFrag = getChildFragmentManager()
                                .findFragmentByTag(TAG_CLIENT);
                        if (testFrag == null) {
                            testFrag = TestConfigFragment.newInstance();
                        }

                        getChildFragmentManager()
                                .beginTransaction()
                                .replace(R.id.main, testFrag, TAG_CLIENT)
                                .commitNow();
                    }
                }
        );

        Button buttonExit = viewHierarchy.findViewById(R.id.test_exit);
        buttonExit.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getActivity().finish();
                    }
                }
        );

        Button buttonRunning = viewHierarchy.findViewById(R.id.send_request);
        buttonRunning.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "starting test...");
                        TelephonyManager.BootstrapAuthenticationCallback cb = new
                                TelephonyManager.BootstrapAuthenticationCallback() {
                                    @Override
                                    public void onKeysAvailable(byte[] gbaKey, String btId) {
                                        String result = "onKeysAvailable, key:"
                                                + Settings.byteArrayToHexString(gbaKey)
                                                + ", btid:" + btId;
                                        Log.d(TAG, result);
                                        getActivity().runOnUiThread(()-> {
                                            mTestLog.append(result + "\n");
                                        });
                                    }

                                    @Override
                                    public void onAuthenticationFailure(int reason) {
                                        String result = "onAuthFailure, cause:" + reason;
                                        Log.d(TAG, result);
                                        getActivity().runOnUiThread(
                                                () -> mTestLog.append(result + "\n"));
                                    }
                                };
                        UaSecurityProtocolIdentifier.Builder builder =
                                new UaSecurityProtocolIdentifier.Builder();
                        try {
                            if (mSettings.getOrg() != 0 || mSettings.getSpId() != 0
                                    || mSettings.getTlsCs() != 0) {
                                builder.setOrg(mSettings.getOrg())
                                        .setProtocol(mSettings.getSpId())
                                        .setTlsCipherSuite(mSettings.getTlsCs());
                            }
                        } catch (IllegalArgumentException e) {
                            getActivity().runOnUiThread(() -> mTestLog.append(
                                    "Fail to create UaSecurityProtocolIdentifier " + e + "\n"));
                            return;
                        }

                        UaSecurityProtocolIdentifier spId = builder.build();
                        Log.d(TAG, "bootstrapAuthenticationRequest with parameters [appType:"
                                + mSettings.getAppType() + ", NAF:" + mSettings.getNafUrl()
                                + ", spId:" + spId + ", isForceBootstrap:"
                                + mSettings.isForceBootstrap() + "]");
                        try {
                            mTelephonyManager.bootstrapAuthenticationRequest(
                                    mSettings.getAppType(), Uri.parse(mSettings.getNafUrl()),
                                    spId, mSettings.isForceBootstrap(),
                                    AsyncTask.SERIAL_EXECUTOR, cb);
                        } catch (NullPointerException e) {
                            getActivity().runOnUiThread(() -> mTestLog.append(
                                    "Invalid parameters, please check!" + "\n"));
                        }
                    }
                }
        );

        return viewHierarchy;
    }

    TextView mTestLog;
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mTelephonyManager = (TelephonyManager)
                getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mSettings = Settings.getSettings(getContext());
        mTestLog = getActivity().findViewById(R.id.viewTestOutput);
    }

}
