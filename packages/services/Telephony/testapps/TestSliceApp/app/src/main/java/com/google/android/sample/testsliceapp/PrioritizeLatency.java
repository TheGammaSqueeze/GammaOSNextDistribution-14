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
package com.google.android.sample.testsliceapp;

import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ENTITLEMENT_CHECK_FAILED;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NETWORK_NOT_AVAILABLE;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_FOREGROUND;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_PENDING_NETWORK_SETUP;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_TIMEOUT;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A simple {@link Fragment} subclass. Use the {@link PrioritizeLatency#newInstance} factory method
 * to create an instance of this fragment.
 */
public class PrioritizeLatency extends Fragment {
    Button mPurchase, mNetworkRequestRelease, mPing;
    TextView mResultTextView;
    Network mNetwork = null;
    ConnectivityManager mConnectivityManager;
    NetworkCallback mProfileCheckNetworkCallback;
    TelephonyManager mTelephonyManager;
    Context mContext;
    private final ExecutorService mFixedThreadPool = Executors.newFixedThreadPool(3);

    private static final String LOG_TAG = "PrioritizeLatency";
    private static final int TIMEOUT_FOR_PURCHASE = 5 * 60; // 5 minutes

    public PrioritizeLatency() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of this fragment using the provided
     * parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment PrioritizeLatency.
     */
    // TODO: Rename and change types and number of parameters
    public static PrioritizeLatency newInstance(String param1, String param2) {
        PrioritizeLatency fragment = new PrioritizeLatency();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_prioritize_latency, container, false);
        mResultTextView = view.findViewById(R.id.resultTextView);

        mPurchase = view.findViewById(R.id.purchaseButton);
        mPurchase.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "Clicking purchase button");
                onPurchaseButtonClick();
            }
        });

        mNetworkRequestRelease = view.findViewById(R.id.requestReleaseButton);
        mNetworkRequestRelease.setEnabled(false);
        mNetworkRequestRelease.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "Clicking Request/Release Network button");
                onNetworkRequestReleaseClick();
            }
        });

        mPing = view.findViewById(R.id.pinglatency);
        mPing.setEnabled(false);
        mPing.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "Clicking Ping button");
                if (mNetwork != null) {
                    mFixedThreadPool.execute(() -> {
                        try {
                            RequestTask requestTask = new RequestTask();
                            requestTask.ping(mNetwork);
                            updateResultTextView("Result: Ping is done successfully!");
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Exception at ping: " + e);
                            updateResultTextView("Result: Got exception with ping!!!");
                        }
                    });
                }
            }
        });
        return view;
    }

    private void onNetworkRequestReleaseClick() {
        if (mNetwork == null) {
            mProfileCheckNetworkCallback = new NetworkCallback() {
                @Override
                public void onAvailable(final Network network) {
                    Log.d(LOG_TAG, "onAvailable + " + network);
                    mNetwork = network;
                    updateUIOnNetworkAvailable();
                }
            };
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY);
            mConnectivityManager.requestNetwork(builder.build(),
                    mProfileCheckNetworkCallback);
            Log.d(LOG_TAG, "Network Request/Release onClick + " + builder.build());
            mResultTextView.setText(R.string.network_requested);
        } else {
            try {
                mConnectivityManager.unregisterNetworkCallback(
                        mProfileCheckNetworkCallback);
                mNetwork = null;
                mNetworkRequestRelease.setText(R.string.request_network);
                mResultTextView.setText(R.string.network_released);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Exception when releasing network: " + e);
                mResultTextView.setText(R.string.network_release_failed);
            }
        }
    }

    @TargetApi(34)
    private void onPurchaseButtonClick() {
        try {
            if (mTelephonyManager.isPremiumCapabilityAvailableForPurchase(
                    TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY)) {
                LinkedBlockingQueue<Integer> purchaseRequest = new LinkedBlockingQueue<>(1);

                // Try to purchase the capability
                mTelephonyManager.purchasePremiumCapability(
                        TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY,
                        mFixedThreadPool, purchaseRequest::offer);
                mResultTextView.setText(R.string.purchase_in_progress);

                mFixedThreadPool.execute(() -> {
                    try {
                        Integer result = purchaseRequest.poll(
                                TIMEOUT_FOR_PURCHASE, TimeUnit.SECONDS);
                        if (result == null) {
                            updateResultTextView(R.string.purchase_empty_result);
                            Log.d(LOG_TAG, "Got null result at purchasePremiumCapability");
                            return;
                        }

                        String purchaseResultText = "Result: "
                                + purchasePremiumResultToText(result.intValue());
                        updateResultTextView(purchaseResultText);
                        Log.d(LOG_TAG, purchaseResultText);

                        if (isPremiumCapacityAvailableForUse(result.intValue())) {
                            updateNetworkRequestReleaseButton(true);
                        }
                    } catch (InterruptedException e) {
                        Log.e(LOG_TAG, "InterruptedException at onPurchaseButtonClick: " + e);
                        updateResultTextView(R.string.purchase_exception);
                    }
                });
            } else {
                mResultTextView.setText(R.string.premium_not_available);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception when purchasing network premium: " + e);
            mResultTextView.setText(R.string.purchase_exception);
        }
    }

    private void updateNetworkRequestReleaseButton(boolean enabled) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mNetworkRequestRelease.setEnabled(enabled);
            }
        });
    }

    private void updateResultTextView(int status) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mResultTextView.setText(status);
            }
        });
    }

    private void updateResultTextView(String status) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mResultTextView.setText(status);
            }
        });
    }

    private void updateUIOnNetworkAvailable() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPing.setEnabled(true);
                mNetworkRequestRelease.setText(R.string.release_network);
                mResultTextView.setText(R.string.network_available);
            }
        });
    }

    private String purchasePremiumResultToText(int result) {
        switch (result) {
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS:
                return "Success";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED:
                return "Throttled";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED:
                return "Already purchased";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS:
                return "Already in progress";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_FOREGROUND:
                return "Not foreground";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED:
                return "User canceled";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED:
                return "Carrier disabled";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR:
                return "Carrier error";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_TIMEOUT:
                return "Timeout";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED:
                return "Feature not supported";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_NETWORK_NOT_AVAILABLE:
                return "Network not available";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_ENTITLEMENT_CHECK_FAILED:
                return "Entitlement check failed";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION:
                return "Not default data subscription";
            case PURCHASE_PREMIUM_CAPABILITY_RESULT_PENDING_NETWORK_SETUP:
                return "Pending network setup";
            default:
                String errorStr = "Unknown purchasing result " + result;
                Log.e(LOG_TAG, errorStr);
                return errorStr;
        }
    }

    private boolean isPremiumCapacityAvailableForUse(int purchaseResult) {
        if (purchaseResult == PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS
                || purchaseResult == PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED) {
            return true;
        }
        return false;
    }
}
