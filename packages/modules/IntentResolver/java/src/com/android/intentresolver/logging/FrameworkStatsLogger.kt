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
package com.android.intentresolver.logging

import com.android.internal.util.FrameworkStatsLog

/** A documenting annotation for FrameworkStatsLog methods and their associated UiEvents. */
internal annotation class ForUiEvent(vararg val uiEventId: Int)

/** Isolates the specific method signatures to use for each of the logged UiEvents. */
interface FrameworkStatsLogger {

    @ForUiEvent(FrameworkStatsLog.SHARESHEET_STARTED)
    fun write(
        frameworkEventId: Int,
        appEventId: Int,
        packageName: String?,
        instanceId: Int,
        mimeType: String?,
        numAppProvidedDirectTargets: Int,
        numAppProvidedAppTargets: Int,
        isWorkProfile: Boolean,
        previewType: Int,
        intentType: Int,
        numCustomActions: Int,
        modifyShareActionProvided: Boolean,
    ) {
        FrameworkStatsLog.write(
            frameworkEventId, /* event_id = 1 */
            appEventId, /* package_name = 2 */
            packageName, /* instance_id = 3 */
            instanceId, /* mime_type = 4 */
            mimeType, /* num_app_provided_direct_targets */
            numAppProvidedDirectTargets, /* num_app_provided_app_targets */
            numAppProvidedAppTargets, /* is_workprofile */
            isWorkProfile, /* previewType = 8 */
            previewType, /* intentType = 9 */
            intentType, /* num_provided_custom_actions = 10 */
            numCustomActions, /* modify_share_action_provided = 11 */
            modifyShareActionProvided
        )
    }

    @ForUiEvent(FrameworkStatsLog.RANKING_SELECTED)
    fun write(
        frameworkEventId: Int,
        appEventId: Int,
        packageName: String?,
        instanceId: Int,
        positionPicked: Int,
        isPinned: Boolean,
    ) {
        FrameworkStatsLog.write(
            frameworkEventId, /* event_id = 1 */
            appEventId, /* package_name = 2 */
            packageName, /* instance_id = 3 */
            instanceId, /* position_picked = 4 */
            positionPicked, /* is_pinned = 5 */
            isPinned
        )
    }
}
