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

import android.graphics.Point
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.ext.truth.os.BundleSubject.assertThat
import org.junit.Test

class CreationExtrasExtTest {
    @Test
    fun addDefaultArgs_addsWhenAbsent() {
        val creationExtras: CreationExtras = MutableCreationExtras() // empty

        val updated = creationExtras.addDefaultArgs("POINT" to Point(1, 1))

        val defaultArgs = updated[DEFAULT_ARGS_KEY]
        assertThat(defaultArgs).containsKey("POINT")
        assertThat(defaultArgs).parcelable<Point>("POINT").marshallsEquallyTo(Point(1, 1))
    }

    @Test
    fun addDefaultArgs_addsToExisting() {
        val creationExtras: CreationExtras =
            MutableCreationExtras().apply {
                set(DEFAULT_ARGS_KEY, bundleOf("POINT1" to Point(1, 1)))
            }

        val updated = creationExtras.addDefaultArgs("POINT2" to Point(2, 2))

        val defaultArgs = updated[DEFAULT_ARGS_KEY]
        assertThat(defaultArgs).containsKey("POINT1")
        assertThat(defaultArgs).containsKey("POINT2")
        assertThat(defaultArgs).parcelable<Point>("POINT1").marshallsEquallyTo(Point(1, 1))
        assertThat(defaultArgs).parcelable<Point>("POINT2").marshallsEquallyTo(Point(2, 2))
    }
}
