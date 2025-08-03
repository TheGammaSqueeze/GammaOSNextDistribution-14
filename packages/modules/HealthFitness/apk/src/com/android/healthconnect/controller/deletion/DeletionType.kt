/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.deletion

import android.os.Parcel
import android.os.Parcelable
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.HealthDataCategoryInt

/** Represents the types of deletion that the user can perform. */
sealed class DeletionType : Parcelable {
    class DeletionTypeAllData() : DeletionType() {

        @Suppress(
            "UNUSED_PARAMETER") // the class has no data to write but inherits from a Parcelable
        constructor(parcel: Parcel) : this() {}

        override fun writeToParcel(parcel: Parcel, flags: Int) {}

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<DeletionTypeAllData> {
            override fun createFromParcel(parcel: Parcel): DeletionTypeAllData {
                return DeletionTypeAllData(parcel)
            }

            override fun newArray(size: Int): Array<DeletionTypeAllData?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class DeletionTypeHealthPermissionTypeData(
        val healthPermissionType: HealthPermissionType
    ) : DeletionType() {
        constructor(
            parcel: Parcel
        ) : this(
            HealthPermissionType.valueOf(
                parcel.readString() ?: HealthPermissionType.ACTIVE_CALORIES_BURNED.toString())) {}

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(healthPermissionType.toString())
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<DeletionTypeHealthPermissionTypeData> {
            override fun createFromParcel(parcel: Parcel): DeletionTypeHealthPermissionTypeData {
                return DeletionTypeHealthPermissionTypeData(parcel)
            }

            override fun newArray(size: Int): Array<DeletionTypeHealthPermissionTypeData?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class DeletionTypeCategoryData(val category: @HealthDataCategoryInt Int) : DeletionType() {
        constructor(parcel: Parcel) : this(parcel.readInt()) {}

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(category.toString())
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<DeletionTypeCategoryData> {
            override fun createFromParcel(parcel: Parcel): DeletionTypeCategoryData {
                return DeletionTypeCategoryData(parcel)
            }

            override fun newArray(size: Int): Array<DeletionTypeCategoryData?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class DeletionTypeAppData(val packageName: String, val appName: String) : DeletionType() {
        constructor(parcel: Parcel) : this(parcel.readString() ?: "", parcel.readString() ?: "") {}

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(packageName)
            parcel.writeString(appName)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<DeletionTypeAppData> {
            override fun createFromParcel(parcel: Parcel): DeletionTypeAppData {
                return DeletionTypeAppData(parcel)
            }

            override fun newArray(size: Int): Array<DeletionTypeAppData?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class DeletionTypeHealthPermissionTypeFromApp(
        val healthPermissionType: HealthPermissionType,
        val packageName: String,
        val appName: String
    ) : DeletionType() {
        constructor(
            parcel: Parcel
        ) : this(
            HealthPermissionType.valueOf(
                parcel.readString() ?: HealthPermissionType.ACTIVE_CALORIES_BURNED.toString()),
            parcel.readString() ?: "",
            parcel.readString() ?: "") {}

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(healthPermissionType.toString())
            parcel.writeString(packageName)
            parcel.writeString(appName)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<DeletionTypeAppData> {
            override fun createFromParcel(parcel: Parcel): DeletionTypeAppData {
                return DeletionTypeAppData(parcel)
            }

            override fun newArray(size: Int): Array<DeletionTypeAppData?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class DeleteDataEntry(val id: String, val dataType: DataType, val index: Int) :
        DeletionType() {

        constructor(
            parcel: Parcel
        ) : this(
            parcel.readString().orEmpty(),
            DataType.valueOf(parcel.readString().orEmpty()),
            parcel.readInt())

        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(id)
            parcel.writeString(dataType.name)
            parcel.writeInt(index)
        }

        companion object CREATOR : Parcelable.Creator<DeleteDataEntry> {
            override fun createFromParcel(parcel: Parcel): DeleteDataEntry {
                return DeleteDataEntry(parcel)
            }

            override fun newArray(size: Int): Array<DeleteDataEntry?> {
                return arrayOfNulls(size)
            }
        }
    }

    val hasPermissionType: Boolean
        get() {
            return when (this) {
                is DeletionTypeHealthPermissionTypeData,
                is DeletionTypeHealthPermissionTypeFromApp -> true
                else -> false
            }
        }

    val hasCategory: Boolean
        get() {
            return when (this) {
                is DeletionTypeCategoryData -> true
                else -> false
            }
        }

    val hasAppData: Boolean
        get() {
            return when (this) {
                is DeletionTypeAppData -> true
                is DeletionTypeHealthPermissionTypeFromApp -> true
                else -> false
            }
        }
}
