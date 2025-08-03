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

package com.android.server.wifi.p2p;

import android.annotation.Nullable;
import android.net.MacAddress;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Manage external approvers for Wi-Fi Direct peers.
 */
public class ExternalApproverManager {
    private static final String TAG = "ExternalApproverManager";

    Map<Pair<IBinder, MacAddress>, ApproverEntry> mApprovers = new HashMap<>();

    // Look-up table for device addresses.
    Map<MacAddress, ApproverEntry> mApproverByAddress = new HashMap<>();

    private boolean mVerboseLoggingEnabled = false;

    /**
     * Store the approver.
     *
     * @param key The client binder.
     * @param deviceAddress The peer device address.
     * @param message The approver message which is used for the callback.
     * @return The previous entry associated with the key & the peer, or null if there was
     *         no mapping for key, peer pair
     */
    public ApproverEntry put(@Nullable IBinder key,
            @Nullable MacAddress deviceAddress, @Nullable Message message) {
        if (null == key) return null;
        if (null == deviceAddress) return null;
        if (null == message) return null;

        // Use look-up table to ensure that only one approver is bounded to a peer.
        ApproverEntry existEntry = mApproverByAddress.get(deviceAddress);
        if (null != existEntry) {
            logd("Replace an existing approver: " + existEntry);
            mApprovers.remove(new Pair<>(existEntry.getKey(), existEntry.getAddress()));
            mApproverByAddress.remove(existEntry.getAddress());
        }

        // Make a copy of message, or it might be modified externally.
        ApproverEntry newEntry = new ApproverEntry(
                key, deviceAddress, Message.obtain(message));
        mApprovers.put(new Pair(key, deviceAddress), newEntry);
        mApproverByAddress.put(deviceAddress, newEntry);
        logd("Add an approver: " + newEntry);
        return existEntry;
    }

    /** Return approvers associated with a client. */
    public List<ApproverEntry> get(@Nullable IBinder key) {
        if (null == key) return null;

        return mApprovers.entrySet().stream()
                .filter(e -> e.getKey().first.equals(key))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /** Return the approver associated with a peer. */
    public ApproverEntry get(@Nullable MacAddress deviceAddress) {
        if (null == deviceAddress) return null;
        return mApproverByAddress.get(deviceAddress);
    }

    /** Return the approver associated with a client and a peer. */
    public ApproverEntry get(@Nullable IBinder key,
            @Nullable MacAddress deviceAddress) {
        if (null == key) return null;
        if (null == deviceAddress) return null;
        return mApprovers.get(new Pair<>(key, deviceAddress));
    }

    /** Remove the approver associated with a peer. */
    public ApproverEntry remove(@Nullable MacAddress deviceAddress) {
        if (null == deviceAddress) return null;
        ApproverEntry entry = mApproverByAddress.remove(deviceAddress);
        if (null != entry) {
            mApprovers.remove(new Pair<>(entry.getKey(), entry.getAddress()));
        }
        return entry;
    }

    /** Remove the approver associated with a client and a peer. */
    public ApproverEntry remove(@Nullable IBinder key,
            @Nullable MacAddress deviceAddress) {
        if (null == key) return null;
        if (null == deviceAddress) return null;

        ApproverEntry entry = mApprovers.remove(new Pair<>(key, deviceAddress));
        if (null == entry) return null;

        mApproverByAddress.remove(deviceAddress);
        return entry;
    }

    /** Remove approvers associated with a client. */
    public void removeAll(@Nullable IBinder key) {
        if (null == key) return;

        List<ApproverEntry> entries = get(key);
        entries.forEach(e -> {
            remove(e.getKey(), e.getAddress());
        });
    }

    /** Enable verbose logging. */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    private void logd(String s) {
        if (!mVerboseLoggingEnabled) return;
        Log.d(TAG, s, null);
    }

    /** The approver data. */
    public class ApproverEntry {
        IBinder  mIBinder;
        MacAddress mDeviceAddress;
        Message mMessage;

        private ApproverEntry() {
        }

        public ApproverEntry(IBinder key, MacAddress deviceAddress, Message message) {
            mIBinder = key;
            mDeviceAddress = deviceAddress;
            mMessage = message;
        }

        public IBinder getKey() {
            return mIBinder;
        }

        public MacAddress getAddress() {
            return mDeviceAddress;
        }

        public Message getMessage() {
            return Message.obtain(mMessage);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ApproverEntry that = (ApproverEntry) o;
            return Objects.equals(this.mIBinder, that.mIBinder)
                    && Objects.equals(this.mDeviceAddress, that.mDeviceAddress)
                    && Objects.equals(this.mMessage, that.mMessage);
        }

        @Override
        public int hashCode() {
            int _hash = 1;
            _hash = 31 * _hash +  Objects.hashCode(mIBinder);
            _hash = 31 * _hash +  Objects.hashCode(mDeviceAddress);
            _hash = 31 * _hash +  Objects.hashCode(mMessage);
            return _hash;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Approver {IBinder=").append(mIBinder.toString())
                    .append(", Peer=").append(mDeviceAddress)
                    .append(", Message=").append(mMessage).append("}");
            return sb.toString();
        }
    }
}
