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

package com.android.permissioncontroller.permission.data.v34

import android.app.Application
import android.os.Build
import android.provider.DeviceConfig
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate.Companion.buildUpdateIfSignificantChange
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Job

/** LiveData for [AppDataSharingUpdate]s. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesLiveData(val app: Application) :
    SmartAsyncMediatorLiveData<List<AppDataSharingUpdate>>(),
    AppsSafetyLabelHistoryPersistence.ChangeListener {

    override suspend fun loadDataAndPostValue(job: Job) {
        val updatePeriod =
            DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS,
                Duration.ofDays(DEFAULT_DATA_SHARING_UPDATE_PERIOD_DAYS).toMillis()
            )
        val file =
            AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(app.applicationContext)

        val appSafetyLabelDiffsFromPersistence =
            AppsSafetyLabelHistoryPersistence.getAppSafetyLabelDiffs(
                Instant.now().atZone(ZoneId.systemDefault()).toInstant().minusMillis(updatePeriod),
                file
            )
        val updatesFromPersistence =
            appSafetyLabelDiffsFromPersistence.mapNotNull { it.buildUpdateIfSignificantChange() }

        postValue(updatesFromPersistence)
    }

    override fun onActive() {
        super.onActive()
        AppsSafetyLabelHistoryPersistence.addListener(this)
    }

    override fun onInactive() {
        super.onInactive()
        AppsSafetyLabelHistoryPersistence.removeListener(this)
    }

    override fun onSafetyLabelHistoryChanged() {
        update()
    }

    /** Companion object for [AppDataSharingUpdatesLiveData]. */
    companion object {
        private const val PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS =
            "data_sharing_update_period_millis"
        private const val DEFAULT_DATA_SHARING_UPDATE_PERIOD_DAYS: Long = 30
    }
}
