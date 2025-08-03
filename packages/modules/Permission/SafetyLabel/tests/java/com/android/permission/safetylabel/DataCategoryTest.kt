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
import com.android.permission.safetylabel.DataCategoryConstants.CATEGORY_LOCATION
import com.android.permission.safetylabel.DataCategoryConstants.VALID_CATEGORIES
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.INVALID_KEY
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createCategoryMapPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createCategoryMapPersistableBundleWithInvalidTypes
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createDataLabelPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createDataLabelPersistableBundleWithAdditonalInvalidCategory
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createInvalidCategoryMapPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createInvalidDataLabelPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createTypePersistableBundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [DataCategory]. */
@RunWith(AndroidJUnit4::class)
class DataCategoryTest {
    @Test
    fun getDataCategoryMap_dataUsageCollected_nullPersistableBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(null, DataLabelConstants.DATA_USAGE_COLLECTED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageCollected_emptyPersistableBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                PersistableBundle.EMPTY, DataLabelConstants.DATA_USAGE_COLLECTED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageCollected_invalidBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                createInvalidDataLabelPersistableBundle(), DataLabelConstants.DATA_USAGE_COLLECTED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageCollected_validBundle_hasAllValidCategories() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                createDataLabelPersistableBundle(), DataLabelConstants.DATA_USAGE_COLLECTED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap.keys).containsExactlyElementsIn(VALID_CATEGORIES)
    }

    @Test
    fun getDataCategoryMap_dataUsageCollected_additionalInvalidCategory_hasOnlyValidCategories() {
        // Create valid data label and put an additional invalid/unknown category key
        val dataLabelBundle = createDataLabelPersistableBundleWithAdditonalInvalidCategory()

        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                dataLabelBundle, DataLabelConstants.DATA_USAGE_COLLECTED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap.keys).containsExactlyElementsIn(VALID_CATEGORIES)
    }

    @Test
    fun getDataCategoryMap_dataUsageShared_nullPersistableBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(null, DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageShared_emptyPersistableBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                PersistableBundle.EMPTY, DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageShared_invalidBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                createInvalidDataLabelPersistableBundle(), DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageShared_validBundle_hasAllValidCategories() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                createDataLabelPersistableBundle(), DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap.keys).containsExactlyElementsIn(VALID_CATEGORIES)
    }

    @Test
    fun getDataCategoryMap_dataUsageShared_additionalInvalidCategory_hasOnlyValidCategories() {
        // Create valid data label and put an additional invalid/unknown category key
        val dataLabelBundle = createDataLabelPersistableBundleWithAdditonalInvalidCategory()

        val dataCategoryMap =
            DataCategory.getDataCategoryMap(dataLabelBundle, DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap.keys).containsExactlyElementsIn(VALID_CATEGORIES)
    }

    @Test
    fun getDataCategoryMap_dataUsageInvalid_nullPersistableBundle_emptyMap() {
        val dataCategoryMap = DataCategory.getDataCategoryMap(null, "invalid_datausage_key")

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageInvalid_emptyPersistableBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(PersistableBundle.EMPTY, "invalid_datausage_key")

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageInvalid_invalidBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                createInvalidDataLabelPersistableBundle(), "invalid_datausage_key")

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageInvalid_validBundle_emptyMap() {
        val dataCategoryMap =
            DataCategory.getDataCategoryMap(
                createDataLabelPersistableBundle(), "invalid_datausage_key")

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategoryMap_dataUsageInvalid_validBundleWithAdditionalInvalidCategory_null() {
        // Create valid data label and put an additional invalid/unknown category key
        val dataLabelBundle = createDataLabelPersistableBundleWithAdditonalInvalidCategory()

        val dataCategoryMap =
            DataCategory.getDataCategoryMap(dataLabelBundle, "invalid_datausage_key")

        assertThat(dataCategoryMap).isNotNull()
        assertThat(dataCategoryMap).isEmpty()
    }

    @Test
    fun getDataCategory_nullBundle_nullDataCategory() {
        val dataCategory =
            DataCategory.getDataCategory(
                null, DataLabelConstants.DATA_USAGE_SHARED, CATEGORY_LOCATION)

        assertThat(dataCategory).isNull()
    }

    @Test
    fun getDataCategory_emptyBundle_nullDataCategory() {
        val dataCategory =
            DataCategory.getDataCategory(
                PersistableBundle.EMPTY, DataLabelConstants.DATA_USAGE_SHARED, CATEGORY_LOCATION)

        assertThat(dataCategory).isNull()
    }

    @Test
    fun getDataCategory_invalidBundle_nullDataCategory() {
        val dataCategory =
            DataCategory.getDataCategory(
                createInvalidCategoryMapPersistableBundle(),
                DataLabelConstants.DATA_USAGE_SHARED,
                CATEGORY_LOCATION)

        assertThat(dataCategory).isNull()
    }

    @Test
    fun getDataCategory_validCategoriesAndInvalidType_nullDataCategory() {
        val dataCategory =
            DataCategory.getDataCategory(
                createCategoryMapPersistableBundleWithInvalidTypes(),
                DataLabelConstants.DATA_USAGE_SHARED,
                INVALID_KEY)

        assertThat(dataCategory).isNull()
    }

    @Test
    fun getDataCategory_validBundle_validCategoryAndExpectedTypes() {
        val dataCategory =
            DataCategory.getDataCategory(
                createCategoryMapPersistableBundle(),
                DataLabelConstants.DATA_USAGE_SHARED,
                CATEGORY_LOCATION)

        assertThat(dataCategory).isNotNull()
        assertThat(dataCategory?.dataTypes).isNotEmpty()
        assertThat(dataCategory?.dataTypes?.keys)
            .containsExactlyElementsIn(
                DataTypeConstants.getValidDataTypesForCategory(CATEGORY_LOCATION))
    }

    @Test
    fun getDataCategory_validBundleWithAddedInvalidType_validCategoryAndOnlyExpectedTypes() {
        // Create valid bundle with additional invalid type
        val dataTypeMapBundle = createCategoryMapPersistableBundle()
        dataTypeMapBundle.putPersistableBundle(INVALID_KEY, createTypePersistableBundle())

        val dataCategory =
            DataCategory.getDataCategory(
                dataTypeMapBundle, DataLabelConstants.DATA_USAGE_SHARED, CATEGORY_LOCATION)

        assertThat(dataCategory).isNotNull()
        assertThat(dataCategory?.dataTypes).isNotEmpty()
        assertThat(dataCategory?.dataTypes?.keys)
            .containsExactlyElementsIn(
                DataTypeConstants.getValidDataTypesForCategory(CATEGORY_LOCATION))
    }
}
