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

package com.android.permission.safetylabel

import android.os.PersistableBundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createInvalidSafetyLabelPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundleWithEmptyDataCollected
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundleWithEmptyDataShared
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundleWithInvalidDataCollected
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundleWithInvalidDataShared
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundleWithNullDataCollected
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundleWithNullDataShared
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [DataLabel]. */
@RunWith(AndroidJUnit4::class)
class DataLabelTest {
    @Test
    fun getDataLabel_nullBundle_nullDataLabel() {
        val dataLabel: DataLabel? = DataLabel.getDataLabel(null)

        assertThat(dataLabel).isNull()
    }

    @Test
    fun getDataLabel_emptyBundle_nullDataLabel() {
        val dataLabel: DataLabel? = DataLabel.getDataLabel(PersistableBundle.EMPTY)

        assertThat(dataLabel).isNull()
    }

    @Test
    fun getDataLabel_invalidBundle_nullDataLabel() {
        val dataLabel: DataLabel? =
            DataLabel.getDataLabel(createInvalidSafetyLabelPersistableBundle())

        assertThat(dataLabel).isNull()
    }

    @Test
    fun getDataLabel_nullDataCollectedBundle_dataCollectedEmpty() {
        val dataLabel: DataLabel? =
            DataLabel.getDataLabel(createSafetyLabelPersistableBundleWithNullDataCollected())

        assertThat(dataLabel).isNotNull()
        assertThat(dataLabel?.dataCollected).isEmpty()
        assertThat(dataLabel?.dataShared).isNotEmpty()
    }

    @Test
    fun getDataLabel_nullDataSharedBundle_dataSharedEmpty() {
        val dataLabel: DataLabel? =
            DataLabel.getDataLabel(createSafetyLabelPersistableBundleWithNullDataShared())

        assertThat(dataLabel).isNotNull()
        assertThat(dataLabel?.dataCollected).isNotEmpty()
        assertThat(dataLabel?.dataShared).isEmpty()
    }

    @Test
    fun getDataLabel_emptyDataCollectedBundle_dataCollectedEmpty() {
        val dataLabel: DataLabel? =
            DataLabel.getDataLabel(createSafetyLabelPersistableBundleWithEmptyDataCollected())

        assertThat(dataLabel).isNotNull()
        assertThat(dataLabel?.dataCollected).isEmpty()
        assertThat(dataLabel?.dataShared).isNotEmpty()
    }

    @Test
    fun getDataLabel_emptyDataSharedBundle_dataSharedEmpty() {
        val dataLabel: DataLabel? =
            DataLabel.getDataLabel(createSafetyLabelPersistableBundleWithEmptyDataShared())

        assertThat(dataLabel).isNotNull()
        assertThat(dataLabel?.dataCollected).isNotEmpty()
        assertThat(dataLabel?.dataShared).isEmpty()
    }

    @Test
    fun getDataLabel_invalidDataCollectedBundle_dataCollectedEmpty() {
        val dataLabel: DataLabel? =
            DataLabel.getDataLabel(createSafetyLabelPersistableBundleWithInvalidDataCollected())

        assertThat(dataLabel).isNotNull()
        assertThat(dataLabel?.dataCollected).isEmpty()
        assertThat(dataLabel?.dataShared).isNotEmpty()
    }

    @Test
    fun getDataLabel_invalidDataSharedBundle_dataSharedEmpty() {
        val dataLabel: DataLabel? =
            DataLabel.getDataLabel(createSafetyLabelPersistableBundleWithInvalidDataShared())

        assertThat(dataLabel).isNotNull()
        assertThat(dataLabel?.dataCollected).isNotEmpty()
        assertThat(dataLabel?.dataShared).isEmpty()
    }

    @Test
    fun getDataLabel_validBundle() {
        val dataLabel: DataLabel? = DataLabel.getDataLabel(createSafetyLabelPersistableBundle())

        assertThat(dataLabel).isNotNull()
        assertThat(dataLabel?.dataCollected).isNotEmpty()
        assertThat(dataLabel?.dataShared).isNotEmpty()
    }
}
