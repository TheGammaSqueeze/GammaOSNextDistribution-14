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

/** State information about a PluginController. */
public enum PluginState implements Parcelable {
    // Plugin is loaded.
    STATE_LOADED,

    // Plugin is not loaded.
    STATE_NO_SERVICE,
    STATE_NOT_LOADED,
    STATE_EXCEPTION_THROWN,

    // Plugin state is unknown.
    STATE_UNKNOWN;

    public static final Creator<PluginState> CREATOR =
            new Creator<PluginState>() {
                @Override
                public PluginState createFromParcel(Parcel in) {
                    return PluginState.values()[in.readInt()];
                }

                @Override
                public PluginState[] newArray(int size) {
                    return new PluginState[size];
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
