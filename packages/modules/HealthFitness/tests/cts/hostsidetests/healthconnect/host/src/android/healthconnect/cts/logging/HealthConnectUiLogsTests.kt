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

package android.healthconnect.cts.logging

import android.cts.statsdatom.lib.AtomTestUtils
import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.DeviceUtils
import android.cts.statsdatom.lib.ReportUtils
import android.healthconnect.cts.HostSideTestUtil
import android.healthconnect.cts.HostSideTestUtil.isHardwareSupported
import android.healthfitness.ui.ElementId
import android.healthfitness.ui.PageId
import com.android.os.StatsLog
import com.android.os.healthfitness.ui.UiExtensionAtoms
import com.android.tradefed.build.IBuildInfo
import com.android.tradefed.testtype.DeviceTestCase
import com.android.tradefed.testtype.IBuildReceiver
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ExtensionRegistry

class HealthConnectUiLogsTests : DeviceTestCase(), IBuildReceiver {

    companion object {
        private const val TAG = "HomeFragmentHostTest"
        private const val TEST_APP_PKG_NAME = "android.healthconnect.cts.testhelper"
    }

    private lateinit var mCtsBuild: IBuildInfo
    private lateinit var packageName: String

    override fun setUp() {
        super.setUp()
        if (!isHardwareSupported(device)) {
            return
        }
        assertThat(mCtsBuild).isNotNull()
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        // TODO(b/313055175): Do not disable rate limiting once b/300238889 is resolved.
        HostSideTestUtil.setupRateLimitingFeatureFlag(device)
        val pmResult =
            device.executeShellCommand(
                "pm list packages com.google.android.healthconnect.controller")
        packageName =
            if (pmResult.isEmpty()) {
                "com.android.healthconnect.controller"
            } else {
                "com.google.android.healthconnect.controller"
            }

        ConfigUtils.createConfigBuilder(packageName)
        ConfigUtils.uploadConfigForPushedAtoms(
            device,
            packageName,
            intArrayOf(
                UiExtensionAtoms.HEALTH_CONNECT_UI_IMPRESSION_FIELD_NUMBER,
                UiExtensionAtoms.HEALTH_CONNECT_UI_INTERACTION_FIELD_NUMBER))
    }

    @Throws(Exception::class)
    override fun tearDown() {
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        // TODO(b/313055175): Do not disable rate limiting once b/300238889 is resolved.
        HostSideTestUtil.restoreRateLimitingFeatureFlag(device)
        super.tearDown()
    }

    override fun setBuild(buildInfo: IBuildInfo) {
        mCtsBuild = buildInfo
    }

    fun testImpressionsAndInteractionsSent() {
        if (!isHardwareSupported(device)) {
            return
        }
        DeviceUtils.runDeviceTests(
            device, TEST_APP_PKG_NAME, ".HealthConnectUiTestHelper", "openHomeFragment")
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())
        val registry = ExtensionRegistry.newInstance()
        UiExtensionAtoms.registerAllExtensions(registry)

        val data = ReportUtils.getEventMetricDataList(device, registry)
        assertThat(data.size).isAtLeast(2)

        val homePageId = PageId.HOME_PAGE
        val categoriesPageId = PageId.CATEGORIES_PAGE
        val homePageImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page ==
                    homePageId &&
                    !it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).hasElement()
            }
        assertThat(homePageImpression.size).isAtLeast(1)

        val categoriesPageImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page ==
                    categoriesPageId &&
                    !it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).hasElement()
            }
        assertThat(categoriesPageImpression.size).isAtLeast(1)

        val dataAndAccessInteraction =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiInteraction).page ==
                    homePageId &&
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiInteraction).element ==
                        ElementId.DATA_AND_ACCESS_BUTTON
            }
        assertThat(dataAndAccessInteraction.size).isAtLeast(1)

        val appPermissionsImpression =
            filterImpressionLogs(data, ElementId.APP_PERMISSIONS_BUTTON)
        assertThat(appPermissionsImpression.size).isAtLeast(1)

        val dataAndAccessImpression =
            filterImpressionLogs(data, ElementId.DATA_AND_ACCESS_BUTTON)
        assertThat(dataAndAccessImpression.size).isAtLeast(1)

        val recentAccessDataImpression =
            filterImpressionLogs(data, ElementId.RECENT_ACCESS_ENTRY)
        assertThat(recentAccessDataImpression.size).isAtLeast(1)

        val seeAllRecentAccessImpression =
            filterImpressionLogs(data, ElementId.SEE_ALL_RECENT_ACCESS_BUTTON)
        assertThat(seeAllRecentAccessImpression.size).isAtLeast(1)

        val toolbarImpression =
            filterImpressionLogs(data, ElementId.TOOLBAR_SETTINGS_BUTTON)
        assertThat(toolbarImpression.size).isAtLeast(1)

        val categoryImpression =
            filterImpressionLogs(data, ElementId.CATEGORY_BUTTON)
        assertThat(categoryImpression.size).isAtLeast(2)

        val seeAllCategoriesImpression =
            filterImpressionLogs(data, ElementId.SEE_ALL_CATEGORIES_BUTTON)
        assertThat(seeAllCategoriesImpression.size).isAtLeast(1)

        val deleteAllDataImpression =
            filterImpressionLogs(data, ElementId.DELETE_ALL_DATA_BUTTON)
        assertThat(deleteAllDataImpression.size).isAtLeast(1)
    }

    private fun filterImpressionLogs(
        data: List<StatsLog.EventMetricData>,
        elementId: ElementId
    ): List<StatsLog.EventMetricData> {
        return data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                    elementId
        }
    }
}
