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

package com.android.ondevicepersonalization.services.policyengine

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Before
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Test

import android.util.Log

import android.adservices.ondevicepersonalization.AppInfo
import android.adservices.ondevicepersonalization.OSVersion
import android.adservices.ondevicepersonalization.UserData
import android.os.Parcel
import android.util.ArrayMap

import com.android.libraries.pcc.chronicle.util.MutableTypedMap
import com.android.libraries.pcc.chronicle.util.TypedMap
import com.android.libraries.pcc.chronicle.api.ConnectionRequest
import com.android.libraries.pcc.chronicle.api.ConnectionResult
import com.android.libraries.pcc.chronicle.api.ReadConnection
import com.android.libraries.pcc.chronicle.api.error.ChronicleError
import com.android.libraries.pcc.chronicle.api.error.PolicyNotFound
import com.android.libraries.pcc.chronicle.api.error.PolicyViolation
import com.android.libraries.pcc.chronicle.api.error.Disabled
import com.android.libraries.pcc.chronicle.api.ProcessorNode

import com.android.ondevicepersonalization.services.policyengine.api.ChronicleManager
import com.android.ondevicepersonalization.services.policyengine.data.UserDataReader
import com.android.ondevicepersonalization.services.policyengine.data.impl.UserDataConnectionProvider
import com.android.ondevicepersonalization.services.policyengine.policy.DataIngressPolicy
import com.android.ondevicepersonalization.services.policyengine.policy.rules.KidStatusEnabled
import com.android.ondevicepersonalization.services.policyengine.policy.rules.LimitedAdsTrackingEnabled

import com.android.ondevicepersonalization.services.data.user.RawUserData
import com.android.ondevicepersonalization.services.data.user.UserDataCollector

import com.google.common.truth.Truth.assertThat

import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class UserDataReaderTest : ProcessorNode {

    private lateinit var policyContext: MutableTypedMap
    private val userDataCollector: UserDataCollector =
            UserDataCollector.getInstanceForTest(ApplicationProvider.getApplicationContext())
    private val rawUserData: RawUserData = RawUserData.getInstance()
    private val TAG: String = "UserDataReaderTest"

    override val requiredConnectionTypes = setOf(UserDataReader::class.java)

    private val chronicleManager: ChronicleManager = ChronicleManager.getInstance(
        connectionProviders = setOf(UserDataConnectionProvider()),
        policies = setOf(DataIngressPolicy.NPA_DATA_POLICY),
        connectionContext = TypedMap()
    )

    @Before
    fun setUp() {
        policyContext = MutableTypedMap()
        policyContext[KidStatusEnabled] = false
        policyContext[LimitedAdsTrackingEnabled] = false

        chronicleManager.chronicle.updateConnectionContext(TypedMap(policyContext))
        chronicleManager.failNewConnections(false)
        userDataCollector.updateUserData(rawUserData)
    }

    @Test
    fun testUserDataConnection() {
        val result: ConnectionResult<UserDataReader>? = chronicleManager.chronicle.getConnection(
            ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
        )
        assertThat(result).isNotNull()
        assertThat(result).isInstanceOf(ConnectionResult.Success::class.java)
    }

    @Test
    fun testUserDataReader() {
        try {
            val userDataReader: UserDataReader? = chronicleManager.chronicle.getConnectionOrThrow(
                ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
            )
            val userData: UserData? = userDataReader?.readUserData()
            // Whether user data is null should not matter to policy engine
            if (userData != null) {
                verifyData(userData, rawUserData)
                // test real-time data update
                userDataCollector.getRealTimeData(rawUserData)
                val updatedUserData: UserData? = userDataReader.readUserData()
                if (updatedUserData != null) {
                    verifyData(updatedUserData, rawUserData)
                }
            }
        } catch (e: ChronicleError) {
            Log.e(TAG, "Expect success but connection failed with: ", e)
        }
    }

    @Test
    fun testFailedConnectionContext() {
        policyContext[KidStatusEnabled] = true
        chronicleManager.chronicle.updateConnectionContext(TypedMap(policyContext))
        val result: ConnectionResult<UserDataReader>? = chronicleManager.chronicle.getConnection(
            ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
        )
        assertThat(result).isNotNull()
        result?.expectFailure(PolicyViolation::class.java)
    }

    @Test
    fun testFailNewConnection() {
        chronicleManager.failNewConnections(true)
        val result: ConnectionResult<UserDataReader>? = chronicleManager.chronicle.getConnection(
            ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
        )
        assertThat(result).isNotNull()
        result?.expectFailure(Disabled::class.java)
    }

    @Test
    fun testAppInstallInfo() {
        var appInstallStatus1 = AppInfo.Builder()
                .setInstalled(true)
                .build()
        var parcel = Parcel.obtain()
        appInstallStatus1.writeToParcel(parcel, 0)
        parcel.setDataPosition(0);
        var appInstallStatus2 = AppInfo.CREATOR.createFromParcel(parcel)
        assertThat(appInstallStatus1).isEqualTo(appInstallStatus2)
        assertThat(appInstallStatus1.hashCode()).isEqualTo(appInstallStatus2.hashCode())
        assertThat(appInstallStatus1.describeContents()).isEqualTo(0)
    }

    @Test
    fun testOSVersion() {
        var oSVersion1 = OSVersion.Builder()
                .setMajor(111)
                .setMinor(222)
                .setMicro(333)
                .build()
        var parcel = Parcel.obtain()
        oSVersion1.writeToParcel(parcel, 0)
        parcel.setDataPosition(0);
        var oSVersion2 = OSVersion.CREATOR.createFromParcel(parcel)
        assertThat(oSVersion1).isEqualTo(oSVersion2)
        assertThat(oSVersion1.hashCode()).isEqualTo(oSVersion2.hashCode())
        assertThat(oSVersion1.describeContents()).isEqualTo(0)
    }

    @Test
    fun testUserData() {
        val appInstalledHistory: Map<String, AppInfo> = mapOf<String, AppInfo>();
        var userData1 = UserData.Builder()
                .setTimezoneUtcOffsetMins(1)
                .setOrientation(1)
                .setAvailableStorageBytes(222)
                .setBatteryPercentage(33)
                .setCarrier("AT_T")
                .setDataNetworkType(1)
                .setAppInfos(appInstalledHistory)
                .build()
        var parcel = Parcel.obtain()
        userData1.writeToParcel(parcel, 0)
        parcel.setDataPosition(0);
        var userData2 = UserData.CREATOR.createFromParcel(parcel)
        assertThat(userData1).isEqualTo(userData2)
        assertThat(userData1.hashCode()).isEqualTo(userData2.hashCode())
        assertThat(userData1.describeContents()).isEqualTo(0)
    }

    private fun verifyData(userData: UserData, ref: RawUserData) {
        assertThat(userData.getTimezoneUtcOffsetMins()).isEqualTo(ref.utcOffset)
        assertThat(userData.getOrientation()).isEqualTo(ref.orientation)
        assertThat(userData.getAvailableStorageBytes()).isEqualTo(ref.availableStorageBytes)
        assertThat(userData.getBatteryPercentage()).isEqualTo(ref.batteryPercentage)
        assertThat(userData.getCarrier()).isEqualTo(ref.carrier.toString())

        assertThat(userData.getNetworkCapabilities()).isEqualTo(ref.networkCapabilities)
        assertThat(userData.getDataNetworkType()).isEqualTo(ref.dataNetworkType)

        assertThat(userData.getAppInfos().size).isEqualTo(rawUserData.appsInfo.size)
    }

    private fun ConnectionResult<*>.expectFailure(cls: Class<out ChronicleError>) {
        when (this) {
            is ConnectionResult.Success -> fail("Expected failure with $cls, but got success")
            is ConnectionResult.Failure -> assertThat(error).isInstanceOf(cls)
        }
    }
}
