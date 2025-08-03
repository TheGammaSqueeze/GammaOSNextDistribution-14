/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.threadnetwork.demoapp;

import static com.google.common.io.BaseEncoding.base16;

import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.thread.ActiveOperationalDataset;
import android.net.thread.OperationalDatasetTimestamp;
import android.net.thread.PendingOperationalDataset;
import android.net.thread.ThreadNetworkController;
import android.net.thread.ThreadNetworkException;
import android.net.thread.ThreadNetworkManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;

public final class ThreadNetworkSettingsFragment extends Fragment {
    private static final String TAG = "ThreadNetworkSettings";

    // This is a mirror of NetworkCapabilities#NET_CAPABILITY_LOCAL_NETWORK which is @hide for now
    private static final int NET_CAPABILITY_LOCAL_NETWORK = 36;

    private ThreadNetworkController mThreadController;
    private TextView mTextState;
    private TextView mTextNetworkInfo;
    private TextView mMigrateNetworkState;
    private Executor mMainExecutor;

    private int mDeviceRole;
    private long mPartitionId;
    private ActiveOperationalDataset mActiveDataset;

    private static final byte[] DEFAULT_ACTIVE_DATASET_TLVS =
            base16().lowerCase()
                    .decode(
                            "0e080000000000010000000300001235060004001fffe00208dae21bccb8c321c40708fdc376ead74396bb0510c52f56cd2d38a9eb7a716954f8efd939030f4f70656e5468726561642d646231390102db190410fcb737e6fd6bb1b0fed524a4496363110c0402a0f7f8");
    private static final ActiveOperationalDataset DEFAULT_ACTIVE_DATASET =
            ActiveOperationalDataset.fromThreadTlvs(DEFAULT_ACTIVE_DATASET_TLVS);

    private static String deviceRoleToString(int mDeviceRole) {
        switch (mDeviceRole) {
            case ThreadNetworkController.DEVICE_ROLE_STOPPED:
                return "Stopped";
            case ThreadNetworkController.DEVICE_ROLE_DETACHED:
                return "Detached";
            case ThreadNetworkController.DEVICE_ROLE_CHILD:
                return "Child";
            case ThreadNetworkController.DEVICE_ROLE_ROUTER:
                return "Router";
            case ThreadNetworkController.DEVICE_ROLE_LEADER:
                return "Leader";
            default:
                return "Unknown";
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.thread_network_settings_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ConnectivityManager cm = getActivity().getSystemService(ConnectivityManager.class);
        cm.registerNetworkCallback(
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_THREAD)
                        .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .build(),
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        Log.i(TAG, "New Thread network is available");
                    }

                    @Override
                    public void onLinkPropertiesChanged(
                            Network network, LinkProperties linkProperties) {
                        updateNetworkInfo(linkProperties);
                    }

                    @Override
                    public void onLost(Network network) {
                        Log.i(TAG, "Thread network " + network + " is lost");
                        updateNetworkInfo(null /* linkProperties */);
                    }
                },
                new Handler(Looper.myLooper()));

        mMainExecutor = ContextCompat.getMainExecutor(getActivity());
        ThreadNetworkManager threadManager =
                getActivity().getSystemService(ThreadNetworkManager.class);
        if (threadManager != null) {
            mThreadController = threadManager.getAllThreadNetworkControllers().get(0);
            mThreadController.registerStateCallback(
                    mMainExecutor,
                    new ThreadNetworkController.StateCallback() {
                        @Override
                        public void onDeviceRoleChanged(int mDeviceRole) {
                            ThreadNetworkSettingsFragment.this.mDeviceRole = mDeviceRole;
                            updateState();
                        }

                        @Override
                        public void onPartitionIdChanged(long mPartitionId) {
                            ThreadNetworkSettingsFragment.this.mPartitionId = mPartitionId;
                            updateState();
                        }
                    });
            mThreadController.registerOperationalDatasetCallback(
                    mMainExecutor,
                    newActiveDataset -> {
                        this.mActiveDataset = newActiveDataset;
                        updateState();
                    });
        }

        mTextState = (TextView) view.findViewById(R.id.text_state);
        mTextNetworkInfo = (TextView) view.findViewById(R.id.text_network_info);

        if (mThreadController == null) {
            mTextState.setText("Thread not supported!");
            return;
        }

        ((Button) view.findViewById(R.id.button_join_network)).setOnClickListener(v -> doJoin());
        ((Button) view.findViewById(R.id.button_leave_network)).setOnClickListener(v -> doLeave());

        mMigrateNetworkState = view.findViewById(R.id.text_migrate_network_state);
        ((Button) view.findViewById(R.id.button_migrate_network))
                .setOnClickListener(v -> doMigration());

        updateState();
    }

    private void doJoin() {
        mThreadController.join(
                DEFAULT_ACTIVE_DATASET,
                mMainExecutor,
                new OutcomeReceiver<Void, ThreadNetworkException>() {
                    @Override
                    public void onError(ThreadNetworkException error) {
                        Log.e(TAG, "Failed to join network " + DEFAULT_ACTIVE_DATASET, error);
                    }

                    @Override
                    public void onResult(Void v) {
                        Log.i(TAG, "Successfully Joined");
                    }
                });
    }

    private void doLeave() {
        mThreadController.leave(
                mMainExecutor,
                new OutcomeReceiver<>() {
                    @Override
                    public void onError(ThreadNetworkException error) {
                        Log.e(TAG, "Failed to leave network " + DEFAULT_ACTIVE_DATASET, error);
                    }

                    @Override
                    public void onResult(Void v) {
                        Log.i(TAG, "Successfully Left");
                    }
                });
    }

    private void doMigration() {
        var newActiveDataset =
                new ActiveOperationalDataset.Builder(DEFAULT_ACTIVE_DATASET)
                        .setNetworkName("NewThreadNet")
                        .setActiveTimestamp(OperationalDatasetTimestamp.fromInstant(Instant.now()))
                        .build();
        var pendingDataset =
                new PendingOperationalDataset(
                        newActiveDataset,
                        OperationalDatasetTimestamp.fromInstant(Instant.now()),
                        Duration.ofSeconds(30));
        mThreadController.scheduleMigration(
                pendingDataset,
                mMainExecutor,
                new OutcomeReceiver<Void, ThreadNetworkException>() {
                    @Override
                    public void onResult(Void v) {
                        mMigrateNetworkState.setText(
                                "Scheduled migration to network \"NewThreadNet\" in 30s");
                        // TODO: update Pending Dataset state
                    }

                    @Override
                    public void onError(ThreadNetworkException e) {
                        mMigrateNetworkState.setText(
                                "Failed to schedule migration: " + e.getMessage());
                    }
                });
    }

    private void updateState() {
        Log.i(
                TAG,
                String.format(
                        "Updating Thread states (mDeviceRole: %s)",
                        deviceRoleToString(mDeviceRole)));

        String state =
                String.format(
                        "Role             %s\n"
                                + "Partition ID     %d\n"
                                + "Network Name     %s\n"
                                + "Extended PAN ID  %s",
                        deviceRoleToString(mDeviceRole),
                        mPartitionId,
                        mActiveDataset != null ? mActiveDataset.getNetworkName() : null,
                        mActiveDataset != null
                                ? base16().encode(mActiveDataset.getExtendedPanId())
                                : null);
        mTextState.setText(state);
    }

    private void updateNetworkInfo(LinkProperties linProperties) {
        if (linProperties == null) {
            mTextNetworkInfo.setText("");
            return;
        }

        StringBuilder sb = new StringBuilder("Interface name:\n");
        sb.append(linProperties.getInterfaceName() + "\n");
        sb.append("Addresses:\n");
        for (LinkAddress la : linProperties.getLinkAddresses()) {
            sb.append(la + "\n");
        }
        sb.append("Routes:\n");
        for (RouteInfo route : linProperties.getRoutes()) {
            sb.append(route + "\n");
        }
        mTextNetworkInfo.setText(sb.toString());
    }
}
