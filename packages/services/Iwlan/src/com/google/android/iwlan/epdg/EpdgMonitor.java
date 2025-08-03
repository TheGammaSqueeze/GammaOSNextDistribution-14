/*
 * Copyright 2024 The Android Open Source Project
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

package com.google.android.iwlan.epdg;

import android.annotation.NonNull;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

public class EpdgMonitor {
    private InetAddress mEpdgAddressForNormalSession;
    private InetAddress mSeparateEpdgAddressForEmergencySession;
    private Set<String> mApnConnectToNormalEpdg = new HashSet<>();
    private Set<String> mApnConnectToEmergencyEpdg = new HashSet<>();
    private boolean mHasEmergencyPdnFailedWithConnectedEpdg = false;

    public EpdgMonitor() {}

    /**
     * Called when an APN connects to an ePDG. Tracks the ePDG address and associated APN.
     *
     * @param apnName The name of the Access Point Name (APN).
     * @param address The InetAddress of the connected ePDG.
     */
    public void onApnConnectToEpdg(@NonNull String apnName, @NonNull InetAddress address) {
        if (address.equals(getEpdgAddressForEmergencySession())) {
            mApnConnectToEmergencyEpdg.add(apnName);
            return;
        }

        if (address.equals(getEpdgAddressForNormalSession())) {
            mApnConnectToNormalEpdg.add(apnName);
            return;
        }

        if (!hasEpdgConnectedForNormalSession()) {
            mEpdgAddressForNormalSession = address;
            mApnConnectToNormalEpdg.clear();
            mApnConnectToNormalEpdg.add(apnName);
        } else {
            mSeparateEpdgAddressForEmergencySession = address;
            mApnConnectToEmergencyEpdg.clear();
            mApnConnectToEmergencyEpdg.add(apnName);
        }
    }

    /**
     * Called when an APN disconnects from an ePDG. Updates internal tracking of APN connections to
     * ePDGs.
     *
     * @param apnName The name of the Access Point Name (APN) that disconnected.
     */
    public void onApnDisconnectFromEpdg(String apnName) {
        if (mApnConnectToNormalEpdg.contains(apnName)) {
            mApnConnectToNormalEpdg.remove(apnName);
            if (mApnConnectToNormalEpdg.isEmpty()) {
                mEpdgAddressForNormalSession = null;
                if (hasSeparateEpdgConnectedForEmergencySession()) {
                    // If ePDG for normal session has no PDN and emergency PDN established on an
                    // separate ePDG, mark the ePDG for emergency as ePDG for normal session.
                    mEpdgAddressForNormalSession = mSeparateEpdgAddressForEmergencySession;
                    mApnConnectToNormalEpdg = mApnConnectToEmergencyEpdg;
                    mSeparateEpdgAddressForEmergencySession = null;
                    mApnConnectToEmergencyEpdg.clear();
                    mHasEmergencyPdnFailedWithConnectedEpdg = false;
                }
            }
        } else if (mApnConnectToEmergencyEpdg.contains(apnName)) {
            mApnConnectToEmergencyEpdg.remove(apnName);
            if (mApnConnectToEmergencyEpdg.isEmpty()) {
                mSeparateEpdgAddressForEmergencySession = null;
            }
        }

        if (!hasEpdgConnectedForNormalSession()) {
            mHasEmergencyPdnFailedWithConnectedEpdg = false;
        }
    }

    /**
     * Returns the ePDG address currently associated with normal PDN sessions.
     *
     * @return The InetAddress of the ePDG for normal sessions, or null if not connected.
     */
    public InetAddress getEpdgAddressForNormalSession() {
        return mEpdgAddressForNormalSession;
    }

    /**
     * Returns the ePDG address currently associated with emergency PDN sessions.
     *
     * @return The InetAddress of the ePDG for emergency sessions, or null if not connected.
     */
    public InetAddress getEpdgAddressForEmergencySession() {
        return mSeparateEpdgAddressForEmergencySession;
    }

    /**
     * Checks whether an ePDG connection is established for normal PDN sessions.
     *
     * @return True if an ePDG is connected for normal sessions, false otherwise.
     */
    public boolean hasEpdgConnectedForNormalSession() {
        return mEpdgAddressForNormalSession != null;
    }

    /**
     * Checks whether an separate ePDG connection is established for emergency PDN sessions.
     *
     * @return True if an separate ePDG is connected for emergency sessions, false otherwise.
     */
    public boolean hasSeparateEpdgConnectedForEmergencySession() {
        return mSeparateEpdgAddressForEmergencySession != null;
    }

    /**
     * Checks whether ePDG connection is established.
     *
     * @return True if ePDG is connected, false otherwise.
     */
    public boolean hasEpdgConnected() {
        return hasEpdgConnectedForNormalSession() || hasSeparateEpdgConnectedForEmergencySession();
    }

    /**
     * Indicates whether an attempt to establish an emergency PDN has failed while a normal PDN
     * session is active on the same ePDG.
     *
     * @return True if an emergency PDN establishment has failed on the connected ePDG, false
     *     otherwise.
     */
    public boolean hasEmergencyPdnFailedWithConnectedEpdg() {
        return mHasEmergencyPdnFailedWithConnectedEpdg;
    }

    /**
     * Called when a connection to an ePDG fails. Updates internal tracking and flags if an
     * emergency PDN failure occurred on a previously connected ePDG.
     *
     * @param isEmergency Indicates whether the failed ePDG connection was for an emergency session.
     * @param epdgAddress The InetAddress of the ePDG where the connection failed.
     */
    public void onEpdgConnectionFailed(boolean isEmergency, InetAddress epdgAddress) {
        if (isEmergency
                && hasEpdgConnectedForNormalSession()
                && epdgAddress.equals(getEpdgAddressForNormalSession())) {
            mHasEmergencyPdnFailedWithConnectedEpdg = true;
        }
    }

    /**
     * Determines if the provided EPDG address represents a connected EPDG (i.e., is the EPDG
     * address used for normal or emergency sessions).
     *
     * @param epdgAddress The EPDG address to check.
     * @return `true` if the provided EPDG address is a connected EPDG address, `false` otherwise.
     */
    public boolean isConnectedEpdg(InetAddress epdgAddress) {
        return epdgAddress.equals(getEpdgAddressForNormalSession())
                || epdgAddress.equals(getEpdgAddressForEmergencySession());
    }
}
