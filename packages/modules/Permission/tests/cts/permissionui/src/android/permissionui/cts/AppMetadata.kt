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

package android.permissionui.cts

import android.os.PersistableBundle

/** Helper methods for creating test app metadata [PersistableBundle] */
object AppMetadata {
    /** Returns empty App Metadata [PersistableBundle] representation */
    fun createEmptyAppMetadata(): PersistableBundle {
        return PersistableBundle()
    }

    /** Returns valid App Metadata [PersistableBundle] representation */
    fun createDefaultAppMetadata(): PersistableBundle {
        val approximateLocationBundle =
            PersistableBundle().apply { putIntArray(KEY_PURPOSES, (1..7).toList().toIntArray()) }

        val locationBundle =
            PersistableBundle().apply {
                putPersistableBundle(APPROX_LOCATION, approximateLocationBundle)
            }

        val dataSharedBundle =
            PersistableBundle().apply { putPersistableBundle(LOCATION_CATEGORY, locationBundle) }

        val dataLabelBundle =
            PersistableBundle().apply { putPersistableBundle(KEY_DATA_SHARED, dataSharedBundle) }

        val safetyLabelBundle =
            PersistableBundle().apply {
                putLong(KEY_VERSION, INITIAL_SAFETY_LABELS_VERSION)
                putPersistableBundle(KEY_DATA_LABELS, dataLabelBundle)
            }

        return PersistableBundle().apply {
            putLong(KEY_VERSION, INITIAL_TOP_LEVEL_VERSION)
            putPersistableBundle(KEY_SAFETY_LABELS, safetyLabelBundle)
        }
    }

    /**
     * Returns invalid App Metadata [PersistableBundle] representation. Invalidity due to invalid
     * label name usage
     */
    fun createInvalidAppMetadata(): PersistableBundle {
        val validAppMetaData = createDefaultAppMetadata()
        val validSafetyLabel = validAppMetaData.getPersistableBundle(KEY_SAFETY_LABELS)

        return PersistableBundle().apply {
            putLong(KEY_VERSION, INITIAL_TOP_LEVEL_VERSION)
            putPersistableBundle(KEY_INVALID, validSafetyLabel)
        }
    }

    /**
     * Returns invalid App Metadata [PersistableBundle] representation. Invalidity due to no top
     * level meta data version number.
     */
    fun createInvalidAppMetadataWithoutTopLevelVersion(): PersistableBundle {
        val validAppMetaData = createDefaultAppMetadata()
        val validSafetyLabel = validAppMetaData.getPersistableBundle(KEY_SAFETY_LABELS)

        return PersistableBundle().apply {
            putPersistableBundle(KEY_SAFETY_LABELS, validSafetyLabel)
        }
    }

    /**
     * Returns invalid App Metadata [PersistableBundle] representation. Invalidity due to invalid
     * top level meta data version number.
     */
    fun createInvalidAppMetadataWithInvalidTopLevelVersion(): PersistableBundle {
        val validAppMetaData = createDefaultAppMetadata()
        val validSafetyLabel = validAppMetaData.getPersistableBundle(KEY_SAFETY_LABELS)

        return PersistableBundle().apply {
            putLong(KEY_VERSION, INVALID_TOP_LEVEL_VERSION)
            putPersistableBundle(KEY_SAFETY_LABELS, validSafetyLabel)
        }
    }

    /**
     * Returns invalid App Metadata [PersistableBundle] representation. Invalidity due to no safety
     * label version number.
     */
    fun createInvalidAppMetadataWithoutSafetyLabelVersion(): PersistableBundle {
        val validAppMetaData = createDefaultAppMetadata()
        val invalidSafetyLabel =
            validAppMetaData.getPersistableBundle(KEY_SAFETY_LABELS).apply {
                this?.remove(KEY_VERSION)
            }

        return PersistableBundle().apply {
            putLong(KEY_VERSION, INITIAL_TOP_LEVEL_VERSION)
            putPersistableBundle(KEY_SAFETY_LABELS, invalidSafetyLabel)
        }
    }

    /**
     * Returns invalid App Metadata [PersistableBundle] representation. Invalidity due to invalid
     * safety label version number.
     */
    fun createInvalidAppMetadataWithInvalidSafetyLabelVersion(): PersistableBundle {
        val validAppMetaData = createDefaultAppMetadata()
        val invalidSafetyLabel =
            validAppMetaData.getPersistableBundle(KEY_SAFETY_LABELS)?.apply {
                putLong(KEY_VERSION, INVALID_SAFETY_LABELS_VERSION)
            }

        return PersistableBundle().apply {
            putLong(KEY_VERSION, INITIAL_TOP_LEVEL_VERSION)
            putPersistableBundle(KEY_SAFETY_LABELS, invalidSafetyLabel)
        }
    }
    /** Returns an App Metadata [PersistableBundle] representation where no data is shared. */
    fun createAppMetadataWithNoSharing(): PersistableBundle {
        return createMetadataWithDataShared(PersistableBundle())
    }

    /**
     * Returns an App Metadata [PersistableBundle] representation where location data is shared, but
     * not for advertising purpose.
     */
    fun createAppMetadataWithLocationSharingNoAds(): PersistableBundle {
        val locationBundle =
            PersistableBundle().apply {
                putPersistableBundle(
                    APPROX_LOCATION,
                    PersistableBundle().apply {
                        putIntArray(
                            KEY_PURPOSES,
                            listOf(PURPOSE_FRAUD_PREVENTION_SECURITY).toIntArray()
                        )
                    }
                )
            }

        val dataSharedBundle =
            PersistableBundle().apply { putPersistableBundle(LOCATION_CATEGORY, locationBundle) }

        return createMetadataWithDataShared(dataSharedBundle)
    }

    /**
     * Returns an App Metadata [PersistableBundle] representation where location data is shared,
     * including for advertising purpose.
     */
    fun createAppMetadataWithLocationSharingAds(): PersistableBundle {
        val locationBundle =
            PersistableBundle().apply {
                putPersistableBundle(
                    APPROX_LOCATION,
                    PersistableBundle().apply {
                        putIntArray(KEY_PURPOSES, listOf(PURPOSE_ADVERTISING).toIntArray())
                    }
                )
            }

        val dataSharedBundle =
            PersistableBundle().apply { putPersistableBundle(LOCATION_CATEGORY, locationBundle) }

        return createMetadataWithDataShared(dataSharedBundle)
    }

    private fun createMetadataWithDataShared(
        dataSharedBundle: PersistableBundle
    ): PersistableBundle {
        val dataLabelBundle =
            PersistableBundle().apply { putPersistableBundle(KEY_DATA_SHARED, dataSharedBundle) }

        val safetyLabelBundle =
            PersistableBundle().apply {
                putLong(KEY_VERSION, INITIAL_SAFETY_LABELS_VERSION)
                putPersistableBundle(KEY_DATA_LABELS, dataLabelBundle)
            }

        return PersistableBundle().apply {
            putLong(KEY_VERSION, INITIAL_TOP_LEVEL_VERSION)
            putPersistableBundle(KEY_SAFETY_LABELS, safetyLabelBundle)
        }
    }

    private const val INITIAL_SAFETY_LABELS_VERSION = 1L
    private const val INITIAL_TOP_LEVEL_VERSION = 1L
    private const val INVALID_SAFETY_LABELS_VERSION = -1L
    private const val INVALID_TOP_LEVEL_VERSION = 0L

    private const val LOCATION_CATEGORY = "location"
    private const val APPROX_LOCATION = "approx_location"
    private const val PURPOSE_FRAUD_PREVENTION_SECURITY = 4
    private const val PURPOSE_ADVERTISING = 5

    private const val KEY_VERSION = "version"
    private const val KEY_SAFETY_LABELS = "safety_labels"
    private const val KEY_INVALID = "invalid_safety_labels"
    private const val KEY_DATA_SHARED = "data_shared"
    private const val KEY_DATA_LABELS = "data_labels"
    private const val KEY_PURPOSES = "purposes"
}
