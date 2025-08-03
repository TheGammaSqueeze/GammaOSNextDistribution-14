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

package com.android.healthconnect.controller.tests.safetycenter

import android.content.Context
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceStatus
import com.android.healthconnect.controller.safetycenter.HealthConnectSafetySource.Companion.HEALTH_CONNECT_SOURCE_ID
import com.android.healthconnect.controller.safetycenter.SafetyCenterManagerWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations.initMocks

class SafetyCenterManagerWrapperTest {

    private val mockContext: Context = Mockito.mock(Context::class.java)
    private val mockSafetyCenterManager: SafetyCenterManager =
        Mockito.mock(SafetyCenterManager::class.java)

    lateinit var wrapper: SafetyCenterManagerWrapper

    @Before
    fun setUp() {
        initMocks(this)
        wrapper = SafetyCenterManagerWrapper()
    }

    @Test
    fun isEnabled_whenContextNull_returnsFalse() {
        assertThat(wrapper.isEnabled(null)).isFalse()
    }

    @Test
    fun isEnabled_whenSystemServiceNull_returnsFalse() {
        whenever(mockContext.getSystemService(SafetyCenterManager::class.java)).thenReturn(null)
        assertThat(wrapper.isEnabled(mockContext)).isFalse()
    }

    @Test
    fun setSafetySourceData_callsSafetyCenterManager() {
        whenever(mockContext.getSystemService(SafetyCenterManager::class.java))
            .thenReturn(mockSafetyCenterManager)

        val safetySourceData =
            SafetySourceData.Builder()
                .setStatus(
                    SafetySourceStatus.Builder(
                            HEALTH_CONNECT_TITLE,
                            HEALTH_CONNECT_SUMMARY,
                            SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED)
                        .build())
                .build()

        val safetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(REFRESH_ID)
                .build()

        wrapper.setSafetySourceData(
            mockContext, HEALTH_CONNECT_SOURCE_ID, safetySourceData, safetyEvent)

        Mockito.verify(mockSafetyCenterManager)
            .setSafetySourceData(HEALTH_CONNECT_SOURCE_ID, safetySourceData, safetyEvent)
    }

    /** Companion object for [HealthConnectPrivacySourceTest]. */
    companion object {
        private const val HEALTH_CONNECT_TITLE: String = "Health Connect"
        private const val HEALTH_CONNECT_SUMMARY: String = "App permissions and data management"
        private const val REFRESH_ID: String = "refresh_id"
    }
}
