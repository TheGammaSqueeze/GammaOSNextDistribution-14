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

package com.android.permissioncontroller.tests.mocking.privacysources

import android.content.Context
import android.os.Build
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.privacysources.PrivacySourceData
import com.android.permissioncontroller.privacysources.TextStorageRepository
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class TextStorageRepositoryTest {

    private lateinit var context: Context
    private lateinit var mockitoSession: MockitoSession
    private lateinit var dataFile: File

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()

        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        dataFile = context.getFileStreamPath("testFile")
    }

    @After
    fun cleanup() {
        context.deleteFile("testFile")
        mockitoSession.finishMocking()
    }

    @Test
    fun testEmptyFileReading() {
        val storageRepository = TextStorageRepository(dataFile)
        val dataList = storageRepository.readData(creator)
        Assert.assertTrue(dataList.isEmpty())
    }

    @Test
    fun testWrite() {
        val storageRepository = TextStorageRepository(dataFile)
        val component = TestPrivacySourceComponent("a", 100)
        storageRepository.persistData(listOf(component))
        val dataList = storageRepository.readData(creator)
        Assert.assertEquals(1, dataList.size)
        Assert.assertEquals(component.toStorageData(), dataList[0].toStorageData())
    }

    @Test
    fun testWrite_MultipleEntries() {
        val storageRepository = TextStorageRepository(dataFile)
        val components =
            listOf(
                TestPrivacySourceComponent("a", 100),
                TestPrivacySourceComponent("b", 200),
                TestPrivacySourceComponent("c", 300)
            )
        storageRepository.persistData(components)
        val dataList = storageRepository.readData(creator)
        Assert.assertEquals(3, dataList.size)
    }

    @Test
    fun testRead_Ignore_CorruptedEntries() {
        val storageRepository = TextStorageRepository(dataFile)
        val components =
            listOf(
                TestPrivacySourceComponent("a", 100),
                TestPrivacySourceComponent("b", 200),
                TestPrivacySourceComponent("c", 300)
            )
        storageRepository.persistData(components)
        appendCorruptedData(dataFile, "not_enough_parts")
        appendCorruptedData(dataFile, "not_enough_parts")
        appendCorruptedData(dataFile, "")
        val dataList = storageRepository.readData(creator)
        Assert.assertEquals(3, dataList.size)
    }

    @Test
    fun testRead_Ignore_NumberFormatError() {
        val storageRepository = TextStorageRepository(dataFile)
        val components =
            listOf(TestPrivacySourceComponent("a", 100), TestPrivacySourceComponent("b", 200))
        storageRepository.persistData(components)
        appendCorruptedData(dataFile, "com.example.TestService hundred")
        appendCorruptedData(dataFile, "com.example.TestService1 abc")

        val dataList: List<TestPrivacySourceComponent> = storageRepository.readData(creator)
        Assert.assertEquals(2, dataList.size)
    }

    private fun appendCorruptedData(dataFile: File, data: String) {
        BufferedWriter(FileWriter(dataFile, true)).use { writer ->
            writer.write(data)
            writer.newLine()
        }
    }

    private val creator =
        object : PrivacySourceData.Creator<TestPrivacySourceComponent> {
            override fun fromStorageData(data: String): TestPrivacySourceComponent {
                val lineComponents = data.split(" ")
                return TestPrivacySourceComponent(lineComponents[0], lineComponents[1].toInt())
            }
        }
}

class TestPrivacySourceComponent(private val name: String, private val timestamp: Int) :
    PrivacySourceData {
    override fun toStorageData(): String {
        return "$name $timestamp"
    }
}
