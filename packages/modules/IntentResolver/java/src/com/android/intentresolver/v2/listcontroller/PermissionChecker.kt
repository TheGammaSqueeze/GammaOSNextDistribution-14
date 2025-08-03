package com.android.intentresolver.v2.listcontroller

import android.app.ActivityManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Class for checking if a permission has been granted. */
interface PermissionChecker {
    /** Checks if the given [permission] has been granted. */
    suspend fun checkComponentPermission(
        permission: String,
        uid: Int,
        owningUid: Int,
        exported: Boolean,
    ): Int
}

/**
 * Class for checking if a permission has been granted using the static
 * [ActivityManager.checkComponentPermission].
 */
class ActivityManagerPermissionChecker(
    private val bgDispatcher: CoroutineDispatcher,
) : PermissionChecker {
    override suspend fun checkComponentPermission(
        permission: String,
        uid: Int,
        owningUid: Int,
        exported: Boolean,
    ): Int =
        withContext(bgDispatcher) {
            ActivityManager.checkComponentPermission(permission, uid, owningUid, exported)
        }
}
