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

package com.android.permissioncontroller.safetylabel

import android.content.Context
import android.os.Build
import android.provider.DeviceConfig
import android.util.AtomicFile
import android.util.Log
import android.util.Xml
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppInfo
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppSafetyLabelDiff
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataCategory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer

/** Persists safety label history to disk and allows reading from and writing to this storage */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object AppsSafetyLabelHistoryPersistence {
    private const val TAG_DATA_SHARED_MAP = "shared"
    private const val TAG_DATA_SHARED_ENTRY = "entry"
    private const val TAG_APP_INFO = "app-info"
    private const val TAG_DATA_LABEL = "data-lbl"
    private const val TAG_SAFETY_LABEL = "sfty-lbl"
    private const val TAG_APP_SAFETY_LABEL_HISTORY = "app-hstry"
    private const val TAG_APPS_SAFETY_LABEL_HISTORY = "apps-hstry"
    private const val ATTRIBUTE_VERSION = "vrs"
    private const val ATTRIBUTE_PACKAGE_NAME = "pkg-name"
    private const val ATTRIBUTE_RECEIVED_AT = "rcvd"
    private const val ATTRIBUTE_CATEGORY = "cat"
    private const val ATTRIBUTE_CONTAINS_ADS = "ads"
    private const val CURRENT_VERSION = 0
    private const val INITIAL_VERSION = 0

    /** The name of the file used to persist Safety Label history. */
    private const val APPS_SAFETY_LABEL_HISTORY_PERSISTENCE_FILE_NAME =
        "apps_safety_label_history_persistence.xml"
    private val LOG_TAG = "AppsSafetyLabelHistoryPersistence".take(23)
    private val readWriteLock = Any()

    private var listeners = mutableSetOf<ChangeListener>()

    /** Adds a listener to listen for changes to persisted safety labels. */
    fun addListener(listener: ChangeListener) {
        synchronized(readWriteLock) { listeners.add(listener) }
    }

    /** Removes a listener from listening for changes to persisted safety labels. */
    fun removeListener(listener: ChangeListener) {
        synchronized(readWriteLock) { listeners.remove(listener) }
    }

    /**
     * Reads the provided file storing safety label history and returns the parsed
     * [AppsSafetyLabelHistoryFileContent].
     */
    fun read(file: File): AppsSafetyLabelHistoryFileContent {
        val parser = Xml.newPullParser()
        try {
            AtomicFile(file).openRead().let { inputStream ->
                parser.setInput(inputStream, StandardCharsets.UTF_8.name())
                return parser.parseHistoryFile()
            }
        } catch (e: FileNotFoundException) {
            Log.e(LOG_TAG, "File not found: $file")
        } catch (e: IOException) {
            Log.e(
                LOG_TAG,
                "Failed to read file: $file, encountered exception ${e.localizedMessage}"
            )
        } catch (e: XmlPullParserException) {
            Log.e(
                LOG_TAG,
                "Failed to parse file: $file, encountered exception ${e.localizedMessage}"
            )
        }

        return AppsSafetyLabelHistoryFileContent(appsSafetyLabelHistory = null, INITIAL_VERSION)
    }

    /** Returns the last updated time for each stored [AppSafetyLabelHistory]. */
    fun getSafetyLabelsLastUpdatedTimes(file: File): Map<AppInfo, Instant> {
        synchronized(readWriteLock) {
            val appHistories =
                read(file).appsSafetyLabelHistory?.appSafetyLabelHistories ?: return emptyMap()

            val lastUpdatedTimes = mutableMapOf<AppInfo, Instant>()
            for (appHistory in appHistories) {
                val lastSafetyLabelReceiptTime: Instant? = appHistory.getLastReceiptTime()
                if (lastSafetyLabelReceiptTime != null) {
                    lastUpdatedTimes[appHistory.appInfo] = lastSafetyLabelReceiptTime
                }
            }

            return lastUpdatedTimes
        }
    }

    /**
     * Writes a new safety label to the provided file, if the provided safety label has changed from
     * the last recorded.
     */
    fun recordSafetyLabel(safetyLabel: SafetyLabel, file: File) {
        synchronized(readWriteLock) {
            val currentAppsSafetyLabelHistory =
                read(file).appsSafetyLabelHistory ?: AppsSafetyLabelHistory(listOf())
            val appInfo = safetyLabel.appInfo
            val currentHistories = currentAppsSafetyLabelHistory.appSafetyLabelHistories

            val updatedAppsSafetyLabelHistory: AppsSafetyLabelHistory =
                if (currentHistories.all { it.appInfo != appInfo }) {
                    AppsSafetyLabelHistory(
                        currentHistories.toMutableList().apply {
                            add(AppSafetyLabelHistory(appInfo, listOf(safetyLabel)))
                        }
                    )
                } else {
                    AppsSafetyLabelHistory(
                        currentHistories.map {
                            if (it.appInfo != appInfo) it
                            else it.addSafetyLabelIfChanged(safetyLabel)
                        }
                    )
                }

            write(file, updatedAppsSafetyLabelHistory)
        }
    }

    /**
     * Writes new safety labels to the provided file, if the provided safety labels have changed
     * from the last recorded (when considered in order of [SafetyLabel.receivedAt]).
     */
    fun recordSafetyLabels(safetyLabelsToAdd: Set<SafetyLabel>, file: File) {
        if (safetyLabelsToAdd.isEmpty()) return

        synchronized(readWriteLock) {
            val currentAppsSafetyLabelHistory =
                read(file).appsSafetyLabelHistory ?: AppsSafetyLabelHistory(listOf())
            val appInfoToOrderedSafetyLabels =
                safetyLabelsToAdd
                    .groupBy { it.appInfo }
                    .mapValues { (_, safetyLabels) -> safetyLabels.sortedBy { it.receivedAt } }
            val currentAppHistories = currentAppsSafetyLabelHistory.appSafetyLabelHistories
            val newApps =
                appInfoToOrderedSafetyLabels.keys - currentAppHistories.map { it.appInfo }.toSet()

            // For apps that already have some safety labels persisted, add the provided safety
            // labels to the history.
            var updatedAppHistories: List<AppSafetyLabelHistory> =
                currentAppHistories.map { currentAppHistory: AppSafetyLabelHistory ->
                    val safetyLabels = appInfoToOrderedSafetyLabels[currentAppHistory.appInfo]
                    if (safetyLabels != null) {
                        currentAppHistory.addSafetyLabelsIfChanged(safetyLabels)
                    } else {
                        currentAppHistory
                    }
                }

            // For apps that don't already have some safety labels persisted, add new
            // AppSafetyLabelHistory instances for them with the provided safety labels.
            updatedAppHistories =
                updatedAppHistories.toMutableList().apply {
                    newApps.forEach { newAppInfo ->
                        val safetyLabelsForNewApp = appInfoToOrderedSafetyLabels[newAppInfo]
                        if (safetyLabelsForNewApp != null) {
                            add(AppSafetyLabelHistory(newAppInfo, safetyLabelsForNewApp))
                        }
                    }
                }

            write(file, AppsSafetyLabelHistory(updatedAppHistories))
        }
    }

    /** Deletes stored safety labels for all apps in [appInfosToRemove]. */
    fun deleteSafetyLabelsForApps(appInfosToRemove: Set<AppInfo>, file: File) {
        if (appInfosToRemove.isEmpty()) return

        synchronized(readWriteLock) {
            val currentAppsSafetyLabelHistory =
                read(file).appsSafetyLabelHistory ?: AppsSafetyLabelHistory(listOf())
            val historiesWithAppsRemoved =
                currentAppsSafetyLabelHistory.appSafetyLabelHistories.filter {
                    it.appInfo !in appInfosToRemove
                }

            write(file, AppsSafetyLabelHistory(historiesWithAppsRemoved))
        }
    }

    /**
     * Deletes stored safety labels so that there is at most one safety label for each app with
     * [SafetyLabel.receivedAt] earlier that [startTime].
     */
    fun deleteSafetyLabelsOlderThan(startTime: Instant, file: File) {
        synchronized(readWriteLock) {
            val currentAppsSafetyLabelHistory =
                read(file).appsSafetyLabelHistory ?: AppsSafetyLabelHistory(listOf())
            val updatedAppHistories =
                currentAppsSafetyLabelHistory.appSafetyLabelHistories.map { appHistory ->
                    val history = appHistory.safetyLabelHistory
                    // Retrieve the last safety label that was received prior to startTime.
                    val last =
                        history.indexOfLast { safetyLabels -> safetyLabels.receivedAt <= startTime }
                    if (last <= 0) {
                        // If there is only one or no safety labels received prior to startTime,
                        // then return the history as is.
                        appHistory
                    } else {
                        // Else, discard all safety labels other than the last safety label prior
                        // to startTime. The aim is retain one safety label prior to start time to
                        // be used as the "before" safety label when determining updates.
                        AppSafetyLabelHistory(
                            appHistory.appInfo,
                            history.subList(last, history.size)
                        )
                    }
                }

            write(file, AppsSafetyLabelHistory(updatedAppHistories))
        }
    }

    /**
     * Serializes and writes the provided [AppsSafetyLabelHistory] with [CURRENT_VERSION] schema to
     * the provided file.
     */
    fun write(file: File, appsSafetyLabelHistory: AppsSafetyLabelHistory) {
        write(file, AppsSafetyLabelHistoryFileContent(appsSafetyLabelHistory, CURRENT_VERSION))
    }

    /**
     * Serializes and writes the provided [AppsSafetyLabelHistoryFileContent] to the provided file.
     */
    fun write(file: File, fileContent: AppsSafetyLabelHistoryFileContent) {
        val atomicFile = AtomicFile(file)
        var outputStream: FileOutputStream? = null

        try {
            outputStream = atomicFile.startWrite()
            val serializer = Xml.newSerializer()
            serializer.setOutput(outputStream, StandardCharsets.UTF_8.name())
            serializer.startDocument(null, true)
            serializer.serializeAllAppSafetyLabelHistory(fileContent)
            serializer.endDocument()
            atomicFile.finishWrite(outputStream)
            listeners.forEach { it.onSafetyLabelHistoryChanged() }
        } catch (e: Exception) {
            Log.i(
                LOG_TAG,
                "Failed to write to $file. Previous version of file will be restored.",
                e
            )
            atomicFile.failWrite(outputStream)
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to close $file.", e)
            }
        }
    }

    /** Reads the provided history file and returns all safety label changes since [startTime]. */
    fun getAppSafetyLabelDiffs(startTime: Instant, file: File): List<AppSafetyLabelDiff> {
        val currentAppsSafetyLabelHistory =
            read(file).appsSafetyLabelHistory ?: AppsSafetyLabelHistory(listOf())

        return currentAppsSafetyLabelHistory.appSafetyLabelHistories.mapNotNull {
            val before = it.getSafetyLabelAt(startTime)
            val after = it.getLatestSafetyLabel()
            if (
                before == null ||
                    after == null ||
                    before == after ||
                    before.receivedAt.isAfter(after.receivedAt)
            )
                null
            else AppSafetyLabelDiff(before, after)
        }
    }

    /** Clears the file. */
    fun clear(file: File) {
        AtomicFile(file).delete()
    }

    /** Returns the file persisting safety label history for installed apps. */
    fun getSafetyLabelHistoryFile(context: Context): File =
        File(context.filesDir, APPS_SAFETY_LABEL_HISTORY_PERSISTENCE_FILE_NAME)

    private fun AppSafetyLabelHistory.getLastReceiptTime(): Instant? =
        this.safetyLabelHistory.lastOrNull()?.receivedAt

    private fun XmlPullParser.parseHistoryFile(): AppsSafetyLabelHistoryFileContent {
        if (eventType != XmlPullParser.START_DOCUMENT) {
            throw IllegalArgumentException()
        }
        nextTag()

        val appsSafetyLabelHistory = parseAppsSafetyLabelHistory()

        while (eventType == XmlPullParser.TEXT && isWhitespace) {
            next()
        }
        if (eventType != XmlPullParser.END_DOCUMENT) {
            throw IllegalArgumentException("Unexpected extra element")
        }

        return appsSafetyLabelHistory
    }

    private fun XmlPullParser.parseAppsSafetyLabelHistory(): AppsSafetyLabelHistoryFileContent {
        checkTagStart(TAG_APPS_SAFETY_LABEL_HISTORY)
        var version: Int? = null
        for (i in 0 until attributeCount) {
            when (getAttributeName(i)) {
                ATTRIBUTE_VERSION -> version = getAttributeValue(i).toInt()
                else ->
                    throw IllegalArgumentException(
                        "Unexpected attribute ${getAttributeName(i)} in tag" +
                            " $TAG_APPS_SAFETY_LABEL_HISTORY"
                    )
            }
        }
        if (version == null) {
            version = INITIAL_VERSION
            Log.w(LOG_TAG, "Missing $ATTRIBUTE_VERSION in $TAG_APPS_SAFETY_LABEL_HISTORY")
        }
        nextTag()

        val appSafetyLabelHistories = mutableListOf<AppSafetyLabelHistory>()
        while (eventType == XmlPullParser.START_TAG && name == TAG_APP_SAFETY_LABEL_HISTORY) {
            appSafetyLabelHistories.add(parseAppSafetyLabelHistory())
        }

        checkTagEnd(TAG_APPS_SAFETY_LABEL_HISTORY)
        next()

        return AppsSafetyLabelHistoryFileContent(
            AppsSafetyLabelHistory(appSafetyLabelHistories),
            version
        )
    }

    private fun XmlPullParser.parseAppSafetyLabelHistory(): AppSafetyLabelHistory {
        checkTagStart(TAG_APP_SAFETY_LABEL_HISTORY)
        nextTag()

        val appInfo = parseAppInfo()

        val safetyLabels = mutableListOf<SafetyLabel>()
        while (eventType == XmlPullParser.START_TAG && name == TAG_SAFETY_LABEL) {
            safetyLabels.add(parseSafetyLabel(appInfo))
        }

        checkTagEnd(TAG_APP_SAFETY_LABEL_HISTORY)
        nextTag()

        return AppSafetyLabelHistory(appInfo, safetyLabels)
    }

    private fun XmlPullParser.parseSafetyLabel(appInfo: AppInfo): SafetyLabel {
        checkTagStart(TAG_SAFETY_LABEL)

        var receivedAt: Instant? = null
        for (i in 0 until attributeCount) {
            when (getAttributeName(i)) {
                ATTRIBUTE_RECEIVED_AT -> receivedAt = parseInstant(getAttributeValue(i))
                else ->
                    throw IllegalArgumentException(
                        "Unexpected attribute ${getAttributeName(i)} in tag $TAG_SAFETY_LABEL"
                    )
            }
        }
        if (receivedAt == null) {
            throw IllegalArgumentException("Missing $ATTRIBUTE_RECEIVED_AT in $TAG_SAFETY_LABEL")
        }
        nextTag()

        val dataLabel = parseDataLabel()

        checkTagEnd(TAG_SAFETY_LABEL)
        nextTag()

        return SafetyLabel(appInfo, receivedAt, dataLabel)
    }

    private fun XmlPullParser.parseDataLabel(): DataLabel {
        checkTagStart(TAG_DATA_LABEL)
        nextTag()

        val dataSharing = parseDataShared()

        checkTagEnd(TAG_DATA_LABEL)
        nextTag()

        return DataLabel(dataSharing)
    }

    private fun XmlPullParser.parseDataShared(): Map<String, DataCategory> {
        checkTagStart(TAG_DATA_SHARED_MAP)
        nextTag()

        val sharedCategories = mutableListOf<Pair<String, DataCategory>>()
        while (eventType == XmlPullParser.START_TAG && name == TAG_DATA_SHARED_ENTRY) {
            sharedCategories.add(parseDataSharedEntry())
        }

        checkTagEnd(TAG_DATA_SHARED_MAP)
        nextTag()

        return sharedCategories.associate { it.first to it.second }
    }

    private fun XmlPullParser.parseDataSharedEntry(): Pair<String, DataCategory> {
        checkTagStart(TAG_DATA_SHARED_ENTRY)
        var category: String? = null
        var hasAds: Boolean? = null
        for (i in 0 until attributeCount) {
            when (getAttributeName(i)) {
                ATTRIBUTE_CATEGORY -> category = getAttributeValue(i)
                ATTRIBUTE_CONTAINS_ADS -> hasAds = getAttributeValue(i).toBoolean()
                else ->
                    throw IllegalArgumentException(
                        "Unexpected attribute ${getAttributeName(i)} in tag $TAG_DATA_SHARED_ENTRY"
                    )
            }
        }
        if (category == null) {
            throw IllegalArgumentException("Missing $ATTRIBUTE_CATEGORY in $TAG_DATA_SHARED_ENTRY")
        }
        if (hasAds == null) {
            throw IllegalArgumentException(
                "Missing $ATTRIBUTE_CONTAINS_ADS in $TAG_DATA_SHARED_ENTRY"
            )
        }
        nextTag()

        checkTagEnd(TAG_DATA_SHARED_ENTRY)
        nextTag()

        return category to DataCategory(hasAds)
    }

    private fun XmlPullParser.parseAppInfo(): AppInfo {
        checkTagStart(TAG_APP_INFO)
        var packageName: String? = null
        for (i in 0 until attributeCount) {
            when (getAttributeName(i)) {
                ATTRIBUTE_PACKAGE_NAME -> packageName = getAttributeValue(i)
                else ->
                    throw IllegalArgumentException(
                        "Unexpected attribute ${getAttributeName(i)} in tag $TAG_APP_INFO"
                    )
            }
        }
        if (packageName == null) {
            throw IllegalArgumentException("Missing $ATTRIBUTE_PACKAGE_NAME in $TAG_APP_INFO")
        }

        nextTag()
        checkTagEnd(TAG_APP_INFO)
        nextTag()
        return AppInfo(packageName)
    }

    private fun XmlPullParser.checkTagStart(tag: String) {
        check(eventType == XmlPullParser.START_TAG && tag == name)
    }

    private fun XmlPullParser.checkTagEnd(tag: String) {
        check(eventType == XmlPullParser.END_TAG && tag == name)
    }

    private fun parseInstant(value: String): Instant {
        return try {
            Instant.ofEpochMilli(value.toLong())
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not parse $value as Instant")
        }
    }

    private fun XmlSerializer.serializeAllAppSafetyLabelHistory(
        fileContent: AppsSafetyLabelHistoryFileContent
    ) {
        startTag(null, TAG_APPS_SAFETY_LABEL_HISTORY)
        attribute(null, ATTRIBUTE_VERSION, fileContent.version.toString())
        fileContent.appsSafetyLabelHistory?.appSafetyLabelHistories?.forEach {
            serializeAppSafetyLabelHistory(it)
        }
        endTag(null, TAG_APPS_SAFETY_LABEL_HISTORY)
    }

    private fun XmlSerializer.serializeAppSafetyLabelHistory(
        appSafetyLabelHistory: AppSafetyLabelHistory
    ) {
        startTag(null, TAG_APP_SAFETY_LABEL_HISTORY)
        serializeAppInfo(appSafetyLabelHistory.appInfo)
        appSafetyLabelHistory.safetyLabelHistory.forEach { serializeSafetyLabel(it) }
        endTag(null, TAG_APP_SAFETY_LABEL_HISTORY)
    }

    private fun XmlSerializer.serializeAppInfo(appInfo: AppInfo) {
        startTag(null, TAG_APP_INFO)
        attribute(null, ATTRIBUTE_PACKAGE_NAME, appInfo.packageName)
        endTag(null, TAG_APP_INFO)
    }

    private fun XmlSerializer.serializeSafetyLabel(safetyLabel: SafetyLabel) {
        startTag(null, TAG_SAFETY_LABEL)
        attribute(null, ATTRIBUTE_RECEIVED_AT, safetyLabel.receivedAt.toEpochMilli().toString())
        serializeDataLabel(safetyLabel.dataLabel)
        endTag(null, TAG_SAFETY_LABEL)
    }

    private fun XmlSerializer.serializeDataLabel(dataLabel: DataLabel) {
        startTag(null, TAG_DATA_LABEL)
        serializeDataSharedMap(dataLabel.dataShared)
        endTag(null, TAG_DATA_LABEL)
    }

    private fun XmlSerializer.serializeDataSharedMap(dataShared: Map<String, DataCategory>) {
        startTag(null, TAG_DATA_SHARED_MAP)
        dataShared.entries.forEach { serializeDataSharedEntry(it) }
        endTag(null, TAG_DATA_SHARED_MAP)
    }

    private fun XmlSerializer.serializeDataSharedEntry(
        dataSharedEntry: Map.Entry<String, DataCategory>
    ) {
        startTag(null, TAG_DATA_SHARED_ENTRY)
        attribute(null, ATTRIBUTE_CATEGORY, dataSharedEntry.key)
        attribute(
            null,
            ATTRIBUTE_CONTAINS_ADS,
            dataSharedEntry.value.containsAdvertisingPurpose.toString()
        )
        endTag(null, TAG_DATA_SHARED_ENTRY)
    }

    private fun AppSafetyLabelHistory.addSafetyLabelIfChanged(
        safetyLabel: SafetyLabel
    ): AppSafetyLabelHistory {
        val latestSafetyLabel = safetyLabelHistory.lastOrNull()
        return if (latestSafetyLabel?.dataLabel == safetyLabel.dataLabel) this
        else this.withSafetyLabel(safetyLabel, getMaxSafetyLabelsToPersist())
    }

    private fun AppSafetyLabelHistory.addSafetyLabelsIfChanged(
        safetyLabels: List<SafetyLabel>
    ): AppSafetyLabelHistory {
        var updatedAppHistory = this
        val maxSafetyLabels = getMaxSafetyLabelsToPersist()
        for (safetyLabel in safetyLabels) {
            val latestSafetyLabel = updatedAppHistory.safetyLabelHistory.lastOrNull()
            if (latestSafetyLabel?.dataLabel != safetyLabel.dataLabel) {
                updatedAppHistory = updatedAppHistory.withSafetyLabel(safetyLabel, maxSafetyLabels)
            }
        }

        return updatedAppHistory
    }

    private fun AppSafetyLabelHistory.getLatestSafetyLabel() = safetyLabelHistory.lastOrNull()

    /**
     * Return the safety label known to be the current safety label for the app at the provided
     * time, if available in the history.
     */
    private fun AppSafetyLabelHistory.getSafetyLabelAt(startTime: Instant) =
        safetyLabelHistory.lastOrNull {
            // the last received safety label before or at startTime
            it.receivedAt.isBefore(startTime) || it.receivedAt == startTime
        }
            ?: // the first safety label received after startTime, as a fallback
        safetyLabelHistory.firstOrNull { it.receivedAt.isAfter(startTime) }

    private const val PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP =
        "max_safety_labels_persisted_per_app"

    /**
     * Returns the maximum number of safety labels to persist per app.
     *
     * Note that this will be checked at the time of adding a new safety label to storage for an
     * app; simply changing this Device Config property will not result in any storage being purged.
     */
    private fun getMaxSafetyLabelsToPersist() =
        DeviceConfig.getInt(
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP,
            20
        )

    /** An interface to listen to changes to persisted safety labels. */
    interface ChangeListener {
        /** Callback when the persisted safety labels are changed. */
        fun onSafetyLabelHistoryChanged()
    }

    /** Data class to hold an [AppsSafetyLabelHistory] along with the file schema version. */
    data class AppsSafetyLabelHistoryFileContent(
        val appsSafetyLabelHistory: AppsSafetyLabelHistory?,
        val version: Int,
    )
}
