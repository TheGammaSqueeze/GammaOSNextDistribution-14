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

package android.uidmigration.cts

import android.content.pm.PackageManager
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.server.pm.SharedUidMigration
import com.android.server.pm.SharedUidMigration.PROPERTY_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

const val TMP_APK_PATH = "/data/local/tmp/cts/uidmigration"

val FLAG_ZERO = PackageManager.PackageInfoFlags.of(0)

// What each APK meant
// APK : pkg , with sharedUserId
// APK2: pkg2, with sharedUserId
// APK3: pkg , with sharedUserId removed
// APK4: pkg , with sharedUserMaxSdkVersion="32"

object InstallTest {
    const val APK = "$TMP_APK_PATH/InstallTestApp.apk"
    const val APK2 = "$TMP_APK_PATH/InstallTestApp2.apk"
    const val APK3 = "$TMP_APK_PATH/InstallTestApp3.apk"
    const val APK4 = "$TMP_APK_PATH/InstallTestApp4.apk"
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> T?.assertNotNull(): T {
    assertNotNull(this)
    return this!!
}

@Suppress("NOTHING_TO_INLINE")
inline fun assertEquals(a: Int, b: Int) = assertEquals(a.toLong(), b.toLong())

// Identical regardless of order
fun <T> Array<T>.sameAs(vararg items: T) =
        size == items.size && all { items.contains(it) } && items.all { contains(it) }

fun installPackage(apkPath: String): Boolean {
    return runShellCommand("pm install --force-queryable -t $apkPath") == "Success\n"
}

fun uninstallPackage(packageName: String) {
    runShellCommand("pm uninstall $packageName")
}

@SharedUidMigration.Strategy
var migrationStrategy: Int
    get() = SharedUidMigration.getCurrentStrategy()
    set(value) { runShellCommand("setprop $PROPERTY_KEY $value") }

inline fun withStrategy(strategy: Int? = null, body: () -> Unit) {
    if (SharedUidMigration.isDisabled()) {
        // Nothing to test if shared UID migration is disabled
        return
    }

    val backup = migrationStrategy
    strategy?.let { migrationStrategy = it }
    try {
        body.invoke()
    } finally {
        // Always restore the device state no matter what happened
        migrationStrategy = backup
    }
}