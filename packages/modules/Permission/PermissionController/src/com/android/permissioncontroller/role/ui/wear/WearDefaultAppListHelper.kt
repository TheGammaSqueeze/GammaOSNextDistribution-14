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

package com.android.permissioncontroller.role.ui.wear

import android.content.Context
import android.os.UserHandle
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.role.ui.DefaultAppActivity
import com.android.permissioncontroller.role.ui.RoleItem
import com.android.permissioncontroller.role.utils.RoleUiBehaviorUtils
import com.android.role.controller.model.Roles

/** A helper class to retrieve default apps to [WearDefaultAppListScreen]. */
class WearDefaultAppListHelper(val context: Context, val user: UserHandle) {
    fun getPreferences(defaultRole: List<RoleItem>): List<WearRolePreference> {
        return defaultRole
            .map { roleItem ->
                WearRolePreference(
                        context = context,
                        label = context.getString(roleItem.role.shortLabelResource),
                        onDefaultClicked = {
                            run {
                                val roleName: String = roleItem.role.name
                                val role = Roles.get(context)[roleName]
                                var intent =
                                    RoleUiBehaviorUtils.getManageIntentAsUser(role!!, user, context)
                                if (intent == null) {
                                    intent =
                                        DefaultAppActivity.createIntent(roleName, user, context)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    .apply {
                        val holderApplicationInfos = roleItem.holderApplicationInfos
                        if (holderApplicationInfos.isEmpty()) {
                            icon = null
                            summary = context.getString(R.string.default_app_none)
                        } else {
                            val holderApplicationInfo = holderApplicationInfos[0]
                            icon = Utils.getBadgedIcon(context, holderApplicationInfo)
                            summary = Utils.getAppLabel(holderApplicationInfo, context)
                        }
                    }
                    .let {
                        RoleUiBehaviorUtils.preparePreferenceAsUser(
                            roleItem.role,
                            roleItem.holderApplicationInfos,
                            it,
                            user,
                            context
                        )
                        return@map it
                    }
            }
            .toList()
    }
}
