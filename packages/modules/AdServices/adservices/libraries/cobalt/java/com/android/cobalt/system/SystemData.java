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

package com.android.cobalt.system;

import android.os.Build;

import com.google.cobalt.ReportDefinition;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.SystemProfileField;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

/** Manages SystemProfile data for the current system. */
public final class SystemData {
    private static final ImmutableMap<String, SystemProfile.ARCH> ABI_MAP =
            new ImmutableMap.Builder<String, SystemProfile.ARCH>()
                    .put("arm64-v8a", SystemProfile.ARCH.ARM_64)
                    .put("armeabi-v7a", SystemProfile.ARCH.ARM_32)
                    .put("x86_64", SystemProfile.ARCH.X86_64)
                    .put("x86", SystemProfile.ARCH.X86_32)
                    .buildOrThrow();

    private final SystemProfile mCurrentSystemProfile;

    /** Construct a system data object without a system profile. */
    public SystemData() {
        this(/* cobaltAppVersion= */ null);
    }

    public SystemData(String cobaltAppVersion) {
        SystemProfile.Builder systemProfile =
                SystemProfile.newBuilder()
                        .setBoardName(Build.BOARD)
                        .setSystemVersion(Build.VERSION.RELEASE)
                        .setOs(SystemProfile.OS.ANDROID);
        if (Build.SUPPORTED_ABIS.length > 0) {
            if (ABI_MAP.containsKey(Ascii.toLowerCase(Build.SUPPORTED_ABIS[0]))) {
                systemProfile.setArch(
                        ABI_MAP.getOrDefault(
                                Ascii.toLowerCase(Build.SUPPORTED_ABIS[0]),
                                SystemProfile.ARCH.UNKNOWN_ARCH));
            }
        }
        if (!Strings.isNullOrEmpty(cobaltAppVersion)) {
            systemProfile.setAppVersion(cobaltAppVersion);
        }
        mCurrentSystemProfile = systemProfile.build();
    }

    /** Return the current system profile, filtered to the fields specified for this report. */
    public SystemProfile filteredSystemProfile(ReportDefinition report) {
        SystemProfile.Builder systemProfileBuilder = SystemProfile.newBuilder();
        for (SystemProfileField field : report.getSystemProfileFieldList()) {
            switch (field) {
                case APP_VERSION:
                    systemProfileBuilder.setAppVersion(mCurrentSystemProfile.getAppVersion());
                    break;
                case ARCH:
                    systemProfileBuilder.setArch(mCurrentSystemProfile.getArch());
                    break;
                case BOARD_NAME:
                    systemProfileBuilder.setBoardName(mCurrentSystemProfile.getBoardName());
                    break;
                case BUILD_TYPE:
                    systemProfileBuilder.setBuildType(mCurrentSystemProfile.getBuildType());
                    break;
                case CHANNEL:
                    systemProfileBuilder.setChannel(mCurrentSystemProfile.getChannel());
                    break;
                case EXPERIMENT_IDS:
                    // Experiment IDs are not yet supported
                    break;
                case OS:
                    systemProfileBuilder.setOs(mCurrentSystemProfile.getOs());
                    break;
                case PRODUCT_NAME:
                    systemProfileBuilder.setProductName(mCurrentSystemProfile.getProductName());
                    break;
                case SYSTEM_VERSION:
                    systemProfileBuilder.setSystemVersion(mCurrentSystemProfile.getSystemVersion());
                    break;
                case UNRECOGNIZED:
                    throw new AssertionError("Unrecognized SystemProfileField");
            }
        }
        return systemProfileBuilder.build();
    }
}
