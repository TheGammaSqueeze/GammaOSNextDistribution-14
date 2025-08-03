/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.deletion.api

import android.health.connect.HealthConnectManager
import android.health.connect.RecordIdFilter
import android.health.connect.datatypes.StepsRecord
import com.android.healthconnect.controller.deletion.DeletionType.DeleteDataEntry
import com.android.healthconnect.controller.deletion.api.DeleteEntryUseCase
import com.android.healthconnect.controller.shared.DataType
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Matchers.any
import org.mockito.Matchers.anyListOf
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@HiltAndroidTest
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
class DeleteEntryUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var useCase: DeleteEntryUseCase
    var manager: HealthConnectManager = mock(HealthConnectManager::class.java)

    @Captor lateinit var listCaptor: ArgumentCaptor<List<RecordIdFilter>>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        useCase = DeleteEntryUseCase(manager, Dispatchers.Main)
    }

    @Test
    fun invoke_delete_callsHealthManager() = runTest {
        doAnswer(prepareAnswer())
            .`when`(manager)
            .deleteRecords(anyListOf(RecordIdFilter::class.java), any(), any())

        useCase.invoke(DeleteDataEntry("test_id", DataType.STEPS, 0))

        verify(manager, times(1)).deleteRecords(listCaptor.capture(), any(), any())
        assertThat(listCaptor.value[0].id).isEqualTo("test_id")
        assertThat(listCaptor.value[0].recordType).isEqualTo(StepsRecord::class.java)
    }

    private fun prepareAnswer(): (InvocationOnMock) -> Nothing? {
        val answer = { _: InvocationOnMock -> null }
        return answer
    }
}
