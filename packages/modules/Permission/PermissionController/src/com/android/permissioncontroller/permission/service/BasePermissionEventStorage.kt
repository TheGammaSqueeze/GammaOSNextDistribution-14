/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.util.AtomicFile
import android.util.Log
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.permission.data.PermissionEvent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.xmlpull.v1.XmlPullParserException

/** Thread-safe implementation of [PermissionEventStorage] using an XML file as the database. */
abstract class BasePermissionEventStorage<T : PermissionEvent>(
    private val context: Context,
    jobScheduler: JobScheduler = context.getSystemService(JobScheduler::class.java)!!
) : PermissionEventStorage<T> {

    private val dbFile: AtomicFile = AtomicFile(File(context.filesDir, getDatabaseFileName()))
    private val fileLock = Object()

    companion object {
        private const val LOG_TAG = "BasePermissionEventStorage"
    }

    init {
        PermissionEventCleanupJobService.scheduleOldDataCleanupIfNecessary(context, jobScheduler)
    }

    override suspend fun storeEvent(event: T): Boolean {
        synchronized(fileLock) {
            val existingEvents = readData()

            val newEvents = mutableListOf<T>()
            // add new event first to keep the list ordered
            newEvents.add(event)
            for (existingEvent in existingEvents) {
                // ignore any old events that violate the primary key uniqueness with the database
                if (hasTheSamePrimaryKey(existingEvent, event)) {
                    continue
                }
                newEvents.add(existingEvent)
            }

            return writeData(newEvents)
        }
    }

    override suspend fun loadEvents(): List<T> {
        synchronized(fileLock) {
            return readData()
        }
    }

    override suspend fun clearEvents() {
        synchronized(fileLock) { dbFile.delete() }
    }

    override suspend fun removeOldData(): Boolean {
        synchronized(fileLock) {
            val existingEvents = readData()

            val originalCount = existingEvents.size
            val newEvents =
                existingEvents.filter {
                    (System.currentTimeMillis() - it.eventTime) <= getMaxDataAgeMs()
                }

            DumpableLog.d(
                LOG_TAG,
                "${originalCount - newEvents.size} old permission events removed"
            )

            return writeData(newEvents)
        }
    }

    override suspend fun removeEventsForPackage(packageName: String): Boolean {
        synchronized(fileLock) {
            val existingEvents = readData()

            val newEvents = existingEvents.filter { it.packageName != packageName }
            return writeData(newEvents)
        }
    }

    override suspend fun updateEventsBySystemTimeDelta(diffSystemTimeMillis: Long): Boolean {
        synchronized(fileLock) {
            val existingEvents = readData()

            val newEvents = existingEvents.map { it.copyWithTimeDelta(diffSystemTimeMillis) }
            return writeData(newEvents)
        }
    }

    private fun writeData(events: List<T>): Boolean {
        val stream: FileOutputStream =
            try {
                dbFile.startWrite()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Failed to save db file", e)
                return false
            }
        try {
            serialize(stream, events)
            dbFile.finishWrite(stream)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Failed to save db file, restoring backup", e)
            dbFile.failWrite(stream)
            return false
        }

        return true
    }

    private fun readData(): List<T> {
        if (!dbFile.baseFile.exists()) {
            return emptyList()
        }
        return try {
            parse(dbFile.openRead())
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Failed to read db file", e)
            emptyList()
        } catch (e: XmlPullParserException) {
            Log.e(LOG_TAG, "Failed to read db file", e)
            emptyList()
        }
    }

    /**
     * Serialize a list of permission events.
     *
     * @param stream output stream to serialize events to
     * @param events list of permission events to serialize
     */
    abstract fun serialize(stream: OutputStream, events: List<T>)

    /**
     * Parse a list of permission events from the XML parser.
     *
     * @param inputStream input stream to parse events from
     * @return the list of parsed permission events
     */
    @Throws(XmlPullParserException::class, IOException::class)
    abstract fun parse(inputStream: InputStream): List<T>

    /** Returns file name for database. */
    abstract fun getDatabaseFileName(): String

    /** Returns max time that data should be persisted before being removed. */
    abstract fun getMaxDataAgeMs(): Long

    /** Returns true if the two events have the same primary key for the database store. */
    abstract fun hasTheSamePrimaryKey(first: T, second: T): Boolean

    /** Copies the event with the time delta applied to the [PermissionEvent.eventTime]. */
    abstract fun T.copyWithTimeDelta(timeDelta: Long): T
}
