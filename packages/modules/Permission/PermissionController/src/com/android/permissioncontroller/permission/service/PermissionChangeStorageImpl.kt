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

package com.android.permissioncontroller.permission.service

import android.app.job.JobScheduler
import android.content.Context
import android.provider.DeviceConfig
import android.util.Log
import android.util.Xml
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.hibernation.getUnusedThresholdMs
import com.android.permissioncontroller.permission.data.PermissionChange
import com.android.permissioncontroller.permission.utils.Utils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Implementation of [BasePermissionEventStorage] for storing [PermissionChange] events for long
 * periods of time.
 */
class PermissionChangeStorageImpl(
    context: Context,
    jobScheduler: JobScheduler = context.getSystemService(JobScheduler::class.java)!!
) : BasePermissionEventStorage<PermissionChange>(context, jobScheduler) {

    // We don't use namespaces
    private val ns: String? = null

    /** The format for how dates are stored. */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Exact format if [PROPERTY_PERMISSION_CHANGES_STORE_EXACT_TIME] is true */
    private val exactTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    companion object {
        private const val LOG_TAG = "PermissionChangeStorageImpl"

        private const val DB_VERSION = 1

        /** Config store file name for general shared store file. */
        private const val STORE_FILE_NAME = "permission_changes.xml"

        private const val TAG_PERMISSION_CHANGES = "permission-changes"
        private const val TAG_PERMISSION_CHANGE = "permission-change"
        private const val ATTR_VERSION = "version"
        private const val ATTR_STORE_EXACT_TIME = "store-exact-time"
        private const val ATTR_PACKAGE_NAME = "package-name"
        private const val ATTR_EVENT_TIME = "event-time"

        @Volatile private var INSTANCE: PermissionEventStorage<PermissionChange>? = null

        fun getInstance(): PermissionEventStorage<PermissionChange> =
            INSTANCE ?: synchronized(this) { INSTANCE ?: createInstance().also { INSTANCE = it } }

        private fun createInstance(): PermissionEventStorage<PermissionChange> {
            return PermissionChangeStorageImpl(PermissionControllerApplication.get())
        }

        @OptIn(DelicateCoroutinesApi::class)
        fun recordPermissionChange(packageName: String) {
            GlobalScope.launch(Dispatchers.IO) {
                getInstance().storeEvent(PermissionChange(packageName, System.currentTimeMillis()))
            }
        }
    }

    override fun serialize(stream: OutputStream, events: List<PermissionChange>) {
        val out = Xml.newSerializer()
        out.setOutput(stream, StandardCharsets.UTF_8.name())
        out.startDocument(/* encoding= */ null, /* standalone= */ true)
        out.startTag(ns, TAG_PERMISSION_CHANGES)
        out.attribute(ns, ATTR_VERSION, DB_VERSION.toString())
        val storesExactTime = storesExactTime()
        out.attribute(ns, ATTR_STORE_EXACT_TIME, storesExactTime.toString())
        val format = if (storesExactTime) exactTimeFormat else dateFormat
        for (change in events) {
            out.startTag(ns, TAG_PERMISSION_CHANGE)
            out.attribute(ns, ATTR_PACKAGE_NAME, change.packageName)
            val date = format.format(Date(change.eventTime))
            out.attribute(ns, ATTR_EVENT_TIME, date)
            out.endTag(ns, TAG_PERMISSION_CHANGE)
        }
        out.endTag(ns, TAG_PERMISSION_CHANGES)
        out.endDocument()
    }

    override fun parse(inputStream: InputStream): List<PermissionChange> {
        inputStream.use {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, /* state= */ false)
            parser.setInput(inputStream, /* inputEncoding= */ null)
            parser.nextTag()
            return readPermissionChanges(parser)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPermissionChanges(parser: XmlPullParser): List<PermissionChange> {
        val entries = mutableListOf<PermissionChange>()

        parser.require(XmlPullParser.START_TAG, ns, TAG_PERMISSION_CHANGES)
        // Parse using whatever format was previously used no matter what current device config
        // value is but truncate if we switched from exact granularity to day granularity
        val didStoreExactTime =
            parser.getAttributeValueNullSafe(ns, ATTR_STORE_EXACT_TIME).toBoolean()
        val format = if (didStoreExactTime) exactTimeFormat else dateFormat
        val storesExactTime = storesExactTime()
        val truncateToDay = didStoreExactTime != storesExactTime && !storesExactTime
        while (parser.next() != XmlPullParser.END_TAG) {
            readPermissionChange(parser, format, truncateToDay)?.let { entries.add(it) }
        }
        return entries
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPermissionChange(
        parser: XmlPullParser,
        format: SimpleDateFormat,
        truncateToDay: Boolean
    ): PermissionChange? {
        var change: PermissionChange? = null
        parser.require(XmlPullParser.START_TAG, ns, TAG_PERMISSION_CHANGE)
        try {
            val packageName = parser.getAttributeValueNullSafe(ns, ATTR_PACKAGE_NAME)
            val changeDate = parser.getAttributeValueNullSafe(ns, ATTR_EVENT_TIME)
            var changeTime =
                format.parse(changeDate)?.time
                    ?: throw IllegalArgumentException(
                        "Could not parse date $changeDate on package $packageName"
                    )
            if (truncateToDay) {
                changeTime = dateFormat.parse(dateFormat.format(Date(changeTime)))!!.time
            }
            change = PermissionChange(packageName, changeTime)
        } catch (e: XmlPullParserException) {
            Log.e(LOG_TAG, "Unable to parse permission change", e)
        } catch (e: ParseException) {
            Log.e(LOG_TAG, "Unable to parse permission change", e)
        } catch (e: IllegalArgumentException) {
            Log.e(LOG_TAG, "Unable to parse permission change", e)
        } finally {
            parser.nextTag()
            parser.require(XmlPullParser.END_TAG, ns, TAG_PERMISSION_CHANGE)
        }
        return change
    }

    @Throws(XmlPullParserException::class)
    private fun XmlPullParser.getAttributeValueNullSafe(namespace: String?, name: String): String {
        return this.getAttributeValue(namespace, name)
            ?: throw XmlPullParserException(
                "Could not find attribute: namespace $namespace, name $name"
            )
    }

    override fun getDatabaseFileName(): String {
        return STORE_FILE_NAME
    }

    override fun getMaxDataAgeMs(): Long {
        // Only retain data up to the threshold needed for auto-revoke to trigger
        return getUnusedThresholdMs()
    }

    override fun hasTheSamePrimaryKey(first: PermissionChange, second: PermissionChange): Boolean {
        return first.packageName == second.packageName
    }

    override fun PermissionChange.copyWithTimeDelta(timeDelta: Long): PermissionChange {
        return this.copy(eventTime = this.eventTime + timeDelta)
    }

    /** Should only be true in tests and never true in prod. */
    private fun storesExactTime(): Boolean {
        return DeviceConfig.getBoolean(
            DeviceConfig.NAMESPACE_PERMISSIONS,
            Utils.PROPERTY_PERMISSION_CHANGES_STORE_EXACT_TIME,
            /* defaultValue= */ false
        )
    }
}
