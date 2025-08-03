package com.android.healthconnect.controller.tests.permissiontypes.prioritylist

import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesViewModel
import com.android.healthconnect.controller.permissiontypes.prioritylist.PriorityListAdapter
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_3
import com.google.common.truth.Truth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@ExperimentalCoroutinesApi
@HiltAndroidTest
class PriorityListAdapterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val viewModel = mock(HealthPermissionTypesViewModel::class.java)
    private lateinit var priorityListAdapter: PriorityListAdapter

    @Inject lateinit var appInfoReader: AppInfoReader

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun getPackageNameList_packagesAreReturnedCorrectly() = runTest {
        priorityListAdapter =
            PriorityListAdapter(
                listOf(
                    appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME),
                    appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME_2),
                    appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME_3)),
                viewModel)
        Truth.assertThat(priorityListAdapter.getPackageNameList())
            .containsExactly(
                TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2, TEST_APP_PACKAGE_NAME_3)
    }

    @Test
    fun onItemMove_itemsAreReArrangedCorrectly() = runTest {
        priorityListAdapter =
            PriorityListAdapter(
                listOf(
                    appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME),
                    appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME_2),
                    appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME_3)),
                viewModel)
        priorityListAdapter.onItemMove(2, 1)
        Truth.assertThat(priorityListAdapter.getPackageNameList())
            .containsExactly(
                TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_3, TEST_APP_PACKAGE_NAME_2)
    }
}
