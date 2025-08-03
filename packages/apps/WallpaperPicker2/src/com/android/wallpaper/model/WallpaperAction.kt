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
package com.android.wallpaper.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

data class WallpaperAction(
    val label: String?,
    val applyActionUri: Uri?,
    val effectId: String?,
    var toggled: Boolean
) : Parcelable {
    constructor(
        parcel: Parcel
    ) : this(
        parcel.readString(),
        parcel.readParcelable(Uri::class.java.classLoader, Uri::class.java),
        parcel.readString(),
        parcel.readByte() != 0.toByte()
    ) {}

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(label)
        parcel.writeParcelable(applyActionUri, flags)
        parcel.writeString(effectId)
        parcel.writeByte(if (toggled) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<WallpaperAction> {
        override fun createFromParcel(parcel: Parcel): WallpaperAction {
            return WallpaperAction(parcel)
        }

        override fun newArray(size: Int): Array<WallpaperAction?> {
            return arrayOfNulls(size)
        }
    }
}
