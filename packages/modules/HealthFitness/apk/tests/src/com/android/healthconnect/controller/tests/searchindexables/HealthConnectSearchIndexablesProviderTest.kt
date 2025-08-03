package com.android.healthconnect.controller.tests.searchindexables

import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEY
import com.android.healthconnect.controller.searchindexables.HealthConnectSearchIndexablesProvider
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class HealthConnectSearchIndexablesProviderTest {

    private val INDEX_KEY_HOME = "health_connect_settings_key_home"
    private val INDEX_KEY_PERMISSIONS = "health_connect_settings_key_permissions"
    private val INDEX_KEY_DATA = "health_connect_settings_key_data"

    private lateinit var provider: HealthConnectSearchIndexablesProvider

    @Before
    fun setUp() {
        provider = HealthConnectSearchIndexablesProvider()
    }

    @Test
    fun queryRawDataShouldReturnThreeRows() {
        val cursor = provider.queryRawData(null)
        assertEquals(3, cursor.count)
    }

    @Test
    fun queryRawData_returnsCursorIndexesCorrectly() {
        val cursor = provider.queryRawData(null)

        cursor.moveToFirst()

        val homeIndex = cursor.getString(COLUMN_INDEX_RAW_KEY)

        assertEquals(INDEX_KEY_HOME, homeIndex)

        cursor.moveToNext()

        val permissionsIndex = cursor.getString(COLUMN_INDEX_RAW_KEY)

        assertEquals(INDEX_KEY_PERMISSIONS, permissionsIndex)

        cursor.moveToNext()

        val dataAccessIndex = cursor.getString(COLUMN_INDEX_RAW_KEY)

        assertEquals(INDEX_KEY_DATA, dataAccessIndex)
    }

    @Test
    fun queryXmlResourcesShouldReturnEmptyCursor() {
        val cursor = provider.queryXmlResources(null)
        assertEquals(0, cursor.count)
    }

    @Test
    fun queryNonIndexableKeysShouldReturnEmptyCursor() {
        val cursor = provider.queryNonIndexableKeys(null)
        assertEquals(0, cursor.count)
    }
}
