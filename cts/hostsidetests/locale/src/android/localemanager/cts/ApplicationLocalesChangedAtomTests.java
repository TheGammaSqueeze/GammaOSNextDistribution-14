/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.io.FileNotFoundException;
import java.util.List;

public class ApplicationLocalesChangedAtomTests extends DeviceTestCase implements IBuildReceiver {
    public static final String ACTIVITY_FOR_NULL_CHECK_FOR_INPUT_PACKAGE_NAME =
            "ActivityForNullCheckForInputPackageName";
    public static final String ACTIVITY_FOR_SETTING_LOCALES_OF_ANOTHER_APP =
            "ActivityForSettingLocalesOfAnotherApp";
    public static final String ACTIVITY_FOR_NULL_CHECK_FOR_INPUT_LOCALES =
            "ActivityForNullCheckForInputLocales";
    private int mShellUid;

    private static final String INSTALLED_PACKAGE_NAME_APP1 = "android.localemanager.app";
    private static final String INSTALLED_PACKAGE_NAME_APP2 = "android.localemanager.atom.app";
    private static final String INVALID_PACKAGE_NAME = "invalid.package.name";
    private static final String APK_WITH_LOCALECONFIG = "CtsAtomTestAppWithLocaleConfig.apk";
    private static final String APK_WITHOUT_LOCALECONFIG = "CtsAtomTestAppWithoutLocaleConfig.apk";
    private static final String LOCALECONFIG_APP = "android.localemanager.atom.localeconfig.app";
    private static final String DEFAULT_LANGUAGE_TAGS = "hi-IN,de-DE";
    private static final String DEFAULT_LANGUAGE_TAGS_2 = "hi-IN,es-ES";
    private static final String OVERRIDE_LANGUAGE_TAGS = "hi-IN,de-DE,ja-JP";
    private static final String EMPTY_LANGUAGE_TAGS = "";
    private static final String DEFAULT_PREFIX = "default";
    private static final int INVALID_UID = -1;
    private static final int SYSTEM_UID = 1000;
    private boolean mApkInstalled = false;
    private IBuildInfo mCtsBuildInfo;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.APPLICATION_LOCALES_CHANGED_FIELD_NUMBER);

        // This will be ROOT_UID if adb is running as root, SHELL_UID otherwise.
        mShellUid = DeviceUtils.getHostUid(getDevice());
    }

    @Override
    protected void tearDown() throws Exception {
        resetAppLocales();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        if (mApkInstalled) {
            mApkInstalled = false;
            uninstallPackage(LOCALECONFIG_APP);
        }
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuildInfo = buildInfo;
    }

    public void testAtomLogging_localesChangeFromApp_logsAtomSuccessfully()
            throws Exception {
        // executing API to change the per-app locales from the application, this should trigger an
        // ApplicationLocalesChanged atom entry to be logged.
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP1, DEFAULT_LANGUAGE_TAGS,
                "false");

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());
        AtomsProto.ApplicationLocalesChanged result = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(mShellUid,
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                /* expectedPreviousLocales= */ DEFAULT_PREFIX, DEFAULT_LANGUAGE_TAGS,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_APPS, result);

        // executing API to change the per-app locales from the application, this should trigger an
        // ApplicationLocalesChanged atom entry to be logged.
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP1, DEFAULT_LANGUAGE_TAGS_2,
                "false");

        List<StatsLog.EventMetricData> data2 = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data2.size());
        AtomsProto.ApplicationLocalesChanged result2 = data2.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(mShellUid,
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                DEFAULT_LANGUAGE_TAGS, DEFAULT_LANGUAGE_TAGS_2,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_APPS, result2);
    }

    public void testAtomLogging_localesChangeFromDelegate_logsAtomSuccessfully()
            throws Exception {
        // executing API to change the per-app locales from the delegate, this should trigger an
        // ApplicationLocalesChanged atom entry to be logged.
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP1, DEFAULT_LANGUAGE_TAGS,
                "true");

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());
        AtomsProto.ApplicationLocalesChanged result = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(mShellUid,
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                /* expectedPreviousLocales= */ DEFAULT_PREFIX, DEFAULT_LANGUAGE_TAGS,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_DELEGATE, result);

        // executing API to change the per-app locales from the delegate, this should trigger an
        // ApplicationLocalesChanged atom entry to be logged.
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP1, DEFAULT_LANGUAGE_TAGS_2,
                "true");

        List<StatsLog.EventMetricData> data2 = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data2.size());
        AtomsProto.ApplicationLocalesChanged result2 = data2.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(mShellUid,
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                DEFAULT_LANGUAGE_TAGS, DEFAULT_LANGUAGE_TAGS_2,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_DELEGATE, result2);
    }

    public void testAtomLogging_localesChangeFromLocaleConfigOverride_logsAtomSuccessfully()
            throws Exception {
        // executing API to override the supported locales
        executeOverrideLocaleConfigCommand(INSTALLED_PACKAGE_NAME_APP1, OVERRIDE_LANGUAGE_TAGS);
        // executing API to change the per-app locales from the delegate, this should trigger an
        // ApplicationLocalesChanged atom entry to be logged.
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP1, DEFAULT_LANGUAGE_TAGS,
                "true");

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());
        AtomsProto.ApplicationLocalesChanged result = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(mShellUid,
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                /* expectedPreviousLocales= */ DEFAULT_PREFIX, DEFAULT_LANGUAGE_TAGS,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_DELEGATE, result);

        // ececuting API to remove the LocaleConfig override
        executeOverrideLocaleConfigCommand(INSTALLED_PACKAGE_NAME_APP1, null);

        List<StatsLog.EventMetricData> data2 = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data2.size());
        AtomsProto.ApplicationLocalesChanged result2 = data2.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(SYSTEM_UID,
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                DEFAULT_LANGUAGE_TAGS, /* expectedNewLocales= */ DEFAULT_PREFIX,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_DYNAMIC_LOCALES_CHANGE, result2);
    }

    public void testAtomLogging_localesChangeFromAppUpdate_logsAtomSuccessfully()
            throws Exception {
        // Install the APK with including the LocaleConfig file in the resources
        installPackage(APK_WITH_LOCALECONFIG);
        mApkInstalled = true;
        // executing API to change the per-app locales from the application, this should trigger an
        // ApplicationLocalesChanged atom entry to be logged.
        executeSetApplicationLocalesCommand(LOCALECONFIG_APP, DEFAULT_LANGUAGE_TAGS, "true");

        // Retrieving logged metric entries and asserting if they are as expected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());
        AtomsProto.ApplicationLocalesChanged result = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(mShellUid,
                DeviceUtils.getAppUid(getDevice(), LOCALECONFIG_APP),
                /* expectedPreviousLocales= */ DEFAULT_PREFIX, DEFAULT_LANGUAGE_TAGS,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_DELEGATE, result);

        //Update the APK with no LocaleConfig file in the resources
        installPackage(APK_WITHOUT_LOCALECONFIG);
        RunUtil.getDefault().sleep(1000);

        List<StatsLog.EventMetricData> data2 = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data2.size());
        AtomsProto.ApplicationLocalesChanged result2 = data2.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(SYSTEM_UID,
                DeviceUtils.getAppUid(getDevice(), LOCALECONFIG_APP),
                DEFAULT_LANGUAGE_TAGS, /* expectedNewLocales= */ DEFAULT_PREFIX,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_APP_UPDATE_LOCALES_CHANGE,
                result2);
    }

    public void testAtomLogging_invalidPackage_logsAtomWithFailureInvalidPackageName()
            throws Exception {
        // calling setApplicationLocales() with an invalid package name.
        executeSetApplicationLocalesCommand(INVALID_PACKAGE_NAME, DEFAULT_LANGUAGE_TAGS, "false");

        // retrieving logged metric data
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // assert data was logged.
        assertEquals(1, data.size());
        AtomsProto.ApplicationLocalesChanged result = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        // The input package name is invalid therefore the status should be:
        // FAILURE_INVALID_TARGET_PACKAGE
        verifyAtomDetails(mShellUid,
                INVALID_UID, /* expectedPreviousLocales= */ DEFAULT_PREFIX, DEFAULT_LANGUAGE_TAGS,
                AtomsProto.ApplicationLocalesChanged.Status.FAILURE_INVALID_TARGET_PACKAGE,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_APPS, result);
    }

    public void testAtomLogging_permissionAbsent_logsAtomWithFailurePermissionAbsent()
            throws Exception {
        // For the purpose of testing the failure case of "Permission Absent" we required one app
        // (without the CHANGE_CONFIGURATION permission) to call
        // LocaleManager#setApplicationLocales() for another application so that this call fails in
        // a Security Exception. To replicate this scenario, SetApplicationLocales() was called
        // from the MainActivity of app2, attempting to change locales of app1. When
        // app2/MainActivity is invoked this failure test case gets recorded.
        invokeActivityInApp2AndDestroyIt(ACTIVITY_FOR_SETTING_LOCALES_OF_ANOTHER_APP);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());

        AtomsProto.ApplicationLocalesChanged result = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP2),
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                /* expectedPreviousLocales= */ DEFAULT_PREFIX, DEFAULT_LANGUAGE_TAGS,
                AtomsProto.ApplicationLocalesChanged.Status.FAILURE_PERMISSION_ABSENT,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_DELEGATE, result);
    }

    public void testAtomLogging_inputLocalesNull_logsAtomWithFailure()
            throws Exception {
        // For the purpose of testing the failure case of "null locales" we need an application
        // to call setApplicationLocales() with null locales as input. To replicate this
        // scenario, SetApplicationLocales() was called indirectly
        // from the onCreate() of app2.ActivityForNullCheckForInputLocales with null input locales.
        invokeActivityInApp2AndDestroyIt(ACTIVITY_FOR_NULL_CHECK_FOR_INPUT_LOCALES);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());

        AtomsProto.ApplicationLocalesChanged result = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP2),
                INVALID_UID, /* expectedPreviousLocales= */ DEFAULT_PREFIX,
                /* expectedNewLocales= */ DEFAULT_PREFIX,
                AtomsProto.ApplicationLocalesChanged.Status.STATUS_UNSPECIFIED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_UNKNOWN, result);
    }

    public void testAtomLogging_nullPackageName_logsAtomWithFailure()
            throws Exception {
        // For the purpose of testing the failure case of "null PackageName" we need one application
        // to call setApplicationLocales() with null packageName as input. To replicate this
        // scenario, SetApplicationLocales() was called indirectly
        // from the onCreate() of app2.ActivityForNullCheckForInputPackageName with null input
        // package.
        invokeActivityInApp2AndDestroyIt(ACTIVITY_FOR_NULL_CHECK_FOR_INPUT_PACKAGE_NAME);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertEquals(1, data.size());

        AtomsProto.ApplicationLocalesChanged result = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP2),
                INVALID_UID, /* expectedPreviousLocales= */ DEFAULT_PREFIX,
                /* expectedNewLocales= */ DEFAULT_PREFIX,
                AtomsProto.ApplicationLocalesChanged.Status.STATUS_UNSPECIFIED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_UNKNOWN, result);
    }

    public void testAtomLogging_noConfigChange_logsAtomWithConfigUncommitted()
            throws Exception {
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP1, DEFAULT_LANGUAGE_TAGS,
                "false");
        // same command called twice to replicate the case of no commit as previous config is
        // same as current requested.
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP1, DEFAULT_LANGUAGE_TAGS,
                "false");

        // fetching metric data.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // assert: atom was logged twice
        assertEquals(2, data.size());

        // assert: expected config for the first call
        AtomsProto.ApplicationLocalesChanged result1 = data.get(0)
                .getAtom().getApplicationLocalesChanged();
        verifyAtomDetails(mShellUid,
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                /* expectedPreviousLocales= */DEFAULT_PREFIX, DEFAULT_LANGUAGE_TAGS,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_COMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_APPS, result1);

        // assert: expected config for the second call
        AtomsProto.ApplicationLocalesChanged result2 = data.get(1)
                .getAtom().getApplicationLocalesChanged();

        // previous locales are same as new one, therefore status should be: CONFIG_UNCOMMITTED
        verifyAtomDetails(mShellUid,
                DeviceUtils.getAppUid(getDevice(), INSTALLED_PACKAGE_NAME_APP1),
                DEFAULT_LANGUAGE_TAGS, DEFAULT_LANGUAGE_TAGS,
                AtomsProto.ApplicationLocalesChanged.Status.CONFIG_UNCOMMITTED,
                AtomsProto.ApplicationLocalesChanged.Caller.CALLER_APPS, result2);
    }

    private void resetAppLocales() throws Exception {
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP1, EMPTY_LANGUAGE_TAGS,
                "false");
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME_APP2, EMPTY_LANGUAGE_TAGS,
                "false");
    }


    private void executeSetApplicationLocalesCommand(String packageName, String languageTags,
            String fromDelegate) throws Exception {
        getDevice().executeShellCommand(
                String.format(
                        "cmd locale set-app-locales %s --user current --delegate %s --locales %s",
                        packageName,
                        fromDelegate,
                        languageTags
                )
        );
    }

    private void executeOverrideLocaleConfigCommand(String packageName, String languageTags)
            throws Exception {
        if (languageTags != null) {
            getDevice().executeShellCommand(
                    String.format(
                            "cmd locale set-app-localeconfig %s --user current --locales %s",
                            packageName,
                            languageTags
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

    private void executeCommand(String cmd) throws Exception {
        getDevice().executeShellCommand(cmd);
    }

    private void verifyAtomDetails(int expectedCallingUid, int expectedTargetUid,
            String expectedPreviousLocales, String expectedNewLocales,
            AtomsProto.ApplicationLocalesChanged.Status expectedStatus,
            AtomsProto.ApplicationLocalesChanged.Caller expectedCaller,
            AtomsProto.ApplicationLocalesChanged result) {
        assertEquals(expectedCallingUid, result.getCallingUid());
        assertEquals(expectedTargetUid, result.getTargetUid());
        if (expectedPreviousLocales.equals(DEFAULT_PREFIX)) {
            assertTrue(result.getPrevLocales().startsWith(DEFAULT_PREFIX));
        } else {
            assertEquals(expectedPreviousLocales, result.getPrevLocales());
        }
        if (expectedNewLocales.equals(DEFAULT_PREFIX)) {
            assertTrue(result.getNewLocales().startsWith(DEFAULT_PREFIX));
        } else {
            assertEquals(expectedNewLocales, result.getNewLocales());
        }
        assertEquals(expectedStatus, result.getStatus());
        assertEquals(expectedCaller, result.getCaller());
    }

    private void invokeActivityInApp2AndDestroyIt(String activityName) {
        String activity = INSTALLED_PACKAGE_NAME_APP2 + "/." + activityName;
        try {
            // launch the activity
            getDevice().executeShellCommand(
                    String.format(
                            "am start -W %s ",
                            activity
                    )
            );
        } catch (Exception e) {
            // DO nothing.
        }
    }

    private void installPackage(String apk) throws FileNotFoundException,
            DeviceNotAvailableException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuildInfo);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), true));
    }

    private void uninstallPackage(String packageName) throws DeviceNotAvailableException {
        final String result = getDevice().uninstallPackage(packageName);
        assertNull("uninstallPackage(" + packageName + ") failed: " + result, result);
    }
}
