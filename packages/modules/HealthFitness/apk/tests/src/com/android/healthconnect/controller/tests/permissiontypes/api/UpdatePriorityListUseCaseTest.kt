package com.android.healthconnect.controller.tests.permissiontypes.api

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.UpdateDataOriginPriorityOrderRequest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissiontypes.api.UpdatePriorityListUseCase
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.tests.utils.CoroutineTestRule
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.getDataOrigin
import com.google.common.truth.Truth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@ExperimentalCoroutinesApi
@HiltAndroidTest
class UpdatePriorityListUseCaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private val manager: HealthConnectManager = Mockito.mock(HealthConnectManager::class.java)
    private lateinit var useCase: UpdatePriorityListUseCase
    @Inject lateinit var appInfoReader: AppInfoReader
    private lateinit var context: Context
    @Captor lateinit var filtersCaptor: ArgumentCaptor<UpdateDataOriginPriorityOrderRequest>

    @Before
    fun setup() {
        hiltRule.inject()
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        useCase = UpdatePriorityListUseCase(manager, Dispatchers.Main)
    }

    @Test
    fun invoke_updatePriorityList_callsHealthManager() = runTest {
        Mockito.doAnswer(prepareAnswer())
            .`when`(manager)
            .updateDataOriginPriorityOrder(
                Mockito.any(UpdateDataOriginPriorityOrderRequest::class.java),
                Mockito.any(),
                Mockito.any())

        val updatedPriorityList = listOf(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2)

        useCase.invoke(updatedPriorityList, HealthDataCategory.ACTIVITY)

        Mockito.verify(manager, Mockito.times(1))
            .updateDataOriginPriorityOrder(filtersCaptor.capture(), Mockito.any(), Mockito.any())
        Truth.assertThat(filtersCaptor.value.dataCategory).isEqualTo(HealthDataCategory.ACTIVITY)
        Truth.assertThat(filtersCaptor.value.dataOriginInOrder)
            .containsExactlyElementsIn(
                listOf(
                    getDataOrigin(TEST_APP_PACKAGE_NAME), getDataOrigin(TEST_APP_PACKAGE_NAME_2)))
    }

    private fun prepareAnswer(): (InvocationOnMock) -> Nothing? {
        val answer = { _: InvocationOnMock -> null }
        return answer
    }
}
