/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.appops2.cts

import android.app.AppOpsManager
import android.app.AppOpsManager.OnOpNotedCallback
import android.app.AppOpsManager.permissionToOp
import android.app.AsyncNotedAppOp
import android.app.PendingIntent
import android.app.SyncNotedAppOp
import android.app.appops.cts.eventually
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor

private const val TEST_ATTRIBUTION_TAG = "testAttribution"

@AppModeFull(reason = "Test relies on other app to connect to. Instant apps can't see other apps")
class AppOpsLoggingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!

    // Collected note-op calls inside of this process
    private val noted = mutableListOf<Pair<SyncNotedAppOp, Array<StackTraceElement>>>()
    private val selfNoted = mutableListOf<Pair<SyncNotedAppOp, Array<StackTraceElement>>>()
    private val asyncNoted = mutableListOf<AsyncNotedAppOp>()

    @Before
    fun setNotedAppOpsCollectorAndClearCollectedNoteOps() {
        setNotedAppOpsCollector()
        clearCollectedNotedOps()
    }

    private fun clearCollectedNotedOps() {
        noted.clear()
        selfNoted.clear()
        asyncNoted.clear()
    }

    private fun setNotedAppOpsCollector() {
        appOpsManager.setOnOpNotedCallback(Executor { it.run() },
                object : OnOpNotedCallback() {
                    override fun onNoted(op: SyncNotedAppOp) {
                        noted.add(op to Throwable().stackTrace)
                    }

                    override fun onSelfNoted(op: SyncNotedAppOp) {
                        selfNoted.add(op to Throwable().stackTrace)
                    }

                    override fun onAsyncNoted(asyncOp: AsyncNotedAppOp) {
                        asyncNoted.add(asyncOp)
                    }
                })
    }

    /**
     * Realistic end-to-end test for requesting to install a package
     */
    @Test
    fun requestInstall() {
        val pi = context.createAttributionContext(TEST_ATTRIBUTION_TAG).packageManager
                .packageInstaller
        val sessionId = pi.createSession(SessionParams(MODE_FULL_INSTALL))

        val session = pi.openSession(sessionId)
        try {
            // Write apk data to session
            File("/data/local/tmp/cts/appops2/CtsAppToBlame1.apk")
                    .inputStream().use { fileOnDisk ->
                        session.openWrite("base.apk", 0, -1).use { sessionFile ->
                            fileOnDisk.copyTo(sessionFile)
                        }
                    }

            val installAction = context.packageName + ".install_cb"
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(ignored: Context?, intent: Intent) {
                    if (intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)
                            != STATUS_PENDING_USER_ACTION) {
                        return
                    }

                    // Start package install request UI (should trigger REQUEST_INSTALL_PACKAGES)
                    val activityIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    activityIntent!!.addFlags(
                            FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(activityIntent)
                }
            }, IntentFilter(installAction), Context.RECEIVER_EXPORTED)

            // Commit session (should trigger installAction receiver)
            session.commit(PendingIntent.getBroadcast(context, 0,
                    Intent(installAction).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender)

            eventually {
                assertThat(asyncNoted[0].op).isEqualTo(
                        permissionToOp(android.Manifest.permission.REQUEST_INSTALL_PACKAGES))
                assertThat(asyncNoted[0].attributionTag).isEqualTo(TEST_ATTRIBUTION_TAG)
            }
        } finally {
            session.abandon()
        }
    }

    @After
    fun removeNotedAppOpsCollector() {
        appOpsManager.setOnOpNotedCallback(null, null)
    }
}
