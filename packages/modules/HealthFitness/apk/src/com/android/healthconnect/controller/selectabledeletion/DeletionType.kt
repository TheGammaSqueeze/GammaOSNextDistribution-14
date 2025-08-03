/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.selectabledeletion

import android.os.Parcel
import android.os.Parcelable
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.DataType

/** Represents the types of deletion that the user can perform. */
sealed class DeletionType : Parcelable {
    data class DeletionTypeHealthPermissionTypes(
        val healthPermissionTypes: List<HealthPermissionType>
    ) : DeletionType() {
        constructor(
            parcel: Parcel
        ) : this(
            (parcel.createStringArray()
                    ?: arrayOf(HealthPermissionType.ACTIVE_CALORIES_BURNED.toString()))
                .map { string -> HealthPermissionType.valueOf(string) }
                .toList()) {}

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeStringArray(
                healthPermissionTypes
                    .map { permissionType -> permissionType.toString() }
                    .toTypedArray())
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<DeletionTypeHealthPermissionTypes> {
            override fun createFromParcel(parcel: Parcel): DeletionTypeHealthPermissionTypes {
                return DeletionTypeHealthPermissionTypes(parcel)
            }

            override fun newArray(size: Int): Array<DeletionTypeHealthPermissionTypes?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class DeletionTypeHealthPermissionTypesFromApp(
        val healthPermissionTypes: List<HealthPermissionType>,
        val packageName: String,
        val appName: String
    ) : DeletionType() {
        constructor(
            parcel: Parcel
        ) : this(
            (parcel.createStringArray()
                    ?: arrayOf(HealthPermissionType.ACTIVE_CALORIES_BURNED.toString()))
                .toList()
                .map { string -> HealthPermissionType.valueOf(string) },
            parcel.readString() ?: "",
            parcel.readString() ?: "") {}

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeStringArray(
                healthPermissionTypes
                    .map { permissionType -> permissionType.toString() }
                    .toTypedArray())
            parcel.writeString(packageName)
            parcel.writeString(appName)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<DeletionTypeHealthPermissionTypesFromApp> {
            override fun createFromParcel(
                parcel: Parcel
            ): DeletionTypeHealthPermissionTypesFromApp {
                return DeletionTypeHealthPermissionTypesFromApp(parcel)
            }

            override fun newArray(size: Int): Array<DeletionTypeHealthPermissionTypesFromApp?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class DeletionTypeEntries(val ids: List<String>, val dataType: DataType) : DeletionType() {
        constructor(
            parcel: Parcel
        ) : this(
            (parcel.createStringArray() ?: arrayOf<String>()).toList(),
            DataType.valueOf(parcel.readString().orEmpty()))

        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeStringArray(ids.toTypedArray())
            parcel.writeString(dataType.name)
        }

        companion object CREATOR : Parcelable.Creator<DeletionTypeEntries> {
            override fun createFromParcel(parcel: Parcel): DeletionTypeEntries {
                return DeletionTypeEntries(parcel)
            }

            override fun newArray(size: Int): Array<DeletionTypeEntries?> {
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

    val hasPermissionTypes: Boolean
        get() {
            return when (this) {
                is DeletionTypeHealthPermissionTypes,
                is DeletionTypeHealthPermissionTypesFromApp -> true
                else -> false
            }
        }

    val hasEntryIds: Boolean
        get() {
            return when (this) {
                is DeletionTypeEntries -> true
                else -> false
            }
        }

    val hasAppData: Boolean
        get() {
            return when (this) {
                is DeletionTypeHealthPermissionTypesFromApp -> true
                is DeletionTypeAppData -> true
                else -> false
            }
        }
}
