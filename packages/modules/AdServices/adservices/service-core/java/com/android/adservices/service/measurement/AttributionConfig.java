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

package com.android.adservices.service.measurement;

import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.END;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.EXPIRY;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.FILTER_DATA;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.POST_INSTALL_EXCLUSIVITY_WINDOW;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.PRIORITY;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.SOURCE_EXPIRY_OVERRIDE;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.SOURCE_FILTERS;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.SOURCE_NETWORK;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.SOURCE_NOT_FILTERS;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.SOURCE_PRIORITY_RANGE;
import static com.android.adservices.service.measurement.AttributionConfig.AttributionConfigContract.START;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.MathUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

/** POJO for AttributionConfig. */
public class AttributionConfig {

    @NonNull private final String mSourceAdtech;
    @Nullable private final Pair<Long, Long> mSourcePriorityRange;
    @Nullable private final List<FilterMap> mSourceFilters;
    @Nullable private final List<FilterMap> mSourceNotFilters;
    @Nullable private final Long mSourceExpiryOverride;
    @Nullable private final Long mPriority;
    @Nullable private final Long mExpiry;
    @Nullable private final FilterMap mFilterData;
    @Nullable private final Long mPostInstallExclusivityWindow;

    private AttributionConfig(@NonNull AttributionConfig.Builder builder) {
        mSourceAdtech = builder.mSourceAdtech;
        mSourcePriorityRange = builder.mSourcePriorityRange;
        mSourceFilters = builder.mSourceFilters;
        mSourceNotFilters = builder.mSourceNotFilters;
        mSourceExpiryOverride = builder.mSourceExpiryOverride;
        mPriority = builder.mPriority;
        mExpiry = builder.mExpiry;
        mFilterData = builder.mFilterData;
        mPostInstallExclusivityWindow = builder.mPostInstallExclusivityWindow;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributionConfig)) {
            return false;
        }
        AttributionConfig attributionConfig = (AttributionConfig) obj;
        return Objects.equals(mSourceAdtech, attributionConfig.mSourceAdtech)
                && Objects.equals(mSourcePriorityRange, attributionConfig.mSourcePriorityRange)
                && Objects.equals(mSourceFilters, attributionConfig.mSourceFilters)
                && Objects.equals(mSourceNotFilters, attributionConfig.mSourceNotFilters)
                && Objects.equals(mSourceExpiryOverride, attributionConfig.mSourceExpiryOverride)
                && Objects.equals(mPriority, attributionConfig.mPriority)
                && Objects.equals(mExpiry, attributionConfig.mExpiry)
                && Objects.equals(mFilterData, attributionConfig.mFilterData)
                && Objects.equals(
                        mPostInstallExclusivityWindow,
                        attributionConfig.mPostInstallExclusivityWindow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSourceAdtech,
                mSourcePriorityRange,
                mSourceFilters,
                mSourceNotFilters,
                mSourceExpiryOverride,
                mPriority,
                mExpiry,
                mFilterData,
                mPostInstallExclusivityWindow);
    }

    /** Returns the source adtech as String. */
    @NonNull
    public String getSourceAdtech() {
        return mSourceAdtech;
    }

    /**
     * Returns source priority range JSONObject as a Pair of Long values. example:
     * "source_priority_range": { “start”: 100, “end”: 1000 }
     */
    @Nullable
    public Pair<Long, Long> getSourcePriorityRange() {
        return mSourcePriorityRange;
    }

    /**
     * Returns source filter JSONObject as List of FilterMap. example: "source_filters": {
     * "campaign_type": ["install"], "source_type": ["navigation"] }
     */
    @Nullable
    public List<FilterMap> getSourceFilters() {
        return mSourceFilters;
    }

    /**
     * Returns source not filter JSONObject as List of FilterMap. example: "source_not_filters": {
     * "campaign_type": ["install"] }
     */
    @Nullable
    public List<FilterMap> getSourceNotFilters() {
        return mSourceNotFilters;
    }

    /**
     * Returns source expiry override as long. Source registration time + source expiry override <
     * trigger time.
     */
    @Nullable
    public Long getSourceExpiryOverride() {
        return mSourceExpiryOverride;
    }

    /** Returns the derived priority of the source as long */
    @Nullable
    public Long getPriority() {
        return mPriority;
    }

    /**
     * Returns the derived source expiry in {@link java.util.concurrent.TimeUnit#SECONDS} as long.
     */
    @Nullable
    public Long getExpiry() {
        return mExpiry;
    }

    /**
     * Returns the derived filter data of the source as List of FilterMap. example: "filter_data": {
     * "conversion_subdomain": ["electronics.megastore"], "product": ["1234", "234"] }
     */
    @Nullable
    public FilterMap getFilterData() {
        return mFilterData;
    }

    /** Returns the derived post install exclusivity window as long. */
    @Nullable
    public Long getPostInstallExclusivityWindow() {
        return mPostInstallExclusivityWindow;
    }

    /**
     * Serializes the object as JSON. This is consistent with the format that is received in the
     * response headers as well as stored as is in the database.
     *
     * @return serialized JSON object
     */
    @Nullable
    public JSONObject serializeAsJson(Flags flags) {
        try {
            JSONObject attributionConfig = new JSONObject();
            attributionConfig.put(SOURCE_NETWORK, mSourceAdtech);

            if (mSourcePriorityRange != null) {
                JSONObject sourcePriorityRange = new JSONObject();
                sourcePriorityRange.put(START, mSourcePriorityRange.first);
                sourcePriorityRange.put(END, mSourcePriorityRange.second);
                attributionConfig.put(SOURCE_PRIORITY_RANGE, sourcePriorityRange);
            }

            Filter filter = new Filter(flags);
            if (mSourceFilters != null) {
                attributionConfig.put(SOURCE_FILTERS, filter.serializeFilterSet(mSourceFilters));
            }

            if (mSourceNotFilters != null) {
                attributionConfig.put(
                        SOURCE_NOT_FILTERS, filter.serializeFilterSet(mSourceNotFilters));
            }

            if (mSourceExpiryOverride != null) {
                attributionConfig.put(SOURCE_EXPIRY_OVERRIDE, mSourceExpiryOverride);
            }

            if (mPriority != null) {
                attributionConfig.put(PRIORITY, mPriority);
            }

            if (mExpiry != null) {
                attributionConfig.put(EXPIRY, mExpiry);
            }

            if (mFilterData != null) {
                attributionConfig.put(FILTER_DATA, mFilterData.serializeAsJson(flags));
            }

            if (mPostInstallExclusivityWindow != null) {
                attributionConfig.put(
                        POST_INSTALL_EXCLUSIVITY_WINDOW, mPostInstallExclusivityWindow);
            }

            return attributionConfig;
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger().d(e, "Serializing attribution config failed");
            return null;
        }
    }

    /** Builder for {@link AttributionConfig}. */
    public static final class Builder {
        private String mSourceAdtech;
        private Pair<Long, Long> mSourcePriorityRange;
        private List<FilterMap> mSourceFilters;
        private List<FilterMap> mSourceNotFilters;
        private Long mSourceExpiryOverride;
        private Long mPriority;
        private Long mExpiry;
        private FilterMap mFilterData;
        private Long mPostInstallExclusivityWindow;

        public Builder() {}

        /**
         * Parses the string serialized json object under an {@link AttributionConfig}.
         *
         * @throws JSONException if JSON parsing fails
         */
        public Builder(@NonNull JSONObject attributionConfigsJson, Flags flags)
                throws JSONException {
            if (attributionConfigsJson == null) {
                throw new JSONException(
                        "AttributionConfig.Builder: Empty or null attributionConfigsJson");
            }
            if (attributionConfigsJson.isNull(SOURCE_NETWORK)) {
                throw new JSONException(
                        "AttributionConfig.Builder: Required field source_network is not present.");
            }

            mSourceAdtech = attributionConfigsJson.getString(SOURCE_NETWORK);
            Filter filter = new Filter(flags);
            if (!attributionConfigsJson.isNull(SOURCE_PRIORITY_RANGE)) {
                JSONObject sourcePriorityRangeJson =
                        attributionConfigsJson.getJSONObject(SOURCE_PRIORITY_RANGE);
                mSourcePriorityRange =
                        new Pair<>(
                                sourcePriorityRangeJson.getLong(START),
                                sourcePriorityRangeJson.getLong(END));
            }
            if (!attributionConfigsJson.isNull(SOURCE_FILTERS)) {
                JSONArray filterSet =
                        Filter.maybeWrapFilters(attributionConfigsJson, SOURCE_FILTERS);
                mSourceFilters = filter.deserializeFilterSet(filterSet);
            }
            if (!attributionConfigsJson.isNull(SOURCE_NOT_FILTERS)) {
                JSONArray filterSet =
                        Filter.maybeWrapFilters(attributionConfigsJson, SOURCE_NOT_FILTERS);
                mSourceNotFilters = filter.deserializeFilterSet(filterSet);
            }
            if (!attributionConfigsJson.isNull(SOURCE_EXPIRY_OVERRIDE)) {
                long override = attributionConfigsJson.getLong(SOURCE_EXPIRY_OVERRIDE);
                mSourceExpiryOverride =
                        MathUtils.extractValidNumberInRange(
                                override,
                                flags.getMeasurementMinReportingRegisterSourceExpirationInSeconds(),
                                flags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds());
            }
            if (!attributionConfigsJson.isNull(PRIORITY)) {
                mPriority = attributionConfigsJson.getLong(PRIORITY);
            }
            if (!attributionConfigsJson.isNull(EXPIRY)) {
                long expiry = attributionConfigsJson.getLong(EXPIRY);
                mExpiry =
                        MathUtils.extractValidNumberInRange(
                                expiry,
                                flags.getMeasurementMinReportingRegisterSourceExpirationInSeconds(),
                                flags
                                        .getMeasurementMaxReportingRegisterSourceExpirationInSeconds());
            }
            if (!attributionConfigsJson.isNull(FILTER_DATA)) {
                mFilterData =
                        new FilterMap.Builder()
                                .buildFilterData(
                                        attributionConfigsJson.optJSONObject(FILTER_DATA), flags)
                                .build();
            }
            if (!attributionConfigsJson.isNull(POST_INSTALL_EXCLUSIVITY_WINDOW)) {
                mPostInstallExclusivityWindow =
                        attributionConfigsJson.getLong(POST_INSTALL_EXCLUSIVITY_WINDOW);
            }
        }

        /** See {@link AttributionConfig#getSourceAdtech()} */
        @NonNull
        public Builder setSourceAdtech(@NonNull String sourceAdtech) {
            Objects.requireNonNull(sourceAdtech);
            mSourceAdtech = sourceAdtech;
            return this;
        }

        /** See {@link AttributionConfig#getSourcePriorityRange()} */
        @NonNull
        public Builder setSourcePriorityRange(@Nullable Pair<Long, Long> sourcePriorityRange) {
            mSourcePriorityRange = sourcePriorityRange;
            return this;
        }

        /** See {@link AttributionConfig#getSourceFilters()} */
        @NonNull
        public Builder setSourceFilters(@Nullable List<FilterMap> sourceFilters) {
            mSourceFilters = sourceFilters;
            return this;
        }

        /** See {@link AttributionConfig#getSourceNotFilters()} */
        @NonNull
        public Builder setSourceNotFilters(@Nullable List<FilterMap> sourceNotFilters) {
            mSourceNotFilters = sourceNotFilters;
            return this;
        }

        /** See {@link AttributionConfig#getSourceExpiryOverride()} */
        @NonNull
        public Builder setSourceExpiryOverride(@Nullable Long sourceExpiryOverride) {
            mSourceExpiryOverride = sourceExpiryOverride;
            return this;
        }

        /** See {@link AttributionConfig#getPriority()} */
        @NonNull
        public Builder setPriority(@Nullable Long priority) {
            mPriority = priority;
            return this;
        }

        /** See {@link AttributionConfig#getExpiry()} */
        @NonNull
        public Builder setExpiry(@Nullable Long expiry) {
            mExpiry = expiry;
            return this;
        }

        /** See {@link AttributionConfig#getFilterData()} */
        @NonNull
        public Builder setFilterData(@Nullable FilterMap filterData) {
            mFilterData = filterData;
            return this;
        }

        /** See {@link AttributionConfig#getPostInstallExclusivityWindow()} */
        @NonNull
        public Builder setPostInstallExclusivityWindow(
                @Nullable Long postInstallExclusivityWindow) {
            mPostInstallExclusivityWindow = postInstallExclusivityWindow;
            return this;
        }

        /** Build the {@link AttributionConfig}. */
        @NonNull
        public AttributionConfig build() {
            Objects.requireNonNull(mSourceAdtech);
            return new AttributionConfig(this);
        }
    }

    /** Attribution Config field keys. */
    public interface AttributionConfigContract {
        String SOURCE_NETWORK = "source_network";
        String SOURCE_PRIORITY_RANGE = "source_priority_range";
        String SOURCE_FILTERS = "source_filters";
        String SOURCE_NOT_FILTERS = "source_not_filters";
        String SOURCE_EXPIRY_OVERRIDE = "source_expiry_override";
        String PRIORITY = "priority";
        String EXPIRY = "expiry";
        String FILTER_DATA = "filter_data";
        String POST_INSTALL_EXCLUSIVITY_WINDOW = "post_install_exclusivity_window";
        String START = "start";
        String END = "end";
    }
}
