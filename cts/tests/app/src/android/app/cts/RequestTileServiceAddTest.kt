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

package android.app.cts

import android.Manifest.permission.STATUS_BAR
import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Instrumentation
import android.app.StatusBarManager
import android.app.stubs.MockActivity
import android.app.stubs.NotExportedTestTileService
import android.app.stubs.TestTileService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.permission.cts.PermissionUtils
import android.service.quicksettings.TileService
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Test that the request fails in all the expected ways.
 *
 * These tests are for [StatusBarManager.requestAddTileService].
 */
@RunWith(AndroidJUnit4::class)
class RequestTileServiceAddTest {

    companion object {
        private const val LABEL = "label"
        private const val TIME_OUT_SECONDS = 5L
    }

    private lateinit var statusBarService: StatusBarManager
    private lateinit var context: Context
    private lateinit var icon: Icon
    private lateinit var consumer: StoreIntConsumer
    private lateinit var instrumentation: Instrumentation
    private val executor = MoreExecutors.directExecutor()
    private lateinit var latch: CountDownLatch

    @Before
    fun setUp() {
        Assume.assumeTrue(TileService.isQuickSettingsSupported())

        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.getTargetContext()
        statusBarService = context.getSystemService(StatusBarManager::class.java)!!

        icon = Icon.createWithResource(context, R.drawable.ic_android)
        latch = CountDownLatch(1)
        consumer = StoreIntConsumer(latch)
    }

    @Test
    fun testRequestBadPackageFails() {
        val componentName = ComponentName("test_pkg", "test_cls")

        statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor,
                consumer
        )

        latch.await(TIME_OUT_SECONDS, TimeUnit.SECONDS)

        assertThat(consumer.result)
                .isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ERROR_MISMATCHED_PACKAGE)
    }

    @Test
    fun testRequestBadComponentName() {
        val componentName = ComponentName(context, "test_cls")
        statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor,
                consumer
        )

        latch.await(TIME_OUT_SECONDS, TimeUnit.SECONDS)

        assertThat(consumer.result).isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ERROR_BAD_COMPONENT)
    }

    @Test
    fun testDisabledComponent() {
        val componentName = TestTileService.getComponentName()
        context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.SYNCHRONOUS or PackageManager.DONT_KILL_APP
        )

        statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor,
                consumer
        )

        latch.await(TIME_OUT_SECONDS, TimeUnit.SECONDS)

        assertThat(consumer.result).isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ERROR_BAD_COMPONENT)

        // Cleanup
        context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.SYNCHRONOUS or PackageManager.DONT_KILL_APP
        )
    }

    @Test
    fun testNotExportedComponent() {
        val componentName = NotExportedTestTileService.getComponentName()

        statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor,
                consumer
        )

        latch.await(TIME_OUT_SECONDS, TimeUnit.SECONDS)

        assertThat(consumer.result).isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ERROR_BAD_COMPONENT)
    }

    @Test
    fun testAppNotInForeground() {
        // This test is never run in foreground, so it's a good candidate for testing this
        val componentName = TestTileService.getComponentName()
        val activityManager = context.getSystemService(ActivityManager::class.java)!!
        withPermission(context.packageName, android.Manifest.permission.PACKAGE_USAGE_STATS) {
            assertThat(activityManager.getPackageImportance(context.packageName))
                    .isNotEqualTo(IMPORTANCE_FOREGROUND)
        }

        statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor,
                consumer
        )

        latch.await(TIME_OUT_SECONDS, TimeUnit.SECONDS)

        assertThat(consumer.result)
                .isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND)
    }

    @Test
    fun testTwoSimultaneousRequests() {
        // We need an activity in the foreground for the first request to not be denied
        val activity = setUpForActivity()
        val componentName = TestTileService.getComponentName()

        statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor,
                {}
        )

        Thread.sleep(TimeUnit.SECONDS.toMillis(TIME_OUT_SECONDS))

        statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor,
                consumer
        )

        latch.await(TIME_OUT_SECONDS, TimeUnit.SECONDS)
        assertThat(consumer.result)
                .isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS)

        SystemUtil.callWithShellPermissionIdentity(
                { statusBarService.cancelRequestAddTile(componentName.packageName) },
                STATUS_BAR
        )

        activity.finish()
    }

    private inline fun withPermission(packageName: String, permission: String, block: () -> Unit) {
        val isPermissionGranted = PermissionUtils.isPermissionGranted(packageName, permission)
        try {
            PermissionUtils.grantPermission(packageName, permission)
            block()
        } finally {
            if (!isPermissionGranted) {
                PermissionUtils.revokePermission(packageName, permission)
            }
        }
    }

    private fun setUpForActivity(): Activity {
        val intent = Intent(context, MockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        return activity
    }

    private class StoreIntConsumer(private val latch: CountDownLatch) : Consumer<Int> {
        var result: Int? = null

        override fun accept(t: Int) {
            result = t
            latch.countDown()
        }
    }
}