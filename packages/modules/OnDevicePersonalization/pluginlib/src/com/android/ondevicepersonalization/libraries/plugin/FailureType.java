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

package com.android.ondevicepersonalization.libraries.plugin;

import android.os.Parcel;
import android.os.Parcelable;

/** FailureType for plugin operations. */
public enum FailureType implements Parcelable {
    ERROR_LOADING_PLUGIN,
    ERROR_EXECUTING_PLUGIN,
    ERROR_UNLOADING_PLUGIN,
    ERROR_UNKNOWN;

    public static final Creator<FailureType> CREATOR =
            new Creator<FailureType>() {
                @Override
                public FailureType createFromParcel(Parcel in) {
                    return FailureType.values()[in.readInt()];
                }

                @Override
                public FailureType[] newArray(int size) {
                    return new FailureType[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.ordinal());
    }
}
