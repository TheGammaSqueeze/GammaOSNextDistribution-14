/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.connectivity.mdns;

import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * The class that contains mDNS feature flags;
 */
public class MdnsFeatureFlags {
    /**
     * A feature flag to control whether the mDNS offload is enabled or not.
     */
    public static final String NSD_FORCE_DISABLE_MDNS_OFFLOAD = "nsd_force_disable_mdns_offload";

    /**
     * A feature flag to control whether the probing question should include
     * InetAddressRecords or not.
     */
    public static final String INCLUDE_INET_ADDRESS_RECORDS_IN_PROBING =
            "include_inet_address_records_in_probing";
    /**
     * A feature flag to control whether expired services removal should be enabled.
     */
    public static final String NSD_EXPIRED_SERVICES_REMOVAL =
            "nsd_expired_services_removal";

    /**
     * A feature flag to control whether the label count limit should be enabled.
     */
    public static final String NSD_LIMIT_LABEL_COUNT = "nsd_limit_label_count";

    /**
     * A feature flag to control whether the known-answer suppression should be enabled.
     */
    public static final String NSD_KNOWN_ANSWER_SUPPRESSION = "nsd_known_answer_suppression";

    /**
     * A feature flag to control whether unicast replies should be enabled.
     *
     * <p>Enabling this feature causes replies to queries with the Query Unicast (QU) flag set to be
     * sent unicast instead of multicast, as per RFC6762 5.4.
     */
    public static final String NSD_UNICAST_REPLY_ENABLED = "nsd_unicast_reply_enabled";

    /**
     * A feature flag to control whether the aggressive query mode should be enabled.
     */
    public static final String NSD_AGGRESSIVE_QUERY_MODE = "nsd_aggressive_query_mode";

    // Flag for offload feature
    public final boolean mIsMdnsOffloadFeatureEnabled;

    // Flag for including InetAddressRecords in probing questions.
    public final boolean mIncludeInetAddressRecordsInProbing;

    // Flag for expired services removal
    public final boolean mIsExpiredServicesRemovalEnabled;

    // Flag for label count limit
    public final boolean mIsLabelCountLimitEnabled;

    // Flag for known-answer suppression
    public final boolean mIsKnownAnswerSuppressionEnabled;

    // Flag to enable replying unicast to queries requesting unicast replies
    public final boolean mIsUnicastReplyEnabled;

    // Flag for aggressive query mode
    public final boolean mIsAggressiveQueryModeEnabled;

    @Nullable
    private final FlagOverrideProvider mOverrideProvider;

    /**
     * A provider that can indicate whether a flag should be force-enabled for testing purposes.
     */
    public interface FlagOverrideProvider {
        /**
         * Indicates whether the flag should be force-enabled for testing purposes.
         */
        boolean isForceEnabledForTest(@NonNull String flag);
    }

    /**
     * Indicates whether the flag should be force-enabled for testing purposes.
     */
    private boolean isForceEnabledForTest(@NonNull String flag) {
        return mOverrideProvider != null && mOverrideProvider.isForceEnabledForTest(flag);
    }

    /**
     * Indicates whether {@link #NSD_UNICAST_REPLY_ENABLED} is enabled, including for testing.
     */
    public boolean isUnicastReplyEnabled() {
        return mIsUnicastReplyEnabled || isForceEnabledForTest(NSD_UNICAST_REPLY_ENABLED);
    }

    /**
     * Indicates whether {@link #NSD_AGGRESSIVE_QUERY_MODE} is enabled, including for testing.
     */
    public boolean isAggressiveQueryModeEnabled() {
        return mIsAggressiveQueryModeEnabled || isForceEnabledForTest(NSD_AGGRESSIVE_QUERY_MODE);
    }

    /**
     * Indicates whether {@link #NSD_KNOWN_ANSWER_SUPPRESSION} is enabled, including for testing.
     */
    public boolean isKnownAnswerSuppressionEnabled() {
        return mIsKnownAnswerSuppressionEnabled
                || isForceEnabledForTest(NSD_KNOWN_ANSWER_SUPPRESSION);
    }

    /**
     * The constructor for {@link MdnsFeatureFlags}.
     */
    public MdnsFeatureFlags(boolean isOffloadFeatureEnabled,
            boolean includeInetAddressRecordsInProbing,
            boolean isExpiredServicesRemovalEnabled,
            boolean isLabelCountLimitEnabled,
            boolean isKnownAnswerSuppressionEnabled,
            boolean isUnicastReplyEnabled,
            boolean isAggressiveQueryModeEnabled,
            @Nullable FlagOverrideProvider overrideProvider) {
        mIsMdnsOffloadFeatureEnabled = isOffloadFeatureEnabled;
        mIncludeInetAddressRecordsInProbing = includeInetAddressRecordsInProbing;
        mIsExpiredServicesRemovalEnabled = isExpiredServicesRemovalEnabled;
        mIsLabelCountLimitEnabled = isLabelCountLimitEnabled;
        mIsKnownAnswerSuppressionEnabled = isKnownAnswerSuppressionEnabled;
        mIsUnicastReplyEnabled = isUnicastReplyEnabled;
        mIsAggressiveQueryModeEnabled = isAggressiveQueryModeEnabled;
        mOverrideProvider = overrideProvider;
    }


    /** Returns a {@link Builder} for {@link MdnsFeatureFlags}. */
    public static Builder newBuilder() {
        return new Builder();
    }

    /** A builder to create {@link MdnsFeatureFlags}. */
    public static final class Builder {

        private boolean mIsMdnsOffloadFeatureEnabled;
        private boolean mIncludeInetAddressRecordsInProbing;
        private boolean mIsExpiredServicesRemovalEnabled;
        private boolean mIsLabelCountLimitEnabled;
        private boolean mIsKnownAnswerSuppressionEnabled;
        private boolean mIsUnicastReplyEnabled;
        private boolean mIsAggressiveQueryModeEnabled;
        private FlagOverrideProvider mOverrideProvider;

        /**
         * The constructor for {@link Builder}.
         */
        public Builder() {
            mIsMdnsOffloadFeatureEnabled = false;
            mIncludeInetAddressRecordsInProbing = false;
            mIsExpiredServicesRemovalEnabled = false;
            mIsLabelCountLimitEnabled = true; // Default enabled.
            mIsKnownAnswerSuppressionEnabled = false;
            mIsUnicastReplyEnabled = true;
            mIsAggressiveQueryModeEnabled = false;
            mOverrideProvider = null;
        }

        /**
         * Set whether the mDNS offload feature is enabled.
         *
         * @see #NSD_FORCE_DISABLE_MDNS_OFFLOAD
         */
        public Builder setIsMdnsOffloadFeatureEnabled(boolean isMdnsOffloadFeatureEnabled) {
            mIsMdnsOffloadFeatureEnabled = isMdnsOffloadFeatureEnabled;
            return this;
        }

        /**
         * Set whether the probing question should include InetAddressRecords.
         *
         * @see #INCLUDE_INET_ADDRESS_RECORDS_IN_PROBING
         */
        public Builder setIncludeInetAddressRecordsInProbing(
                boolean includeInetAddressRecordsInProbing) {
            mIncludeInetAddressRecordsInProbing = includeInetAddressRecordsInProbing;
            return this;
        }

        /**
         * Set whether the expired services removal is enabled.
         *
         * @see #NSD_EXPIRED_SERVICES_REMOVAL
         */
        public Builder setIsExpiredServicesRemovalEnabled(boolean isExpiredServicesRemovalEnabled) {
            mIsExpiredServicesRemovalEnabled = isExpiredServicesRemovalEnabled;
            return this;
        }

        /**
         * Set whether the label count limit is enabled.
         *
         * @see #NSD_LIMIT_LABEL_COUNT
         */
        public Builder setIsLabelCountLimitEnabled(boolean isLabelCountLimitEnabled) {
            mIsLabelCountLimitEnabled = isLabelCountLimitEnabled;
            return this;
        }

        /**
         * Set whether the known-answer suppression is enabled.
         *
         * @see #NSD_KNOWN_ANSWER_SUPPRESSION
         */
        public Builder setIsKnownAnswerSuppressionEnabled(boolean isKnownAnswerSuppressionEnabled) {
            mIsKnownAnswerSuppressionEnabled = isKnownAnswerSuppressionEnabled;
            return this;
        }

        /**
         * Set whether the unicast reply feature is enabled.
         *
         * @see #NSD_UNICAST_REPLY_ENABLED
         */
        public Builder setIsUnicastReplyEnabled(boolean isUnicastReplyEnabled) {
            mIsUnicastReplyEnabled = isUnicastReplyEnabled;
            return this;
        }

        /**
         * Set a {@link FlagOverrideProvider} to be used by {@link #isForceEnabledForTest(String)}.
         *
         * If non-null, features that use {@link #isForceEnabledForTest(String)} will use that
         * provider to query whether the flag should be force-enabled.
         */
        public Builder setOverrideProvider(@Nullable FlagOverrideProvider overrideProvider) {
            mOverrideProvider = overrideProvider;
            return this;
        }

        /**
         * Set whether the aggressive query mode is enabled.
         *
         * @see #NSD_AGGRESSIVE_QUERY_MODE
         */
        public Builder setIsAggressiveQueryModeEnabled(boolean isAggressiveQueryModeEnabled) {
            mIsAggressiveQueryModeEnabled = isAggressiveQueryModeEnabled;
            return this;
        }

        /**
         * Builds a {@link MdnsFeatureFlags} with the arguments supplied to this builder.
         */
        public MdnsFeatureFlags build() {
            return new MdnsFeatureFlags(mIsMdnsOffloadFeatureEnabled,
                    mIncludeInetAddressRecordsInProbing,
                    mIsExpiredServicesRemovalEnabled,
                    mIsLabelCountLimitEnabled,
                    mIsKnownAnswerSuppressionEnabled,
                    mIsUnicastReplyEnabled,
                    mIsAggressiveQueryModeEnabled,
                    mOverrideProvider);
        }
    }
}
