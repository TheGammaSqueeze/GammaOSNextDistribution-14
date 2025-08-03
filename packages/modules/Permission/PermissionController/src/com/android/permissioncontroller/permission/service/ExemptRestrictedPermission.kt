package com.android.permissioncontroller.permission.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle

/**
 * For manually exempting a restricted permission. STOPSHIP This functionality should not be in the
 * final release.
 */
class ExemptRestrictedPermission : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra("package") ?: return
        val permission = intent.getStringExtra("permission") ?: return
        val userId = intent.getIntExtra("user", Process.myUserHandle().identifier)

        val userContext = context.createContextAsUser(UserHandle.of(userId), 0)

        // Use upgrade flag. If the permission needs to be manually exempted then it probably
        // should have been done on upgrade.
        userContext.packageManager.addWhitelistedRestrictedPermission(
            packageName,
            permission,
            PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
        )
    }
}
