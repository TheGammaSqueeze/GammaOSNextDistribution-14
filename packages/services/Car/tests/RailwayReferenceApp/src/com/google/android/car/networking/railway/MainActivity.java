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
package com.google.android.car.networking.railway;

import android.app.AlertDialog;
import android.app.Application;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.EthernetNetworkManagementException;
import android.net.EthernetNetworkSpecifier;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.common.base.Strings;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MainActivity extends FragmentActivity {
    private static final String TAG = MainActivity.class.getName();
    private static final Duration REQUEST_NETWORK_TIMEOUT =
            Duration.of(2000, ChronoUnit.MILLIS);

    private ConfigurationUpdater mConfigurationUpdater;
    private InterfaceEnabler mInterfaceEnabler;
    private CurrentEthernetNetworksViewModel mCurrentEthernetNetworksViewModel;
    private ConnectivityManager mConnectivityManager;

    private final List<Button> mButtons = new ArrayList<>();
    private final List<EditText> mEditTexts = new ArrayList<>();

    private EditText mAllowedPackageNames;
    private EditText mIpConfiguration;
    private EditText mInterfaceName; // used in updateConfiguration
    private EditText mNetworkCapabilities;
    private EditText mInterfaceName2; // used in connect/enable/disable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAllowedPackageNames = findViewById(R.id.allowedPackageNamesInput);
        mIpConfiguration = findViewById(R.id.ipConfigurationInput);
        mInterfaceName = findViewById(R.id.interfaceNameInput);
        mNetworkCapabilities = findViewById(R.id.networkCapabilitiesInput);
        Button updateButton = findViewById(R.id.updateButton);
        updateButton.setOnClickListener(v -> onUpdateButtonClick());

        mInterfaceName2 = findViewById(R.id.interfaceNameInput2);
        Button connectButton = findViewById(R.id.connectButton);
        connectButton.setOnClickListener(v -> onConnectButtonClick());
        Button enableButton = findViewById(R.id.enableButton);
        enableButton.setOnClickListener(v -> onEnableButtonClick());
        Button disableButton = findViewById(R.id.disableButton);
        disableButton.setOnClickListener(v -> onDisableButtonClick());

        mButtons.add(updateButton);
        mButtons.add(connectButton);
        mButtons.add(enableButton);
        mButtons.add(disableButton);

        mEditTexts.add(mAllowedPackageNames);
        mEditTexts.add(mIpConfiguration);
        mEditTexts.add(mInterfaceName);
        mEditTexts.add(mNetworkCapabilities);
        mEditTexts.add(mInterfaceName2);

        TextView currentEthernetNetworksOutput = findViewById(R.id.currentEthernetNetworksOutput);

        mCurrentEthernetNetworksViewModel =
                new ViewModelProvider(this,
                        (ViewModelProvider.Factory) new CurrentEthernetNetworksViewModelFactory(
                                (Application) getApplicationContext()))
                        .get(CurrentEthernetNetworksViewModel.class);
        mCurrentEthernetNetworksViewModel.getNetworksLiveData().observe(this,
                networkNetworkInfoMap -> currentEthernetNetworksOutput
                        .setText(mCurrentEthernetNetworksViewModel
                                .getCurrentEthernetNetworksText(networkNetworkInfoMap)));

        OutcomeReceiver<String, EthernetNetworkManagementException> outcomeReceiver =
                new OutcomeReceiver<String, EthernetNetworkManagementException>() {
                    @Override
                    public void onResult(String result) {
                        showOperationResultDialog(result, /* isSuccess= */ true);
                    }

                    @Override
                    public void onError(EthernetNetworkManagementException error) {
                        OutcomeReceiver.super.onError(error);
                        showOperationResultDialog(error.getLocalizedMessage(),
                                /* isSuccess= */ false);
                    }
                };

        mConfigurationUpdater = new ConfigurationUpdater(getApplicationContext(), outcomeReceiver);
        mInterfaceEnabler = new InterfaceEnabler(getApplicationContext(), outcomeReceiver);
        mConnectivityManager = getSystemService(ConnectivityManager.class);
    }

    private void setButtonsAndEditTextsEnabled(boolean isEnabled) {
        for (Button button : mButtons) {
            button.setEnabled(isEnabled);
        }

        for (EditText editText : mEditTexts) {
            editText.setEnabled(isEnabled);
        }
    }

    private void showOperationResultDialog(String message, boolean isSuccess) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isSuccess ? R.string.success : R.string.error)
                .setMessage(message)
                .setOnDismissListener(d -> setButtonsAndEditTextsEnabled(true))
                .create();
        dialog.show();
    }

    private String getUpdateConfigurationIface() {
        return mInterfaceName.getText().toString();
    }

    private String getEnableDisableConnectIface() {
        return mInterfaceName2.getText().toString();
    }

    private void onUpdateButtonClick() {
        setButtonsAndEditTextsEnabled(false);
        Log.d(TAG, "configuration update started");

        try {
            validateInterfaceName(getUpdateConfigurationIface());
            mConfigurationUpdater.updateNetworkConfiguration(
                    mAllowedPackageNames.getText().toString(),
                    mIpConfiguration.getText().toString(),
                    mNetworkCapabilities.getText().toString(),
                    getUpdateConfigurationIface());
        } catch (IllegalStateException | IllegalArgumentException
                | PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error updating: " + e.getMessage());
            showOperationResultDialog(e.getLocalizedMessage(), /* isSuccess= */ false);
        }
    }

    private void onConnectButtonClick() {
        setButtonsAndEditTextsEnabled(false);
        Log.d(TAG, "connect interface started");

        try {
            validateInterfaceName(getEnableDisableConnectIface());
            new NetworkConnector().checkNetworkConnectionAvailable(
                    new EthernetNetworkSpecifier(getEnableDisableConnectIface()));
        } catch (SecurityException | IllegalArgumentException e) {
            showOperationResultDialog(e.getLocalizedMessage(), /* isSuccess= */ false);
        }
    }

    private void onEnableButtonClick() {
        setButtonsAndEditTextsEnabled(false);
        Log.d(TAG, "enable interface started");

        try {
            validateInterfaceName(getEnableDisableConnectIface());
            mInterfaceEnabler.enableInterface(getEnableDisableConnectIface());
        } catch (IllegalArgumentException e) {
            showOperationResultDialog(e.getLocalizedMessage(), /* isSuccess= */ false);
        }
    }

    private void onDisableButtonClick() {
        setButtonsAndEditTextsEnabled(false);
        Log.d(TAG, "disable interface started");

        try {
            validateInterfaceName(getEnableDisableConnectIface());
            mInterfaceEnabler.disableInterface(getEnableDisableConnectIface());
        } catch (IllegalArgumentException e) {
            showOperationResultDialog(e.getLocalizedMessage(), /* isSuccess= */ false);
        }
    }

    private void validateInterfaceName(String iface) {
        if (Strings.isNullOrEmpty(iface)) {
            throw new IllegalArgumentException("Interface Name can't be empty.");
        }
    }

    private class NetworkConnector {
        private final CompletableFuture<Network> mNetworkFuture = new CompletableFuture();
        private final NetworkCallback mCb = new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                mNetworkFuture.complete(network);

                try (Socket socket = new Socket()) {
                    network.bindSocket(socket);
                } catch (IOException e) {
                    runOnUiThread(() -> showOperationResultDialog(
                            e.getLocalizedMessage(), /* isSuccess= */ false));
                    return;
                } finally {
                    mConnectivityManager.unregisterNetworkCallback(this);
                }

                runOnUiThread(() -> showOperationResultDialog(
                        mConnectivityManager.getLinkProperties(network).getInterfaceName(),
                        /* isSuccess= */ true));
            }
        };

        void checkNetworkConnectionAvailable(EthernetNetworkSpecifier specifier) {
            NetworkRequest request =
                    new NetworkRequest.Builder()
                            .clearCapabilities()
                            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                            .setNetworkSpecifier(specifier)
                            .build();
            try {
                mConnectivityManager.registerNetworkCallback(request, mCb);
                mNetworkFuture.get(REQUEST_NETWORK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                Log.e(TAG, e.getClass().getSimpleName());
                mConnectivityManager.unregisterNetworkCallback(mCb);
                runOnUiThread(() -> showOperationResultDialog(
                        mInterfaceName2.getText().toString() + " is not available",
                        /* isSuccess= */ false));
            }
        }
    }

    private static class CurrentEthernetNetworksViewModelFactory extends
            ViewModelProvider.AndroidViewModelFactory {
        private final Application mApplication;

        private CurrentEthernetNetworksViewModelFactory(Application application) {
            super(application);
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return modelClass.cast(new CurrentEthernetNetworksViewModel(mApplication));
        }
    }
}
