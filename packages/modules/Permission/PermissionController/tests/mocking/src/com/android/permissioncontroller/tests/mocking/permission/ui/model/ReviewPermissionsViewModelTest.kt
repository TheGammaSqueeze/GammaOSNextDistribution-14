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

package com.android.permissioncontroller.tests.mocking.permission.ui.model

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel.PermissionTarget.PERMISSION_BACKGROUND
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel.PermissionTarget.PERMISSION_BOTH
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel.PermissionTarget.PERMISSION_FOREGROUND
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel.SummaryMessage
import com.android.permissioncontroller.permission.utils.Utils
import com.android.settingslib.RestrictedLockUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/** Unit tests for [ReviewPermissionsViewModel] */
@RunWith(AndroidJUnit4::class)
class ReviewPermissionsViewModelTest {

    private val testPackageName = "test.package"

    @Mock private lateinit var application: PermissionControllerApplication
    @Mock private lateinit var permGroup: LightAppPermGroup
    @Mock private lateinit var foregroundSubGroup: LightAppPermGroup.AppPermSubGroup
    @Mock private lateinit var backgroundSubGroup: LightAppPermGroup.AppPermSubGroup
    @Mock private lateinit var admin: RestrictedLockUtils.EnforcedAdmin
    @Mock private lateinit var packageManager: PackageManager

    private lateinit var mockitoSession: MockitoSession
    private lateinit var context: Context
    private lateinit var packageInfo: PackageInfo
    private lateinit var model: ReviewPermissionsViewModel

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(Utils::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        context = ApplicationProvider.getApplicationContext()
        val userHandle: UserHandle = android.os.Process.myUserHandle()
        packageInfo = getPackageInfo()

        whenever(packageManager.getPackageInfo(testPackageName, 0)).thenReturn(packageInfo)
        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(Utils.getUserContext(application, userHandle)).thenReturn(context)
        whenever(application.applicationContext).thenReturn(context)
        whenever(application.packageName).thenReturn(testPackageName)
        whenever(permGroup.foreground).thenReturn(foregroundSubGroup)
        whenever(permGroup.background).thenReturn(backgroundSubGroup)

        model = ReviewPermissionsViewModel(application, packageInfo)
    }

    @After
    fun finish() {
        mockitoSession.finishMocking()
    }

    @Test
    fun getSummary_individuallyControllerGroup() {
        val permissionsMap = mutableMapOf<String, LightPermission>()
        val permission1: LightPermission = mock(LightPermission::class.java)
        val permission2: LightPermission = mock(LightPermission::class.java)
        permissionsMap["mockedPermission0"] = permission1
        permissionsMap["mockedPermission1"] = permission2

        whenever(permGroup.allPermissions).thenReturn(permissionsMap)
        whenever(permission1.isGranted).thenReturn(true)

        val summary = model.getSummaryForIndividuallyControlledPermGroup(permGroup)
        assertEquals(
            ReviewPermissionsViewModel.PermissionSummary(SummaryMessage.REVOKED_COUNT, false, 1),
            summary
        )
    }

    @Test
    fun getSummary_foregroundFixedPolicy() {
        whenever(permGroup.isGranted).thenReturn(true)
        whenever(foregroundSubGroup.isPolicyFixed).thenReturn(true)

        val summary =
            model.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_FOREGROUND,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.ENABLED_BY_POLICY_FOREGROUND_ONLY.toPermSummary(), summary)

        val spyViewModel = spy(model)
        doReturn(admin).`when`(spyViewModel).getAdmin(context, permGroup)
        val summaryAdmin =
            spyViewModel.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_FOREGROUND,
                permGroup,
                context
            )
        assertEquals(
            SummaryMessage.ENABLED_BY_ADMIN_FOREGROUND_ONLY.toPermSummary(true),
            summaryAdmin
        )
    }

    @Test
    fun getSummary_backgroundFixedPolicy_foregroundRequested() {
        whenever(backgroundSubGroup.isPolicyFixed).thenReturn(true)

        val summary =
            model.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_FOREGROUND,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.DISABLED_BY_POLICY_BACKGROUND_ONLY.toPermSummary(), summary)

        val spyViewModel = spy(model)
        doReturn(admin).`when`(spyViewModel).getAdmin(context, permGroup)
        val summaryAdmin =
            spyViewModel.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_FOREGROUND,
                permGroup,
                context
            )
        assertEquals(
            SummaryMessage.DISABLED_BY_ADMIN_BACKGROUND_ONLY.toPermSummary(true),
            summaryAdmin
        )
    }

    @Test
    fun getSummary_backgroundFixedPolicy() {
        whenever(backgroundSubGroup.isPolicyFixed).thenReturn(true)

        val summary =
            model.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_BACKGROUND,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.ENABLED_BY_POLICY_BACKGROUND_ONLY.toPermSummary(), summary)

        val spyViewModel = spy(model)
        doReturn(admin).`when`(spyViewModel).getAdmin(context, permGroup)
        val summaryAdmin =
            spyViewModel.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_BACKGROUND,
                permGroup,
                context
            )
        assertEquals(
            SummaryMessage.ENABLED_BY_ADMIN_BACKGROUND_ONLY.toPermSummary(true),
            summaryAdmin
        )
    }

    @Test
    fun getSummary_fullyFixedPolicy_hasForegroundGroup() {
        whenever(permGroup.isPolicyFullyFixed).thenReturn(true)
        whenever(permGroup.hasBackgroundGroup).thenReturn(true)

        val summary =
            model.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_FOREGROUND,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.ENABLED_BY_POLICY_BACKGROUND_ONLY.toPermSummary(), summary)

        val spyViewModel = spy(model)
        doReturn(admin).`when`(spyViewModel).getAdmin(context, permGroup)
        val summaryAdmin =
            spyViewModel.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_FOREGROUND,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.ENABLED_BY_ADMIN_FOREGROUND_ONLY.toPermSummary(), summaryAdmin)
    }

    @Test
    fun getSummary_fullyFixedPolicy_hasBackgroundGroup() {
        whenever(permGroup.isPolicyFullyFixed).thenReturn(true)
        whenever(permGroup.hasBackgroundGroup).thenReturn(true)

        val summary =
            model.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_BACKGROUND,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.ENFORCED_BY_POLICY.toPermSummary(), summary)

        val spyViewModel = spy(model)
        doReturn(admin).`when`(spyViewModel).getAdmin(context, permGroup)
        val summaryAdmin =
            spyViewModel.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_BACKGROUND,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.ENABLED_BY_ADMIN.toPermSummary(), summaryAdmin)
    }

    @Test
    fun getSummary_fullyFixedPolicy_hasNoBackgroundGroup() {
        whenever(permGroup.isPolicyFullyFixed).thenReturn(true)

        val summary =
            model.getSummaryForFixedByPolicyPermissionGroup(PERMISSION_BOTH, permGroup, context)
        assertEquals(SummaryMessage.ENFORCED_BY_POLICY.toPermSummary(), summary)

        val spyViewModel = spy(model)
        doReturn(admin).`when`(spyViewModel).getAdmin(context, permGroup)
        val summaryAdmin =
            spyViewModel.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_BOTH,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.ENABLED_BY_ADMIN.toPermSummary(), summaryAdmin)
    }

    @Test
    fun getSummary_ForegroundDisabledByPolicy() {
        whenever(foregroundSubGroup.isPolicyFixed).thenReturn(true)
        whenever(permGroup.isGranted).thenReturn(false)

        val summary =
            model.getSummaryForFixedByPolicyPermissionGroup(PERMISSION_BOTH, permGroup, context)
        assertEquals(SummaryMessage.ENFORCED_BY_POLICY.toPermSummary(), summary)

        val spyViewModel = spy(model)
        doReturn(admin).`when`(spyViewModel).getAdmin(context, permGroup)
        val adminSummary =
            spyViewModel.getSummaryForFixedByPolicyPermissionGroup(
                PERMISSION_BOTH,
                permGroup,
                context
            )
        assertEquals(SummaryMessage.DISABLED_BY_ADMIN.toPermSummary(), adminSummary)
    }

    @Test
    fun getSummary_systemFixedPolicy() {
        whenever(permGroup.isSystemFixed).thenReturn(true)

        val summary =
            model.getSummaryForFixedByPolicyPermissionGroup(PERMISSION_BOTH, permGroup, context)
        assertEquals(SummaryMessage.ENABLED_SYSTEM_FIXED.toPermSummary(), summary)
    }

    private fun getPackageInfo(): PackageInfo {
        return PackageInfo().apply {
            packageName = testPackageName
            requestedPermissions = listOf<String>().toTypedArray()
            requestedPermissionsFlags = listOf<Int>().toIntArray()
        }
    }
}
