/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.android.permissioncontroller.permission.data.PermGroupsPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.data.StandardPermGroupNamesLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo

/**
 * A [androidx.lifecycle.ViewModel] for [ManagePermissionsFragment] and
 * [ManagePermissionsOtherFragment]. However, [ManagePermissionsViewModel] is designed in a way so
 * that its owner should be an [Activity][androidx.fragment.app.FragmentActivity] rather than
 * individual [Fragments][androidx.fragment.app.Fragment], and the aforementioned Fragments that
 * manage different sets of the permission groups should to share a single instance of
 * [ManagePermissionsViewModel].
 */
class ManagePermissionsViewModel(app: Application) : AndroidViewModel(app) {

    /** [LiveData] that contains a list of all platform-defined permission groups. */
    val standardPermGroupsLiveData: LiveData<List<PermGroupPackagesUiInfo>> =
        MediatorLiveData<List<PermGroupPackagesUiInfo>>().apply {
            addSource(PermGroupsPackagesUiInfoLiveData(app, StandardPermGroupNamesLiveData)) {
                permGroups ->
                value = permGroups.values.filterNotNull()
            }
        }

    /**
     * [LiveData] that contains a list of platform-defined permission groups, such that at least one
     * the permissions in the group has been requested at runtime by at least one non-system
     * application or has been pregranted to a non-system application.
     *
     * @see com.android.permissioncontroller.permission.ui.television.ManagePermissionsFragment
     */
    val usedPermissionGroups: LiveData<List<PermGroupPackagesUiInfo>> =
        MediatorLiveData<List<PermGroupPackagesUiInfo>>().apply {
            addSource(standardPermGroupsLiveData) { permGroups ->
                value = permGroups.filter { it.nonSystemUserSetOrPreGranted > 0 }
            }
        }

    /**
     * [LiveData] that contains a list of platform-defined permission groups, such that all of the
     * permissions in the group neither has been requested at runtime by any of the non-system
     * applications nor has been pregranted to any such application. But at least one of the
     * permissions in the group is requested by or pregranted to at least one system application,
     * other than the Shell (we do not show permission groups that are granted only to the Shell,
     * because it has all the permissions granted).
     *
     * @see com.android.permissioncontroller.permission.ui.television.ManagePermissionsOtherFragment
     */
    val unusedPermissionGroups: LiveData<List<PermGroupPackagesUiInfo>> =
        MediatorLiveData<List<PermGroupPackagesUiInfo>>().apply {
            addSource(standardPermGroupsLiveData) { permGroups ->
                value =
                    permGroups
                        .filter { it.nonSystemUserSetOrPreGranted == 0 }
                        .filter { it.systemUserSetOrPreGranted > 0 }
                        .filterNot { it.onlyShellPackageGranted }
            }
        }

    /**
     * [LiveData] that contains a list of the application-defined permission groups (a.k.a. "custom"
     * permissions), such that at least one of the permissions in the group has been requested at
     * runtime by or has been pregranted to at least one application (system or non-system).
     *
     * @see com.android.permissioncontroller.permission.ui.television.ManagePermissionsOtherFragment
     */
    val additionalPermissionGroups: LiveData<List<PermGroupPackagesUiInfo>> =
        MediatorLiveData<List<PermGroupPackagesUiInfo>>().apply {
            addSource(PermGroupsPackagesUiInfoLiveData(app, UsedCustomPermGroupNamesLiveData())) {
                permGroups ->
                value =
                    permGroups.values.filterNotNull().filter {
                        (it.nonSystemUserSetOrPreGranted > 0) or (it.systemUserSetOrPreGranted > 0)
                    }
            }
        }

    /**
     * [LiveData] that indicates whether there any unused or additional permission groups.
     *
     * @see com.android.permissioncontroller.permission.ui.television.ManagePermissionsFragment
     */
    @get:JvmName("hasUnusedOrAdditionalPermissionGroups")
    val hasUnusedOrAdditionalPermissionGroups: LiveData<Boolean> =
        MediatorLiveData<Boolean>().apply {
            val updateValue: (Any?) -> Unit = {
                value =
                    !unusedPermissionGroups.value.isNullOrEmpty() ||
                        !additionalPermissionGroups.value.isNullOrEmpty()
            }
            addSource(unusedPermissionGroups, updateValue)
            addSource(additionalPermissionGroups, updateValue)
        }
}
