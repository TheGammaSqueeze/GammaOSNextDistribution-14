/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.server.connectivity

import android.Manifest
import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.Build
import android.os.Handler
import android.os.UserHandle
import android.util.ArraySet
import com.android.server.makeMockUserManager
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.concurrent.Executor
import java.util.function.Consumer

private const val USER = 0
val USER_INFO = UserInfo(USER, "" /* name */, UserInfo.FLAG_PRIMARY)
val USER_HANDLE = UserHandle(USER)
private const val PRIMARY_USER = 0
private const val SECONDARY_USER = 10
private val PRIMARY_USER_HANDLE = UserHandle.of(PRIMARY_USER)
private val SECONDARY_USER_HANDLE = UserHandle.of(SECONDARY_USER)
// sms app names
private const val SMS_APP1 = "sms_app_1"
private const val SMS_APP2 = "sms_app_2"
// sms app ids
private const val SMS_APP_ID1 = 100
private const val SMS_APP_ID2 = 101
// UID for app1 and app2 on primary user
// These app could become default sms app for user1
private val PRIMARY_USER_SMS_APP_UID1 = UserHandle.getUid(PRIMARY_USER, SMS_APP_ID1)
private val PRIMARY_USER_SMS_APP_UID2 = UserHandle.getUid(PRIMARY_USER, SMS_APP_ID2)
// UID for app1 and app2 on secondary user
// These app could become default sms app for user2
private val SECONDARY_USER_SMS_APP_UID1 = UserHandle.getUid(SECONDARY_USER, SMS_APP_ID1)
private val SECONDARY_USER_SMS_APP_UID2 = UserHandle.getUid(SECONDARY_USER, SMS_APP_ID2)

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class SatelliteAccessControllerTest {
    private val context = mock(Context::class.java)
    private val mPackageManager = mock(PackageManager::class.java)
    private val mHandler = mock(Handler::class.java)
    private val mRoleManager =
        mock(SatelliteAccessController.Dependencies::class.java)
    private val mCallback = mock(Consumer::class.java) as Consumer<Set<Int>>
    private val mSatelliteAccessController =
        SatelliteAccessController(context, mRoleManager, mCallback, mHandler)
    private lateinit var mRoleHolderChangedListener: OnRoleHoldersChangedListener
    @Before
    @Throws(PackageManager.NameNotFoundException::class)
    fun setup() {
        makeMockUserManager(USER_INFO, USER_HANDLE)
        doReturn(context).`when`(context).createContextAsUser(any(), anyInt())
        doReturn(mPackageManager).`when`(context).packageManager

        doReturn(PackageManager.PERMISSION_GRANTED)
            .`when`(mPackageManager)
            .checkPermission(Manifest.permission.SATELLITE_COMMUNICATION, SMS_APP1)
        doReturn(PackageManager.PERMISSION_GRANTED)
            .`when`(mPackageManager)
            .checkPermission(Manifest.permission.SATELLITE_COMMUNICATION, SMS_APP2)

        // Initialise default message application primary user package1
        val applicationInfo1 = ApplicationInfo()
        applicationInfo1.uid = PRIMARY_USER_SMS_APP_UID1
        doReturn(applicationInfo1)
            .`when`(mPackageManager)
            .getApplicationInfo(eq(SMS_APP1), anyInt())

        // Initialise default message application primary user package2
        val applicationInfo2 = ApplicationInfo()
        applicationInfo2.uid = PRIMARY_USER_SMS_APP_UID2
        doReturn(applicationInfo2)
            .`when`(mPackageManager)
            .getApplicationInfo(eq(SMS_APP2), anyInt())

        // Get registered listener using captor
        val listenerCaptor = ArgumentCaptor.forClass(
            OnRoleHoldersChangedListener::class.java
        )
        mSatelliteAccessController.start()
        verify(mRoleManager).addOnRoleHoldersChangedListenerAsUser(
            any(Executor::class.java), listenerCaptor.capture(), any(UserHandle::class.java))
        mRoleHolderChangedListener = listenerCaptor.value
    }

    @Test
    fun test_onRoleHoldersChanged_SatelliteFallbackUid_Changed_SingleUser() {
        doReturn(listOf<String>()).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback, never()).accept(any())

        // check DEFAULT_MESSAGING_APP1 is available as satellite network fallback uid
        doReturn(listOf(SMS_APP1))
            .`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback).accept(setOf(PRIMARY_USER_SMS_APP_UID1))

        // check SMS_APP2 is available as satellite network Fallback uid
        doReturn(listOf(SMS_APP2)).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback).accept(setOf(PRIMARY_USER_SMS_APP_UID2))

        // check no uid is available as satellite network fallback uid
        doReturn(listOf<String>()).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback).accept(ArraySet())
    }

    @Test
    fun test_onRoleHoldersChanged_NoSatelliteCommunicationPermission() {
        doReturn(listOf<Any>()).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback, never()).accept(any())

        // check DEFAULT_MESSAGING_APP1 is not available as satellite network fallback uid
        // since satellite communication permission not available.
        doReturn(PackageManager.PERMISSION_DENIED)
            .`when`(mPackageManager)
            .checkPermission(Manifest.permission.SATELLITE_COMMUNICATION, SMS_APP1)
        doReturn(listOf(SMS_APP1))
            .`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback, never()).accept(any())
    }

    @Test
    fun test_onRoleHoldersChanged_RoleSms_NotAvailable() {
        doReturn(listOf(SMS_APP1))
            .`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_BROWSER,
            PRIMARY_USER_HANDLE)
        verify(mCallback, never()).accept(any())
    }

    @Test
    fun test_onRoleHoldersChanged_SatelliteNetworkFallbackUid_Changed_multiUser() {
        doReturn(listOf<String>()).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback, never()).accept(any())

        // check SMS_APP1 is available as satellite network fallback uid at primary user
        doReturn(listOf(SMS_APP1))
            .`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback).accept(setOf(PRIMARY_USER_SMS_APP_UID1))

        // check SMS_APP2 is available as satellite network fallback uid at primary user
        doReturn(listOf(SMS_APP2)).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, PRIMARY_USER_HANDLE)
        verify(mCallback).accept(setOf(PRIMARY_USER_SMS_APP_UID2))

        // check SMS_APP1 is available as satellite network fallback uid at secondary user
        val applicationInfo1 = ApplicationInfo()
        applicationInfo1.uid = SECONDARY_USER_SMS_APP_UID1
        doReturn(applicationInfo1).`when`(mPackageManager)
            .getApplicationInfo(eq(SMS_APP1), anyInt())
        doReturn(listOf(SMS_APP1)).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            SECONDARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, SECONDARY_USER_HANDLE)
        verify(mCallback).accept(setOf(PRIMARY_USER_SMS_APP_UID2, SECONDARY_USER_SMS_APP_UID1))

        // check no uid is available as satellite network fallback uid at primary user
        doReturn(listOf<String>()).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            PRIMARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS,
            PRIMARY_USER_HANDLE)
        verify(mCallback).accept(setOf(SECONDARY_USER_SMS_APP_UID1))

        // check SMS_APP2 is available as satellite network fallback uid at secondary user
        applicationInfo1.uid = SECONDARY_USER_SMS_APP_UID2
        doReturn(applicationInfo1).`when`(mPackageManager)
            .getApplicationInfo(eq(SMS_APP2), anyInt())
        doReturn(listOf(SMS_APP2))
            .`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS, SECONDARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, SECONDARY_USER_HANDLE)
        verify(mCallback).accept(setOf(SECONDARY_USER_SMS_APP_UID2))

        // check no uid is available as satellite network fallback uid at secondary user
        doReturn(listOf<String>()).`when`(mRoleManager).getRoleHoldersAsUser(RoleManager.ROLE_SMS,
            SECONDARY_USER_HANDLE)
        mRoleHolderChangedListener.onRoleHoldersChanged(RoleManager.ROLE_SMS, SECONDARY_USER_HANDLE)
        verify(mCallback).accept(ArraySet())
    }
}
