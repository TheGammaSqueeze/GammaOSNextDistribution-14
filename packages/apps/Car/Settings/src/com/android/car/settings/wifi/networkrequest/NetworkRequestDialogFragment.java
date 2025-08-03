/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.wifi.networkrequest;

import static java.util.stream.Collectors.toList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.NetworkRequestMatchCallback;
import android.net.wifi.WifiManager.NetworkRequestUserSelectionCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.wifi.WifiUtil;
import com.android.car.ui.AlertDialogBuilder;
import com.android.settingslib.Utils;
import com.android.settingslib.core.lifecycle.ObservableDialogFragment;
import com.android.wifitrackerlib.WifiEntry;
import com.android.wifitrackerlib.WifiPickerTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * The Fragment sets up callback {@link NetworkRequestMatchCallback} with framework. To handle most
 * behaviors of the callback when requesting wifi network, except for error message. When error
 * happens, {@link NetworkRequestErrorDialogFragment} will be called to display error message.
 */
public class NetworkRequestDialogFragment extends ObservableDialogFragment implements
        DialogInterface.OnClickListener, NetworkRequestMatchCallback {

    private static final String TAG = "NetworkRequestDialogFragment";

    /** Message sent to us to stop scanning wifi and pop up timeout dialog. */
    private static final int MESSAGE_STOP_SCAN_WIFI_LIST = 0;

    /**
     * Spec defines there should be 5 wifi ap on the list at most or just show all if {@code
     * mShowLimitedItem} is false.
     */
    private static final int MAX_NUMBER_LIST_ITEM = 5;
    private boolean mShowLimitedItem = true;

    /** Delayed time to stop scanning wifi. */
    private static final int DELAY_TIME_STOP_SCAN_MS = 30 * 1000;

    static final String EXTRA_APP_NAME = "com.android.settings.wifi.extra.APP_NAME";
    static final String EXTRA_IS_SPECIFIED_SSID =
            "com.android.settings.wifi.extra.REQUEST_IS_FOR_SINGLE_NETWORK";

    private final List<WifiEntry> mWifiEntryList = new ArrayList<>();
    private WifiPickerTracker mWifiPickerTracker;
    /** Worker thread used for WifiPickerTracker work. */
    private HandlerThread mWorkerThread;
    private FilterWifiTracker mFilterWifiTracker;
    private WifiEntryAdapter mDialogAdapter;
    private NetworkRequestUserSelectionCallback mUserSelectionCallback;
    private boolean mIsSpecifiedSsid;
    private boolean mWaitingConnectCallback;

    private final WifiPickerTracker.WifiPickerTrackerCallback mWifiListener =
            new WifiPickerTracker.WifiPickerTrackerCallback() {
                @Override
                public void onWifiEntriesChanged() {
                    notifyAdapterRefresh();
                }

                @Override
                public void onNumSavedNetworksChanged() {
                    // Do nothing.
                }

                @Override
                public void onNumSavedSubscriptionsChanged() {
                    // Do nothing.
                }

                @Override
                public void onWifiStateChanged() {
                    notifyAdapterRefresh();
                }
            };

    /** Creates Network Request dialog. */
    public static NetworkRequestDialogFragment newInstance() {
        NetworkRequestDialogFragment dialogFragment = new NetworkRequestDialogFragment();
        return dialogFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWorkerThread = new HandlerThread(
                TAG + "{" + Integer.toHexString(System.identityHashCode(this)) + "}",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mWorkerThread.start();
        mWifiPickerTracker = WifiUtil.createWifiPickerTracker(getLifecycle(),
                getContext(), new Handler(Looper.getMainLooper()), mWorkerThread.getThreadHandler(),
                mWifiListener);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = getContext();

        Intent intent = getActivity().getIntent();
        if (intent != null) {
            mIsSpecifiedSsid = intent.getBooleanExtra(EXTRA_IS_SPECIFIED_SSID, false);
        }

        // Prepares adapter.
        mDialogAdapter = new WifiEntryAdapter(context,
                R.layout.preference_wifi_entry, getWifiEntryList());

        AlertDialogBuilder builder = new AlertDialogBuilder(context)
                .setTitle(getTitle())
                .setAdapter(mDialogAdapter, /* listener= */ this)
                .setNegativeButton(R.string.cancel, (dialog, which) -> onCancel(dialog))
                // Do nothings, will replace the onClickListener to avoid auto closing dialog.
                .setNeutralButton(R.string.network_connection_request_dialog_showall,
                        /* listener= */ null);
        if (mIsSpecifiedSsid) {
            builder.setPositiveButton(R.string.wifi_setup_connect, /* listener= */ null);
        }

        // Clicking list item is to connect Wi-Fi entry.
        AlertDialog dialog = builder.create();
        dialog.getListView().setOnItemClickListener(
                (parent, view, position, id) -> this.onClick(dialog, position));

        // Don't dismiss dialog when touching outside. User reports it is easy to touch outside.
        // This causes dialog to close.
        setCancelable(false);

        dialog.setOnShowListener((dialogInterface) -> {
            // Replace NeutralButton onClickListener to avoid closing dialog
            Button neutralBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            neutralBtn.setVisibility(View.GONE);
            neutralBtn.setOnClickListener(v -> {
                mShowLimitedItem = false;
                renewWifiEntryList(/* scanResults= */ null);
                notifyAdapterRefresh();
                neutralBtn.setVisibility(View.GONE);
            });

            // Replace Positive onClickListener to avoid closing dialog
            if (mIsSpecifiedSsid) {
                Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveBtn.setOnClickListener(v -> {
                    // When clicking connect button, should connect to the first and the only one
                    // list item.
                    this.onClick(dialog, /* position= */ 0);
                });
                // Disable button at first and enable it after there are some Wi-Fi entries in
                // the list.
                positiveBtn.setEnabled(false);
            }
        });
        return dialog;
    }

    private String getTitle() {
        Intent intent = getActivity().getIntent();
        String appName = "";
        if (intent != null) {
            appName = intent.getStringExtra(EXTRA_APP_NAME);
        }

        return getString(R.string.network_connection_request_dialog_title, appName);
    }

    @NonNull
    List<WifiEntry> getWifiEntryList() {
        return mWifiEntryList;
    }

    private BaseAdapter getDialogAdapter() {
        return mDialogAdapter;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        List<WifiEntry> wifiEntryList = getWifiEntryList();
        if (wifiEntryList.isEmpty()) {
            return;  // Invalid values.
        }
        if (mUserSelectionCallback == null) {
            return; // Callback is missing or not ready.
        }

        if (which < wifiEntryList.size()) {
            WifiEntry selectedWifiEntry = wifiEntryList.get(which);
            WifiConfiguration wifiConfig = selectedWifiEntry.getWifiConfiguration();
            if (wifiConfig == null) {
                if (selectedWifiEntry != null) {
                    wifiConfig = WifiUtil.getWifiConfig(selectedWifiEntry, /* password= */ null);
                }
            }

            if (wifiConfig != null) {
                mUserSelectionCallback.select(wifiConfig);

                mWaitingConnectCallback = true;
                updateConnectButton(false);
            }
        }
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        // Finishes the activity when user clicks back key or outside of the dialog.
        if (getActivity() != null) {
            getActivity().finish();
        }
        if (mUserSelectionCallback != null) {
            mUserSelectionCallback.reject();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mHandler.removeMessages(MESSAGE_STOP_SCAN_WIFI_LIST);
        WifiManager wifiManager = getContext().getApplicationContext()
                .getSystemService(WifiManager.class);
        if (wifiManager != null) {
            wifiManager.unregisterNetworkRequestMatchCallback(this);
        }
    }

    @Override
    public void onDestroy() {
        if (mWorkerThread != null) {
            mWorkerThread.quit();
        }
        super.onDestroy();
    }

    private void showAllButton() {
        AlertDialog alertDialog = (AlertDialog) getDialog();
        if (alertDialog == null) {
            return;
        }

        Button neutralBtn = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutralBtn != null) {
            neutralBtn.setVisibility(View.VISIBLE);
        }
    }

    private void updateConnectButton(boolean enabled) {
        // The button is only showed in single SSID mode.
        if (!mIsSpecifiedSsid) {
            return;
        }

        AlertDialog alertDialog = (AlertDialog) getDialog();
        if (alertDialog == null) {
            return;
        }

        Button positiveBtn = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveBtn != null) {
            positiveBtn.setEnabled(enabled);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        WifiManager wifiManager = getContext().getApplicationContext()
                .getSystemService(WifiManager.class);
        if (wifiManager != null) {
            wifiManager.registerNetworkRequestMatchCallback(new HandlerExecutor(mHandler), this);
        }
        // Sets time-out to stop scanning.
        mHandler.sendEmptyMessageDelayed(MESSAGE_STOP_SCAN_WIFI_LIST, DELAY_TIME_STOP_SCAN_MS);

        if (mFilterWifiTracker == null) {
            mFilterWifiTracker = new FilterWifiTracker();
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STOP_SCAN_WIFI_LIST:
                    removeMessages(MESSAGE_STOP_SCAN_WIFI_LIST);
                    stopScanningAndPopErrorDialog(
                            NetworkRequestErrorDialogFragment.ERROR_DIALOG_TYPE.TIME_OUT);
                    break;
                default:
                    // Do nothing.
                    break;
            }
        }
    };

    protected void stopScanningAndPopErrorDialog(
            NetworkRequestErrorDialogFragment.ERROR_DIALOG_TYPE type) {
        // Dismisses current dialog.
        Dialog dialog =  getDialog();
        if (dialog != null && dialog.isShowing()) {
            dismiss();
        }

        // Throws error dialog.
        NetworkRequestErrorDialogFragment fragment = NetworkRequestErrorDialogFragment
                .newInstance();
        Bundle bundle = new Bundle();
        bundle.putSerializable(NetworkRequestErrorDialogFragment.DIALOG_TYPE, type);
        fragment.setArguments(bundle);
        fragment.show(getActivity().getSupportFragmentManager(),
                NetworkRequestErrorDialogFragment.class.getSimpleName());
    }

    private static class WifiEntryAdapter extends ArrayAdapter<WifiEntry> {

        private final int mResourceId;
        private final LayoutInflater mInflater;

        WifiEntryAdapter(Context context, int resourceId, List<WifiEntry> objects) {
            super(context, resourceId, objects);
            mResourceId = resourceId;
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = mInflater.inflate(mResourceId, parent, false);

                View divider = view.findViewById(
                        com.android.settingslib.R.id.two_target_divider);
                divider.setVisibility(View.GONE);
            }

            WifiEntry wifiEntry = getItem(position);

            TextView titleView = view.findViewById(android.R.id.title);
            if (titleView != null) {
                // Shows whole SSID for better UX.
                titleView.setSingleLine(false);
                titleView.setText(wifiEntry.getTitle());
            }

            TextView summary = view.findViewById(android.R.id.summary);
            if (summary != null) {
                String summaryString = wifiEntry.getSummary();
                if (TextUtils.isEmpty(summaryString)) {
                    summary.setVisibility(View.GONE);
                } else {
                    summary.setVisibility(View.VISIBLE);
                    summary.setText(summaryString);
                }
            }

            ImageView imageView = view.findViewById(android.R.id.icon);
            int level = wifiEntry.getLevel();
            if (imageView != null) {
                Drawable drawable = getContext().getDrawable(
                        Utils.getWifiIconResource(level));
                drawable.setTintList(
                        Utils.getColorAttr(getContext(), android.R.attr.colorControlNormal));
                imageView.setImageDrawable(drawable);
            }

            return view;
        }
    }

    @Override
    public void onAbort() {
        stopScanningAndPopErrorDialog(NetworkRequestErrorDialogFragment.ERROR_DIALOG_TYPE.ABORT);
    }

    @Override
    public void onUserSelectionCallbackRegistration(
            NetworkRequestUserSelectionCallback userSelectionCallback) {
        mUserSelectionCallback = userSelectionCallback;
    }

    @Override
    public void onMatch(List<ScanResult> scanResults) {
        // Shouldn't need to renew cached list, since input result is empty.
        if (scanResults != null && scanResults.size() > 0) {
            mHandler.removeMessages(MESSAGE_STOP_SCAN_WIFI_LIST);
            renewWifiEntryList(scanResults);

            notifyAdapterRefresh();
        }
    }

    // Updates internal WifiEntry list from WifiTracker. scanResults are used to update key list
    // of WifiEntry, and could be null if there is no need to update key list.
    private void renewWifiEntryList(@Nullable List<ScanResult> scanResults) {
        if (mFilterWifiTracker == null) {
            return;
        }

        // TODO(b/119846365): Checks if we could escalate the converting effort.
        // Updates keys of scanResults into FilterWifiTracker for updating matched WifiEntries.
        if (scanResults != null) {
            mFilterWifiTracker.updateKeys(scanResults);
        }

        // Re-gets matched WifiEntries from WifiTracker.
        List<WifiEntry> list = getWifiEntryList();
        list.clear();
        list.addAll(mFilterWifiTracker.getWifiEntries());
    }

    @VisibleForTesting
    void notifyAdapterRefresh() {
        if (getDialogAdapter() != null) {
            getDialogAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public void onUserSelectionConnectSuccess(WifiConfiguration wificonfiguration) {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, R.string.network_connection_connect_successful,
                    Toast.LENGTH_SHORT).show();
            activity.finish();
        }
    }

    @Override
    public void onUserSelectionConnectFailure(WifiConfiguration wificonfiguration) {
        // Do nothing when selection is failed, let user could try again easily.
        mWaitingConnectCallback = false;
        updateConnectButton(true);
    }

    private final class FilterWifiTracker {
        private List<ScanResult> mMatchedScanResults;

        FilterWifiTracker() {
            mMatchedScanResults = new ArrayList<>();
        }

        /**
         * Updates key list from input. {@code onMatch()} may be called in multi-times according
         * wifi scanning result, so needs patchwork here.
         */
        void updateKeys(List<ScanResult> scanResults) {
            mMatchedScanResults = scanResults;
        }

        /**
         * Returns only WifiEntries whose key is in {@code mMatchedScanResults}.
         *
         * @return List of matched WifiEntries.
         */
        List<WifiEntry> getWifiEntries() {
            List<WifiEntry> wifiEntries =
                    new ArrayList<>(mWifiPickerTracker.getActiveWifiEntries());
            wifiEntries.addAll(mWifiPickerTracker.getWifiEntries());

            List<WifiEntry> filteredWifiEntries = new ArrayList<>();
            filteredWifiEntries.addAll(wifiEntries.stream().filter(entry -> {
                for (ScanResult matchedScanResult : mMatchedScanResults) {
                    if (TextUtils.equals(entry.getSsid(), matchedScanResult.SSID)
                            && entry.getSecurity()
                                    == WifiUtil.getWifiEntrySecurity(matchedScanResult)) {
                        return true;
                    }
                }
                return false;
            }).limit(mShowLimitedItem ? MAX_NUMBER_LIST_ITEM : Long.MAX_VALUE)
                    .collect(toList()));

            // Update related UI buttons
            if (mShowLimitedItem && (filteredWifiEntries.size() >= MAX_NUMBER_LIST_ITEM)) {
                showAllButton();
            }
            // Enable connect button if there is no pending callback.
            if (!mWaitingConnectCallback) {
                updateConnectButton(true);
            }

            return filteredWifiEntries;
        }
    }
}
