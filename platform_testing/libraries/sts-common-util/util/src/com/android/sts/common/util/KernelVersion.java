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

package com.android.sts.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tools for parsing kernel version strings */
public final class KernelVersion implements Comparable<KernelVersion> {
    public final int version;
    public final int patchLevel;
    public final int subLevel;

    public KernelVersion(int version, int patchLevel, int subLevel) {
        this.version = version;
        this.patchLevel = patchLevel;
        this.subLevel = subLevel;
    }

    /**
     * Parse a kernel version string in the format "version.patchlevel.sublevel" - "5.4.123".
     * Trailing values are ignored so `uname -r` can be parsed properly.
     *
     * @param versionString The version string to parse
     */
    public static KernelVersion parse(String versionString) {
        Pattern kernelReleasePattern =
                Pattern.compile("(?<version>\\d+)\\.(?<patchLevel>\\d+)\\.(?<subLevel>\\d+)(.*)");
        Matcher matcher = kernelReleasePattern.matcher(versionString);
        if (matcher.find()) {
            return new KernelVersion(
                    Integer.parseInt(matcher.group("version")),
                    Integer.parseInt(matcher.group("patchLevel")),
                    Integer.parseInt(matcher.group("subLevel")));
        }
        throw new IllegalArgumentException(
                String.format("Could not parse kernel version string (%s)", versionString));
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        // 2147483647 (INT_MAX)
        // vvppppssss
        return version * 10000000 + patchLevel * 10000 + subLevel;
    }

    /** Compare by version, patchlevel, and sublevel in that order. */
    public int compareTo(KernelVersion o) {
        if (version != o.version) {
            return Integer.compare(version, o.version);
        }
        if (patchLevel != o.patchLevel) {
            return Integer.compare(patchLevel, o.patchLevel);
        }
        return Integer.compare(subLevel, o.subLevel);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (o instanceof KernelVersion) {
            return this.compareTo((KernelVersion) o) == 0;
        }
        return false;
    }

    /** Format as "version.patchlevel.sublevel" */
    @Override
    public String toString() {
        return String.format("%d.%d.%d", version, patchLevel, subLevel);
    }

    /** Format as "version.patchlevel" */
    public String toStringShort() {
        return String.format("%d.%d", version, patchLevel);
    }
}
