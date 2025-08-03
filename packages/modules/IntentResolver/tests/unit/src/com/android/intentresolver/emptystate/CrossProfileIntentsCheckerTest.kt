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

package com.android.intentresolver.emptystate

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.IPackageManager
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.nullable

class CrossProfileIntentsCheckerTest {
    private val PERSONAL_USER_ID = 10
    private val WORK_USER_ID = 20

    private val contentResolver = mock<ContentResolver>()

    @Test
    fun testChecker_hasCrossProfileIntents() {
        val packageManager =
            mock<IPackageManager> {
                whenever(
                        canForwardTo(
                            any(Intent::class.java),
                            nullable(String::class.java),
                            eq(PERSONAL_USER_ID),
                            eq(WORK_USER_ID)
                        )
                    )
                    .thenReturn(true)
            }
        val checker = CrossProfileIntentsChecker(contentResolver, packageManager)
        val intents = listOf(Intent())
        assertThat(checker.hasCrossProfileIntents(intents, PERSONAL_USER_ID, WORK_USER_ID)).isTrue()
    }

    @Test
    fun testChecker_noCrossProfileIntents() {
        val packageManager =
            mock<IPackageManager> {
                whenever(
                        canForwardTo(
                            any(Intent::class.java),
                            nullable(String::class.java),
                            anyInt(),
                            anyInt()
                        )
                    )
                    .thenReturn(false)
            }
        val checker = CrossProfileIntentsChecker(contentResolver, packageManager)
        val intents = listOf(Intent())
        assertThat(checker.hasCrossProfileIntents(intents, PERSONAL_USER_ID, WORK_USER_ID))
            .isFalse()
    }

    @Test
    fun testChecker_noIntents() {
        val packageManager = mock<IPackageManager>()
        val checker = CrossProfileIntentsChecker(contentResolver, packageManager)
        val intents = listOf<Intent>()
        assertThat(checker.hasCrossProfileIntents(intents, PERSONAL_USER_ID, WORK_USER_ID))
            .isFalse()
    }
}
