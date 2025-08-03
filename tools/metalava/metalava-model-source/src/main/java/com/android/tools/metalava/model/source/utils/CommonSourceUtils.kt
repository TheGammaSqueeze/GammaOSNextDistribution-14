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

package com.android.tools.metalava.model.source.utils

import com.android.tools.metalava.model.Item
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// a property with a lazily calculated default value
class LazyDelegate<T>(val defaultValueProvider: () -> T) : ReadWriteProperty<Item, T> {
    private var currentValue: T? = null

    override operator fun setValue(thisRef: Item, property: KProperty<*>, value: T) {
        currentValue = value
    }

    override operator fun getValue(thisRef: Item, property: KProperty<*>): T {
        if (currentValue == null) {
            currentValue = defaultValueProvider()
        }

        return currentValue!!
    }
}
