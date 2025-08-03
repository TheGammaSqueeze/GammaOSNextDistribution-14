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

package com.android.intentresolver.v2.listcontroller

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.UserHandle
import com.android.intentresolver.ResolvedComponentInfo
import com.google.common.truth.Truth.assertThat
import java.lang.IndexOutOfBoundsException
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ResolveListDeduperTest {

    private lateinit var resolveListDeduper: ResolveListDeduper

    @Before
    fun setup() {
        resolveListDeduper = ResolveListDeduperImpl(NoComponentPinning())
    }

    @Test
    fun addResolveListDedupe_addsDifferentComponents() {
        // Arrange
        val testIntent = Intent()
        val testResolveInfo1 =
            ResolveInfo().apply {
                userHandle = UserHandle(456)
                activityInfo = ActivityInfo()
                activityInfo.packageName = "TestPackage1"
                activityInfo.name = "TestClass1"
            }
        val testResolveInfo2 =
            ResolveInfo().apply {
                userHandle = UserHandle(456)
                activityInfo = ActivityInfo()
                activityInfo.packageName = "TestPackage2"
                activityInfo.name = "TestClass2"
            }
        val testResolvedComponentInfo1 =
            ResolvedComponentInfo(
                    ComponentName("TestPackage1", "TestClass1"),
                    testIntent,
                    testResolveInfo1,
                )
                .apply { isPinned = false }
        val listUnderTest = mutableListOf(testResolvedComponentInfo1)
        val listToAdd = listOf(testResolveInfo2)

        // Act
        resolveListDeduper.addToResolveListWithDedupe(
            into = listUnderTest,
            intent = testIntent,
            from = listToAdd,
        )

        // Assert
        listUnderTest.forEachIndexed { index, it ->
            val postfix = index + 1
            assertThat(it.name.packageName).isEqualTo("TestPackage$postfix")
            assertThat(it.name.className).isEqualTo("TestClass$postfix")
            assertThat(it.getIntentAt(0)).isEqualTo(testIntent)
            assertThrows(IndexOutOfBoundsException::class.java) { it.getIntentAt(1) }
        }
    }

    @Test
    fun addResolveListDedupe_combinesDuplicateComponents() {
        // Arrange
        val testIntent = Intent()
        val testResolveInfo1 =
            ResolveInfo().apply {
                userHandle = UserHandle(456)
                activityInfo = ActivityInfo()
                activityInfo.packageName = "DuplicatePackage"
                activityInfo.name = "DuplicateClass"
            }
        val testResolveInfo2 =
            ResolveInfo().apply {
                userHandle = UserHandle(456)
                activityInfo = ActivityInfo()
                activityInfo.packageName = "DuplicatePackage"
                activityInfo.name = "DuplicateClass"
            }
        val testResolvedComponentInfo1 =
            ResolvedComponentInfo(
                    ComponentName("DuplicatePackage", "DuplicateClass"),
                    testIntent,
                    testResolveInfo1,
                )
                .apply { isPinned = false }
        val listUnderTest = mutableListOf(testResolvedComponentInfo1)
        val listToAdd = listOf(testResolveInfo2)

        // Act
        resolveListDeduper.addToResolveListWithDedupe(
            into = listUnderTest,
            intent = testIntent,
            from = listToAdd,
        )

        // Assert
        assertThat(listUnderTest).containsExactly(testResolvedComponentInfo1)
        assertThat(testResolvedComponentInfo1.getResolveInfoAt(0)).isEqualTo(testResolveInfo1)
        assertThat(testResolvedComponentInfo1.getResolveInfoAt(1)).isEqualTo(testResolveInfo2)
    }
}
