/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.intentresolver.v2.ui.model

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.android.intentresolver.v2.ext.readParcelable
import com.android.intentresolver.v2.ext.requireParcelable
import java.util.Objects

/** Contains Activity-scope information about the state when started. */
data class ActivityModel(
    /** The [Intent] received by the app */
    val intent: Intent,
    /** The identifier for the sending app and user */
    val launchedFromUid: Int,
    /** The package of the sending app */
    val launchedFromPackage: String,
    /** The referrer as supplied to the activity. */
    val referrer: Uri?
) : Parcelable {
    constructor(
        source: Parcel
    ) : this(
        intent = source.requireParcelable(),
        launchedFromUid = source.readInt(),
        launchedFromPackage = requireNotNull(source.readString()),
        referrer = source.readParcelable()
    )

    /** A package name from referrer, if it is an android-app URI */
    val referrerPackage = referrer?.takeIf { it.scheme == ANDROID_APP_SCHEME }?.authority

    override fun describeContents() = 0 /* flags */

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(intent, flags)
        dest.writeInt(launchedFromUid)
        dest.writeString(launchedFromPackage)
        dest.writeParcelable(referrer, flags)
    }

    companion object {
        const val ACTIVITY_MODEL_KEY = "com.android.intentresolver.ACTIVITY_MODEL"

        @JvmField
        @Suppress("unused")
        val CREATOR =
            object : Parcelable.Creator<ActivityModel> {
                override fun newArray(size: Int) = arrayOfNulls<ActivityModel>(size)
                override fun createFromParcel(source: Parcel) = ActivityModel(source)
            }

        @JvmStatic
        fun createFrom(activity: Activity): ActivityModel {
            return ActivityModel(
                activity.intent,
                activity.launchedFromUid,
                Objects.requireNonNull<String>(activity.launchedFromPackage),
                activity.referrer
            )
        }
    }
}
