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

package com.android.bedstead.metricsrecorder;

import com.android.os.nano.AtomsProto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EnterpriseMetricInfo {
    private final String mAdminPackageName;
    private final int mType;
    private final boolean mBoolean;
    private final List<String> mStrings;
    private final int mInteger;

    EnterpriseMetricInfo(AtomsProto.DevicePolicyEvent event) {
        mAdminPackageName = event.adminPackageName;
        mType = event.eventId;
        mBoolean = event.booleanValue;
        mStrings = (event.stringListValue == null) ? new ArrayList<>() : Arrays.asList(
                event.stringListValue.stringValue);
        mInteger = event.integerValue;
    }

    /** Admin package name. */
    public String adminPackageName() {
        return mAdminPackageName;
    }

    /** Type of metric. */
    public int type() {
        return mType;
    }

    /** Arbitrary boolean value. */
    public boolean Boolean() {
        return mBoolean;
    }

    /** Arbitrary list of strings. */
    public List<String> strings() {
        return mStrings;
    }

    /** Arbitrary integer value. */
    public int integer() {
        return mInteger;
    }

    @Override
    public String toString() {
        return "EnterpriseMetricInfo{"
                + "adminPackageName=" + mAdminPackageName
                + ", type=" + mType
                + ", boolean=" + mBoolean
                + ", strings=" + mStrings
                + ", integer=" + mInteger
                + "}";
    }
}
