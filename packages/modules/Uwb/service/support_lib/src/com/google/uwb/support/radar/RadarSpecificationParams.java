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

package com.google.uwb.support.radar;

import android.os.PersistableBundle;
import android.uwb.UwbManager;

import com.google.uwb.support.base.FlagEnum;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Defines parameters for Radar capability reports
 *
 * <p>This is returned as a bundle from the service API {@link UwbManager#getSpecificationInfo}.
 */
public class RadarSpecificationParams extends RadarParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final EnumSet<RadarCapabilityFlag> mRadarCapabilities;

    private static final String KEY_RADAR_CAPABILITIES = "radar_capabilities";

    private RadarSpecificationParams(EnumSet<RadarCapabilityFlag> radarCapabilities) {
        mRadarCapabilities = radarCapabilities;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_RADAR_CAPABILITIES, FlagEnum.toInt(mRadarCapabilities));
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RadarSpecificationParams} */
    public static RadarSpecificationParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static RadarSpecificationParams parseVersion1(PersistableBundle bundle) {
        return new RadarSpecificationParams.Builder()
                .setRadarCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_RADAR_CAPABILITIES),
                                RadarCapabilityFlag.values()))
                .build();
    }

    public EnumSet<RadarCapabilityFlag> getRadarCapabilities() {
        return mRadarCapabilities;
    }

    /** Builder */
    public static final class Builder {
        private final EnumSet<RadarCapabilityFlag> mRadarCapabilities =
                EnumSet.noneOf(RadarCapabilityFlag.class);

        /** Adds a collection of {@link RadarCapabilityFlag} */
        public Builder setRadarCapabilities(Collection<RadarCapabilityFlag> radarCapabilities) {
            mRadarCapabilities.addAll(radarCapabilities);
            return this;
        }

        /** Adds a single {@link RadarCapabilityFlag} */
        public Builder addRadarCapability(RadarCapabilityFlag radarCapability) {
            mRadarCapabilities.add(radarCapability);
            return this;
        }

        /** Build {@link RadarSpecificationParams} */
        public RadarSpecificationParams build() {
            return new RadarSpecificationParams(mRadarCapabilities);
        }
    }
}
