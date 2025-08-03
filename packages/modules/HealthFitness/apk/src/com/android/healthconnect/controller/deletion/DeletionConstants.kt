/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.deletion

/** Constants used for deletion operations. */
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
object DeletionConstants {

    /** Used for attaching the DeletionFragment. */
    const val FRAGMENT_TAG_DELETION = "FRAGMENT_TAG_DELETION"

    /** The key of a fragment result representing that the delete data button has been pressed. */
    const val START_DELETION_EVENT = "START_DELETION_EVENT"

    /**
     * The key of a fragment result representing that the delete data button has been pressed on an
     * inactive app preference.
     */
    const val START_INACTIVE_APP_DELETION_EVENT = "START_INACTIVE_APP_DELETION_EVENT"

    /** The key of a fragment result representing that a time range has been selected. */
    const val TIME_RANGE_SELECTION_EVENT = "TIME_RANGE_SELECTION_EVENT"

    /** The key of a fragment result representing that the go back button has been pressed. */
    const val GO_BACK_EVENT = "GO_BACK_EVENT"

    /**
     * The key of a fragment result representing that the deletion parameters have been confirmed.
     */
    const val CONFIRMATION_EVENT = "CONFIRMATION_EVENT"

    /** The key of a fragment result representing a successful deletion. */
    const val DELETION_COMPLETED_SUCCESS_EVENT = "DELETION_COMPLETED_SUCCESS_EVENT"

    /** The key of a fragment result representing a failed deletion. */
    const val DELETION_FAILURE_EVENT = "DELETION_FAILURE_EVENT"

    /** The key of a fragment result representing that the try again button has been pressed. */
    const val TRY_AGAIN_EVENT = "TRY_AGAIN_EVENT"

    /** The bundle key for the deletion type requested by a fragment. */
    const val DELETION_TYPE = "DELETION_TYPE"

    /**
     * The bundle key for showing the time range picker dialog from a fragment, used when the value
     * should be false.
     */
    const val SHOW_PICKER = "SHOW_PICKER"

    /** The bundle key for start time. */
    const val START_TIME = "START_TIME"

    /** The bundle key for end time. */
    const val END_TIME = "END_TIME"
}
