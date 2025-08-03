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

package com.android.car.carlauncher;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.carlauncher.LauncherItemProto.LauncherItemMessage;

/**
 * LauncherItem can be an app or a folder that contains app
 */
public abstract class LauncherItem implements Parcelable {
    private String mPackageName;
    private String mClassName;
    private String mDisplayName;

    public LauncherItem(String packageName, String className, String displayName) {
        mPackageName = packageName;
        mClassName = className;
        mDisplayName = displayName;
    }

    protected LauncherItem(Parcel in) {
        mPackageName = in.readString();
        mClassName = in.readString();
        mDisplayName = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeString(mClassName);
        dest.writeString(mDisplayName);
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getClassName() {
        return mClassName;
    }


    /**
     * This method is used to convert a LauncherItem to a protobuf class
     */
    public LauncherItemMessage convertToMessage(int relativePosition, int containerID) {
        LauncherItemMessage.Builder builder = LauncherItemMessage.newBuilder()
                .setPackageName(mPackageName)
                .setClassName(mClassName)
                .setDisplayName(mDisplayName)
                .setRelativePosition(relativePosition)
                .setContainerID(containerID);
        return builder.build();
    }

    /**
     * This method should return true if the two LauncherItems contain the same app and metadata.
     */
    abstract boolean areContentsTheSame(LauncherItem launcherItem);
}
