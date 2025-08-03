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

package com.android.intentresolver.v2.ext

import android.os.Parcel
import android.os.Parcelable
import java.lang.reflect.Field

inline fun <reified T : Parcelable> T.toParcelAndBack(): T {
    val creator: Parcelable.Creator<out T> = getCreator()
    val parcel = Parcel.obtain()
    writeToParcel(parcel, 0)
    parcel.setDataPosition(0)
    return creator.createFromParcel(parcel)
}

inline fun <reified T : Parcelable> getCreator(): Parcelable.Creator<out T> {
    return getCreator(T::class.java)
}

inline fun <reified T : Parcelable> getCreator(clazz: Class<out T>): Parcelable.Creator<out T> {
    return try {
        val field: Field = clazz.getDeclaredField("CREATOR")
        @Suppress("UNCHECKED_CAST")
        field.get(null) as Parcelable.Creator<T>
    } catch (e: NoSuchFieldException) {
        error("$clazz is a Parcelable without CREATOR")
    } catch (e: IllegalAccessException) {
        error("CREATOR in $clazz::class is not accessible")
    }
}
