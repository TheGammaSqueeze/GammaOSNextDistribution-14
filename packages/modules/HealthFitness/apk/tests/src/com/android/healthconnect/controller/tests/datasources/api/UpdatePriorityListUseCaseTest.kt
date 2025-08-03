package com.android.healthconnect.controller.tests.datasources.api

import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.UpdateDataOriginPriorityOrderRequest
import android.health.connect.datatypes.DataOrigin
import com.android.healthconnect.controller.datasources.api.UpdatePriorityListUseCase
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_3
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Captor
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.invocation.InvocationOnMock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
@HiltAndroidTest
class UpdatePriorityListUseCaseTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    private lateinit var useCase: UpdatePriorityListUseCase
    private val healthConnectManager : HealthConnectManager = mock(HealthConnectManager::class.java)

    @Captor lateinit var requestCaptor : ArgumentCaptor<UpdateDataOriginPriorityOrderRequest>
    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        hiltRule.inject()
        useCase = UpdatePriorityListUseCase(healthConnectManager, Dispatchers.Main)
    }

    @Test
    fun invoke_callsHealthConnectManager() = runTest {
        doAnswer(prepareAnswer())
            .`when`(healthConnectManager)
            .updateDataOriginPriorityOrder(any(UpdateDataOriginPriorityOrderRequest::class.java), any(), any())

        val priorityList = listOf(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_3)
        useCase.invoke(
            priorityList = priorityList,
            category = HealthDataCategory.ACTIVITY
        )
        val expectedPriorityList = priorityList
            .map { packageName -> DataOrigin.Builder().setPackageName(packageName).build() }
            .toList()

        verify(healthConnectManager, times(1))
            .updateDataOriginPriorityOrder(requestCaptor.capture(), any(), any())
        assertThat(requestCaptor.value.dataCategory).isEqualTo(HealthDataCategory.ACTIVITY)
        assertThat(requestCaptor.value.dataOriginInOrder)
            .isEqualTo(expectedPriorityList)
    }

    private fun prepareAnswer(): (InvocationOnMock) -> Nothing? {
        val answer = { _: InvocationOnMock -> null }
        return answer
    }

}