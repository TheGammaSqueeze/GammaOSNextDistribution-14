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

package android.telephony.mockmodem;

import android.annotation.NonNull;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;

/**
 * Contains attributes required to determine the domain for a telephony service
 * @hide
 */
public final class MockEmergencyRegResult {

    /**
     * Indicates the cellular network type of the acquired system.
     */
    private @AccessNetworkConstants.RadioAccessNetworkType int mAccessNetworkType;

    /**
     * Registration state of the acquired system.
     */
    private @NetworkRegistrationInfo.RegistrationState int mRegState;

    /**
     * EMC domain indicates the current domain of the acquired system.
     */
    private @NetworkRegistrationInfo.Domain int mDomain;

    /**
     * Indicates whether the network supports voice over PS network.
     */
    private boolean mIsVopsSupported;

    /**
     * This indicates if camped network supports VoLTE emergency bearers.
     * This should only be set if the UE is in LTE mode.
     */
    private boolean mIsEmcBearerSupported;

    /**
     * The value of the network provided EMC in 5G Registration ACCEPT.
     * This should be set only if the UE is in 5G mode.
     */
    private int mNwProvidedEmc;

    /**
     * The value of the network provided EMF(EPS Fallback) in 5G Registration ACCEPT.
     * This should be set only if the UE is in 5G mode.
     */
    private int mNwProvidedEmf;

    /** 3-digit Mobile Country Code, 000..999, empty string if unknown. */
    private @NonNull String mMcc;

    /** 2 or 3-digit Mobile Network Code, 00..999, empty string if unknown. */
    private @NonNull String mMnc;

    /**
     * Constructor
     * @param accessNetwork Indicates the network type of the acquired system.
     * @param regState Indicates the registration state of the acquired system.
     * @param domain Indicates the current domain of the acquired system.
     * @param isVopsSupported Indicates whether the network supports voice over PS network.
     * @param isEmcBearerSupported  Indicates if camped network supports VoLTE emergency bearers.
     * @param emc The value of the network provided EMC in 5G Registration ACCEPT.
     * @param emf The value of the network provided EMF(EPS Fallback) in 5G Registration ACCEPT.
     * @param mcc Mobile country code, empty string if unknown.
     * @param mnc Mobile network code, empty string if unknown.
     */
    public MockEmergencyRegResult(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork,
            @NetworkRegistrationInfo.RegistrationState int regState,
            @NetworkRegistrationInfo.Domain int domain,
            boolean isVopsSupported, boolean isEmcBearerSupported, int emc, int emf,
            @NonNull String mcc, @NonNull String mnc) {
        mAccessNetworkType = accessNetwork;
        mRegState = regState;
        mDomain = domain;
        mIsVopsSupported = isVopsSupported;
        mIsEmcBearerSupported = isEmcBearerSupported;
        mNwProvidedEmc = emc;
        mNwProvidedEmf = emf;
        mMcc = mcc;
        mMnc = mnc;
    }

    /**
     * Returns the cellular access network type of the acquired system.
     *
     * @return the cellular network type.
     */
    public @AccessNetworkConstants.RadioAccessNetworkType int getAccessNetwork() {
        return mAccessNetworkType;
    }

    /**
     * Returns the registration state of the acquired system.
     *
     * @return the registration state.
     */
    public @NetworkRegistrationInfo.RegistrationState int getRegState() {
        return mRegState;
    }

    /**
     * Returns the current domain of the acquired system.
     *
     * @return the current domain.
     */
    public @NetworkRegistrationInfo.Domain int getDomain() {
        return mDomain;
    }

    /**
     * Returns whether the network supports voice over PS network.
     *
     * @return {@code true} if the network supports voice over PS network.
     */
    public boolean isVopsSupported() {
        return mIsVopsSupported;
    }

    /**
     * Returns whether camped network supports VoLTE emergency bearers.
     * This is not valid if the UE is not in LTE mode.
     *
     * @return {@code true} if the network supports VoLTE emergency bearers.
     */
    public boolean isEmcBearerSupported() {
        return mIsEmcBearerSupported;
    }

    /**
     * Returns the value of the network provided EMC in 5G Registration ACCEPT.
     * This is not valid if UE is not in 5G mode.
     *
     * @return the value of the network provided EMC.
     */
    public int getNwProvidedEmc() {
        return mNwProvidedEmc;
    }

    /**
     * Returns the value of the network provided EMF(EPS Fallback) in 5G Registration ACCEPT.
     * This is not valid if UE is not in 5G mode.
     *
     * @return the value of the network provided EMF.
     */
    public int getNwProvidedEmf() {
        return mNwProvidedEmf;
    }

    /**
     * Returns 3-digit Mobile Country Code.
     *
     * @return Mobile Country Code.
     */
    public @NonNull String getMcc() {
        return mMcc;
    }

    /**
     * Returns 2 or 3-digit Mobile Network Code.
     *
     * @return Mobile Network Code.
     */
    public @NonNull String getMnc() {
        return mMnc;
    }
}
