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
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.UserHandle
import com.android.intentresolver.any
import com.android.intentresolver.eq
import com.android.intentresolver.kotlinArgumentCaptor
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertThat
import java.lang.IndexOutOfBoundsException
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class IntentResolverTest {

    @Mock lateinit var mockPackageManager: PackageManager

    private lateinit var intentResolver: IntentResolver

    private val fakePinnableComponents =
        object : PinnableComponents {
            override fun isComponentPinned(name: ComponentName): Boolean {
                return name.packageName == "PinnedPackage"
            }
        }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        intentResolver =
            IntentResolverImpl(mockPackageManager, ResolveListDeduperImpl(fakePinnableComponents))
    }

    @Test
    fun getResolversForIntentAsUser_noIntents_returnsEmptyList() {
        // Arrange
        val testIntents = emptyList<Intent>()

        // Act
        val result =
            intentResolver.getResolversForIntentAsUser(
                shouldGetResolvedFilter = false,
                shouldGetActivityMetadata = false,
                shouldGetOnlyDefaultActivities = false,
                intents = testIntents,
                userHandle = UserHandle(456),
            )

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun getResolversForIntentAsUser_noResolveInfo_returnsEmptyList() {
        // Arrange
        val testIntents = listOf(Intent("TestAction"))
        val testResolveInfos = emptyList<ResolveInfo>()
        whenever(mockPackageManager.queryIntentActivitiesAsUser(any(), anyInt(), any<UserHandle>()))
            .thenReturn(testResolveInfos)

        // Act
        val result =
            intentResolver.getResolversForIntentAsUser(
                shouldGetResolvedFilter = false,
                shouldGetActivityMetadata = false,
                shouldGetOnlyDefaultActivities = false,
                intents = testIntents,
                userHandle = UserHandle(456),
            )

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun getResolversForIntentAsUser_returnsAllResolveComponentInfo() {
        // Arrange
        val testIntent1 = Intent("TestAction1")
        val testIntent2 = Intent("TestAction2")
        val testIntents = listOf(testIntent1, testIntent2)
        val testResolveInfos1 =
            listOf(
                ResolveInfo().apply {
                    userHandle = UserHandle(456)
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "TestPackage1"
                    activityInfo.name = "TestClass1"
                },
                ResolveInfo().apply {
                    userHandle = UserHandle(456)
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "TestPackage2"
                    activityInfo.name = "TestClass2"
                },
            )
        val testResolveInfos2 =
            listOf(
                ResolveInfo().apply {
                    userHandle = UserHandle(456)
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "TestPackage3"
                    activityInfo.name = "TestClass3"
                },
                ResolveInfo().apply {
                    userHandle = UserHandle(456)
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "TestPackage4"
                    activityInfo.name = "TestClass4"
                },
            )
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    eq(testIntent1),
                    anyInt(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(testResolveInfos1)
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    eq(testIntent2),
                    anyInt(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(testResolveInfos2)

        // Act
        val result =
            intentResolver.getResolversForIntentAsUser(
                shouldGetResolvedFilter = false,
                shouldGetActivityMetadata = false,
                shouldGetOnlyDefaultActivities = false,
                intents = testIntents,
                userHandle = UserHandle(456),
            )

        // Assert
        result.forEachIndexed { index, it ->
            val postfix = index + 1
            assertThat(it.name.packageName).isEqualTo("TestPackage$postfix")
            assertThat(it.name.className).isEqualTo("TestClass$postfix")
            assertThrows(IndexOutOfBoundsException::class.java) { it.getIntentAt(1) }
        }
        assertThat(result.map { it.getIntentAt(0) })
            .containsExactly(
                testIntent1,
                testIntent1,
                testIntent2,
                testIntent2,
            )
    }

    @Test
    fun getResolversForIntentAsUser_resolveInfoWithoutUserHandle_isSkipped() {
        // Arrange
        val testIntent = Intent("TestAction")
        val testIntents = listOf(testIntent)
        val testResolveInfos =
            listOf(
                ResolveInfo().apply {
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "TestPackage"
                    activityInfo.name = "TestClass"
                },
            )
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    any(),
                    anyInt(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(testResolveInfos)

        // Act
        val result =
            intentResolver.getResolversForIntentAsUser(
                shouldGetResolvedFilter = false,
                shouldGetActivityMetadata = false,
                shouldGetOnlyDefaultActivities = false,
                intents = testIntents,
                userHandle = UserHandle(456),
            )

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun getResolversForIntentAsUser_duplicateComponents_areCombined() {
        // Arrange
        val testIntent1 = Intent("TestAction1")
        val testIntent2 = Intent("TestAction2")
        val testIntents = listOf(testIntent1, testIntent2)
        val testResolveInfos1 =
            listOf(
                ResolveInfo().apply {
                    userHandle = UserHandle(456)
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "DuplicatePackage"
                    activityInfo.name = "DuplicateClass"
                },
            )
        val testResolveInfos2 =
            listOf(
                ResolveInfo().apply {
                    userHandle = UserHandle(456)
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "DuplicatePackage"
                    activityInfo.name = "DuplicateClass"
                },
            )
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    eq(testIntent1),
                    anyInt(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(testResolveInfos1)
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    eq(testIntent2),
                    anyInt(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(testResolveInfos2)

        // Act
        val result =
            intentResolver.getResolversForIntentAsUser(
                shouldGetResolvedFilter = false,
                shouldGetActivityMetadata = false,
                shouldGetOnlyDefaultActivities = false,
                intents = testIntents,
                userHandle = UserHandle(456),
            )

        // Assert
        assertThat(result).hasSize(1)
        with(result.first()) {
            assertThat(name.packageName).isEqualTo("DuplicatePackage")
            assertThat(name.className).isEqualTo("DuplicateClass")
            assertThat(getIntentAt(0)).isEqualTo(testIntent1)
            assertThat(getIntentAt(1)).isEqualTo(testIntent2)
            assertThrows(IndexOutOfBoundsException::class.java) { getIntentAt(2) }
        }
    }

    @Test
    fun getResolversForIntentAsUser_pinnedComponentsArePinned() {
        // Arrange
        val testIntent1 = Intent("TestAction1")
        val testIntent2 = Intent("TestAction2")
        val testIntents = listOf(testIntent1, testIntent2)
        val testResolveInfos1 =
            listOf(
                ResolveInfo().apply {
                    userHandle = UserHandle(456)
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "UnpinnedPackage"
                    activityInfo.name = "UnpinnedClass"
                },
            )
        val testResolveInfos2 =
            listOf(
                ResolveInfo().apply {
                    userHandle = UserHandle(456)
                    activityInfo = ActivityInfo()
                    activityInfo.packageName = "PinnedPackage"
                    activityInfo.name = "PinnedClass"
                },
            )
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    eq(testIntent1),
                    anyInt(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(testResolveInfos1)
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    eq(testIntent2),
                    anyInt(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(testResolveInfos2)

        // Act
        val result =
            intentResolver.getResolversForIntentAsUser(
                shouldGetResolvedFilter = false,
                shouldGetActivityMetadata = false,
                shouldGetOnlyDefaultActivities = false,
                intents = testIntents,
                userHandle = UserHandle(456),
            )

        // Assert
        assertThat(result.map { it.isPinned }).containsExactly(false, true)
    }

    @Test
    fun getResolversForIntentAsUser_whenNoExtraBehavior_usesBaseFlags() {
        // Arrange
        val baseFlags =
            PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE or
                PackageManager.MATCH_CLONE_PROFILE
        val testIntent = Intent()
        val testIntents = listOf(testIntent)

        // Act
        intentResolver.getResolversForIntentAsUser(
            shouldGetResolvedFilter = false,
            shouldGetActivityMetadata = false,
            shouldGetOnlyDefaultActivities = false,
            intents = testIntents,
            userHandle = UserHandle(456),
        )

        // Assert
        val flags = kotlinArgumentCaptor<Int>()
        verify(mockPackageManager)
            .queryIntentActivitiesAsUser(
                any(),
                flags.capture(),
                any<UserHandle>(),
            )
        assertThat(flags.value).isEqualTo(baseFlags)
    }

    @Test
    fun getResolversForIntentAsUser_whenShouldGetResolvedFilter_usesGetResolvedFilterFlag() {
        // Arrange
        val testIntent = Intent()
        val testIntents = listOf(testIntent)

        // Act
        intentResolver.getResolversForIntentAsUser(
            shouldGetResolvedFilter = true,
            shouldGetActivityMetadata = false,
            shouldGetOnlyDefaultActivities = false,
            intents = testIntents,
            userHandle = UserHandle(456),
        )

        // Assert
        val flags = kotlinArgumentCaptor<Int>()
        verify(mockPackageManager)
            .queryIntentActivitiesAsUser(
                any(),
                flags.capture(),
                any<UserHandle>(),
            )
        assertThat(flags.value and PackageManager.GET_RESOLVED_FILTER)
            .isEqualTo(PackageManager.GET_RESOLVED_FILTER)
    }

    @Test
    fun getResolversForIntentAsUser_whenShouldGetActivityMetadata_usesGetMetaDataFlag() {
        // Arrange
        val testIntent = Intent()
        val testIntents = listOf(testIntent)

        // Act
        intentResolver.getResolversForIntentAsUser(
            shouldGetResolvedFilter = false,
            shouldGetActivityMetadata = true,
            shouldGetOnlyDefaultActivities = false,
            intents = testIntents,
            userHandle = UserHandle(456),
        )

        // Assert
        val flags = kotlinArgumentCaptor<Int>()
        verify(mockPackageManager)
            .queryIntentActivitiesAsUser(
                any(),
                flags.capture(),
                any<UserHandle>(),
            )
        assertThat(flags.value and PackageManager.GET_META_DATA)
            .isEqualTo(PackageManager.GET_META_DATA)
    }

    @Test
    fun getResolversForIntentAsUser_whenShouldGetOnlyDefaultActivities_usesMatchDefaultOnlyFlag() {
        // Arrange
        val testIntent = Intent()
        val testIntents = listOf(testIntent)

        // Act
        intentResolver.getResolversForIntentAsUser(
            shouldGetResolvedFilter = false,
            shouldGetActivityMetadata = false,
            shouldGetOnlyDefaultActivities = true,
            intents = testIntents,
            userHandle = UserHandle(456),
        )

        // Assert
        val flags = kotlinArgumentCaptor<Int>()
        verify(mockPackageManager)
            .queryIntentActivitiesAsUser(
                any(),
                flags.capture(),
                any<UserHandle>(),
            )
        assertThat(flags.value and PackageManager.MATCH_DEFAULT_ONLY)
            .isEqualTo(PackageManager.MATCH_DEFAULT_ONLY)
    }

    @Test
    fun getResolversForIntentAsUser_whenWebIntent_usesMatchInstantFlag() {
        // Arrange
        val testIntent = Intent(Intent.ACTION_VIEW, Uri.fromParts(IntentFilter.SCHEME_HTTP, "", ""))
        val testIntents = listOf(testIntent)

        // Act
        intentResolver.getResolversForIntentAsUser(
            shouldGetResolvedFilter = false,
            shouldGetActivityMetadata = false,
            shouldGetOnlyDefaultActivities = false,
            intents = testIntents,
            userHandle = UserHandle(456),
        )

        // Assert
        val flags = kotlinArgumentCaptor<Int>()
        verify(mockPackageManager)
            .queryIntentActivitiesAsUser(
                any(),
                flags.capture(),
                any<UserHandle>(),
            )
        assertThat(flags.value and PackageManager.MATCH_INSTANT)
            .isEqualTo(PackageManager.MATCH_INSTANT)
    }

    @Test
    fun getResolversForIntentAsUser_whenActivityMatchExternalFlag_usesMatchInstantFlag() {
        // Arrange
        val testIntent = Intent().addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL)
        val testIntents = listOf(testIntent)

        // Act
        intentResolver.getResolversForIntentAsUser(
            shouldGetResolvedFilter = false,
            shouldGetActivityMetadata = false,
            shouldGetOnlyDefaultActivities = false,
            intents = testIntents,
            userHandle = UserHandle(456),
        )

        // Assert
        val flags = kotlinArgumentCaptor<Int>()
        verify(mockPackageManager)
            .queryIntentActivitiesAsUser(
                any(),
                flags.capture(),
                any<UserHandle>(),
            )
        assertThat(flags.value and PackageManager.MATCH_INSTANT)
            .isEqualTo(PackageManager.MATCH_INSTANT)
    }
}
