/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.bluetooth.avrcp;

import android.os.SystemProperties;

/**
 * A class to represent an AVRCP version
 */
final class AvrcpVersion {
    public static final AvrcpVersion AVRCP_VERSION_1_3 = new AvrcpVersion(1, 3);
    public static final AvrcpVersion AVRCP_VERSION_1_4 = new AvrcpVersion(1, 4);
    public static final AvrcpVersion AVRCP_VERSION_1_5 = new AvrcpVersion(1, 5);
    public static final AvrcpVersion AVRCP_VERSION_1_6 = new AvrcpVersion(1, 6);

    // System settings version strings
    private static final String AVRCP_VERSION_PROPERTY = "persist.bluetooth.avrcpversion";
    private static final String AVRCP_VERSION_1_3_STRING = "avrcp13";
    private static final String AVRCP_VERSION_1_4_STRING = "avrcp14";
    private static final String AVRCP_VERSION_1_5_STRING = "avrcp15";
    private static final String AVRCP_VERSION_1_6_STRING = "avrcp16";

    public int major;
    public int minor;

    public static AvrcpVersion getCurrentSystemPropertiesValue() {
        // Make sure this default version agrees with avrc_api.h's "AVRC_DEFAULT_VERSION"
        String version = SystemProperties.get(AVRCP_VERSION_PROPERTY, AVRCP_VERSION_1_5_STRING);
        switch (version) {
            case AVRCP_VERSION_1_3_STRING:
                return AVRCP_VERSION_1_3;
            case AVRCP_VERSION_1_4_STRING:
                return AVRCP_VERSION_1_4;
            case AVRCP_VERSION_1_5_STRING:
                return AVRCP_VERSION_1_5;
            case AVRCP_VERSION_1_6_STRING:
                return AVRCP_VERSION_1_6;
            default:
                return new AvrcpVersion(-1, -1);
        }
    }

    public boolean isAtleastVersion(AvrcpVersion version) {
        if (version == null) return true;
        if (major < version.major) return false;
        if (major > version.major) return true;
        if (minor < version.minor) return false;
        if (minor > version.minor) return true;
        return true;
    }

    AvrcpVersion(int majorVersion, int minorVersion) {
        major = majorVersion;
        minor = minorVersion;
    }

    public String toString() {
        if (major < 0 || minor < 0) return "Invalid";
        return major + "." + minor;
    }
}
