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

package com.android.permissioncontroller.privacysources

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class TextStorageRepository(private val file: File) : PrivacySourceStorageRepository {

    val LOG_TAG = TextStorageRepository::class.java.simpleName

    override fun persistData(dataList: List<PrivacySourceData>) {
        try {
            writeLines(dataList.map { it.toStorageData() })
        } catch (ex: IOException) {
            Log.e(LOG_TAG, "Could not read ${file.absolutePath}", ex)
        }
    }

    override fun <T> readData(creator: PrivacySourceData.Creator<T>): List<T> {
        try {
            BufferedReader(FileReader(file)).useLines { lines ->
                return lines
                    .mapNotNull {
                        try {
                            creator.fromStorageData(it)
                        } catch (ex: Exception) {
                            Log.e(LOG_TAG, "corrupted data : $it in file ${file.absolutePath}", ex)
                            null
                        }
                    }
                    .toList()
            }
        } catch (ignored: FileNotFoundException) {
            Log.e(LOG_TAG, "Could not find file ${file.absolutePath}")
            return emptyList()
        } catch (ex: IOException) {
            Log.e(LOG_TAG, "Could not read ${file.absolutePath}", ex)
            return emptyList()
        }
    }

    private fun writeLines(lines: List<String>) {
        BufferedWriter(FileWriter(file)).use { writer ->
            lines.forEach {
                writer.write(it)
                writer.newLine()
            }
        }
    }
}
