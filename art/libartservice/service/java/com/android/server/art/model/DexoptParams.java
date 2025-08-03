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

package com.android.server.art.model;

import static com.android.server.art.model.ArtFlags.DexoptFlags;
import static com.android.server.art.model.ArtFlags.PriorityClassApi;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.Immutable;
import com.android.server.art.ArtConstants;
import com.android.server.art.ReasonMapping;
import com.android.server.art.Utils;

/** @hide */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Immutable
public class DexoptParams {
    /** @hide */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    public static final class Builder {
        private DexoptParams mParams = new DexoptParams();

        /**
         * Creates a builder.
         *
         * Uses default flags ({@link ArtFlags#defaultDexoptFlags()}).
         *
         * @param reason Compilation reason. Can be a string defined in {@link ReasonMapping} or a
         *         custom string. If the value is a string defined in {@link ReasonMapping}, it
         *         determines the compiler filter and/or the priority class, if those values are not
         *         explicitly set. If the value is a custom string, the priority class and the
         *         compiler filter must be explicitly set.
         */
        public Builder(@NonNull String reason) {
            this(reason, ArtFlags.defaultDexoptFlags(reason));
        }

        /**
         * Same as above, but allows to specify flags.
         */
        public Builder(@NonNull String reason, @DexoptFlags int flags) {
            mParams.mReason = reason;
            setFlags(flags);
        }

        /** Replaces all flags with the given value. */
        @NonNull
        public Builder setFlags(@DexoptFlags int value) {
            mParams.mFlags = value;
            return this;
        }

        /** Replaces the flags specified by the mask with the given value. */
        @NonNull
        public Builder setFlags(@DexoptFlags int value, @DexoptFlags int mask) {
            mParams.mFlags = (mParams.mFlags & ~mask) | (value & mask);
            return this;
        }

        /**
         * The target compiler filter, passed as the {@code --compiler-filer} option to dex2oat.
         * Supported values are listed in
         * https://source.android.com/docs/core/dalvik/configure#compilation_options.
         *
         * Note that the compiler filter might be adjusted before the execution based on factors
         * like dexopt flags, whether the profile is available, or whether the app is used by other
         * apps. If not set, the default compiler filter for the given reason will be used.
         */
        @NonNull
        public Builder setCompilerFilter(@NonNull String value) {
            mParams.mCompilerFilter = value;
            return this;
        }

        /**
         * The priority of the operation. If not set, the default priority class for the given
         * reason will be used.
         *
         * @see PriorityClassApi
         */
        @NonNull
        public Builder setPriorityClass(@PriorityClassApi int value) {
            mParams.mPriorityClass = value;
            return this;
        }

        /**
         * The name of the split to dexopt, or null for the base split. This option is only
         * available when {@link ArtFlags#FLAG_FOR_SINGLE_SPLIT} is set.
         */
        @NonNull
        public Builder setSplitName(@Nullable String value) {
            mParams.mSplitName = value;
            return this;
        }

        /**
         * Returns the built object.
         *
         * @throws IllegalArgumentException if the built options would be invalid
         */
        @NonNull
        public DexoptParams build() {
            if (mParams.mReason.isEmpty()) {
                throw new IllegalArgumentException("Reason must not be empty");
            }
            if (mParams.mReason.equals(ArtConstants.REASON_VDEX)) {
                throw new IllegalArgumentException(
                        "Reason must not be '" + ArtConstants.REASON_VDEX + "'");
            }

            if (mParams.mCompilerFilter.isEmpty()) {
                mParams.mCompilerFilter = ReasonMapping.getCompilerFilterForReason(mParams.mReason);
            } else if (!Utils.isValidArtServiceCompilerFilter(mParams.mCompilerFilter)) {
                throw new IllegalArgumentException(
                        "Invalid compiler filter '" + mParams.mCompilerFilter + "'");
            }

            if (mParams.mPriorityClass == ArtFlags.PRIORITY_NONE) {
                mParams.mPriorityClass = ReasonMapping.getPriorityClassForReason(mParams.mReason);
            } else if (mParams.mPriorityClass < 0 || mParams.mPriorityClass > 100) {
                throw new IllegalArgumentException("Invalid priority class "
                        + mParams.mPriorityClass + ". Must be between 0 and 100");
            }

            if ((mParams.mFlags & (ArtFlags.FLAG_FOR_PRIMARY_DEX | ArtFlags.FLAG_FOR_SECONDARY_DEX))
                    == 0) {
                throw new IllegalArgumentException("Nothing to dexopt");
            }

            if ((mParams.mFlags & ArtFlags.FLAG_FOR_PRIMARY_DEX) == 0
                    && (mParams.mFlags & ArtFlags.FLAG_SHOULD_INCLUDE_DEPENDENCIES) != 0) {
                throw new IllegalArgumentException(
                        "FLAG_SHOULD_INCLUDE_DEPENDENCIES must not set if FLAG_FOR_PRIMARY_DEX is "
                        + "not set.");
            }

            if ((mParams.mFlags & ArtFlags.FLAG_FOR_SINGLE_SPLIT) != 0) {
                if ((mParams.mFlags & ArtFlags.FLAG_FOR_PRIMARY_DEX) == 0) {
                    throw new IllegalArgumentException(
                            "FLAG_FOR_PRIMARY_DEX must be set when FLAG_FOR_SINGLE_SPLIT is set");
                }
                if ((mParams.mFlags
                            & (ArtFlags.FLAG_FOR_SECONDARY_DEX
                                    | ArtFlags.FLAG_SHOULD_INCLUDE_DEPENDENCIES))
                        != 0) {
                    throw new IllegalArgumentException(
                            "FLAG_FOR_SECONDARY_DEX and FLAG_SHOULD_INCLUDE_DEPENDENCIES must "
                            + "not be set when FLAG_FOR_SINGLE_SPLIT is set");
                }
            } else {
                if (mParams.mSplitName != null) {
                    throw new IllegalArgumentException(
                            "Split name must not be set when FLAG_FOR_SINGLE_SPLIT is not set");
                }
            }

            return mParams;
        }
    }

    /**
     * A value indicating that dexopt shouldn't be run. This value is consumed by ART Services and
     * is not propagated to dex2oat.
     */
    public static final String COMPILER_FILTER_NOOP = "skip";

    private @DexoptFlags int mFlags = 0;
    private @NonNull String mCompilerFilter = "";
    private @PriorityClassApi int mPriorityClass = ArtFlags.PRIORITY_NONE;
    private @NonNull String mReason = "";
    private @Nullable String mSplitName = null;

    private DexoptParams() {}

    /** Returns all flags. */
    public @DexoptFlags int getFlags() {
        return mFlags;
    }

    /** The target compiler filter. */
    public @NonNull String getCompilerFilter() {
        return mCompilerFilter;
    }

    /** The priority class. */
    public @PriorityClassApi int getPriorityClass() {
        return mPriorityClass;
    }

    /**
     * The compilation reason.
     *
     * DO NOT directly use the string value to determine the resource usage and the process
     * priority. Use {@link #getPriorityClass}.
     */
    public @NonNull String getReason() {
        return mReason;
    }

    /** The name of the split to dexopt, or null for the base split. */
    public @Nullable String getSplitName() {
        return mSplitName;
    }

    /** @hide */
    public @NonNull Builder toBuilder() {
        return new Builder(mReason, mFlags)
                .setCompilerFilter(mCompilerFilter)
                .setPriorityClass(mPriorityClass)
                .setSplitName(mSplitName);
    }
}
