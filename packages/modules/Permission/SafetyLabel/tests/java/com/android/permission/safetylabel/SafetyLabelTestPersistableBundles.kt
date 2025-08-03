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

import androidx.annotation.VisibleForTesting

import com.android.permission.safetylabel.DataCategoryConstants.CATEGORY_LOCATION
import com.android.permission.safetylabel.DataCategoryConstants.Category
import com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ADVERTISING
import com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_APP_FUNCTIONALITY
import com.android.permission.safetylabel.DataType.KEY_EPHEMERAL
import com.android.permission.safetylabel.DataType.KEY_PURPOSES
import com.android.permission.safetylabel.DataType.KEY_IS_COLLECTION_OPTIONAL
import com.android.permission.safetylabel.SafetyLabel.KEY_VERSION

/** A class that facilitates creating test safety label persistable bundles. */
object SafetyLabelTestPersistableBundles {
  private const val TOP_LEVEL_VERSION = 1L
  private const val SAFETY_LABELS_VERSION = 1L
  @VisibleForTesting const val INVALID_TOP_LEVEL_VERSION = -1L
  @VisibleForTesting const val INVALID_SAFETY_LABELS_VERSION = -2L
  const val INVALID_KEY = "invalid_key"

  /**
   * Returns [PersistableBundle] representation of an empty top level metadata persistable bundle.
   */
  fun createNonVersionedEmptyMetadataPersistableBundle(): PersistableBundle {
    return PersistableBundle()
  }

  /**
   * Returns [PersistableBundle] representation of a top level metadata versioned by the provided
   * top level version number, or the default value [TOP_LEVEL_VERSION] if not provided.
   */
  fun createVersionedEmptyMetadataPersistableBundle(
    version: Long = TOP_LEVEL_VERSION
  ): PersistableBundle {
    return PersistableBundle().apply {
      putLong(KEY_VERSION, version)
    }
  }

  /**
   * Returns [PersistableBundle] representation of a top level metadata versioned by the provided
   * top level version number, or the default value [TOP_LEVEL_VERSION] if not provided.
   * And the embedded safety labels will be versioned by the provided safety labels version number,
   * or the default value [SAFETY_LABELS_VERSION] if not provided.
   */
  fun createMetadataPersistableBundle(
    topLevelVersion: Long = TOP_LEVEL_VERSION,
    safetyLabelsVersion: Long = SAFETY_LABELS_VERSION
  ): PersistableBundle {
    return createVersionedEmptyMetadataPersistableBundle(topLevelVersion).apply {
      putPersistableBundle(SafetyLabel.KEY_SAFETY_LABEL,
        createSafetyLabelPersistableBundle(safetyLabelsVersion))
    }
  }

  /**
   * Returns [PersistableBundle] representation of a metadata with invalid safety label key.
   */
  fun createInvalidMetadataPersistableBundle(): PersistableBundle {
    return createVersionedEmptyMetadataPersistableBundle().apply {
      putPersistableBundle(INVALID_KEY, createSafetyLabelPersistableBundle())
    }
  }

  /**
   * Returns [PersistableBundle] representation of a metadata with invalid safety label bundle.
   */
  fun createMetadataPersistableBundleWithInvalidSafetyLabel(): PersistableBundle {
    return createVersionedEmptyMetadataPersistableBundle().apply {
      putPersistableBundle(
          SafetyLabel.KEY_SAFETY_LABEL, createInvalidSafetyLabelPersistableBundle())
    }
  }

  /**
   * Returns [PersistableBundle] representation of an invalid metadata without version number.
   */
  fun createMetadataPersistableBundleWithoutVersion(): PersistableBundle {
    return createMetadataPersistableBundle().apply { remove(KEY_VERSION) }
  }

  /**
   * Returns [PersistableBundle] representation of a metadata with invalid top level version number.
   */
  fun createMetadataPersistableBundleInvalidVersion(): PersistableBundle {
    return createMetadataPersistableBundle().apply {
      putLong(KEY_VERSION, INVALID_TOP_LEVEL_VERSION)
    }
  }

  /**
   * Returns [PersistableBundle] representation of an empty safety label versioned by the provided
   * version number, or the default value [SAFETY_LABELS_VERSION] if not provided.
   */
  private fun createVersionedEmptySafetyLabelsPersistableBundle(
    version: Long = SAFETY_LABELS_VERSION
  ): PersistableBundle {
    return PersistableBundle().apply {
      putLong(KEY_VERSION, version)
    }
  }

  /** Returns [PersistableBundle] representation of a valid safety label */
  fun createSafetyLabelPersistableBundle(
    version: Long = SAFETY_LABELS_VERSION
  ): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle(version).apply {
      putPersistableBundle(DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundle())
    }
  }

  /** Returns [PersistableBundle] representation of a non-versioned invalid safety label */
  fun createSafetyLabelPersistableBundleWithoutVersion(): PersistableBundle {
    return createSafetyLabelPersistableBundle().apply {
     remove(KEY_VERSION)
    }
  }

  /** Returns [PersistableBundle] representation of a safety label with invalid version number */
  fun createSafetyLabelPersistableBundleWithInvalidVersion(): PersistableBundle {
    return createSafetyLabelPersistableBundle().apply {
      putLong(KEY_VERSION, INVALID_SAFETY_LABELS_VERSION)
    }
  }

  /** Returns [PersistableBundle] representation of an invalid safety label */
  fun createInvalidSafetyLabelPersistableBundle(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(INVALID_KEY, createDataLabelPersistableBundle())
    }
  }

  /** Returns [PersistableBundle] representation of an invalid safety label */
  fun createSafetyLabelPersistableBundleWithNullDataCollected(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(
          DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithNullDataCollected())
    }
  }

  /** Returns [PersistableBundle] representation of an invalid safety label */
  fun createSafetyLabelPersistableBundleWithNullDataShared(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(
          DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithNullDataShared())
    }
  }

  /** Returns [PersistableBundle] representation of an invalid safety label */
  fun createSafetyLabelPersistableBundleWithEmptyDataCollected(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(
          DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithEmptyDataCollected())
    }
  }

  /** Returns [PersistableBundle] representation of an invalid safety label */
  fun createSafetyLabelPersistableBundleWithEmptyDataShared(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(
          DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithEmptyDataShared())
    }
  }

  /** Returns [PersistableBundle] representation of an invalid safety label */
  fun createSafetyLabelPersistableBundleWithInvalidDataCollected(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(
          DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithInvalidDataCollected())
    }
  }

  /** Returns [PersistableBundle] representation of an invalid safety label */
  fun createSafetyLabelPersistableBundleWithInvalidDataShared(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(
          DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithInvalidDataShared())
    }
  }

  /** Returns [PersistableBundle] representation of a data label */
  fun createDataLabelPersistableBundle(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(
          DataLabelConstants.DATA_USAGE_SHARED, createCategoryMapPersistableBundle())
      putPersistableBundle(
          DataLabelConstants.DATA_USAGE_COLLECTED, createCategoryMapPersistableBundle())
    }
  }

  /** Returns [PersistableBundle] representation of an invalid data label */
  fun createInvalidDataLabelPersistableBundle(): PersistableBundle {
    return createVersionedEmptySafetyLabelsPersistableBundle().apply {
      putPersistableBundle(INVALID_KEY, createCategoryMapPersistableBundle())
    }
  }

  /** Returns [PersistableBundle] representation of a null data collected data label */
  private fun createDataLabelPersistableBundleWithNullDataCollected(): PersistableBundle {
    return createDataLabelPersistableBundle().apply {
      remove(DataLabelConstants.DATA_USAGE_COLLECTED)
    }
  }

  /** Returns [PersistableBundle] representation of a null data shared data label */
  private fun createDataLabelPersistableBundleWithNullDataShared(): PersistableBundle {
    return createDataLabelPersistableBundle().apply { remove(DataLabelConstants.DATA_USAGE_SHARED) }
  }

  /** Returns [PersistableBundle] representation of an empty data collected data label */
  private fun createDataLabelPersistableBundleWithEmptyDataCollected(): PersistableBundle {
    return createDataLabelPersistableBundle().apply {
      remove(DataLabelConstants.DATA_USAGE_COLLECTED)
      putPersistableBundle(DataLabelConstants.DATA_USAGE_COLLECTED, PersistableBundle.EMPTY)
    }
  }

  /** Returns [PersistableBundle] representation of an empty data shared data label */
  private fun createDataLabelPersistableBundleWithEmptyDataShared(): PersistableBundle {
    return createDataLabelPersistableBundle().apply {
      remove(DataLabelConstants.DATA_USAGE_SHARED)
      putPersistableBundle(DataLabelConstants.DATA_USAGE_SHARED, PersistableBundle.EMPTY)
    }
  }

  /** Returns [PersistableBundle] representation of an invalid data collected data label */
  private fun createDataLabelPersistableBundleWithInvalidDataCollected(): PersistableBundle {
    return createDataLabelPersistableBundle().apply {
      remove(DataLabelConstants.DATA_USAGE_COLLECTED)
      putPersistableBundle(
          DataLabelConstants.DATA_USAGE_COLLECTED, createInvalidCategoryMapPersistableBundle())
    }
  }

  /** Returns [PersistableBundle] representation of an invalid data shared data label */
  private fun createDataLabelPersistableBundleWithInvalidDataShared(): PersistableBundle {
    return createDataLabelPersistableBundle().apply {
      remove(DataLabelConstants.DATA_USAGE_SHARED)
      putPersistableBundle(
          DataLabelConstants.DATA_USAGE_SHARED, createInvalidCategoryMapPersistableBundle())
    }
  }

  /** Returns [PersistableBundle] representation of data label with additional invalid categories*/
  fun createDataLabelPersistableBundleWithAdditonalInvalidCategory(): PersistableBundle {
    return PersistableBundle().apply {
      putPersistableBundle(
          DataLabelConstants.DATA_USAGE_SHARED,
          createCategoryMapPersistableBundleWithAdditionalInvalidCategory())
      putPersistableBundle(
          DataLabelConstants.DATA_USAGE_COLLECTED,
          createCategoryMapPersistableBundleWithAdditionalInvalidCategory())
    }
  }

  /** Returns [PersistableBundle] representation of a [Map] of valid data categories */
  fun createCategoryMapPersistableBundle(): PersistableBundle {
    return PersistableBundle().apply {
      DataCategoryConstants.VALID_CATEGORIES.forEach { categoryKey ->
        putPersistableBundle(categoryKey, createTypeMapPersistableBundle(categoryKey))
      }
    }
  }

  /** Returns [PersistableBundle] representation of a [Map] of valid data categories */
  fun createCategoryMapPersistableBundleWithAdditionalInvalidCategory(): PersistableBundle {
    return PersistableBundle().apply {
      DataCategoryConstants.VALID_CATEGORIES.forEach { categoryKey ->
        putPersistableBundle(categoryKey, createTypeMapPersistableBundle(categoryKey))
      }
      putPersistableBundle(INVALID_KEY, createTypeMapPersistableBundle(CATEGORY_LOCATION))
    }
  }

  /**
   * Returns [PersistableBundle] representation of a [Map] of valid data categories and invalid
   * types
   */
  fun createCategoryMapPersistableBundleWithInvalidTypes(): PersistableBundle {
    return PersistableBundle().apply {
      DataCategoryConstants.VALID_CATEGORIES.forEach { categoryKey ->
        putPersistableBundle(categoryKey, createInvalidTypeMapPersistableBundle())
      }
    }
  }

  /** Returns [PersistableBundle] representation of a [Map] of invalid data categories */
  fun createInvalidCategoryMapPersistableBundle(): PersistableBundle {
    return PersistableBundle().apply {
      putPersistableBundle(INVALID_KEY, createTypeMapPersistableBundle(CATEGORY_LOCATION))
    }
  }

  /** Returns [PersistableBundle] representation of a [Map] of valid data type */
  fun createTypeMapPersistableBundle(@Category category: String): PersistableBundle {
    return PersistableBundle().apply {
      DataTypeConstants.getValidDataTypesForCategory(category).forEach { type ->
        putPersistableBundle(type, createTypePersistableBundle())
      }
    }
  }

  /** Returns [PersistableBundle] representation of a [Map] of invalid data type */
  fun createInvalidTypeMapPersistableBundle(): PersistableBundle {
    return PersistableBundle().apply {
      putPersistableBundle(INVALID_KEY, createTypePersistableBundle())
    }
  }

  /** Returns [PersistableBundle] representation of a [Map] of valid type, with invalid data */
  fun createTypeMapWithInvalidTypeDataPersistableBundle(
      @Category category: String
  ): PersistableBundle {
    return PersistableBundle().apply {
      DataTypeConstants.getValidDataTypesForCategory(category).forEach { type ->
        putPersistableBundle(type, createInvalidTypePersistableBundle())
      }
    }
  }

  /** Returns [PersistableBundle] representation of a valid data type */
  fun createTypePersistableBundle(): PersistableBundle {
    return PersistableBundle().apply {
      putIntArray(KEY_PURPOSES, intArrayOf(PURPOSE_APP_FUNCTIONALITY, PURPOSE_ADVERTISING))
      putBoolean(KEY_IS_COLLECTION_OPTIONAL, true)
      putBoolean(KEY_EPHEMERAL, true)
    }
  }

  /** Returns [PersistableBundle] representation of an invalid data type */
  fun createInvalidTypePersistableBundle(): PersistableBundle {
    return PersistableBundle().apply { putLong(INVALID_KEY, 0) }
  }
}
