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
package com.android.healthconnect.controller.permissions.connectedapps.searchapps

import com.android.healthconnect.controller.service.MainDispatcher
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.utils.NormalizeUtf8
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class SearchHealthPermissionApps
@Inject
constructor(@MainDispatcher private val mainDispatcher: CoroutineDispatcher) {

    /** Returns a filtered result list of ConnectedAppMetadata. */
    suspend fun search(
        list: List<ConnectedAppMetadata>,
        searchValue: String
    ): List<ConnectedAppMetadata> =
        withContext(mainDispatcher) {
            list.filter { connectedAppMetadata ->
                NormalizeUtf8.normalizeForMatch(connectedAppMetadata.appMetadata.appName)
                    .contains(NormalizeUtf8.normalizeForMatch(searchValue))
            }
        }
}
