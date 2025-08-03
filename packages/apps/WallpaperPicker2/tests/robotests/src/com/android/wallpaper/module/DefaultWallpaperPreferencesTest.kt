/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wallpaper.module

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.wallpaper.model.LiveWallpaperPrefMetadata
import com.android.wallpaper.model.StaticWallpaperPrefMetadata
import com.android.wallpaper.module.WallpaperPreferenceKeys.NoBackupKeys
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultWallpaperPreferencesTest {

    private val wallpaperPreferences: DefaultWallpaperPreferences =
        DefaultWallpaperPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun setHomeStaticImageWallpaperMetadata_metadataShouldBeSavedToPreferences() {
        wallpaperPreferences.setHomeStaticImageWallpaperMetadata(
            StaticWallpaperPrefMetadata(
                attributions = listOf("attr1", "attr2"),
                actionUrl = "http://www.google.com/",
                collectionId = "cultural_events",
                hashCode = 10013L,
                managerId = 3,
                remoteId = "ocean",
            )
        )

        val sharedPref =
            (ApplicationProvider.getApplicationContext() as Context).getSharedPreferences(
                DefaultWallpaperPreferences.PREFS_NAME,
                Context.MODE_PRIVATE
            )
        assertThat(sharedPref.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_1, null))
            .isEqualTo("attr1")
        assertThat(sharedPref.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_2, null))
            .isEqualTo("attr2")
        assertThat(
                sharedPref.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ACTION_URL, null)
            )
            .isEqualTo("http://www.google.com/")
        assertThat(
                sharedPref.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_COLLECTION_ID, null)
            )
            .isEqualTo("cultural_events")
        assertThat(sharedPref.getLong(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_HASH_CODE, 0L))
            .isEqualTo(10013)
        val noBackupPref =
            (ApplicationProvider.getApplicationContext() as Context).getSharedPreferences(
                DefaultWallpaperPreferences.NO_BACKUP_PREFS_NAME,
                Context.MODE_PRIVATE
            )
        assertThat(noBackupPref.getInt(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID, 0)).isEqualTo(3)
        assertThat(noBackupPref.getString(NoBackupKeys.KEY_HOME_WALLPAPER_REMOTE_ID, null))
            .isEqualTo("ocean")
    }

    @Test
    fun setHomeLiveWallpaperMetadata_metadataShouldBeSavedToPreferences() {
        wallpaperPreferences.setHomeLiveWallpaperMetadata(
            LiveWallpaperPrefMetadata(
                attributions = listOf("attr1", "attr2"),
                serviceName =
                    "com.google.pixel.livewallpaper.dioramas.fiji.wallpapers.FijiWallpaper",
                effectName = null,
                collectionId = "living_universe",
                managerId = 2,
            )
        )

        val sharedPref =
            (ApplicationProvider.getApplicationContext() as Context).getSharedPreferences(
                DefaultWallpaperPreferences.PREFS_NAME,
                Context.MODE_PRIVATE
            )
        assertThat(sharedPref.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_1, null))
            .isEqualTo("attr1")
        assertThat(sharedPref.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_2, null))
            .isEqualTo("attr2")
        assertThat(
                sharedPref.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_COLLECTION_ID, null)
            )
            .isEqualTo("living_universe")
        val noBackupPref =
            (ApplicationProvider.getApplicationContext() as Context).getSharedPreferences(
                DefaultWallpaperPreferences.NO_BACKUP_PREFS_NAME,
                Context.MODE_PRIVATE
            )
        assertThat(noBackupPref.getString(NoBackupKeys.KEY_HOME_WALLPAPER_SERVICE_NAME, null))
            .isEqualTo("com.google.pixel.livewallpaper.dioramas.fiji.wallpapers.FijiWallpaper")
        assertThat(noBackupPref.getString(NoBackupKeys.KEY_HOME_WALLPAPER_EFFECTS, null))
            .isEqualTo(null)
        assertThat(noBackupPref.getInt(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID, 0)).isEqualTo(2)
    }

    @Test
    fun setLockStaticImageWallpaperMetadata_metadataShouldBeSavedToPreferences() {
        wallpaperPreferences.setLockStaticImageWallpaperMetadata(
            StaticWallpaperPrefMetadata(
                attributions = listOf("attr1", "attr2"),
                actionUrl = "http://www.google.com/",
                collectionId = "cultural_events",
                hashCode = 10013L,
                managerId = 3,
                remoteId = "ocean",
            )
        )

        val sharedPref =
            (ApplicationProvider.getApplicationContext() as Context).getSharedPreferences(
                DefaultWallpaperPreferences.PREFS_NAME,
                Context.MODE_PRIVATE
            )
        assertThat(sharedPref.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_1, null))
            .isEqualTo("attr1")
        assertThat(sharedPref.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_2, null))
            .isEqualTo("attr2")
        assertThat(
                sharedPref.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ACTION_URL, null)
            )
            .isEqualTo("http://www.google.com/")
        assertThat(
                sharedPref.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_COLLECTION_ID, null)
            )
            .isEqualTo("cultural_events")
        assertThat(sharedPref.getLong(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_HASH_CODE, 0L))
            .isEqualTo(10013)
        val noBackupPref =
            (ApplicationProvider.getApplicationContext() as Context).getSharedPreferences(
                DefaultWallpaperPreferences.NO_BACKUP_PREFS_NAME,
                Context.MODE_PRIVATE
            )
        assertThat(noBackupPref.getInt(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID, 0)).isEqualTo(3)
        assertThat(noBackupPref.getString(NoBackupKeys.KEY_LOCK_WALLPAPER_REMOTE_ID, null))
            .isEqualTo("ocean")
    }

    @Test
    fun setLockLiveWallpaperMetadata_metadataShouldBeSavedToPreferences() {
        wallpaperPreferences.setLockLiveWallpaperMetadata(
            LiveWallpaperPrefMetadata(
                attributions = listOf("attr1", "attr2"),
                serviceName =
                    "com.google.pixel.livewallpaper.dioramas.fiji.wallpapers.FijiWallpaper",
                effectName = null,
                collectionId = "living_universe",
                managerId = 2,
            )
        )

        val sharedPref =
            (ApplicationProvider.getApplicationContext() as Context).getSharedPreferences(
                DefaultWallpaperPreferences.PREFS_NAME,
                Context.MODE_PRIVATE
            )
        assertThat(sharedPref.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_1, null))
            .isEqualTo("attr1")
        assertThat(sharedPref.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_2, null))
            .isEqualTo("attr2")
        assertThat(
                sharedPref.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_COLLECTION_ID, null)
            )
            .isEqualTo("living_universe")
        val noBackupPref =
            (ApplicationProvider.getApplicationContext() as Context).getSharedPreferences(
                DefaultWallpaperPreferences.NO_BACKUP_PREFS_NAME,
                Context.MODE_PRIVATE
            )
        assertThat(noBackupPref.getString(NoBackupKeys.KEY_LOCK_WALLPAPER_SERVICE_NAME, null))
            .isEqualTo("com.google.pixel.livewallpaper.dioramas.fiji.wallpapers.FijiWallpaper")
        assertThat(noBackupPref.getString(NoBackupKeys.KEY_LOCK_WALLPAPER_EFFECTS, null))
            .isEqualTo(null)
        assertThat(noBackupPref.getInt(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID, 0)).isEqualTo(2)
    }
}
