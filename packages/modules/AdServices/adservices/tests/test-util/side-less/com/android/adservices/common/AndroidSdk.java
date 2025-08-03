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
package com.android.adservices.common;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/** Abstraction for Android SDK levels (so it can be used both on device and host side tests). */
public final class AndroidSdk {

    /** Android version {@code RVC}. */
    public static final int RVC = 30;

    /** Android version {@code SC}. */
    public static final int SC = 31;

    /** Android version {@code SC_V2}. */
    public static final int SC_V2 = 32;

    /**
     * Convenience for ranges that are "less than T" (for example {@code
     * RequiresSdkRange(atMost=PRE_T)}), as S had 2 APIs (31 and 32)
     */
    public static final int PRE_T = SC_V2;

    public static final int TM = 33;
    public static final int UDC = 34;

    /** Represents a specific SDK level. */
    protected enum Level {
        ANY(Integer.MIN_VALUE),
        R(RVC),
        S(SC),
        S2(SC_V2),
        T(TM),
        U(UDC);

        private final int mLevel;

        Level(int level) {
            mLevel = level;
        }

        boolean isAtLeast(Level level) {
            return mLevel >= level.mLevel;
        }

        int getLevel() {
            return mLevel;
        }

        public static Level forLevel(int level) {
            switch (level) {
                case 30:
                    return R;
                case 31:
                    return S;
                case 32:
                    return S2;
                case 33:
                    return T;
                case 34:
                    return U;
            }
            throw new IllegalArgumentException("Unsupported level: " + level);
        }
    }

    /** Represents a range of Android API levels. */
    static final class Range {
        static final int NO_MIN = Integer.MIN_VALUE;
        static final int NO_MAX = Integer.MAX_VALUE;

        private final int mMinLevel;
        private final int mMaxLevel;

        private Range(int minLevel, int maxLevel) {
            if (minLevel > maxLevel || minLevel == NO_MAX || maxLevel == NO_MIN) {
                throw new IllegalArgumentException(
                        "maxLevel ("
                                + maxLevel
                                + ") must equal or higher than minLevel ("
                                + minLevel
                                + ")");
            }
            mMinLevel = minLevel;
            mMaxLevel = maxLevel;
        }

        public static Range forAtLeast(int level) {
            return new Range(/* minLevel= */ level, NO_MAX);
        }

        public static Range forAtMost(int level) {
            return new Range(NO_MIN, /* maxLevel= */ level);
        }

        public static Range forRange(int minLevel, int maxLevel) {
            return new Range(minLevel, maxLevel);
        }

        public static Range forExactly(int level) {
            return new Range(/* minLevel= */ level, /* maxLevel= */ level);
        }

        public static Range forAnyLevel() {
            return new Range(NO_MIN, NO_MAX);
        }

        public boolean isInRange(int level) {
            return level >= mMinLevel && level <= mMaxLevel;
        }

        @VisibleForTesting
        static Range merge(Range... ranges) {
            return merge(Arrays.asList(ranges));
        }

        static Range merge(Collection<Range> ranges) {
            Objects.requireNonNull(ranges, "ranges cannot be null");
            if (ranges.isEmpty()) {
                throw new IllegalArgumentException("ranges cannot be empty");
            }
            int minRange = NO_MIN;
            int maxRange = NO_MAX;
            for (Range range : ranges) {
                if (range == null) {
                    throw new IllegalArgumentException("ranges cannot have null range: " + ranges);
                }
                minRange = Math.max(minRange, range.mMinLevel);
                maxRange = Math.min(maxRange, range.mMaxLevel);
            }
            return forRange(minRange, maxRange);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMaxLevel, mMinLevel);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Range other = (Range) obj;
            return mMaxLevel == other.mMaxLevel && mMinLevel == other.mMinLevel;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("AndroidSdkRange[minLevel=");
            if (mMinLevel == NO_MIN) {
                builder.append("OPEN");
            } else {
                builder.append(mMinLevel);
            }
            builder.append(", maxLevel=");
            if (mMaxLevel == NO_MAX) {
                builder.append("OPEN");
            } else {
                builder.append(mMaxLevel);
            }
            return builder.append(']').toString();
        }
    }

    private AndroidSdk() {
        throw new UnsupportedOperationException();
    }
}
