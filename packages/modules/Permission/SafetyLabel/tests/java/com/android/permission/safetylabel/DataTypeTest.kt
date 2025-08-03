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
import com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ADVERTISING
import com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_APP_FUNCTIONALITY
import com.android.permission.safetylabel.DataType.KEY_EPHEMERAL
import com.android.permission.safetylabel.DataType.KEY_PURPOSES
import com.android.permission.safetylabel.DataType.KEY_IS_COLLECTION_OPTIONAL
import com.android.permission.safetylabel.DataTypeConstants.LOCATION_APPROX_LOCATION
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.INVALID_KEY
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createInvalidTypeMapPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createInvalidTypePersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createTypeMapPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createTypeMapWithInvalidTypeDataPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createTypePersistableBundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [DataType]. */
@RunWith(AndroidJUnit4::class)
class DataTypeTest {
    @Test
    fun getDataTypeMap_invalidCategory_nullPersistableBundle_emptyMap() {
        val dataTypeMap =
            DataType.getDataTypeMap(null, DataLabelConstants.DATA_USAGE_SHARED, INVALID_KEY)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).isEmpty()
    }

    @Test
    fun getDataTypeMap_invalidCategory_emptyPersistableBundle_emptyMap() {
        val dataTypeMap =
            DataType.getDataTypeMap(
                PersistableBundle.EMPTY, DataLabelConstants.DATA_USAGE_SHARED, INVALID_KEY)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).isEmpty()
    }

    @Test
    fun getDataTypeMap_invalidCategory_invalidBundle_emptyMap() {
        val dataTypeMap =
            DataType.getDataTypeMap(
                createInvalidTypeMapPersistableBundle(),
                DataLabelConstants.DATA_USAGE_SHARED,
                INVALID_KEY)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).isEmpty()
    }

    @Test
    fun getDataTypeMap_invalidCategory_validBundle_emptyMap() {
        val dataTypeMap =
            DataType.getDataTypeMap(
                createTypeMapPersistableBundle(CATEGORY_LOCATION),
                DataLabelConstants.DATA_USAGE_SHARED,
                INVALID_KEY)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).isEmpty()
    }

    @Test
    fun getDataTypeMap_validCategory_nullPersistableBundle_emptyMap() {
        val dataTypeMap =
            DataType.getDataTypeMap(null, DataLabelConstants.DATA_USAGE_SHARED, CATEGORY_LOCATION)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).isEmpty()
    }

    @Test
    fun getDataTypeMap_validCategory_emptyPersistableBundle_emptyMap() {
        val dataTypeMap =
            DataType.getDataTypeMap(
                PersistableBundle.EMPTY, DataLabelConstants.DATA_USAGE_SHARED, CATEGORY_LOCATION)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).isEmpty()
    }

    @Test
    fun getDataTypeMap_validCategory_invalidBundle_emptyMap() {
        val dataTypeMap =
            DataType.getDataTypeMap(
                createInvalidTypeMapPersistableBundle(),
                DataLabelConstants.DATA_USAGE_SHARED,
                CATEGORY_LOCATION)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).isEmpty()
    }

    @Test
    fun getDataTypeMap_validCategory_validBundle_hasAllExpectedTypes() {
        val typeMapPersistableBundle = createTypeMapPersistableBundle(CATEGORY_LOCATION)

        val dataTypeMap =
            DataType.getDataTypeMap(
                typeMapPersistableBundle, DataLabelConstants.DATA_USAGE_SHARED, CATEGORY_LOCATION)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap.keys)
            .containsExactlyElementsIn(
                DataTypeConstants.getValidDataTypesForCategory(CATEGORY_LOCATION))
    }

    @Test
    fun getDataTypeMap_validCategory_validBundleWithAddedInvalidType_hasOnlyExpectedTypes() {
        val typeMapPersistableBundle = createTypeMapPersistableBundle(CATEGORY_LOCATION)
        // Add additional valid persistable bundle under invalid key
        typeMapPersistableBundle.putPersistableBundle(INVALID_KEY, createTypePersistableBundle())

        val dataTypeMap =
            DataType.getDataTypeMap(
                typeMapPersistableBundle, DataLabelConstants.DATA_USAGE_SHARED, CATEGORY_LOCATION)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap.keys)
            .containsExactlyElementsIn(
                DataTypeConstants.getValidDataTypesForCategory(CATEGORY_LOCATION))
        assertThat(dataTypeMap.keys).doesNotContain(INVALID_KEY)
    }

    @Test
    fun getDataTypeMap_validCategory_validType_invalidData_emptyMap() {
        val typeMapPersistableBundle =
            createTypeMapWithInvalidTypeDataPersistableBundle(CATEGORY_LOCATION)

        val dataTypeMap =
            DataType.getDataTypeMap(
                typeMapPersistableBundle, DataLabelConstants.DATA_USAGE_SHARED, CATEGORY_LOCATION)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).isEmpty()
    }

    @Test
    fun getDataTypeMap_dataCollected_validCategory_validBundle_validateSingleExpectedType() {
        val dataTypeMap =
            DataType.getDataTypeMap(
                createTypeMapPersistableBundle(CATEGORY_LOCATION),
                DataLabelConstants.DATA_USAGE_COLLECTED,
                CATEGORY_LOCATION)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).containsKey(LOCATION_APPROX_LOCATION)
        val type = dataTypeMap[LOCATION_APPROX_LOCATION]!!
        assertThat(type.purposeSet).containsExactly(PURPOSE_APP_FUNCTIONALITY, PURPOSE_ADVERTISING)
        assertThat(type.isCollectionOptional).isTrue()
        assertThat(type.ephemeral).isTrue()
    }

    @Test
    fun getDataTypeMap_dataShared_validCategory_validBundle_validateSingleExpectedType() {
        val dataTypeMap =
            DataType.getDataTypeMap(
                createTypeMapPersistableBundle(CATEGORY_LOCATION),
                DataLabelConstants.DATA_USAGE_SHARED,
                CATEGORY_LOCATION)

        assertThat(dataTypeMap).isNotNull()
        assertThat(dataTypeMap).containsKey(LOCATION_APPROX_LOCATION)
        val type = dataTypeMap[LOCATION_APPROX_LOCATION]!!
        assertThat(type.purposeSet).containsExactly(PURPOSE_APP_FUNCTIONALITY, PURPOSE_ADVERTISING)
        assertThat(type.isCollectionOptional).isNull()
        assertThat(type.ephemeral).isNull()
    }

    @Test
    fun getDataType_invalidBundle_nullDataType() {
        val dataType =
            DataType.getDataType(
                createInvalidTypePersistableBundle(), DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataType).isNull()
    }

    @Test
    fun getDataType_dataCollected_validDataType() {
        val dataType =
            DataType.getDataType(
                createTypePersistableBundle(), DataLabelConstants.DATA_USAGE_COLLECTED)

        assertThat(dataType).isNotNull()
        assertThat(dataType?.purposeSet)
            .containsExactly(PURPOSE_APP_FUNCTIONALITY, PURPOSE_ADVERTISING)
        assertThat(dataType?.isCollectionOptional).isTrue()
        assertThat(dataType?.ephemeral).isTrue()
    }

    @Test
    fun getDataType_dataShared_validDataType() {
        val dataType =
            DataType.getDataType(
                createTypePersistableBundle(), DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataType).isNotNull()
        assertThat(dataType?.purposeSet)
            .containsExactly(PURPOSE_APP_FUNCTIONALITY, PURPOSE_ADVERTISING)
        assertThat(dataType?.isCollectionOptional).isNull()
        assertThat(dataType?.ephemeral).isNull()
    }

    @Test
    fun getDataType_validDataTypeWithAddedInvalidPurpose_onlyValidPurposes() {
        val typePersistableBundle = createTypePersistableBundle()
        val purposes: IntArray = typePersistableBundle.getIntArray(KEY_PURPOSES)!!
        val updatedPurposes: IntArray = intArrayOf(-1, *purposes)
        typePersistableBundle.putIntArray(KEY_PURPOSES, updatedPurposes)

        val dataType =
            DataType.getDataType(typePersistableBundle, DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataType).isNotNull()
        // Should not contain the additional "-1" purpose added above
        assertThat(dataType?.purposeSet)
            .containsExactly(PURPOSE_APP_FUNCTIONALITY, PURPOSE_ADVERTISING)
    }

    @Test
    fun getDataType_dataTypeWithInvalidPurpose_nullDataType() {
        val typePersistableBundle = createTypePersistableBundle()
        typePersistableBundle.remove(KEY_PURPOSES)
        val updatedPurposes: IntArray = intArrayOf(-1)
        typePersistableBundle.putIntArray(KEY_PURPOSES, updatedPurposes)

        val dataType =
            DataType.getDataType(typePersistableBundle, DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataType).isNull()
    }

    @Test
    fun getDataType_noPurpose_nullDataType() {
        val typePersistableBundle = createTypePersistableBundle()
        typePersistableBundle.remove(KEY_PURPOSES)

        val dataType =
            DataType.getDataType(typePersistableBundle, DataLabelConstants.DATA_USAGE_SHARED)

        assertThat(dataType).isNull()
    }

    @Test
    fun getDataType_dataCollected_validDataType_noUserControl_noEphemeral() {
        val bundle = createTypePersistableBundle()
        bundle.remove(KEY_IS_COLLECTION_OPTIONAL)
        bundle.remove(KEY_EPHEMERAL)

        val dataType = DataType.getDataType(bundle, DataLabelConstants.DATA_USAGE_COLLECTED)

        assertThat(dataType).isNotNull()
        assertThat(dataType?.purposeSet)
            .containsExactly(PURPOSE_APP_FUNCTIONALITY, PURPOSE_ADVERTISING)
        assertThat(dataType?.isCollectionOptional).isNull()
        assertThat(dataType?.ephemeral).isNull()
    }
}
