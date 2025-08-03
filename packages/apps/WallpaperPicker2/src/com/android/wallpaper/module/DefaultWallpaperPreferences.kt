/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.WallpaperColors
import android.app.WallpaperManager.SetWallpaperFlags
import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.LiveWallpaperPrefMetadata
import com.android.wallpaper.model.StaticWallpaperPrefMetadata
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPreferenceKeys.NoBackupKeys
import com.android.wallpaper.module.WallpaperPreferences.Companion.generateRecentsKey
import com.android.wallpaper.module.WallpaperPreferences.PendingDailyWallpaperUpdateStatus
import com.android.wallpaper.module.WallpaperPreferences.PendingWallpaperSetStatus
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.data.WallpaperModel.LiveWallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.StaticWallpaperModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONException

/** Default implementation that writes to and reads from SharedPreferences. */
open class DefaultWallpaperPreferences(private val context: Context) : WallpaperPreferences {
    protected val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    protected val noBackupPrefs: SharedPreferences =
        context.getSharedPreferences(NO_BACKUP_PREFS_NAME, Context.MODE_PRIVATE)

    private val backupManager = BackupManager(context)
    private val sharedPrefsChangedListener = OnSharedPreferenceChangeListener { _, _ ->
        backupManager.dataChanged()
    }

    init {
        if (noBackupPrefs.all.isEmpty() && sharedPrefs.all.isNotEmpty()) {
            upgradePrefs()
        }
        // Register a prefs changed listener so that all prefs changes trigger a backup event.
        sharedPrefs.registerOnSharedPreferenceChangeListener(sharedPrefsChangedListener)
    }

    /**
     * Move [NoBackupKeys] preferences that might have been in mSharedPrefs from previous versions
     * of the app into mNoBackupPrefs.
     */
    private fun upgradePrefs() {
        val noBackupEditor = noBackupPrefs.edit()
        if (sharedPrefs.contains(NoBackupKeys.KEY_HOME_WALLPAPER_BASE_IMAGE_URL)) {
            noBackupEditor.putString(
                NoBackupKeys.KEY_HOME_WALLPAPER_BASE_IMAGE_URL,
                sharedPrefs.getString(NoBackupKeys.KEY_HOME_WALLPAPER_BASE_IMAGE_URL, null)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID)) {
            noBackupEditor.putInt(
                NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID,
                sharedPrefs.getInt(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID, 0)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_HOME_WALLPAPER_REMOTE_ID)) {
            noBackupEditor.putString(
                NoBackupKeys.KEY_HOME_WALLPAPER_REMOTE_ID,
                sharedPrefs.getString(NoBackupKeys.KEY_HOME_WALLPAPER_REMOTE_ID, null)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_HOME_WALLPAPER_BACKING_FILE)) {
            noBackupEditor.putString(
                NoBackupKeys.KEY_HOME_WALLPAPER_BACKING_FILE,
                sharedPrefs.getString(NoBackupKeys.KEY_HOME_WALLPAPER_BACKING_FILE, null)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID)) {
            noBackupEditor.putInt(
                NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID,
                sharedPrefs.getInt(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID, 0)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_LOCK_WALLPAPER_BACKING_FILE)) {
            noBackupEditor.putString(
                NoBackupKeys.KEY_LOCK_WALLPAPER_BACKING_FILE,
                sharedPrefs.getString(NoBackupKeys.KEY_LOCK_WALLPAPER_BACKING_FILE, null)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_DAILY_ROTATION_TIMESTAMPS)) {
            noBackupEditor.putString(
                NoBackupKeys.KEY_DAILY_ROTATION_TIMESTAMPS,
                sharedPrefs.getString(NoBackupKeys.KEY_DAILY_ROTATION_TIMESTAMPS, null)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_DAILY_WALLPAPER_ENABLED_TIMESTAMP)) {
            noBackupEditor.putLong(
                NoBackupKeys.KEY_DAILY_WALLPAPER_ENABLED_TIMESTAMP,
                sharedPrefs.getLong(NoBackupKeys.KEY_DAILY_WALLPAPER_ENABLED_TIMESTAMP, -1)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_LAST_DAILY_LOG_TIMESTAMP)) {
            noBackupEditor.putLong(
                NoBackupKeys.KEY_LAST_DAILY_LOG_TIMESTAMP,
                sharedPrefs.getLong(NoBackupKeys.KEY_LAST_DAILY_LOG_TIMESTAMP, 0)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_LAST_APP_ACTIVE_TIMESTAMP)) {
            noBackupEditor.putLong(
                NoBackupKeys.KEY_LAST_APP_ACTIVE_TIMESTAMP,
                sharedPrefs.getLong(NoBackupKeys.KEY_LAST_APP_ACTIVE_TIMESTAMP, 0)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_LAST_ROTATION_STATUS)) {
            noBackupEditor.putInt(
                NoBackupKeys.KEY_LAST_ROTATION_STATUS,
                sharedPrefs.getInt(NoBackupKeys.KEY_LAST_ROTATION_STATUS, -1)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_LAST_ROTATION_STATUS_TIMESTAMP)) {
            noBackupEditor.putLong(
                NoBackupKeys.KEY_LAST_ROTATION_STATUS_TIMESTAMP,
                sharedPrefs.getLong(NoBackupKeys.KEY_LAST_ROTATION_STATUS_TIMESTAMP, 0)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_LAST_SYNC_TIMESTAMP)) {
            noBackupEditor.putLong(
                NoBackupKeys.KEY_LAST_SYNC_TIMESTAMP,
                sharedPrefs.getLong(NoBackupKeys.KEY_LAST_SYNC_TIMESTAMP, 0)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_PENDING_WALLPAPER_SET_STATUS)) {
            noBackupEditor.putInt(
                NoBackupKeys.KEY_PENDING_WALLPAPER_SET_STATUS,
                sharedPrefs.getInt(
                    NoBackupKeys.KEY_PENDING_WALLPAPER_SET_STATUS,
                    WallpaperPreferences.WALLPAPER_SET_NOT_PENDING
                )
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_PENDING_DAILY_WALLPAPER_UPDATE_STATUS)) {
            noBackupEditor.putInt(
                NoBackupKeys.KEY_PENDING_DAILY_WALLPAPER_UPDATE_STATUS,
                sharedPrefs.getInt(
                    NoBackupKeys.KEY_PENDING_DAILY_WALLPAPER_UPDATE_STATUS,
                    WallpaperPreferences.DAILY_WALLPAPER_UPDATE_NOT_PENDING
                )
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_NUM_DAYS_DAILY_ROTATION_FAILED)) {
            noBackupEditor.putInt(
                NoBackupKeys.KEY_NUM_DAYS_DAILY_ROTATION_FAILED,
                sharedPrefs.getInt(NoBackupKeys.KEY_NUM_DAYS_DAILY_ROTATION_FAILED, 0)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_NUM_DAYS_DAILY_ROTATION_NOT_ATTEMPTED)) {
            noBackupEditor.putInt(
                NoBackupKeys.KEY_NUM_DAYS_DAILY_ROTATION_NOT_ATTEMPTED,
                sharedPrefs.getInt(NoBackupKeys.KEY_NUM_DAYS_DAILY_ROTATION_NOT_ATTEMPTED, 0)
            )
        }
        if (sharedPrefs.contains(NoBackupKeys.KEY_HOME_WALLPAPER_SERVICE_NAME)) {
            noBackupEditor.putString(
                NoBackupKeys.KEY_HOME_WALLPAPER_SERVICE_NAME,
                sharedPrefs.getString(NoBackupKeys.KEY_HOME_WALLPAPER_SERVICE_NAME, null)
            )
        }
        noBackupEditor.apply()
    }

    private fun getResIdPersistedByName(key: String, type: String): Int {
        val resName = sharedPrefs.getString(key, null) ?: return 0
        return context.resources.getIdentifier(resName, type, context.packageName)
    }

    private fun persistResIdByName(key: String, resId: Int) {
        sharedPrefs.edit().putString(key, getResName(resId)).apply()
    }

    private fun getResName(resId: Int): String {
        return context.resources.getResourceName(resId)
    }

    override fun getWallpaperPresentationMode(): Int {
        @PresentationMode
        val homeWallpaperPresentationMode =
            sharedPrefs.getInt(
                WallpaperPreferenceKeys.KEY_WALLPAPER_PRESENTATION_MODE,
                WallpaperPreferences.PRESENTATION_MODE_STATIC
            )
        return homeWallpaperPresentationMode
    }

    override fun setWallpaperPresentationMode(@PresentationMode presentationMode: Int) {
        sharedPrefs
            .edit()
            .putInt(WallpaperPreferenceKeys.KEY_WALLPAPER_PRESENTATION_MODE, presentationMode)
            .apply()
    }

    override fun getHomeWallpaperAttributions(): List<String?>? {
        return listOf(
            sharedPrefs.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_1, null),
            sharedPrefs.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_2, null),
            sharedPrefs.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_3, null)
        )
    }

    override fun setHomeWallpaperAttributions(attributions: List<String?>?) {
        if (attributions.isNullOrEmpty()) {
            return
        }
        val editor = sharedPrefs.edit()
        attributions.take(3).forEachIndexed { index, attr ->
            when (index) {
                0 -> editor.putString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_1, attr)
                1 -> editor.putString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_2, attr)
                2 -> editor.putString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_3, attr)
            }
        }
        editor.apply()
    }

    override fun getHomeWallpaperActionUrl(): String? {
        return sharedPrefs.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ACTION_URL, null)
    }

    override fun setHomeWallpaperActionUrl(actionUrl: String?) {
        sharedPrefs
            .edit()
            .putString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ACTION_URL, actionUrl)
            .apply()
    }

    override fun getHomeWallpaperCollectionId(): String? {
        return sharedPrefs.getString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_COLLECTION_ID, null)
    }

    override fun setHomeWallpaperCollectionId(collectionId: String?) {
        sharedPrefs
            .edit()
            .putString(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_COLLECTION_ID, collectionId)
            .apply()
    }

    override fun clearHomeWallpaperMetadata() {
        sharedPrefs
            .edit()
            .remove(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_1)
            .remove(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_2)
            .remove(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_3)
            .remove(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ACTION_URL)
            .remove(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_COLLECTION_ID)
            .remove(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_HASH_CODE)
            .apply()
        noBackupPrefs
            .edit()
            .remove(NoBackupKeys.KEY_HOME_WALLPAPER_SERVICE_NAME)
            .remove(NoBackupKeys.KEY_HOME_WALLPAPER_EFFECTS)
            .remove(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID)
            .remove(NoBackupKeys.KEY_HOME_WALLPAPER_REMOTE_ID)
            .remove(NoBackupKeys.KEY_HOME_WALLPAPER_BASE_IMAGE_URL)
            .remove(NoBackupKeys.KEY_HOME_WALLPAPER_BACKING_FILE)
            .apply()
    }

    override fun setHomeStaticImageWallpaperMetadata(metadata: StaticWallpaperPrefMetadata) {
        val sharedEditor = sharedPrefs.edit()
        val attributions = metadata.attributions
        if (!attributions.isNullOrEmpty()) {
            attributions.take(3).forEachIndexed { index, attr ->
                when (index) {
                    0 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_1,
                            attr
                        )
                    1 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_2,
                            attr
                        )
                    2 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_3,
                            attr
                        )
                }
            }
        }
        sharedEditor.putString(
            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ACTION_URL,
            metadata.actionUrl
        )
        sharedEditor.putString(
            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_COLLECTION_ID,
            metadata.collectionId
        )
        val hashCode = metadata.hashCode
        if (hashCode != null) {
            sharedEditor.putLong(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_HASH_CODE, hashCode)
        }
        sharedEditor.apply()

        val noBackupEditor = noBackupPrefs.edit()
        noBackupEditor.putInt(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID, metadata.managerId)
        noBackupEditor.putString(NoBackupKeys.KEY_HOME_WALLPAPER_REMOTE_ID, metadata.remoteId)
        noBackupEditor.apply()
    }

    override fun setHomeLiveWallpaperMetadata(metadata: LiveWallpaperPrefMetadata) {
        val sharedEditor = sharedPrefs.edit()
        val attributions = metadata.attributions
        if (!attributions.isNullOrEmpty()) {
            attributions.take(3).forEachIndexed { index, attr ->
                when (index) {
                    0 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_1,
                            attr
                        )
                    1 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_2,
                            attr
                        )
                    2 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_ATTRIB_3,
                            attr
                        )
                }
            }
        }
        sharedEditor.putString(
            WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_COLLECTION_ID,
            metadata.collectionId
        )
        sharedEditor.apply()

        val noBackupEditor = noBackupPrefs.edit()
        noBackupEditor.putString(NoBackupKeys.KEY_HOME_WALLPAPER_SERVICE_NAME, metadata.serviceName)
        noBackupEditor.putString(NoBackupKeys.KEY_HOME_WALLPAPER_EFFECTS, metadata.effectName)
        noBackupEditor.putInt(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID, metadata.managerId)
        noBackupEditor.apply()
    }

    override fun getHomeWallpaperHashCode(): Long {
        return sharedPrefs.getLong(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_HASH_CODE, 0)
    }

    override fun setHomeWallpaperHashCode(hashCode: Long) {
        sharedPrefs
            .edit()
            .putLong(WallpaperPreferenceKeys.KEY_HOME_WALLPAPER_HASH_CODE, hashCode)
            .apply()
    }

    override fun getHomeWallpaperServiceName(): String? {
        return noBackupPrefs.getString(NoBackupKeys.KEY_HOME_WALLPAPER_SERVICE_NAME, null)
    }

    override fun setHomeWallpaperServiceName(serviceName: String?) {
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_HOME_WALLPAPER_SERVICE_NAME, serviceName)
            .apply()
        setFirstWallpaperApplyDateIfNeeded()
    }

    override fun getHomeWallpaperManagerId(): Int {
        return noBackupPrefs.getInt(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID, 0)
    }

    override fun setHomeWallpaperManagerId(homeWallpaperId: Int) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_HOME_WALLPAPER_MANAGER_ID, homeWallpaperId)
            .apply()
    }

    override fun getHomeWallpaperRemoteId(): String? {
        return noBackupPrefs.getString(NoBackupKeys.KEY_HOME_WALLPAPER_REMOTE_ID, null)
    }

    override fun setHomeWallpaperRemoteId(wallpaperRemoteId: String?) {
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_HOME_WALLPAPER_REMOTE_ID, wallpaperRemoteId)
            .apply()
        setFirstWallpaperApplyDateIfNeeded()
    }

    override fun getHomeWallpaperRecentsKey(): String? {
        return noBackupPrefs.getString(
            NoBackupKeys.KEY_HOME_WALLPAPER_RECENTS_KEY,
            generateRecentsKey(getHomeWallpaperRemoteId(), getHomeWallpaperHashCode())
        )
    }

    override fun setHomeWallpaperRecentsKey(recentsKey: String?) {
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_HOME_WALLPAPER_RECENTS_KEY, recentsKey)
            .apply()
    }

    override fun getHomeWallpaperEffects(): String? {
        return noBackupPrefs.getString(NoBackupKeys.KEY_HOME_WALLPAPER_EFFECTS, null)
    }

    override fun setHomeWallpaperEffects(wallpaperEffects: String?) {
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_HOME_WALLPAPER_EFFECTS, wallpaperEffects)
            .apply()
    }

    override fun getLockWallpaperAttributions(): List<String?>? {
        return listOf(
            sharedPrefs.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_1, null),
            sharedPrefs.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_2, null),
            sharedPrefs.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_3, null)
        )
    }

    override fun setLockWallpaperAttributions(attributions: List<String?>?) {
        if (attributions.isNullOrEmpty()) {
            return
        }
        val editor = sharedPrefs.edit()
        attributions.take(3).forEachIndexed { index, attr ->
            when (index) {
                0 -> editor.putString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_1, attr)
                1 -> editor.putString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_2, attr)
                2 -> editor.putString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_3, attr)
            }
        }
        editor.apply()
    }

    override fun getLockWallpaperActionUrl(): String? {
        return sharedPrefs.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ACTION_URL, null)
    }

    override fun setLockWallpaperActionUrl(actionUrl: String?) {
        sharedPrefs
            .edit()
            .putString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ACTION_URL, actionUrl)
            .apply()
    }

    override fun getLockWallpaperCollectionId(): String? {
        return sharedPrefs.getString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_COLLECTION_ID, null)
    }

    override fun setLockWallpaperCollectionId(collectionId: String?) {
        sharedPrefs
            .edit()
            .putString(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_COLLECTION_ID, collectionId)
            .apply()
    }

    override fun clearLockWallpaperMetadata() {
        sharedPrefs
            .edit()
            .remove(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_1)
            .remove(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_2)
            .remove(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_3)
            .remove(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ACTION_URL)
            .remove(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_COLLECTION_ID)
            .remove(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_HASH_CODE)
            .apply()
        noBackupPrefs
            .edit()
            .remove(NoBackupKeys.KEY_LOCK_WALLPAPER_SERVICE_NAME)
            .remove(NoBackupKeys.KEY_LOCK_WALLPAPER_EFFECTS)
            .remove(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID)
            .remove(NoBackupKeys.KEY_LOCK_WALLPAPER_REMOTE_ID)
            .remove(NoBackupKeys.KEY_LOCK_WALLPAPER_BACKING_FILE)
            .apply()
    }

    override fun setLockStaticImageWallpaperMetadata(metadata: StaticWallpaperPrefMetadata) {
        val sharedEditor = sharedPrefs.edit()
        val attributions = metadata.attributions
        if (!attributions.isNullOrEmpty()) {
            attributions.take(3).forEachIndexed { index, attr ->
                when (index) {
                    0 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_1,
                            attr
                        )
                    1 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_2,
                            attr
                        )
                    2 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_3,
                            attr
                        )
                }
            }
        }
        sharedEditor.putString(
            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ACTION_URL,
            metadata.actionUrl
        )
        sharedEditor.putString(
            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_COLLECTION_ID,
            metadata.collectionId
        )
        val hashCode = metadata.hashCode
        if (hashCode != null) {
            sharedEditor.putLong(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_HASH_CODE, hashCode)
        }
        sharedEditor.apply()

        val noBackupEditor = noBackupPrefs.edit()
        noBackupEditor.putInt(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID, metadata.managerId)
        noBackupEditor.putString(NoBackupKeys.KEY_LOCK_WALLPAPER_REMOTE_ID, metadata.remoteId)
        noBackupEditor.apply()
    }

    override fun setLockLiveWallpaperMetadata(metadata: LiveWallpaperPrefMetadata) {
        val sharedEditor = sharedPrefs.edit()
        val attributions = metadata.attributions
        if (!attributions.isNullOrEmpty()) {
            attributions.take(3).forEachIndexed { index, attr ->
                when (index) {
                    0 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_1,
                            attr
                        )
                    1 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_2,
                            attr
                        )
                    2 ->
                        sharedEditor.putString(
                            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_ATTRIB_3,
                            attr
                        )
                }
            }
        }
        sharedEditor.putString(
            WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_COLLECTION_ID,
            metadata.collectionId
        )
        sharedEditor.apply()

        val noBackupEditor = noBackupPrefs.edit()
        noBackupEditor.putString(NoBackupKeys.KEY_LOCK_WALLPAPER_SERVICE_NAME, metadata.serviceName)
        noBackupEditor.putString(NoBackupKeys.KEY_LOCK_WALLPAPER_EFFECTS, metadata.effectName)
        noBackupEditor.putInt(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID, metadata.managerId)
        noBackupEditor.apply()
    }

    override fun getLockWallpaperHashCode(): Long {
        return sharedPrefs.getLong(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_HASH_CODE, 0)
    }

    override fun setLockWallpaperHashCode(hashCode: Long) {
        sharedPrefs
            .edit()
            .putLong(WallpaperPreferenceKeys.KEY_LOCK_WALLPAPER_HASH_CODE, hashCode)
            .apply()
    }

    override fun getLockWallpaperServiceName(): String? {
        return noBackupPrefs.getString(NoBackupKeys.KEY_LOCK_WALLPAPER_SERVICE_NAME, null)
    }

    override fun setLockWallpaperServiceName(serviceName: String?) {
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_LOCK_WALLPAPER_SERVICE_NAME, serviceName)
            .apply()
    }

    override fun getLockWallpaperManagerId(): Int {
        return noBackupPrefs.getInt(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID, 0)
    }

    override fun setLockWallpaperManagerId(lockWallpaperId: Int) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_LOCK_WALLPAPER_MANAGER_ID, lockWallpaperId)
            .apply()
    }

    override fun getLockWallpaperRemoteId(): String? {
        return noBackupPrefs.getString(NoBackupKeys.KEY_LOCK_WALLPAPER_REMOTE_ID, null)
    }

    override fun setLockWallpaperRemoteId(wallpaperRemoteId: String?) {
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_LOCK_WALLPAPER_REMOTE_ID, wallpaperRemoteId)
            .apply()
        setFirstWallpaperApplyDateIfNeeded()
    }

    override fun getLockWallpaperRecentsKey(): String? {
        return noBackupPrefs.getString(
            NoBackupKeys.KEY_LOCK_WALLPAPER_RECENTS_KEY,
            generateRecentsKey(getLockWallpaperRemoteId(), getLockWallpaperHashCode())
        )
    }

    override fun setLockWallpaperRecentsKey(recentsKey: String?) {
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_LOCK_WALLPAPER_RECENTS_KEY, recentsKey)
            .apply()
    }

    override fun getLockWallpaperEffects(): String? {
        return noBackupPrefs.getString(NoBackupKeys.KEY_LOCK_WALLPAPER_EFFECTS, null)
    }

    override fun setLockWallpaperEffects(wallpaperEffects: String?) {
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_LOCK_WALLPAPER_EFFECTS, wallpaperEffects)
            .apply()
    }

    override fun addDailyRotation(timestamp: Long) {
        val jsonString = noBackupPrefs.getString(NoBackupKeys.KEY_DAILY_ROTATION_TIMESTAMPS, "[]")
        try {
            val jsonArray = JSONArray(jsonString)
            jsonArray.put(timestamp)
            noBackupPrefs
                .edit()
                .putString(NoBackupKeys.KEY_DAILY_ROTATION_TIMESTAMPS, jsonArray.toString())
                .apply()
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to add a daily rotation timestamp due to a JSON parse exception")
        }
    }

    override fun getLastDailyRotationTimestamp(): Long {
        val jsonString = noBackupPrefs.getString(NoBackupKeys.KEY_DAILY_ROTATION_TIMESTAMPS, "[]")
        return try {
            val jsonArray = JSONArray(jsonString)
            if (jsonArray.length() == 0) {
                -1
            } else jsonArray.getLong(jsonArray.length() - 1)
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to find a daily rotation timestamp due to a JSON parse exception")
            -1
        }
    }

    override fun getDailyWallpaperEnabledTimestamp(): Long {
        return noBackupPrefs.getLong(NoBackupKeys.KEY_DAILY_WALLPAPER_ENABLED_TIMESTAMP, -1)
    }

    override fun setDailyWallpaperEnabledTimestamp(timestamp: Long) {
        noBackupPrefs
            .edit()
            .putLong(NoBackupKeys.KEY_DAILY_WALLPAPER_ENABLED_TIMESTAMP, timestamp)
            .apply()
    }

    override fun clearDailyRotations() {
        noBackupPrefs
            .edit()
            .remove(NoBackupKeys.KEY_DAILY_ROTATION_TIMESTAMPS)
            .remove(NoBackupKeys.KEY_DAILY_WALLPAPER_ENABLED_TIMESTAMP)
            .apply()
    }

    override fun getLastDailyLogTimestamp(): Long {
        return noBackupPrefs.getLong(NoBackupKeys.KEY_LAST_DAILY_LOG_TIMESTAMP, 0)
    }

    override fun setLastDailyLogTimestamp(timestamp: Long) {
        noBackupPrefs.edit().putLong(NoBackupKeys.KEY_LAST_DAILY_LOG_TIMESTAMP, timestamp).apply()
    }

    override fun getLastAppActiveTimestamp(): Long {
        return noBackupPrefs.getLong(NoBackupKeys.KEY_LAST_APP_ACTIVE_TIMESTAMP, 0)
    }

    override fun setLastAppActiveTimestamp(timestamp: Long) {
        noBackupPrefs.edit().putLong(NoBackupKeys.KEY_LAST_APP_ACTIVE_TIMESTAMP, timestamp).apply()
    }

    override fun setDailyWallpaperRotationStatus(status: Int, timestamp: Long) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_LAST_ROTATION_STATUS, status)
            .putLong(NoBackupKeys.KEY_LAST_ROTATION_STATUS_TIMESTAMP, timestamp)
            .apply()
    }

    override fun setPendingWallpaperSetStatusSync(@PendingWallpaperSetStatus setStatus: Int) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_PENDING_WALLPAPER_SET_STATUS, setStatus)
            .commit()
    }

    @PendingWallpaperSetStatus
    override fun getPendingWallpaperSetStatus(): Int {
        return noBackupPrefs.getInt(
            NoBackupKeys.KEY_PENDING_WALLPAPER_SET_STATUS,
            WallpaperPreferences.WALLPAPER_SET_NOT_PENDING
        )
    }

    override fun setPendingWallpaperSetStatus(@PendingWallpaperSetStatus setStatus: Int) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_PENDING_WALLPAPER_SET_STATUS, setStatus)
            .apply()
    }

    override fun setPendingDailyWallpaperUpdateStatusSync(
        @PendingDailyWallpaperUpdateStatus updateStatus: Int
    ) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_PENDING_DAILY_WALLPAPER_UPDATE_STATUS, updateStatus)
            .commit()
    }

    @PendingDailyWallpaperUpdateStatus
    override fun getPendingDailyWallpaperUpdateStatus(): Int {
        return noBackupPrefs.getInt(
            NoBackupKeys.KEY_PENDING_DAILY_WALLPAPER_UPDATE_STATUS,
            WallpaperPreferences.DAILY_WALLPAPER_UPDATE_NOT_PENDING
        )
    }

    override fun setPendingDailyWallpaperUpdateStatus(
        @PendingDailyWallpaperUpdateStatus updateStatus: Int
    ) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_PENDING_DAILY_WALLPAPER_UPDATE_STATUS, updateStatus)
            .apply()
    }

    override fun getAppLaunchCount(): Int {
        return noBackupPrefs.getInt(NoBackupKeys.KEY_APP_LAUNCH_COUNT, 0)
    }

    override fun getFirstLaunchDateSinceSetup(): Int {
        return noBackupPrefs.getInt(NoBackupKeys.KEY_FIRST_LAUNCH_DATE_SINCE_SETUP, 0)
    }

    override fun incrementAppLaunched() {
        if (getFirstLaunchDateSinceSetup() == 0) {
            setFirstLaunchDateSinceSetup(getCurrentDate())
        }

        val appLaunchCount = getAppLaunchCount()
        if (appLaunchCount < Int.MAX_VALUE) {
            setAppLaunchCount(appLaunchCount + 1)
        }
    }

    override fun getFirstWallpaperApplyDateSinceSetup(): Int {
        return noBackupPrefs.getInt(NoBackupKeys.KEY_FIRST_WALLPAPER_APPLY_DATE_SINCE_SETUP, 0)
    }

    override fun storeWallpaperColors(
        storedWallpaperId: String?,
        wallpaperColors: WallpaperColors?
    ) {
        if (storedWallpaperId == null || wallpaperColors == null) {
            return
        }
        val primaryColor = wallpaperColors.primaryColor
        var value = java.lang.String(primaryColor.toArgb().toString()) as String
        val secondaryColor = wallpaperColors.secondaryColor
        if (secondaryColor != null) {
            value += "," + secondaryColor.toArgb()
        }
        val tertiaryColor = wallpaperColors.tertiaryColor
        if (tertiaryColor != null) {
            value += "," + tertiaryColor.toArgb()
        }
        noBackupPrefs
            .edit()
            .putString(NoBackupKeys.KEY_PREVIEW_WALLPAPER_COLOR_ID + storedWallpaperId, value)
            .apply()
    }

    override fun getWallpaperColors(storedWallpaperId: String): WallpaperColors? {
        val value =
            noBackupPrefs.getString(
                NoBackupKeys.KEY_PREVIEW_WALLPAPER_COLOR_ID + storedWallpaperId,
                null
            )
        if (value == null || value.isEmpty()) {
            return null
        }
        val colorStrings = value.split(",")
        val colorPrimary = Color.valueOf(colorStrings[0].toInt())
        var colorSecondary: Color? = null
        if (colorStrings.size >= 2) {
            colorSecondary = Color.valueOf(colorStrings[1].toInt())
        }
        var colorTerTiary: Color? = null
        if (colorStrings.size >= 3) {
            colorTerTiary = Color.valueOf(colorStrings[2].toInt())
        }
        return WallpaperColors(
            colorPrimary,
            colorSecondary,
            colorTerTiary,
            WallpaperColors.HINT_FROM_BITMAP
        )
    }

    override fun updateDailyWallpaperSet(
        @WallpaperPersister.Destination destination: Int,
        collectionId: String?,
        wallpaperId: String?,
    ) {
        // Assign wallpaper info by destination.
        when (destination) {
            WallpaperPersister.DEST_HOME_SCREEN -> {
                setHomeWallpaperCollectionId(collectionId!!)
                setHomeWallpaperRemoteId(wallpaperId)
            }
            WallpaperPersister.DEST_LOCK_SCREEN -> {
                setLockWallpaperCollectionId(collectionId!!)
                setLockWallpaperRemoteId(wallpaperId!!)
            }
            WallpaperPersister.DEST_BOTH -> {
                setHomeWallpaperCollectionId(collectionId!!)
                setHomeWallpaperRemoteId(wallpaperId)
                setLockWallpaperCollectionId(collectionId)
                setLockWallpaperRemoteId(wallpaperId!!)
            }
        }
        setHomeWallpaperEffects(null)
    }

    override fun storeLatestWallpaper(
        @SetWallpaperFlags which: Int,
        wallpaperId: String,
        wallpaper: LiveWallpaperInfo,
        colors: WallpaperColors,
    ) {}

    override fun storeLatestWallpaper(
        @SetWallpaperFlags which: Int,
        wallpaperId: String,
        wallpaper: WallpaperInfo,
        croppedWallpaperBitmap: Bitmap,
        colors: WallpaperColors,
    ) {}

    override fun storeLatestWallpaper(
        @SetWallpaperFlags which: Int,
        wallpaperId: String,
        attributions: List<String>?,
        actionUrl: String?,
        collectionId: String?,
        croppedWallpaperBitmap: Bitmap,
        colors: WallpaperColors,
    ) {}

    override suspend fun addStaticWallpaperToRecentWallpapers(
        destination: WallpaperDestination,
        wallpaperModel: StaticWallpaperModel,
        bitmap: Bitmap,
        cropHints: Map<Point, Rect>?,
    ) {}

    override suspend fun addLiveWallpaperToRecentWallpapers(
        destination: WallpaperDestination,
        wallpaperModel: LiveWallpaperModel
    ) {}

    override fun setHasPreviewTooltipBeenShown(hasTooltipBeenShown: Boolean) {
        sharedPrefs
            .edit()
            .putBoolean(
                WallpaperPreferenceKeys.KEY_HAS_PREVIEW_TOOLTIP_BEEN_SHOWN,
                hasTooltipBeenShown
            )
            .apply()
    }

    override fun getHasPreviewTooltipBeenShown(): Boolean {
        return sharedPrefs.getBoolean(
            WallpaperPreferenceKeys.KEY_HAS_PREVIEW_TOOLTIP_BEEN_SHOWN,
            false
        )
    }

    private fun setFirstLaunchDateSinceSetup(firstLaunchDate: Int) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_FIRST_LAUNCH_DATE_SINCE_SETUP, firstLaunchDate)
            .apply()
    }

    private fun setAppLaunchCount(count: Int) {
        noBackupPrefs.edit().putInt(NoBackupKeys.KEY_APP_LAUNCH_COUNT, count).apply()
    }

    private fun setFirstWallpaperApplyDateSinceSetup(firstApplyDate: Int) {
        noBackupPrefs
            .edit()
            .putInt(NoBackupKeys.KEY_FIRST_WALLPAPER_APPLY_DATE_SINCE_SETUP, firstApplyDate)
            .apply()
    }

    private fun setFirstWallpaperApplyDateIfNeeded() {
        if (getFirstWallpaperApplyDateSinceSetup() == 0) {
            setFirstWallpaperApplyDateSinceSetup(getCurrentDate())
        }
    }

    private fun getCurrentDate(): Int {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val format = SimpleDateFormat("yyyyMMdd", Locale.US)
        return format.format(calendar.time).toInt()
    }

    companion object {
        const val PREFS_NAME = "wallpaper"
        const val NO_BACKUP_PREFS_NAME = "wallpaper-nobackup"
        const val KEY_VALUE_DIVIDER = "="
        private const val TAG = "DefaultWallpaperPreferences"
    }
}
