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
package android.activityrecognition.cts

import android.Manifest
import android.app.AppOpsManager
import android.app.Instrumentation
import android.app.UiAutomation
import android.app.role.RoleManager
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@AppModeFull(reason="Instant apps have no access to the RoleManager")
class RenouncedPermissionsTest {

    var oldActivityRecognizers: List<String>? = null
        get() { return field }
        set(value) { field = value }

    @Before
    fun makeSelfActivityRecognizer() {
        SystemUtil.runWithShellPermissionIdentity {
            val roleManager = context.getSystemService(RoleManager::class.java)!!
            oldActivityRecognizers = roleManager.getRoleHolders(
                    RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER)
            roleManager.isBypassingRoleQualification = true
            addActivityRecognizer(context.packageName)
        }
    }

    @After
    fun restoreActivityRecognizers() {
        if (oldActivityRecognizers != null) {
            SystemUtil.runWithShellPermissionIdentity {
                for (oldActivityRecongizer in oldActivityRecognizers!!) {
                    addActivityRecognizer(oldActivityRecongizer)
                }
                val roleManager = context.getSystemService(RoleManager::class.java)!!
                roleManager.isBypassingRoleQualification = false
            }
        }
    }

    @Before
    fun setUpTest() {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
        SystemUtil.runWithShellPermissionIdentity {
            appOpsManager.resetPackageOpsNoHistory(context.packageName)
        }
    }

    fun addActivityRecognizer(activityRecognizer: String) {
        val latch = CountDownLatch(1)
        val roleManager = context.getSystemService(RoleManager::class.java)!!
        roleManager.addRoleHolderAsUser(RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER,
                activityRecognizer, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                UserHandle.SYSTEM, context.mainExecutor) {
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    @Test
    fun testActivityRecognitionAttributionTagBlaming() {
        // Using an AR allow listed tag
        var timeBeforeArAccess = System.currentTimeMillis()
        accessActivityRecognition(VALID_AR_ATTRIBUTION_TAG)
        assertNotedOpsSinceLastArAccess(timeBeforeArAccess, /*expectedOp*/
                AppOpsManager.OPSTR_ACTIVITY_RECOGNITION_SOURCE, /*unexpectedOp*/
                AppOpsManager.OPSTR_ACTIVITY_RECOGNITION)

        // Using an AR not allow listed tag
        timeBeforeArAccess = System.currentTimeMillis()
        accessActivityRecognition(INVALID_AR_ATTRIBUTION_TAG)
        assertNotedOpsSinceLastArAccess(timeBeforeArAccess, /*expectedOp*/
                AppOpsManager.OPSTR_ACTIVITY_RECOGNITION, /*unexpectedOp*/
                AppOpsManager.OPSTR_ACTIVITY_RECOGNITION_SOURCE)
    }

    fun accessActivityRecognition(attributionTag: String) {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        appOpsManager?.noteOp(AppOpsManager.OPSTR_ACTIVITY_RECOGNITION, Process.myUid(),
                context.packageName, attributionTag, /*message*/ null)
    }

    fun assertNotedOpsSinceLastArAccess(timeBeforeArAccess: Long,
            expectedOp: String, unexpectedOp: String) {
        val automation: UiAutomation = instrumentation.getUiAutomation()
        automation.adoptShellPermissionIdentity(Manifest.permission.GET_APP_OPS_STATS)
        try {
            val appOpsManager: AppOpsManager = context.getSystemService(AppOpsManager::class.java)!!
            val affectedPackageOps = appOpsManager.getPackagesForOps(
                    arrayOf(expectedOp, unexpectedOp))
            val packageCount = affectedPackageOps.size
            for (i in 0 until packageCount) {
                val packageOps = affectedPackageOps[i]
                if (!context.getPackageName().equals(packageOps.packageName)) {
                    continue
                }
                // We are pulling stats only for one app op.
                val opEntries = packageOps.ops
                val opEntryCount = opEntries.size
                for (j in 0 until opEntryCount) {
                    val opEntry = opEntries[j]
                    if (unexpectedOp == opEntry.opStr) {
                        fail("Unexpected access to $unexpectedOp")
                    } else if (expectedOp == opEntry.opStr) {
                        if (opEntry.getLastAccessTime(AppOpsManager.OP_FLAGS_ALL_TRUSTED) >=
                                timeBeforeArAccess) {
                            return
                        }
                        break
                    }
                }
            }
            fail("No expected access to $expectedOp")
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    companion object {
        val VALID_AR_ATTRIBUTION_TAG = "valid_ar_attribution_tag"
        val INVALID_AR_ATTRIBUTION_TAG = "invalid_ar_attribution_tag"

        private val context: Context
            get () = InstrumentationRegistry.getInstrumentation().getContext()

        private val instrumentation: Instrumentation
            get () = InstrumentationRegistry.getInstrumentation()
    }
}
