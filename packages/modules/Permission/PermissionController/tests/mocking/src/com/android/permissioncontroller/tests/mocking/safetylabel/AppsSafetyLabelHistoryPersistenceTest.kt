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

package com.android.permissioncontroller.tests.mocking.safetylabel

import android.content.Context
import android.os.Build
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppInfo
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppSafetyLabelDiff
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence.AppsSafetyLabelHistoryFileContent
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence.ChangeListener
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.DATE_2022_10_10
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.DATE_2022_10_12
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.DATE_2022_10_14
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.DATE_2022_12_10
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.DATE_2022_12_30
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.PACKAGE_NAME_1
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.PACKAGE_NAME_2
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.PACKAGE_NAME_3
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.SAFETY_LABEL_PKG_1_V1
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.SAFETY_LABEL_PKG_1_V2
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.SAFETY_LABEL_PKG_1_V3
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.SAFETY_LABEL_PKG_2_V1
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.SAFETY_LABEL_PKG_2_V2
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.SAFETY_LABEL_PKG_2_V3
import com.android.permissioncontroller.tests.mocking.safetylabel.TestSafetyLabels.SAFETY_LABEL_PKG_3_V1
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/** Tests for [AppsSafetyLabelHistoryPersistence]. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class AppsSafetyLabelHistoryPersistenceTest {
    private lateinit var context: Context
    private lateinit var dataFile: File
    private lateinit var mockitoSession: MockitoSession

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()
        dataFile = context.getFileStreamPath(TEST_FILE_NAME)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        setMaxSafetyLabelsToPersist(20)
    }

    @After
    fun cleanup() {
        context.deleteFile(TEST_FILE_NAME)
        mockitoSession.finishMocking()
    }

    @Test
    fun read_afterDeleted_returnsNull() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(),
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)
        AppsSafetyLabelHistoryPersistence.clear(dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(null)
    }

    @Test
    fun read_afterWrite_noHistory_returnsIdenticalAppsSafetyLabelHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(),
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(appsSafetyLabelHistory)
    }

    @Test
    fun read_afterWrite_noSharing_returnsIdenticalAppsSafetyLabelHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_2), listOf(SAFETY_LABEL_PKG_2_V2))
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(appsSafetyLabelHistory)
    }

    @Test
    fun read_afterWrite_returnsIdenticalAppsSafetyLabelHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_1), listOf(SAFETY_LABEL_PKG_1_V1)),
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(appsSafetyLabelHistory)
    }

    @Test
    fun read_noFile_returnsInitialVersion() {
        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).version).isEqualTo(0)
    }

    @Test
    fun read_afterWrite_defaultVersion_returnsInitialVersion() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_2), listOf(SAFETY_LABEL_PKG_2_V2))
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).version).isEqualTo(0)
    }

    @Test
    fun read_afterWrite_specifiedVersion_returnsSpecifiedVersion() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_2), listOf(SAFETY_LABEL_PKG_2_V2))
                )
            )
        AppsSafetyLabelHistoryPersistence.write(
            dataFile,
            AppsSafetyLabelHistoryFileContent(appsSafetyLabelHistory, 5)
        )

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).version).isEqualTo(5)
    }

    @Test
    fun recordSafetyLabel_noAppsHistory_addsAppsHistory() {
        AppsSafetyLabelHistoryPersistence.clear(dataFile)

        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_1_V1, dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(
                AppsSafetyLabelHistory(
                    listOf(
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_1),
                            listOf(SAFETY_LABEL_PKG_1_V1)
                        )
                    )
                )
            )
    }

    @Test
    fun recordSafetyLabel_noAppHistory_addsAppHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_1), listOf(SAFETY_LABEL_PKG_1_V1))
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_2_V1, dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(
                AppsSafetyLabelHistory(
                    listOf(
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_1),
                            listOf(SAFETY_LABEL_PKG_1_V1)
                        ),
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_2),
                            listOf(SAFETY_LABEL_PKG_2_V1)
                        )
                    )
                )
            )
    }

    @Test
    fun recordSafetyLabel_existingAppHistory_addsToHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_1), listOf(SAFETY_LABEL_PKG_1_V1)),
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_2_V3, dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(
                AppsSafetyLabelHistory(
                    listOf(
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_1),
                            listOf(SAFETY_LABEL_PKG_1_V1)
                        ),
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_2),
                            listOf(
                                SAFETY_LABEL_PKG_2_V1,
                                SAFETY_LABEL_PKG_2_V2,
                                SAFETY_LABEL_PKG_2_V3
                            )
                        )
                    )
                )
            )
    }

    @Test
    fun recordSafetyLabel_whenMaximumSafetyLabelsAlreadyStoredForApp_dropsOldSafetyLabels() {
        setMaxSafetyLabelsToPersist(2)
        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_2_V1, dataFile)
        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_2_V2, dataFile)

        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_2_V3, dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(
                AppsSafetyLabelHistory(
                    listOf(
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_2),
                            listOf(SAFETY_LABEL_PKG_2_V2, SAFETY_LABEL_PKG_2_V3)
                        )
                    )
                )
            )
    }

    @Test
    fun recordSafetyLabel_noChangeToLastLabel_doesNothing() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_1), listOf(SAFETY_LABEL_PKG_1_V2)),
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_1_V3, dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(appsSafetyLabelHistory)
    }

    @Test
    fun recordSafetyLabels_addsToHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_1), listOf(SAFETY_LABEL_PKG_1_V1)),
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_2), listOf(SAFETY_LABEL_PKG_2_V1))
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        AppsSafetyLabelHistoryPersistence.recordSafetyLabels(
            setOf(
                SAFETY_LABEL_PKG_1_V2,
                SAFETY_LABEL_PKG_2_V2,
                SAFETY_LABEL_PKG_2_V3,
                SAFETY_LABEL_PKG_3_V1
            ),
            dataFile
        )

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(
                AppsSafetyLabelHistory(
                    listOf(
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_1),
                            listOf(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2)
                        ),
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_2),
                            listOf(
                                SAFETY_LABEL_PKG_2_V1,
                                SAFETY_LABEL_PKG_2_V2,
                                SAFETY_LABEL_PKG_2_V3
                            )
                        ),
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_3),
                            listOf(SAFETY_LABEL_PKG_3_V1)
                        ),
                    )
                )
            )
    }

    @Test
    fun getAppSafetyLabelDiffs_whenNoHistory_returnsEmpty() {
        AppsSafetyLabelHistoryPersistence.clear(dataFile)

        val safetyLabelDiffs: List<AppSafetyLabelDiff> =
            AppsSafetyLabelHistoryPersistence.getAppSafetyLabelDiffs(DATE_2022_12_10, dataFile)

        assertThat(safetyLabelDiffs).isEqualTo(listOf<AppSafetyLabelDiff>())
    }

    @Test
    fun getAppSafetyLabelDiffs_whenNoSafetyLabelChangesSinceStartTime_returnsEmpty() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(AppInfo(PACKAGE_NAME_1), listOf(SAFETY_LABEL_PKG_1_V1))
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        val safetyLabelDiffs: List<AppSafetyLabelDiff> =
            AppsSafetyLabelHistoryPersistence.getAppSafetyLabelDiffs(DATE_2022_10_14, dataFile)

        assertThat(safetyLabelDiffs).isEqualTo(listOf<AppSafetyLabelDiff>())
    }

    @Test
    fun getAppSafetyLabelDiffs_whenNoSafetyLabelsSinceStartTime_returnsEmpty() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_1),
                        listOf(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        val safetyLabelDiffs: List<AppSafetyLabelDiff> =
            AppsSafetyLabelHistoryPersistence.getAppSafetyLabelDiffs(DATE_2022_12_10, dataFile)

        assertThat(safetyLabelDiffs).isEqualTo(listOf<AppSafetyLabelDiff>())
    }

    @Test
    fun getAppSafetyLabelDiffs_whenNoSafetyLabelsBeforeStartTime_returnsMoreRecentDiffs() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V2, SAFETY_LABEL_PKG_2_V3)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        val safetyLabelDiffs: List<AppSafetyLabelDiff> =
            AppsSafetyLabelHistoryPersistence.getAppSafetyLabelDiffs(DATE_2022_10_10, dataFile)

        assertThat(safetyLabelDiffs)
            .isEqualTo(listOf(AppSafetyLabelDiff(SAFETY_LABEL_PKG_2_V2, SAFETY_LABEL_PKG_2_V3)))
    }

    @Test
    fun getAppSafetyLabelDiffs_returnsAvailableDiffs() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_1),
                        listOf(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2)
                    ),
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2, SAFETY_LABEL_PKG_2_V3)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        val safetyLabelDiffs =
            AppsSafetyLabelHistoryPersistence.getAppSafetyLabelDiffs(DATE_2022_10_12, dataFile)

        assertThat(safetyLabelDiffs)
            .isEqualTo(
                listOf(
                    AppSafetyLabelDiff(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2),
                    AppSafetyLabelDiff(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V3)
                )
            )
    }

    @Test
    fun getSafetyLabelsLastUpdatedTimes_noAppsPersisted_returnsEmptyMap() {
        val lastUpdatedTimes =
            AppsSafetyLabelHistoryPersistence.getSafetyLabelsLastUpdatedTimes(dataFile)

        assertThat(lastUpdatedTimes).isEmpty()
    }

    @Test
    fun getSafetyLabelsLastUpdatedTimes_appsPersisted_returnsLastUpdatedTimes() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_1),
                        listOf(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2)
                    ),
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2, SAFETY_LABEL_PKG_2_V3)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        val lastUpdatedTimes =
            AppsSafetyLabelHistoryPersistence.getSafetyLabelsLastUpdatedTimes(dataFile)

        assertThat(lastUpdatedTimes)
            .isEqualTo(
                mapOf(
                    AppInfo(PACKAGE_NAME_1) to DATE_2022_10_14,
                    AppInfo(PACKAGE_NAME_2) to DATE_2022_12_30
                )
            )
    }

    @Test
    fun deleteSafetyLabelsForApps_removesSafetyLabelsFromPersistence() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_1),
                        listOf(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2)
                    ),
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        AppsSafetyLabelHistoryPersistence.deleteSafetyLabelsForApps(
            setOf(AppInfo(PACKAGE_NAME_2)),
            dataFile
        )

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(
                AppsSafetyLabelHistory(
                    listOf(
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_1),
                            listOf(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2)
                        )
                    )
                )
            )
    }

    @Test
    fun deleteSafetyLabelsOlderThan_laterTime_removesCorrectSafetyLabelsFromPersistence() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_1),
                        listOf(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2, SAFETY_LABEL_PKG_1_V3)
                    ),
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        AppsSafetyLabelHistoryPersistence.deleteSafetyLabelsOlderThan(DATE_2022_12_30, dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(
                AppsSafetyLabelHistory(
                    listOf(
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_1),
                            listOf(SAFETY_LABEL_PKG_1_V3)
                        ),
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_2),
                            listOf(SAFETY_LABEL_PKG_2_V2)
                        )
                    )
                )
            )
    }

    @Test
    fun deleteSafetyLabelsOlderThan_earlierTime_removesCorrectSafetyLabelsFromPersistence() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_1),
                        listOf(SAFETY_LABEL_PKG_1_V1, SAFETY_LABEL_PKG_1_V2, SAFETY_LABEL_PKG_1_V3)
                    ),
                    AppSafetyLabelHistory(
                        AppInfo(PACKAGE_NAME_2),
                        listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2)
                    )
                )
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        AppsSafetyLabelHistoryPersistence.deleteSafetyLabelsOlderThan(DATE_2022_10_14, dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile).appsSafetyLabelHistory)
            .isEqualTo(
                AppsSafetyLabelHistory(
                    listOf(
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_1),
                            listOf(SAFETY_LABEL_PKG_1_V2, SAFETY_LABEL_PKG_1_V3)
                        ),
                        AppSafetyLabelHistory(
                            AppInfo(PACKAGE_NAME_2),
                            listOf(SAFETY_LABEL_PKG_2_V1, SAFETY_LABEL_PKG_2_V2)
                        )
                    )
                )
            )
    }

    @Test
    fun registerListener_receivesUpdates() {
        var onChangedCount = 0
        val testChangeListener: ChangeListener =
            object : ChangeListener {
                override fun onSafetyLabelHistoryChanged() {
                    onChangedCount++
                }
            }
        AppsSafetyLabelHistoryPersistence.addListener(testChangeListener)

        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_1_V1, dataFile)

        assertThat(onChangedCount).isEqualTo(1)
    }

    @Test
    fun unregisterListener_doesNotReceiveUpdates() {
        var onChangedCount = 0
        val testChangeListener: ChangeListener =
            object : ChangeListener {
                override fun onSafetyLabelHistoryChanged() {
                    onChangedCount++
                }
            }
        AppsSafetyLabelHistoryPersistence.addListener(testChangeListener)
        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_1_V1, dataFile)
        assertThat(onChangedCount).isEqualTo(1)

        AppsSafetyLabelHistoryPersistence.removeListener(testChangeListener)
        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(SAFETY_LABEL_PKG_1_V2, dataFile)

        assertThat(onChangedCount).isEqualTo(1)
    }

    companion object {
        private const val TEST_FILE_NAME = "test_safety_label_history_file"
        private const val PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP =
            "max_safety_labels_persisted_per_app"

        /** Sets the value for the Permission Rationale feature [DeviceConfig] property. */
        private fun setMaxSafetyLabelsToPersist(max: Int) {
            whenever(
                    DeviceConfig.getInt(
                        eq(NAMESPACE_PRIVACY),
                        eq(PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP),
                        anyInt()
                    )
                )
                .thenReturn(max)
        }
    }
}
