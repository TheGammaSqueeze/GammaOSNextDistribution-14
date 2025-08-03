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

package android.app.appsearch.safeparcel;

import android.os.Build
import android.os.Parcel
import android.os.Parcelable.Creator
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
@SafeParcelable.Class(creator = "TestSafeParcelableWithRequiresApiCreator")
class TestSafeParcelableWithRequiresApi
@SafeParcelable.Constructor
constructor(
    @field:SafeParcelable.Field(id = 1, defaultValue = "1", getter = "getValue")
    @param:SafeParcelable.Param(id = 1)
    val value: String,
) : AbstractSafeParcelable() {

    override fun writeToParcel(out: Parcel, flags: Int) {
        TestSafeParcelableWithRequiresApiCreator.writeToParcel(this, out, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Creator<TestSafeParcelableWithRequiresApi> =
            TestSafeParcelableWithRequiresApiCreator()
    }
}
