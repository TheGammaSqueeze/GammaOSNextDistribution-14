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

package com.google.uwb.support.generic;

import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.uwb.support.aliro.AliroParams;
import com.google.uwb.support.aliro.AliroSpecificationParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarSpecificationParams;

import java.util.Objects;

/**
 * Defines parameters for generic capability.
 *
 * <p>This is returned as a bundle from the service API {@link UwbManager#getSpecificationInfo}.
 */
public class GenericSpecificationParams extends GenericParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private FiraSpecificationParams mFiraSpecificationParams;
    private final CccSpecificationParams mCccSpecificationParams;
    private final AliroSpecificationParams mAliroSpecificationParams;
    private final RadarSpecificationParams mRadarSpecificationParams;
    private final boolean mHasPowerStatsSupport;

    private static final String KEY_FIRA_SPECIFICATION_PARAMS = FiraParams.PROTOCOL_NAME;
    private static final String KEY_ALIRO_SPECIFICATION_PARAMS = AliroParams.PROTOCOL_NAME;
    private static final String KEY_CCC_SPECIFICATION_PARAMS = CccParams.PROTOCOL_NAME;
    private static final String KEY_RADAR_SPECIFICATION_PARAMS = RadarParams.PROTOCOL_NAME;
    private static final String KEY_POWER_STATS_QUERY_SUPPORT = "power_stats_query";

    private GenericSpecificationParams(
            FiraSpecificationParams firaSpecificationParams,
            CccSpecificationParams cccSpecificationParams,
            AliroSpecificationParams aliroSpecificationParams,
            RadarSpecificationParams radarSpecificationParams,
            boolean hasPowerStatsSupport) {
        mFiraSpecificationParams = firaSpecificationParams;
        mCccSpecificationParams = cccSpecificationParams;
        mAliroSpecificationParams = aliroSpecificationParams;
        mRadarSpecificationParams = radarSpecificationParams;
        mHasPowerStatsSupport = hasPowerStatsSupport;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Nullable
    public FiraSpecificationParams getFiraSpecificationParams() {
        return mFiraSpecificationParams;
    }

    @Nullable
    public CccSpecificationParams getCccSpecificationParams() {
        return mCccSpecificationParams;
    }

    @Nullable
    public AliroSpecificationParams getAliroSpecificationParams() {
        return mAliroSpecificationParams;
    }

    @Nullable
    public RadarSpecificationParams getRadarSpecificationParams() {
        return mRadarSpecificationParams;
    }

    /**
     * @return if the power stats is supported
     */
    public boolean hasPowerStatsSupport() {
        return mHasPowerStatsSupport;
    }

    public void setFiraSpecificationParams(FiraSpecificationParams params) {
        mFiraSpecificationParams = params;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putPersistableBundle(KEY_FIRA_SPECIFICATION_PARAMS,
                mFiraSpecificationParams.toBundle());
        if (mAliroSpecificationParams != null) {
            bundle.putPersistableBundle(KEY_ALIRO_SPECIFICATION_PARAMS,
                    mAliroSpecificationParams.toBundle());
        }
        if (mCccSpecificationParams != null) {
            bundle.putPersistableBundle(KEY_CCC_SPECIFICATION_PARAMS,
                    mCccSpecificationParams.toBundle());
        }
        if (mRadarSpecificationParams != null) {
            bundle.putPersistableBundle(KEY_RADAR_SPECIFICATION_PARAMS,
                    mRadarSpecificationParams.toBundle());
        }
        bundle.putBoolean(KEY_POWER_STATS_QUERY_SUPPORT, mHasPowerStatsSupport);
        return bundle;
    }

    public static GenericSpecificationParams fromBundle(PersistableBundle bundle) {
        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static GenericSpecificationParams parseVersion1(PersistableBundle bundle) {
        GenericSpecificationParams.Builder builder = new GenericSpecificationParams.Builder();
        builder = builder.setFiraSpecificationParams(
                FiraSpecificationParams.fromBundle(
                        bundle.getPersistableBundle(KEY_FIRA_SPECIFICATION_PARAMS)))
                .hasPowerStatsSupport(bundle.getBoolean(KEY_POWER_STATS_QUERY_SUPPORT));
        PersistableBundle cccBundle = bundle.getPersistableBundle(KEY_CCC_SPECIFICATION_PARAMS);
        if (cccBundle != null) {
            builder = builder.setCccSpecificationParams(
                    CccSpecificationParams.fromBundle(cccBundle));
        }

        PersistableBundle aliroBundle = bundle.getPersistableBundle(KEY_ALIRO_SPECIFICATION_PARAMS);
        if (aliroBundle != null) {
            builder = builder.setAliroSpecificationParams(
                    AliroSpecificationParams.fromBundle(aliroBundle));
        }

        PersistableBundle radarBundle = bundle.getPersistableBundle(
                KEY_RADAR_SPECIFICATION_PARAMS);
        if (radarBundle != null) {
            builder = builder.setRadarSpecificationParams(
                    RadarSpecificationParams.fromBundle(radarBundle));
        }
        return builder.build();
    }

    /** Builder */
    public static class Builder {
        private FiraSpecificationParams mFiraSpecificationParams = null;
        private CccSpecificationParams mCccSpecificationParams = null;
        private AliroSpecificationParams mAliroSpecificationParams = null;
        private RadarSpecificationParams mRadarSpecificationParams = null;
        private boolean mHasPowerStatsSupport = false;

        /**
         * Set FIRA specification params
         */
        public Builder setFiraSpecificationParams(
                @NonNull FiraSpecificationParams firaSpecificationParams) {
            mFiraSpecificationParams = Objects.requireNonNull(firaSpecificationParams);
            return this;
        }

        /**
         * Set CCC specification params
         */
        public Builder setCccSpecificationParams(
                @NonNull CccSpecificationParams cccSpecificationParams) {
            mCccSpecificationParams = Objects.requireNonNull(cccSpecificationParams);
            return this;
        }

        /**
         * Set ALIRO specification params.
         */
        public Builder setAliroSpecificationParams(
                @NonNull AliroSpecificationParams aliroSpecificationParams) {
            mAliroSpecificationParams = Objects.requireNonNull(aliroSpecificationParams);
            return this;
        }

        /**
         * Set RADAR specification params
         */
        public Builder setRadarSpecificationParams(
                @NonNull RadarSpecificationParams radarSpecificationParams) {
            mRadarSpecificationParams = Objects.requireNonNull(radarSpecificationParams);
            return this;
        }

        /**
         * Sets if the power stats is supported
         */
        public Builder hasPowerStatsSupport(boolean value) {
            mHasPowerStatsSupport = value;
            return this;
        }

        /**
         * Build {@link GenericSpecificationParams}
         */
        public GenericSpecificationParams build() {
            return new GenericSpecificationParams(
                    mFiraSpecificationParams,
                    mCccSpecificationParams,
                    mAliroSpecificationParams,
                    mRadarSpecificationParams,
                    mHasPowerStatsSupport);
        }
    }
}
