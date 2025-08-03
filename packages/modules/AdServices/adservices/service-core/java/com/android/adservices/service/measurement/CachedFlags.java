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

package com.android.adservices.service.measurement;

import com.android.adservices.service.Flags;

/** Class for cached Phenotype flags. */
public class CachedFlags {
    private final boolean mEnableSessionStableKillSwitches;
    private final boolean mApiRegisterSourceKillSwitch;
    private final boolean mApiRegisterSourcesKillSwitch;
    private final boolean mApiRegisterWebSourceKillSwitch;
    private final boolean mApiRegisterTriggerKillSwitch;
    private final boolean mApiRegisterWebTriggerKillSwitch;
    private final boolean mApiDeleteRegistrationsKillSwitch;
    private final boolean mApiStatusKillSwitch;
    private final boolean mEnforceForegroundStatusForMeasurementRegisterWebSource;
    private final boolean mEnforceForegroundStatusForMeasurementRegisterWebTrigger;
    private final boolean mEnforceForegroundStatusForMeasurementRegisterSource;
    private final boolean mEnforceForegroundStatusForMeasurementRegisterTrigger;
    private final boolean mEnforceForegroundStatusForMeasurementRegisterSources;
    private final boolean mEnforceForegroundStatusForMeasurementDeleteRegistrations;
    private final boolean mEnforceForegroundStatusForMeasurementStatus;
    private final boolean mEnableApiStatusAllowListCheck;
    private final boolean mConsentNotifiedDebugMode;
    private final String mApiAppAllowList;
    private final String mApiAppBlockList;
    private final String mWebContextClientAppAllowList;
    private final Flags mFlags;

    public CachedFlags(Flags flags) {
        mFlags = flags;
        mEnableSessionStableKillSwitches = flags.getMeasurementEnableSessionStableKillSwitches();
        mApiRegisterSourceKillSwitch = flags.getMeasurementApiRegisterSourceKillSwitch();
        mApiRegisterSourcesKillSwitch = flags.getMeasurementApiRegisterSourcesKillSwitch();
        mApiRegisterWebSourceKillSwitch = flags.getMeasurementApiRegisterWebSourceKillSwitch();
        mApiRegisterTriggerKillSwitch = flags.getMeasurementApiRegisterTriggerKillSwitch();
        mApiRegisterWebTriggerKillSwitch = flags.getMeasurementApiRegisterWebTriggerKillSwitch();
        mApiDeleteRegistrationsKillSwitch = flags.getMeasurementApiDeleteRegistrationsKillSwitch();
        mApiStatusKillSwitch = flags.getMeasurementApiStatusKillSwitch();
        mEnforceForegroundStatusForMeasurementRegisterWebTrigger =
                flags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger();
        mEnforceForegroundStatusForMeasurementRegisterWebSource =
                flags.getEnforceForegroundStatusForMeasurementRegisterWebSource();
        mEnforceForegroundStatusForMeasurementRegisterTrigger =
                flags.getEnforceForegroundStatusForMeasurementRegisterTrigger();
        mEnforceForegroundStatusForMeasurementRegisterSource =
                flags.getEnforceForegroundStatusForMeasurementRegisterSource();
        mEnforceForegroundStatusForMeasurementRegisterSources =
                flags.getEnforceForegroundStatusForMeasurementRegisterSources();
        mEnforceForegroundStatusForMeasurementDeleteRegistrations =
                flags.getEnforceForegroundStatusForMeasurementDeleteRegistrations();
        mEnforceForegroundStatusForMeasurementStatus =
                flags.getEnforceForegroundStatusForMeasurementStatus();
        mApiAppAllowList = flags.getMsmtApiAppAllowList();
        mApiAppBlockList = flags.getMsmtApiAppBlockList();
        mWebContextClientAppAllowList = flags.getWebContextClientAppAllowList();
        mEnableApiStatusAllowListCheck = flags.getMsmtEnableApiStatusAllowListCheck();
        mConsentNotifiedDebugMode = flags.getConsentNotifiedDebugMode();
    }

    public boolean getMeasurementApiRegisterSourceKillSwitch() {
        return mEnableSessionStableKillSwitches
                ? mApiRegisterSourceKillSwitch
                : mFlags.getMeasurementApiRegisterSourceKillSwitch();
    }

    public boolean getMeasurementApiRegisterSourcesKillSwitch() {
        return mEnableSessionStableKillSwitches
                ? mApiRegisterSourcesKillSwitch
                : mFlags.getMeasurementApiRegisterSourcesKillSwitch();
    }

    public boolean getMeasurementApiRegisterWebSourceKillSwitch() {
        return mEnableSessionStableKillSwitches
                ? mApiRegisterWebSourceKillSwitch
                : mFlags.getMeasurementApiRegisterWebSourceKillSwitch();
    }

    public boolean getMeasurementApiRegisterTriggerKillSwitch() {
        return mEnableSessionStableKillSwitches
                ? mApiRegisterTriggerKillSwitch
                : mFlags.getMeasurementApiRegisterTriggerKillSwitch();
    }

    public boolean getMeasurementApiRegisterWebTriggerKillSwitch() {
        return mEnableSessionStableKillSwitches
                ? mApiRegisterWebTriggerKillSwitch
                : mFlags.getMeasurementApiRegisterWebTriggerKillSwitch();
    }

    public boolean getMeasurementApiDeleteRegistrationsKillSwitch() {
        return mEnableSessionStableKillSwitches
                ? mApiDeleteRegistrationsKillSwitch
                : mFlags.getMeasurementApiDeleteRegistrationsKillSwitch();
    }

    public boolean getMeasurementApiStatusKillSwitch() {
        return mEnableSessionStableKillSwitches
                ? mApiStatusKillSwitch
                : mFlags.getMeasurementApiStatusKillSwitch();
    }

    public boolean getEnforceForegroundStatusForMeasurementRegisterSource() {
        return mEnableSessionStableKillSwitches
                ? mEnforceForegroundStatusForMeasurementRegisterSource
                : mFlags.getEnforceForegroundStatusForMeasurementRegisterSource();
    }

    public boolean getEnforceForegroundStatusForMeasurementRegisterTrigger() {
        return mEnableSessionStableKillSwitches
                ? mEnforceForegroundStatusForMeasurementRegisterTrigger
                : mFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger();
    }

    public boolean getEnforceForegroundStatusForMeasurementRegisterSources() {
        return mEnableSessionStableKillSwitches
                ? mEnforceForegroundStatusForMeasurementRegisterSources
                : mFlags.getEnforceForegroundStatusForMeasurementRegisterSources();
    }

    public boolean getEnforceForegroundStatusForMeasurementRegisterWebSource() {
        return mEnableSessionStableKillSwitches
                ? mEnforceForegroundStatusForMeasurementRegisterWebSource
                : mFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource();
    }

    public boolean getEnforceForegroundStatusForMeasurementRegisterWebTrigger() {
        return mEnableSessionStableKillSwitches
                ? mEnforceForegroundStatusForMeasurementRegisterWebTrigger
                : mFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger();
    }

    public boolean getEnforceForegroundStatusForMeasurementDeleteRegistrations() {
        return mEnableSessionStableKillSwitches
                ? mEnforceForegroundStatusForMeasurementDeleteRegistrations
                : mFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations();
    }

    public boolean getMsmtEnableApiStatusAllowListCheck() {
        return mEnableSessionStableKillSwitches
                ? mEnableApiStatusAllowListCheck
                : mFlags.getMsmtEnableApiStatusAllowListCheck();
    }

    public boolean getConsentNotifiedDebugMode() {
        return mEnableSessionStableKillSwitches
                ? mConsentNotifiedDebugMode
                : mFlags.getConsentNotifiedDebugMode();
    }

    public boolean getEnforceForegroundStatusForMeasurementStatus() {
        return mEnableSessionStableKillSwitches
                ? mEnforceForegroundStatusForMeasurementStatus
                : mFlags.getEnforceForegroundStatusForMeasurementStatus();
    }

    public String getMsmtApiAppAllowList() {
        return mEnableSessionStableKillSwitches
                ? mApiAppAllowList
                : mFlags.getMsmtApiAppAllowList();
    }

    public String getMsmtApiAppBlockList() {
        return mEnableSessionStableKillSwitches
                ? mApiAppBlockList
                : mFlags.getMsmtApiAppBlockList();
    }

    public String getWebContextClientAppAllowList() {
        return mEnableSessionStableKillSwitches
                ? mWebContextClientAppAllowList
                : mFlags.getWebContextClientAppAllowList();
    }
}
