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

package android.localemanager.cts;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.locale.AppSupportedLocalesChanged;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class AppSupportedLocalesChangedAtomTest extends DeviceTestCase implements IBuildReceiver {
    public static final String ACTIVITY_FOR_SETTING_OVERRIDE_LOCALE_CONFIG =
            "ActivityForSettingOverrideLocaleConfig";
    private static final String INSTALLED_PACKAGE_NAME =
            "android.localemanager.atom.overridelocaleconfig";
    private static final String INVALID_PACKAGE_NAME = "invalid.package.name";
    private static final String OVERRIDE_LOCALE_CONFIG_KEY = "override_locale_config";
    private static final String OVERRIDE_LOCALES = "de-DE,en-US,it-IT,ja-JP,zh-Hans-SG";
    private static final String OVERRIDE_RESOURCE_LOCALES =
            "de-DE,en-US,fr-FR,it-IT,ja-JP,pt-PT,zh-Hans-SG,zh-Hant-TW";
    private static final String REMOVE_OVERRIDE = "remove_override";
    private static final int NUM_OVERRIDE_LOCALES = 5;
    private static final int NUM_RESOURCE_LOCALES = 8;
    private static final int INVALID_NUM = -1;
    private static final int INVALID_UID = -1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.APP_SUPPORTED_LOCALES_CHANGED_FIELD_NUMBER);
    }

    @Override
    protected void tearDown() throws Exception {
        resetAppLocaleConfigOverride();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
    }

    public void testAtomLogging_overrideLocaleConfig_logsAtomSuccessfully()
            throws Exception {
        // Launch the activity to override the LocaleConfig of the installed application. This
        // should trigger an AppSupportedLocalesChanged atom entry to be logged.
        invokeActivity(OVERRIDE_LOCALES);

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());

        AppSupportedLocalesChanged result = data.get(0).getAtom().getAppSupportedLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME),
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME), NUM_OVERRIDE_LOCALES,
                false, false, false, AppSupportedLocalesChanged.Status.SUCCESS, result);
    }

    public void testAtomLogging_removeOverride_logsAtomSuccessfully()
            throws Exception {
        // Launch the activity to override the LocaleConfig of the installed application. This
        // should trigger an AppSupportedLocalesChanged atom entry to be logged.
        invokeActivity(OVERRIDE_LOCALES);

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());

        AppSupportedLocalesChanged result = data.get(0).getAtom().getAppSupportedLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME),
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME), NUM_OVERRIDE_LOCALES,
                false, false, false, AppSupportedLocalesChanged.Status.SUCCESS, result);

        // Launch the activity to remove the LocaleConfig override of the installed application.
        // This should trigger an AppSupportedLocalesChanged atom entry to be logged.
        invokeActivity(REMOVE_OVERRIDE);

        List<StatsLog.EventMetricData> data2 = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data2.size());
        AppSupportedLocalesChanged result2 = data2.get(0).getAtom().getAppSupportedLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME),
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME), INVALID_NUM, true,
                false, false, AppSupportedLocalesChanged.Status.SUCCESS, result2);
    }

    public void testAtomLogging_sameAsResLocaleConfig_logsAtomSuccessfully()
            throws Exception {
        // Launch the activity to override the LocaleConfig of the installed application, which
        // is the same as APP's LocaleConfig. This should trigger an AppSupportedLocalesChanged
        // atom entry to be logged.
        invokeActivity(OVERRIDE_RESOURCE_LOCALES);

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());

        AppSupportedLocalesChanged result = data.get(0).getAtom().getAppSupportedLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME),
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME), NUM_RESOURCE_LOCALES,
                false, true, false, AppSupportedLocalesChanged.Status.SUCCESS, result);
    }

    public void testAtomLogging_sameAsPrevLocaleConfig_logsAtomUnspecified()
            throws Exception {
        // Launch the activity to override the LocaleConfig of the installed application. This
        // should trigger an AppSupportedLocalesChanged atom entry to be logged.
        invokeActivity(OVERRIDE_LOCALES);

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());

        AppSupportedLocalesChanged result = data.get(0).getAtom().getAppSupportedLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME),
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME), NUM_OVERRIDE_LOCALES,
                false, false, false, AppSupportedLocalesChanged.Status.SUCCESS, result);

        // Launch the activity to override the LocaleConfig which is the same as the previous one.
        invokeActivity(OVERRIDE_LOCALES);

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data2 = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data2.size());

        AppSupportedLocalesChanged result2 = data2.get(0).getAtom().getAppSupportedLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME),
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME), INVALID_NUM, false,
                false, true, AppSupportedLocalesChanged.Status.STATUS_UNSPECIFIED, result2);
    }

    public void testAtomLogging_invalidPackage_logsAtomWithFailureInvalidPackageName()
            throws Exception {
        // Calling setOverrideLocaleConfig() with an invalid package name.
        executeOverrideLocaleConfigCommand(INVALID_PACKAGE_NAME, OVERRIDE_LOCALES);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());

        AppSupportedLocalesChanged result = data.get(0).getAtom().getAppSupportedLocalesChanged();
        // The input package name is invalid therefore the status should be:
        // FAILURE_INVALID_TARGET_PACKAGE
        verifyAtomDetails(DeviceUtils.getHostUid(getDevice()), INVALID_UID, INVALID_NUM, false,
                false, false, AppSupportedLocalesChanged.Status.FAILURE_INVALID_TARGET_PACKAGE,
                result);
    }

    private void resetAppLocaleConfigOverride() throws Exception {
        executeOverrideLocaleConfigCommand(INSTALLED_PACKAGE_NAME, null);
    }

    private void verifyAtomDetails(int expectedCallingUid, int expectedTargetUid,
            int expectedNumLocales, boolean expectedIsOverrideRemoved,
            boolean expectedIsSameAsResConfig, boolean expectedIsSameAsPrevConfig,
            AppSupportedLocalesChanged.Status expectedStatus,
            AppSupportedLocalesChanged result) {
        assertEquals(expectedCallingUid, result.getCallingUid());
        assertEquals(expectedTargetUid, result.getTargetUid());
        assertEquals(expectedNumLocales, result.getNumLocales());
        assertEquals(expectedIsOverrideRemoved, result.getRemoveOverride());
        assertEquals(expectedIsSameAsResConfig, result.getSameAsResourceLocaleconfig());
        assertEquals(expectedIsSameAsPrevConfig, result.getSameAsPreviousLocaleconfig());
        assertEquals(expectedStatus, result.getStatus());
    }

    private void executeOverrideLocaleConfigCommand(String packageName, String locales)
            throws Exception {
        if (locales != null) {
            getDevice().executeShellCommand(
                    String.format(
                            "cmd locale set-app-localeconfig %s --user current --locales %s",
                            packageName,
                            locales
                    )
            );
        } else {
            getDevice().executeShellCommand(
                    String.format(
                            "cmd locale set-app-localeconfig %s --user current --locales",
                            packageName
                    )
            );
        }
    }

    private void invokeActivity(String extra) throws Exception {
        String activity =
                INSTALLED_PACKAGE_NAME + "/." + ACTIVITY_FOR_SETTING_OVERRIDE_LOCALE_CONFIG;
        getDevice().executeShellCommand(
                String.format("am start -W -n %s --es %s %s", activity, OVERRIDE_LOCALE_CONFIG_KEY,
                        extra));
    }
}
