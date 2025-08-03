/*
 * Copyright (C) 2022 The Android Open Source Project.
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

package com.android.car.cartelemetryapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CarTelemetryActivity extends Activity {
    private static final int LOG_SIZE = 100;
    private ICarMetricsCollectorService mService;
    private TextView mConfigNameView;
    private TextView mHistoryView;
    private TextView mLogView;
    private RecyclerView mRecyclerView;
    private PopupWindow mConfigPopup;
    private Button mPopupCloseButton;
    private Button mConfigButton;
    private ConfigListAdaptor mAdapter;
    private List<IConfigData> mConfigData = new ArrayList<>();
    private Map<String, Integer> mConfigNameIndex = new HashMap<>();
    private Deque<String> mLogs = new ArrayDeque<>();
    private Map<String, List<PersistableBundle>> mBundleHistory = new HashMap<>();
    private Map<String, List<String>> mErrorHistory = new HashMap<>();
    private boolean mDataRadioSelected = true;
    private IConfigData mSelectedInfoConfig = new IConfigData();
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = ICarMetricsCollectorService.Stub.asInterface(service);
            onServiceBound();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConfigNameView = findViewById(R.id.config_name_text);
        mHistoryView = findViewById(R.id.history_text);
        mLogView = findViewById(R.id.log_text);

        RadioButton dataRadio = findViewById(R.id.data_radio);
        dataRadio.setOnClickListener(v -> {
            mDataRadioSelected = true;
            mHistoryView.setText(getBundleHistoryString(mSelectedInfoConfig.name));
        });

        RadioButton errorRadio = findViewById(R.id.error_radio);
        errorRadio.setOnClickListener(v -> {
            mDataRadioSelected = false;
            mHistoryView.setText(getErrorHistoryString(mSelectedInfoConfig.name));
        });

        ViewGroup parent = findViewById(R.id.mainLayout);
        View configsView = this.getLayoutInflater().inflate(R.layout.config_popup, parent, false);
        mConfigPopup = new PopupWindow(
                configsView, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        mConfigButton = findViewById(R.id.config_button);
        mConfigButton.setOnClickListener(v -> {
            mConfigPopup.showAtLocation(configsView, Gravity.CENTER, 0, 0);
        });
        mPopupCloseButton = configsView.findViewById(R.id.popup_close_button);
        mPopupCloseButton.setOnClickListener(v -> {
            mConfigPopup.dismiss();
        });

        mRecyclerView = configsView.findViewById(R.id.config_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        Intent intent = new Intent(this, CarMetricsCollectorService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (mConnection != null) {
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    private void onServiceBound() {
        try {
            printLog(mService.getLog());
        } catch (RemoteException e) {
            throw new IllegalStateException(
                    "Failed to call ICarMetricsCollectorService.getLog.", e);
        }
        IConfigStateListener configStateListener = new IConfigStateListener.Stub() {
            @Override
            public void onConfigAdded(String configName) {
                // Set config to checked
                mConfigData.get(mConfigNameIndex.get(configName)).selected = true;
                getMainExecutor().execute(() -> {
                    mAdapter.notifyItemChanged(mConfigNameIndex.get(configName));
                });
                printLog("Added config " + configName);
            }
        };
        try {
            mService.setConfigStateListener(configStateListener);
        } catch (RemoteException e) {
            throw new IllegalStateException(
                    "Failed to call ICarMetricsCollectorService.setConfigStateListener.", e);
        }
        IResultListener resultListener = new IResultListener.Stub() {
            @Override
            public void onResult(
                    String metricsConfigName,
                    IConfigData configData,
                    PersistableBundle report,
                    String telemetryError) {
                lazyInitHistories(metricsConfigName);
                mConfigData.set(mConfigNameIndex.get(metricsConfigName), configData);
                // Add to bundle and error histories
                if (report != null) {
                    if (!mBundleHistory.containsKey(metricsConfigName)) {
                        mBundleHistory.put(metricsConfigName, new ArrayList<PersistableBundle>());
                    }
                    mBundleHistory.get(metricsConfigName).add(report);
                    printLog("Received report for " + metricsConfigName);
                } else {
                    if (!mErrorHistory.containsKey(metricsConfigName)) {
                        mErrorHistory.put(metricsConfigName, new ArrayList<String>());
                    }
                    mErrorHistory.get(metricsConfigName).add(telemetryError);
                    printLog("Received error for " + metricsConfigName);
                }
                if (metricsConfigName.equals(mSelectedInfoConfig.name)) {
                    refreshHistory();
                }
            }
        };
        try {
            mService.setResultListener(resultListener);
        } catch (RemoteException e) {
            throw new IllegalStateException(
                    "Failed to call ICarMetricsCollectorService.setResultListener.", e);
        }

        try {
            mConfigData = mService.getConfigData();
        } catch (RemoteException e) {
            throw new IllegalStateException(
                    "Failed to call ICarMetricsCollectorService.getConfigData.", e);
        }

        for (int i = 0; i < mConfigData.size(); i++) {
            mConfigNameIndex.put(mConfigData.get(i).name, i);
        }
        if (mConfigData.size() != 0) {
            mSelectedInfoConfig = mConfigData.get(0);  // Default to display first config data
            refreshHistory();
        }

        mAdapter = new ConfigListAdaptor(
                mConfigData, new AdaptorCallback());
        mRecyclerView.setAdapter(mAdapter);
    }

    /** Converts bundle to string. */
    private String bundleToString(PersistableBundle bundle) {
        StringBuilder sb = new StringBuilder();
        for (String key : bundle.keySet()) {
            sb.append("--")
                .append(key)
                .append(": ")
                .append(bundle.get(key).toString())
                .append("\n");
        }
        return sb.toString();
    }

    /** Converts bundle history to string. */
    private String getBundleHistoryString(String configName) {
        StringBuilder sb = new StringBuilder();
        if (!mBundleHistory.containsKey(configName)) {
            return "";
        }
        for (PersistableBundle bundle : mBundleHistory.get(configName)) {
            sb.append(bundleToString(bundle)).append("\n");
        }
        return sb.toString();
    }

    /** Converts error history to string. */
    private String getErrorHistoryString(String configName) {
        StringBuilder sb = new StringBuilder();
        if (!mErrorHistory.containsKey(configName)) {
            return "";
        }
        for (String error : mErrorHistory.get(configName)) {
            sb.append(error).append("\n");
        }
        return sb.toString();
    }

    /** Refreshes the history view with the currently selected config's data. */
    private void refreshHistory() {
        getMainExecutor().execute(() -> {
            mConfigNameView.setText(mSelectedInfoConfig.name);
            if (mDataRadioSelected) {
                mHistoryView.setText(getBundleHistoryString(mSelectedInfoConfig.name));
            } else {
                mHistoryView.setText(getErrorHistoryString(mSelectedInfoConfig.name));
            }
        });
    }

    /** Clears the config data and histories. Cleared on server side too. */
    private void clearConfigData(IConfigData configData) {
        configData.onReadyTimes = 0;
        configData.sentBytes = 0;
        configData.errorCount = 0;
        if (mBundleHistory.containsKey(configData.name)) {
            mBundleHistory.get(configData.name).clear();
        }
        if (mErrorHistory.containsKey(configData.name)) {
            mErrorHistory.get(configData.name).clear();
        }
        try {
            mService.clearHistory(configData.name);
        } catch (RemoteException e) {
            throw new IllegalStateException(
                    "Failed to ICarMetricsCollectorService.clearHistory.", e);
        }
    }

    /** Retrieves histories from service if not already present. */
    private void lazyInitHistories(String configName) {
        if (!mBundleHistory.containsKey(configName)) {
            try {
                mBundleHistory.put(configName, mService.getBundleHistory(configName));
            } catch (RemoteException e) {
                throw new IllegalStateException(
                        "Failed to call ICarMetricsCollectorService.getBundleHistory.", e);
            }
        }
        if (!mErrorHistory.containsKey(configName)) {
            try {
                mErrorHistory.put(configName, mService.getErrorHistory(configName));
            } catch (RemoteException e) {
                throw new IllegalStateException(
                        "Failed to call ICarMetricsCollectorService.getErrorHistory.", e);
            }
        }
    }

    /** Prints to log view the log with prefixed timestamp. */
    private void printLog(String log) {
        String text = LocalDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + ": " + log;
        if (mLogs.size() >= LOG_SIZE) {
            // Remove oldest element
            mLogs.pollLast();
        }
        mLogs.addFirst(text);
        getMainExecutor().execute(() -> {
            mLogView.setText(String.join("\n", mLogs));
        });
    }

    private class AdaptorCallback implements ConfigListAdaptor.Callback {
        @Override
        public void onAddButtonClicked(IConfigData configData) {
            try {
                mService.addConfig(configData.name);
            } catch (RemoteException e) {
                throw new IllegalStateException(
                        "Failed to ICarMetricsCollectorService.addConfig.", e);
            }
        }

        @Override
        public void onRemoveButtonClicked(IConfigData configData) {
            try {
                mService.removeConfig(configData.name);
            } catch (RemoteException e) {
                throw new IllegalStateException(
                        "Failed to ICarMetricsCollectorService.removeConfig.", e);
            }
            configData.selected = false;
            getMainExecutor().execute(() -> {
                mAdapter.notifyItemChanged(mConfigNameIndex.get(configData.name));
            });
            printLog("Removed config " + configData.name);
        }

        @Override
        public void onInfoButtonClicked(IConfigData configData) {
            mSelectedInfoConfig = configData;
            mConfigNameView.setText(configData.name);
            lazyInitHistories(configData.name);
            getMainExecutor().execute(() -> {
                if (mDataRadioSelected) {
                    mHistoryView.setText(getBundleHistoryString(configData.name));
                } else {
                    mHistoryView.setText(getErrorHistoryString(configData.name));
                }
            });
        }

        @Override
        public void onClearButtonClicked(IConfigData configData) {
            int index = mConfigNameIndex.get(configData.name);
            clearConfigData(configData);
            getMainExecutor().execute(() -> {
                mAdapter.notifyItemChanged(index);
            });
        }
    }
}
