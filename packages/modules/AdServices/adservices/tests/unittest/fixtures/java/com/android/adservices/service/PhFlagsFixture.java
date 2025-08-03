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

package com.android.adservices.service;

import static com.android.adservices.service.FlagsConstants.KEY_AD_ID_FETCHER_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CPC_BILLING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_CLEANUP_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;

import android.provider.DeviceConfig;

/**
 * In order to use this test fixture, make sure your UNIT test class includes a {@link
 * TestableDeviceConfigRule} like the following:
 *
 * <p>{@code @Rule public final TestableDeviceConfig.TestableDeviceConfigRule deviceConfigRule = new
 * TestableDeviceConfig.TestableDeviceConfigRule(); }
 *
 * <p>If you're using it on CTS tests, you need to make sure the callers have the {@code
 * Manifest.permission.WRITE_DEVICE_CONFIG}, but a better approach would be to use {@link
 * com.android.adservices.common.AdServicesFlagsSetterRule} instead (as that rule will take care of
 * automatically resetting the flags to the initial value, among other features).
 *
 * @deprecated TODO(b/317418539) - most usages (especially on CTS) are better suited by the {@link
 *     com.android.adservices.common.AdServicesFlagsSetterRule} JUnit rule, which provides other
 *     advantages like properly resetting the flag at the end of the test (while tests using this
 *     fixture need to manually use a {@code try / finally} block to do so) and supporting
 *     annotations. So, assuming your test already uses the rule, all you need to do is to annotate
 *     the test method with the proper annotations (like
 *     {@code @SetFlagEnabled(FlagsConstants.MY_FEATURE_FLAG}} or
 *     {@code @SetIntegerFlag(name=FlagsConstants.MY_CONFIG_FLAG}, value=42)} or call the {@code
 *     setFlag()} method of the rule when the value is not static (for example, if the rule
 *     reference is called @{code flags}, simply call {@code
 *     flags.setFlag(FlagsConstant.MY_PGK_ALLOWLIST_FLAG, pkgNames)}).
 */
@Deprecated
public final class PhFlagsFixture {

    /** Enables test to override the flag enabling ad selection filtering */
    public static void overrideFledgeAdSelectionFilteringEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Fledge Run Ad Selection API.
     */
    public static void overrideForegroundStatusForFledgeRunAdSelection(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Fledge Report Impression API.
     */
    public static void overrideForegroundStatusForFledgeReportImpression(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Fledge Report Interaction API.
     */
    public static void overrideForegroundStatusForFledgeReportInteraction(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Fledge Override API.
     */
    public static void overrideForegroundStatusForFledgeOverrides(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Custom Audience API.
     */
    public static void overrideForegroundStatusForFledgeCustomAudience(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the enrollment check for callers of Fledge APIs.
     *
     * @param enable whether enable or disable the check
     */
    public static void overrideFledgeEnrollmentCheck(boolean enable) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK,
                Boolean.toString(!enable),
                false);
    }

    /**
     * Enables test to override the flag enabling Protected Audience's event-level debug reporting.
     *
     * @param enable whether enable or disable the check
     */
    public static void overrideFledgeEventLevelDebugReportingEnabled(boolean enable) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED,
                Boolean.toString(enable),
                false);
    }

    public static void overrideFledgeEventLevelDebugReportSendImmediately(boolean enable) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY,
                Boolean.toString(enable),
                false);
    }

    public static void overrideFledgeEventLevelDebugReportingBatchDelay(int batchDelayInSeconds) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS,
                Integer.toString(batchDelayInSeconds),
                false);
    }

    public static void overrideFledgeEventLevelDebugReportingMaxItemsPerBatch(
            int maxItemsPerBatch) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH,
                Integer.toString(maxItemsPerBatch),
                false);
    }

    public static void overrideFledgeDebugReportSenderJobNetworkConnectionTimeoutMs(
            int phOverrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);
    }

    public static void overrideFledgeDebugReportSenderJobNetworkReadTimeoutMs(int phOverrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_NETWORK_READ_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);
    }

    public static void overrideFledgeDebugReportSenderJobMaxRuntimeMs(long phOverrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_MAX_TIMEOUT_MS,
                Long.toString(phOverrideValue),
                false);
    }

    public static void overrideFledgeDebugReportSenderJobPeriodicMs(long phOverrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORT_SENDER_JOB_PERIOD_MS,
                Long.toString(phOverrideValue),
                false);
    }

    public static void overrideFledgeDebugReportSenderJobFlexMs(long phOverrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORT_SENDER_JOB_FLEX_MS,
                Long.toString(phOverrideValue),
                false);
    }

    public static void overrideEnforceIsolateMaxHeapSize(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE,
                Boolean.toString(value),
                false);
    }

    public static void overrideIsolateMaxHeapSizeBytes(long value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES,
                Long.toString(value),
                false);
    }

    /** Enables or disables the HTTP cache */
    public static void overrideHttpClientCacheEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE,
                Boolean.toString(value),
                false);
    }

    public static void overrideSdkRequestPermitsPerSecond(int value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_SDK_REQUEST_PERMITS_PER_SECOND,
                Integer.toString(value),
                true);
    }

    /** Overrides whether the {@code registerAdBeacon} feature is enabled. */
    public static void overrideFledgeRegisterAdBeaconEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED,
                Boolean.toString(value),
                false);
    }

    /** Overrides whether the CPC billing feature is enabled. */
    public static void overrideFledgeCpcBillingEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CPC_BILLING_ENABLED,
                Boolean.toString(value),
                false);
    }

    /** Overrides whether the protected signals cleanup runs. */
    public static void overrideProtectedSignalsCleanupEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_PROTECTED_SIGNALS_CLEANUP_ENABLED,
                Boolean.toString(value),
                false);
    }

    /** Overrides whether the CPC billing feature is enabled. */
    public static void overrideFledgeDataVersionHeaderEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED,
                Boolean.toString(value),
                false);
    }

    /** Overrides whether the {@code prebuilt Uri} feature is enabled. */
    public static void overrideFledgeAdSelectionPrebuiltUriEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED,
                Boolean.toString(value),
                false);
    }

    /** Overrides whether the auction server APIs are enabled. */
    public static void overrideFledgeAdSelectionAuctionServerApisEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH,
                Boolean.toString(value),
                false);
    }

    /** Overrides the timeouts for on-device auctions. */
    public static void overrideFledgeOnDeviceAdSelectionTimeouts(
            long biddingTimeoutPerCaMs, long scoringTimeoutMs, long overallTimeoutMs) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                Long.toString(biddingTimeoutPerCaMs),
                false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                Long.toString(scoringTimeoutMs),
                false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS,
                Long.toString(overallTimeoutMs),
                false);
    }

    /** Overrides timeout for {@link com.android.adservices.service.adselection.AdIdFetcher}. */
    public static void overrideAdIdFetcherTimeoutMs(long timeoutMs) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_AD_ID_FETCHER_TIMEOUT_MS,
                Long.toString(timeoutMs),
                false);
    }

    // DO NOT ADD MORE METHODS HERE, THIS CLASS IS DEPRECATED.
    //
    // If you still need a new method instead of using AdServicesFlagsSetterRule or other infra,
    // please add a TODO(b/317418539) comment on such method explaining why (and include a
    // Bug: 317418539 line in the CL that adds it).
}
