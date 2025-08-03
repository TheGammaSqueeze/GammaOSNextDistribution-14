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
package android.attributionsource.cts

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.AttributionSource
import android.content.Context
import android.content.ContextParams
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import android.os.UserHandle
import android.permission.PermissionManager
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.ArraySet
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.function.ThrowingRunnable
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class RenouncedPermissionsTest {

    @get:Rule
    public val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @Throws(Exception::class)
    @AppModeFull(reason = "Instant apps cannot hold READ_CALENDAR/READ_CONTACTS permissions")
    fun testRenouncePermissionsChain() {
        val receiverAttributionSource = getShellAttributionSourceWithRenouncedPermissions()
        val activity = createActivityWithAttributionContext(receiverAttributionSource)

        // Out app has the permissions
        assertThat(activity.checkSelfPermission(Manifest.permission.READ_CALENDAR))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)
        assertThat(activity.checkSelfPermission(Manifest.permission.READ_CONTACTS))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)

        // Accessing the data should also fail (for us and next in the data flow)
        assertThrows(
            SecurityException::class.java,
            ThrowingRunnable {
                activity.contentResolver
                    .query(CalendarContract.Calendars.CONTENT_URI, null, null, null)!!
                    .close()
            })
        assertThrows(
            SecurityException::class.java,
            ThrowingRunnable {
                activity.contentResolver
                    .query(ContactsContract.Contacts.CONTENT_URI, null, null, null)!!
                    .close()
            })
    }

    @Test(expected = SecurityException::class)
    fun testCannotRenouncePermissionsWithoutPermission() {
        val renouncedPermissions = ArraySet<String>()
        renouncedPermissions.add(Manifest.permission.READ_CONTACTS)

        // Trying to renounce permissions with no permission throws
        createActivityWithAttributionContext(
            /*receiverAttributionSource*/ null, renouncedPermissions)
    }

    @Test(expected = IllegalArgumentException::class)
    @RequiresFlagsEnabled(Flags.FLAG_ATTRIBUTION_SOURCE_CONSTRUCTOR)
    fun testCannotRequestRenouncePermissions_attributionSourceConstructorEnabled() {
        val renouncedPermissions = arrayOf(Manifest.permission.READ_CONTACTS)
        val attributionSource = AttributionSource(Process.myUid(),
                Process.myPid(), context.packageName, /*attributionTag*/ null,
                /*token*/ Binder(), renouncedPermissions, /*next*/ null)
        val activity = createActivityWithAttributionSource(attributionSource)

        // Requesting renounced permissions throws
        activity.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)
    }

    @Test(expected = IllegalArgumentException::class)
    @RequiresFlagsDisabled(Flags.FLAG_ATTRIBUTION_SOURCE_CONSTRUCTOR)
    fun testCannotRequestRenouncePermissions_attributionSourceConstructorDisabled() {
        val renouncedPermissions = arrayOf(Manifest.permission.READ_CONTACTS)
        val attributionSource = AttributionSource(
                Process.myUid(), context.packageName, null,
                ArraySet<String>(renouncedPermissions), null)
        val activity = createActivityWithAttributionSource(attributionSource)

        // Requesting renounced permissions throws
        activity.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)
    }

    fun createActivityWithAttributionContext(
        receiverAttributionSource: AttributionSource?,
        renouncedPermissions: Set<String>? = null
    ): Activity {
        val contextParams =
            ContextParams.Builder()
                .setRenouncedPermissions(renouncedPermissions)
                .setNextAttributionSource(receiverAttributionSource)
                .build()
        return createActivityWithContextParams(contextParams)
    }

    fun createActivityWithAttributionSource(attributionSource: AttributionSource): Activity {
        val mockActivity = Mockito.mock(Activity::class.java)
        `when`(mockActivity.getAttributionSource()).thenReturn(attributionSource)
        return mockActivity
    }

    fun createActivityWithContextParams(contextParams: ContextParams): Activity {
        val activityReference = AtomicReference<NoOpActivity>()
        instrumentation.runOnMainSync { activityReference.set(NoOpActivity()) }
        val activity = activityReference.get()
        activity.attachBaseContext(context.createContext(contextParams))
        return activity
    }

    companion object {
        private val context: Context
            get() = InstrumentationRegistry.getInstrumentation().getContext()

        private val instrumentation: Instrumentation
            get() = InstrumentationRegistry.getInstrumentation()

        fun getShellAttributionSourceWithRenouncedPermissions(): AttributionSource {
            // Let's cook up an attribution source for the shell with its cooperation
            val renouncedPermissionsSet = ArraySet<String>()
            renouncedPermissionsSet.add(Manifest.permission.READ_CONTACTS)
            renouncedPermissionsSet.add(Manifest.permission.READ_CALENDAR)

            // Calculate the shellUid to account for running this from a secondary user.
            val shellUid =
                UserHandle.getUid(
                    Process.myUserHandle().identifier, UserHandle.getAppId(Process.SHELL_UID))
            var shellAttributionSource =
                AttributionSource.Builder(shellUid)
                    .setPackageName("com.android.shell")
                    .setRenouncedPermissions(renouncedPermissionsSet)
                    .build()

            SystemUtil.runWithShellPermissionIdentity {
                val permissionManager = context.getSystemService(PermissionManager::class.java)!!
                shellAttributionSource =
                    permissionManager.registerAttributionSource(shellAttributionSource)
            }

            return shellAttributionSource
        }
    }
}
