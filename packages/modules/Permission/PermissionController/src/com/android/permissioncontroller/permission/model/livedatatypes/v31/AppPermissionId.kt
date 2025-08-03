package com.android.permissioncontroller.permission.model.livedatatypes.v31

import android.os.UserHandle

/** Identifier for an app permission group combination. */
data class AppPermissionId(
    val packageName: String,
    val userHandle: UserHandle,
    val permissionGroup: String,
)
