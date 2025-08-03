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

package com.android.healthconnect.controller.searchindexables

import android.database.Cursor
import android.database.MatrixCursor
import android.health.connect.HealthConnectManager.ACTION_HEALTH_HOME_SETTINGS
import android.health.connect.HealthConnectManager.ACTION_MANAGE_HEALTH_DATA
import android.health.connect.HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_ACTION
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEY
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEYWORDS
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_SCREEN_TITLE
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_SUMMARY_ON
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_TITLE
import android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS
import android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS
import android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS
import android.provider.SearchIndexablesProvider
import com.android.healthconnect.controller.R

class HealthConnectSearchIndexablesProvider : SearchIndexablesProvider() {

    private val INDEX_KEY_HOME = "health_connect_settings_key_home"
    private val INDEX_KEY_PERMISSIONS = "health_connect_settings_key_permissions"
    private val INDEX_KEY_DATA = "health_connect_settings_key_data"

    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRawData(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(INDEXABLES_RAW_COLUMNS)
        cursor.addRow(createHomeIndex())
        cursor.addRow(createPermissionsIndex())
        cursor.addRow(createDataAccessIndex())
        return cursor
    }

    private fun createHomeIndex(): Array<String?> {
        val homeIndex = arrayOfNulls<String>(INDEXABLES_RAW_COLUMNS.size)
        homeIndex[COLUMN_INDEX_RAW_TITLE] = context?.getString(R.string.app_label)
        homeIndex[COLUMN_INDEX_RAW_SUMMARY_ON] = context?.getString(R.string.home_subtitle)
        homeIndex[COLUMN_INDEX_RAW_SCREEN_TITLE] = context?.getString(R.string.app_label)
        homeIndex[COLUMN_INDEX_RAW_KEYWORDS] = context?.getString(R.string.search_keywords_home)
        homeIndex[COLUMN_INDEX_RAW_KEY] = INDEX_KEY_HOME
        homeIndex[COLUMN_INDEX_RAW_INTENT_ACTION] = ACTION_HEALTH_HOME_SETTINGS
        homeIndex[COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE] = context?.packageName
        return homeIndex
    }

    private fun createPermissionsIndex(): Array<String?> {
        val permissionsIndex = arrayOfNulls<String>(INDEXABLES_RAW_COLUMNS.size)
        permissionsIndex[COLUMN_INDEX_RAW_TITLE] = context?.getString(R.string.connected_apps_title)
        permissionsIndex[COLUMN_INDEX_RAW_SCREEN_TITLE] =
            context?.getString(R.string.search_breadcrumbs_permissions)
        permissionsIndex[COLUMN_INDEX_RAW_KEYWORDS] =
            context?.getString(R.string.search_keywords_permissions)
        permissionsIndex[COLUMN_INDEX_RAW_KEY] = INDEX_KEY_PERMISSIONS
        permissionsIndex[COLUMN_INDEX_RAW_INTENT_ACTION] = ACTION_MANAGE_HEALTH_PERMISSIONS
        permissionsIndex[COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE] = context?.packageName
        return permissionsIndex
    }

    private fun createDataAccessIndex(): Array<String?> {
        val dataAccessIndex = arrayOfNulls<String>(INDEXABLES_RAW_COLUMNS.size)
        dataAccessIndex[COLUMN_INDEX_RAW_TITLE] = context?.getString(R.string.data_title)
        dataAccessIndex[COLUMN_INDEX_RAW_SCREEN_TITLE] =
            context?.getString(R.string.search_breadcrumbs_data)
        dataAccessIndex[COLUMN_INDEX_RAW_KEYWORDS] =
            context?.getString(R.string.search_keywords_data)
        dataAccessIndex[COLUMN_INDEX_RAW_KEY] = INDEX_KEY_DATA
        dataAccessIndex[COLUMN_INDEX_RAW_INTENT_ACTION] = ACTION_MANAGE_HEALTH_DATA
        dataAccessIndex[COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE] = context?.packageName
        return dataAccessIndex
    }

    override fun queryXmlResources(projection: Array<out String>?): Cursor {
        // Unused. Return empty cursor.
        return MatrixCursor(INDEXABLES_XML_RES_COLUMNS)
    }

    override fun queryNonIndexableKeys(projection: Array<out String>?): Cursor {
        // Unused. Return empty cursor.
        return MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS)
    }
}
