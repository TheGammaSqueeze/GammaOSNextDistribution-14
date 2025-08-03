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
package com.google.android.iwlan;

import java.net.InetAddress;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.iwlan.IwlanDataService.IwlanDataServiceProvider;

import java.util.Objects;

public interface TunnelMetricsInterface {
    /** Called for logging the tunnel is opened. */
    void onOpened(OnOpenedMetrics metricsData);
    /** Called for logging the tunnel is closed or bring up failed. */
    void onClosed(OnClosedMetrics metricsData);

    static class TunnelMetricsData {
        private final String mApnName;
        private final String mEpdgServerAddress;
        private final int mEpdgServerSelectionDuration;
        private final int mIkeTunnelEstablishmentDuration;
        private IwlanDataServiceProvider mIwlanDataServiceProvider;

        protected TunnelMetricsData(Builder builder) {
            this.mApnName = builder.mApnName;
            this.mEpdgServerAddress = builder.mEpdgServerAddress;
            this.mEpdgServerSelectionDuration = builder.mEpdgServerSelectionDuration;
            this.mIkeTunnelEstablishmentDuration = builder.mIkeTunnelEstablishmentDuration;
        }

        @Nullable
        public String getApnName() {
            return mApnName;
        }

        @Nullable
        public String getEpdgServerAddress() {
            return mEpdgServerAddress;
        }

        public int getEpdgServerSelectionDuration() {
            return mEpdgServerSelectionDuration;
        }

        public int getIkeTunnelEstablishmentDuration() {
            return mIkeTunnelEstablishmentDuration;
        }

        public IwlanDataServiceProvider getIwlanDataServiceProvider() {
            return mIwlanDataServiceProvider;
        }

        public void setIwlanDataServiceProvider(IwlanDataServiceProvider dsp) {
            mIwlanDataServiceProvider = dsp;
        }

        public static class Builder<T extends Builder> {
            @Nullable private String mApnName = null;
            @Nullable private String mEpdgServerAddress = null;
            private int mEpdgServerSelectionDuration = 0;
            private int mIkeTunnelEstablishmentDuration = 0;

            /** Default constructor for Builder. */
            public Builder() {}

            public T setApnName(@NonNull String apnName) {
                mApnName = Objects.requireNonNull(apnName, "apnName must not be null");
                return (T) this;
            }

            public T setEpdgServerAddress(InetAddress epdgAddress) {
                mEpdgServerAddress = epdgAddress == null ? null : epdgAddress.getHostAddress();
                return (T) this;
            }

            public T setEpdgServerSelectionDuration(int epdgServerSelectionDuration) {
                mEpdgServerSelectionDuration = epdgServerSelectionDuration;
                return (T) this;
            }

            public T setIkeTunnelEstablishmentDuration(int ikeTunnelEstablishmentDuration) {
                mIkeTunnelEstablishmentDuration = ikeTunnelEstablishmentDuration;
                return (T) this;
            }

            public TunnelMetricsData build() {
                if (mApnName == null) {
                    throw new IllegalArgumentException("Necessary parameter missing.");
                }
                return new TunnelMetricsData(this);
            }
        }
    }

    static class OnOpenedMetrics extends TunnelMetricsData {

        protected OnOpenedMetrics(Builder builder) {
            super(builder);
        }

        public static class Builder extends TunnelMetricsData.Builder<Builder> {

            public Builder() {}

            public OnOpenedMetrics build() {
                return new OnOpenedMetrics(this);
            }
        }
    }

    static class OnClosedMetrics extends TunnelMetricsData {

        protected OnClosedMetrics(Builder builder) {
            super(builder);
        }

        public static class Builder extends TunnelMetricsData.Builder<Builder> {

            public Builder() {}

            public OnClosedMetrics build() {
                return new OnClosedMetrics(this);
            }
        }
    }
}
