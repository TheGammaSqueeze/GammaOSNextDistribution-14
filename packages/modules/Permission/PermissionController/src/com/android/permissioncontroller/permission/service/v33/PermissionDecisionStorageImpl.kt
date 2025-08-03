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

package com.android.permissioncontroller.permission.service.v33

import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import android.provider.DeviceConfig
import android.util.Log
import android.util.Xml
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.v33.PermissionDecision
import com.android.permissioncontroller.permission.service.BasePermissionEventStorage
import com.android.permissioncontroller.permission.service.PermissionEventStorage
import com.android.permissioncontroller.permission.utils.Utils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Implementation of [BasePermissionEventStorage] for storing [PermissionDecision] events. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class PermissionDecisionStorageImpl(
    context: Context,
    jobScheduler: JobScheduler = context.getSystemService(JobScheduler::class.java)!!
) : BasePermissionEventStorage<PermissionDecision>(context, jobScheduler) {

    // We don't use namespaces
    private val ns: String? = null

    /** The format for how dates are stored. */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val LOG_TAG = "PermissionDecisionStorageImpl"

        private const val DB_VERSION = 1

        /** Config store file name for general shared store file. */
        private const val STORE_FILE_NAME = "recent_permission_decisions.xml"

        private const val TAG_RECENT_PERMISSION_DECISIONS = "recent-permission-decisions"
        private const val TAG_PERMISSION_DECISION = "permission-decision"
        private const val ATTR_VERSION = "version"
        private const val ATTR_PACKAGE_NAME = "package-name"
        private const val ATTR_PERMISSION_GROUP = "permission-group-name"
        private const val ATTR_DECISION_TIME = "decision-time"
        private const val ATTR_IS_GRANTED = "is-granted"

        private val DEFAULT_MAX_DATA_AGE_MS = TimeUnit.DAYS.toMillis(7)

        @Volatile private var INSTANCE: PermissionEventStorage<PermissionDecision>? = null

        fun getInstance(): PermissionEventStorage<PermissionDecision> =
            INSTANCE ?: synchronized(this) { INSTANCE ?: createInstance().also { INSTANCE = it } }

        private fun createInstance(): PermissionEventStorage<PermissionDecision> {
            return PermissionDecisionStorageImpl(PermissionControllerApplication.get())
        }

        fun recordPermissionDecision(
            context: Context,
            packageName: String,
            permGroupName: String,
            isGranted: Boolean
        ) {
            if (isRecordPermissionsSupported(context)) {
                GlobalScope.launch(Dispatchers.IO) {
                    getInstance()
                        .storeEvent(
                            PermissionDecision(
                                packageName,
                                System.currentTimeMillis(),
                                permGroupName,
                                isGranted
                            )
                        )
                }
            }
        }

        fun isRecordPermissionsSupported(context: Context): Boolean {
            return DeviceUtils.isAuto(context)
        }
    }

    override fun serialize(stream: OutputStream, events: List<PermissionDecision>) {
        val out = Xml.newSerializer()
        out.setOutput(stream, StandardCharsets.UTF_8.name())
        out.startDocument(/* encoding= */ null, /* standalone= */ true)
        out.startTag(ns, TAG_RECENT_PERMISSION_DECISIONS)
        out.attribute(/* namespace= */ null, ATTR_VERSION, DB_VERSION.toString())
        for (decision in events) {
            out.startTag(ns, TAG_PERMISSION_DECISION)
            out.attribute(ns, ATTR_PACKAGE_NAME, decision.packageName)
            out.attribute(ns, ATTR_PERMISSION_GROUP, decision.permissionGroupName)
            val date = dateFormat.format(Date(decision.eventTime))
            out.attribute(ns, ATTR_DECISION_TIME, date)
            out.attribute(ns, ATTR_IS_GRANTED, decision.isGranted.toString())
            out.endTag(ns, TAG_PERMISSION_DECISION)
        }
        out.endTag(/* namespace= */ null, TAG_RECENT_PERMISSION_DECISIONS)
        out.endDocument()
    }

    override fun parse(inputStream: InputStream): List<PermissionDecision> {
        inputStream.use {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, /* inputEncoding= */ null)
            parser.nextTag()
            return readRecentDecisions(parser)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readRecentDecisions(parser: XmlPullParser): List<PermissionDecision> {
        val entries = mutableListOf<PermissionDecision>()

        parser.require(XmlPullParser.START_TAG, ns, TAG_RECENT_PERMISSION_DECISIONS)
        while (parser.next() != XmlPullParser.END_TAG) {
            readPermissionDecision(parser)?.let { entries.add(it) }
        }
        return entries
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPermissionDecision(parser: XmlPullParser): PermissionDecision? {
        var decision: PermissionDecision? = null
        parser.require(XmlPullParser.START_TAG, ns, TAG_PERMISSION_DECISION)
        try {
            val packageName = parser.getAttributeValueNullSafe(ns, ATTR_PACKAGE_NAME)
            val permissionGroup = parser.getAttributeValueNullSafe(ns, ATTR_PERMISSION_GROUP)
            val decisionDate = parser.getAttributeValueNullSafe(ns, ATTR_DECISION_TIME)
            val decisionTime =
                dateFormat.parse(decisionDate)?.time
                    ?: throw IllegalArgumentException(
                        "Could not parse date $decisionDate on package $packageName"
                    )
            val isGranted = parser.getAttributeValueNullSafe(ns, ATTR_IS_GRANTED).toBoolean()
            decision = PermissionDecision(packageName, decisionTime, permissionGroup, isGranted)
        } catch (e: XmlPullParserException) {
            Log.e(LOG_TAG, "Unable to parse permission decision", e)
        } catch (e: ParseException) {
            Log.e(LOG_TAG, "Unable to parse permission decision", e)
        } catch (e: IllegalArgumentException) {
            Log.e(LOG_TAG, "Unable to parse permission decision", e)
        } finally {
            parser.nextTag()
            parser.require(XmlPullParser.END_TAG, ns, TAG_PERMISSION_DECISION)
        }
        return decision
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
        return DeviceConfig.getLong(
            DeviceConfig.NAMESPACE_PERMISSIONS,
            Utils.PROPERTY_PERMISSION_DECISIONS_MAX_DATA_AGE_MILLIS,
            DEFAULT_MAX_DATA_AGE_MS
        )
    }

    override fun hasTheSamePrimaryKey(
        first: PermissionDecision,
        second: PermissionDecision
    ): Boolean {
        return first.packageName == second.packageName &&
            first.permissionGroupName == second.permissionGroupName
    }

    override fun PermissionDecision.copyWithTimeDelta(timeDelta: Long): PermissionDecision {
        return this.copy(eventTime = this.eventTime + timeDelta)
    }
}
