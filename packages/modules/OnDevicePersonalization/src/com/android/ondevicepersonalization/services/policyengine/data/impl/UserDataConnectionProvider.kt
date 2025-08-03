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

package com.android.ondevicepersonalization.services.policyengine.data.impl

import android.adservices.ondevicepersonalization.AppInfo
import android.adservices.ondevicepersonalization.UserData
import android.util.ArrayMap

import com.android.ondevicepersonalization.services.data.user.RawUserData
import com.android.libraries.pcc.chronicle.api.Connection
import com.android.libraries.pcc.chronicle.api.ConnectionProvider
import com.android.libraries.pcc.chronicle.api.ConnectionRequest
import com.android.libraries.pcc.chronicle.api.DataType
import com.android.libraries.pcc.chronicle.api.ManagedDataType
import com.android.libraries.pcc.chronicle.api.ManagementStrategy
import com.android.libraries.pcc.chronicle.api.StorageMedia

import com.android.ondevicepersonalization.services.policyengine.data.USER_DATA_GENERATED_DTD
import com.android.ondevicepersonalization.services.policyengine.data.UserDataReader

import java.time.Duration

/** [ConnectionProvider] implementation for ODA use data. */
class UserDataConnectionProvider() : ConnectionProvider {
    override val dataType: DataType =
        ManagedDataType(
            USER_DATA_GENERATED_DTD,
            ManagementStrategy.Stored(false, StorageMedia.MEMORY, Duration.ofDays(30)),
            setOf(UserDataReader::class.java)
        )

    override fun getConnection(connectionRequest: ConnectionRequest<out Connection>): Connection {
        return UserDataReaderImpl()
    }

    class UserDataReaderImpl : UserDataReader {
        override fun readUserData(): UserData? {
            val rawUserData: RawUserData = RawUserData.getInstance() ?: return null
            // TODO(b/267013762): more privacy-preserving processing may be needed
            val builder: UserData.Builder = UserData.Builder()
                    .setTimezoneUtcOffsetMins(rawUserData.utcOffset)
                    .setOrientation(rawUserData.orientation)
                    .setAvailableStorageBytes(rawUserData.availableStorageBytes)
                    .setBatteryPercentage(rawUserData.batteryPercentage)
                    .setCarrier(rawUserData.carrier.toString())
                    .setDataNetworkType(rawUserData.dataNetworkType)
                    .setAppInfos(getAppInfos(rawUserData))
            // TODO (b/299683848): follow up the codegen bug
            if (rawUserData.networkCapabilities != null) {
                builder.setNetworkCapabilities(rawUserData.networkCapabilities)
            }
            return builder.build()
        }

        private fun getAppInfos(rawUserData: RawUserData): Map<String, AppInfo> {
            var res = ArrayMap<String, AppInfo>()
            for (appInfo in rawUserData.appsInfo) {
                res.put(appInfo.packageName,
                        AppInfo.Builder()
                            .setInstalled(appInfo.installed)
                            .build())
            }
            return res
        }
    }
}
