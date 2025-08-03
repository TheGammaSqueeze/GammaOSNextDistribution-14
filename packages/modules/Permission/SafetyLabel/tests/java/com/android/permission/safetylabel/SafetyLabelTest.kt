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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permission.safetylabel.SafetyLabel.KEY_SAFETY_LABEL
import com.android.permission.safetylabel.SafetyLabel.KEY_VERSION
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createInvalidMetadataPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createMetadataPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createMetadataPersistableBundleInvalidVersion
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createMetadataPersistableBundleWithInvalidSafetyLabel
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createMetadataPersistableBundleWithoutVersion
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createNonVersionedEmptyMetadataPersistableBundle
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundleWithInvalidVersion
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createSafetyLabelPersistableBundleWithoutVersion
import com.android.permission.safetylabel.SafetyLabelTestPersistableBundles.createVersionedEmptyMetadataPersistableBundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyLabel]. */
@RunWith(AndroidJUnit4::class)
class SafetyLabelTest {

  @Test
  fun getSafetyLabelFromMetadata_nullMetadataBundle_nullSafetyLabel() {
    val safetyLabel = SafetyLabel.getSafetyLabelFromMetadata(null)

    assertThat(safetyLabel).isNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_emptyMetadataBundle_nullSafetyLabel() {
    val safetyLabel =
        SafetyLabel.getSafetyLabelFromMetadata(createNonVersionedEmptyMetadataPersistableBundle())

    assertThat(safetyLabel).isNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_emptyVersionedMetadataBundle_nullSafetyLabel() {
    val safetyLabel =
        SafetyLabel.getSafetyLabelFromMetadata(createVersionedEmptyMetadataPersistableBundle())

    assertThat(safetyLabel).isNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_invalidMetadataBundle_nullSafetyLabel() {
    val safetyLabel =
        SafetyLabel.getSafetyLabelFromMetadata(createInvalidMetadataPersistableBundle())

    assertThat(safetyLabel).isNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_invalidSafetyLabelBundle_dataSharedEmpty() {
    val safetyLabel =
        SafetyLabel.getSafetyLabelFromMetadata(
            createMetadataPersistableBundleWithInvalidSafetyLabel())

    assertThat(safetyLabel).isNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_validBundle_hasDataShared() {
    val bundle = createMetadataPersistableBundle(TOP_LEVEL_VERSION, SAFETY_LABELS_VERSION)
    val topLevelVersion = bundle.getLong(KEY_VERSION)
    val safetyLabelBundle = bundle.getPersistableBundle(KEY_SAFETY_LABEL)
    val safetyLabelsVersion = safetyLabelBundle?.getLong(KEY_VERSION)
    val safetyLabel = SafetyLabel.getSafetyLabelFromMetadata(bundle)

    assertThat(topLevelVersion).isEqualTo(TOP_LEVEL_VERSION)
    assertThat(safetyLabelsVersion).isEqualTo(SAFETY_LABELS_VERSION)
    assertThat(safetyLabel).isNotNull()
    assertThat(safetyLabel?.dataLabel).isNotNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_invalidBundle_noTopLevelBundleVersion() {
    val safetyLabel =
        SafetyLabel.getSafetyLabelFromMetadata(createMetadataPersistableBundleWithoutVersion())
    assertThat(safetyLabel).isNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_invalidBundle_InvalidTopLevelBundleVersion() {
    val safetyLabel =
        SafetyLabel.getSafetyLabelFromMetadata(createMetadataPersistableBundleInvalidVersion())
    assertThat(safetyLabel).isNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_invalidBundle_NoSafetyLabelBundleVersion() {
    val bundle = createVersionedEmptyMetadataPersistableBundle()
    bundle.putPersistableBundle(
        SafetyLabel.KEY_SAFETY_LABEL, createSafetyLabelPersistableBundleWithoutVersion())
    val safetyLabel = SafetyLabel.getSafetyLabelFromMetadata(bundle)
    assertThat(safetyLabel).isNull()
  }

  @Test
  fun getSafetyLabelFromMetadata_invalidBundle_InvalidSafetyLabelBundleVersion() {
    val bundle = createVersionedEmptyMetadataPersistableBundle()
    bundle.putPersistableBundle(
        SafetyLabel.KEY_SAFETY_LABEL, createSafetyLabelPersistableBundleWithInvalidVersion())
    val safetyLabel = SafetyLabel.getSafetyLabelFromMetadata(bundle)
    assertThat(safetyLabel).isNull()
  }

  companion object {
    private const val TOP_LEVEL_VERSION = 3L
    private const val SAFETY_LABELS_VERSION = 2L
  }
}