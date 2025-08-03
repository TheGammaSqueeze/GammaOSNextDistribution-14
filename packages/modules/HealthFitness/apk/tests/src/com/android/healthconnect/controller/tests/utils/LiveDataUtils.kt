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
package com.android.healthconnect.controller.tests.utils

import androidx.lifecycle.Observer

/**
 * An observer used in ViewModel tests that captures emitted LiveData values
 * and provides an easy way to access the last posted value.
 */
class TestObserver<T> : Observer<T> {

    private val observedValues = mutableListOf<T>()

    override fun onChanged(value: T) {
        observedValues.add(value)
    }

    fun getLastValue(): T {
        return observedValues.last()
    }
}