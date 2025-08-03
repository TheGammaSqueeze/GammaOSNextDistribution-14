/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.presubmit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.compatibility.common.tradefed.targetprep.FilePusher;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.PushFilePreparer;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.FileUtil;

import com.google.common.base.Joiner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to validate tests Apks in testcases/
 */
@RunWith(JUnit4.class)
public class ApkPackageNameCheck {

    private static final Set<String> EXCEPTION_LIST = new HashSet<>();
    static {
        // TODO: Remove exception when their package have been fixed.
        EXCEPTION_LIST.add("android.app.cts");
        EXCEPTION_LIST.add("android.content.cts");
        // TODO(b/290634145): Refactor WM CTS into fully isolated modules
        EXCEPTION_LIST.add("android.server.wm.cts");
        EXCEPTION_LIST.add("android.systemui.cts");
    }

    /**
     * We ensure that no apk with same package names may be installed. Otherwise it may results in
     * conflicts.
     */
    @Test
    public void testApkPackageNames() throws Exception {
        String ctsRoot = System.getProperty("CTS_ROOT");
        File testcases = new File(ctsRoot, "/android-cts/testcases/");
        if (!testcases.exists()) {
            fail(String.format("%s does not exists", testcases));
            return;
        }
        Set<File> listConfigs = FileUtil.findFilesObject(testcases, ".*\\.config");
        assertTrue(listConfigs.size() > 0);
        // We check all apk installed by all modules
        Map<String, String> packageNames = new HashMap<>();

        List<String> errors = new ArrayList<>();

        for (File config : listConfigs) {
            IConfiguration c = ConfigurationFactory.getInstance()
                    .createConfigurationFromArgs(new String[] {config.getAbsolutePath()});
            // For each config, we check all the apk it's going to install
            List<File> apkNames = new ArrayList<>();
            List<String> packageListNames = new ArrayList<>();
            for (IDeviceConfiguration dConfig : c.getDeviceConfig()) {
                for (ITargetPreparer prep : dConfig.getTargetPreparers()) {
                    if (prep instanceof TestAppInstallSetup) {
                        apkNames.addAll(((TestAppInstallSetup) prep).getTestsFileName());
                    }
                    // Ensure the files requested to be pushed exist.
                    if (prep instanceof FilePusher && ((FilePusher) prep).shouldAppendBitness()) {
                        if (!((FilePusher) prep).shouldAbortOnFailure()) {
                            errors.add(String.format("Config %s should not disable abort-on-push-failure", config.getName()));
                        }
                        for (File f : ((PushFilePreparer) prep).getPushSpecs(null).values()) {
                            String path = f.getPath();
                            File file32 = FileUtil.findFile(config.getParentFile(), path + "32");
                            File file64 = FileUtil.findFile(config.getParentFile(), path + "64");
                            if (file32 == null || file64 == null) {
                                errors.add(
                                        String.format(
                                                "File %s[32/64] wasn't found in module "
                                                        + "dependencies while it's expected to "
                                                        + "be pushed as part of %s. Make sure "
                                                        + "that it's added in the Android.bp "
                                                        + "file of the module under "
                                                        + "'data_device_bins_both' field.",
                                                        path, config.getName()));
                                continue;
                            }
                        }
                    } else if (prep instanceof PushFilePreparer) {
                        if (!((PushFilePreparer) prep).shouldAbortOnFailure()) {
                            errors.add(String.format("Config %s should not disable abort-on-push-failure", config.getName()));
                        }
                        for (File f : ((PushFilePreparer) prep).getPushSpecs(null).values()) {
                            String path = f.getPath();
                            // Use findFiles to also match top-level dir, which is a valid push spec
                            Set<String> toBePushed = FileUtil.findFiles(config.getParentFile(),
                                                                        path);
                            if (toBePushed.isEmpty()) {
                                errors.add(
                                        String.format(
                                                "File %s wasn't found in module dependencies "
                                                        + "while it's expected to be pushed "
                                                        + "as part of %s. Make sure that it's added in the Android.bp file of the module under 'data' field.",
                                                        path, config.getName()));
                                continue;
                            }
                        }
                    }
                }

                // All apks need to be in the config dir or sub-dir
                for (File apk : apkNames) {
                    String apkName = apk.getName();
                    File apkFile = FileUtil.findFile(config.getParentFile(), apkName);
                    if (apkFile == null || !apkFile.exists()) {
                        errors.add(
                                String.format("Module %s is trying to install %s which does not "
                                                + "exists in testcases/. Make sure that it's added "
                                                + "in the Android.bp file of the module under "
                                                + "'data' field.", config.getName(), apkName));
                        continue;
                    }
                    AaptParser res = AaptParser.parse(apkFile);
                    assertNotNull(res);
                    String packageName = res.getPackageName();
                    String put = packageNames.put(packageName, apkName);
                    packageListNames.add(packageName);
                    // The package already exists and it's a different apk
                    if (put != null && !apkName.equals(put) && !EXCEPTION_LIST.contains(packageName)) {
                        errors.add(
                                String.format("Module %s: Package name '%s' from apk '%s' was "
                                                + "already added by previous apk '%s'.",
                                        config.getName(), packageName, apkName, put));
                        continue;
                    }
                }
            }
            // Catch a test trying to run something it doesn't install.
            List<IRemoteTest> tests = c.getTests();
            for (IRemoteTest test : tests) {
                if (test instanceof InstrumentationTest) {
                    InstrumentationTest instrumentationTest = (InstrumentationTest) test;
                    if (instrumentationTest.getPackageName() != null) {
                        if (!packageListNames.contains(instrumentationTest.getPackageName())) {
                            errors.add(
                                    String.format("Module %s requests to run '%s' but it's not "
                                        + "part of any apks.",
                                        config.getName(), instrumentationTest.getPackageName()));
                            continue;
                        }
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ConfigurationException(Joiner.on("\n").join(errors));
        }
    }
}
