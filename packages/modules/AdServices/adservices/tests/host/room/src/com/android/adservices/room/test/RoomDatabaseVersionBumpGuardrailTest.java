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

package com.android.adservices.room.test;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tradefed.config.Option;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.FileUtil;


import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Asserts Room database version get bumped up between M-trains.
 *
 * <p>Since the Room json schema file only live in the UnitTest package, we need to download and
 * read the json from the zip.
 *
 * <p>The test will compare the highest version between mainline branch and M-train release build.
 *
 * <p>If M-train build contains a certain DB, then:
 *
 * <ol>
 *   <li>The DB must present in the new version;
 *   <li>If highest version in the new version is the same, the schema file should stay same; and
 *   <li>Database version should never go down.
 * </ol>
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class RoomDatabaseVersionBumpGuardrailTest extends BaseHostJUnit4Test {

    // This is not a perfect matcher as it could be fragile of file with other naming strategy.
    private static final Pattern SCHEMA_FILE_PATTERN =
            Pattern.compile("^assets/com\\.android\\..*/\\d*\\.json$");
    public static final String AD_SERVICES_SERVICE_CORE_UNIT_TESTS_APK_FILE_NAME =
            "AdServicesServiceCoreUnitTests.apk";
    protected final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    // The schema lib should be configured in gcl to point to the right package.
    @Option(name = "base-schema-lib")
    protected String mBaseSchemaLib;

    @Option(name = "new-schema-lib")
    protected String mNewSchemaLib;

    @Option(name = "schema-apk-name")
    protected String mSchemaApkName = AD_SERVICES_SERVICE_CORE_UNIT_TESTS_APK_FILE_NAME;

    @Test
    public void roomDatabaseVersionBumpGuardrailTest() throws Exception {
        ZipFile baseSchemas = getTestPackageFromFile(mBaseSchemaLib);
        ZipFile newSchemas = getTestPackageFromFile(mNewSchemaLib);

        StringBuilder errors = new StringBuilder();
        Map<String, ZipEntry> newSchemasByFileName =
                newSchemas.stream()
                        .filter(f -> SCHEMA_FILE_PATTERN.matcher(f.getName()).matches())
                        .collect(Collectors.toMap(ZipEntry::getName, e -> e));

        for (ZipEntry baseFile :
                baseSchemas.stream()
                        .filter(f -> SCHEMA_FILE_PATTERN.matcher(f.getName()).matches())
                        .collect(Collectors.toList())) {
            ZipEntry newFile = newSchemasByFileName.get(baseFile.getName());
            if (newFile == null) {
                errors.append(
                        String.format(
                                "Database json file %s is removed in the new version. Please add"
                                        + " back.\n",
                                baseFile.getName()));
                continue;
            }
            if (!Arrays.equals(
                    baseSchemas.getInputStream(baseFile).readAllBytes(),
                    newSchemas.getInputStream(newFile).readAllBytes())) {
                errors.append(
                        String.format(
                                "Database json file '%s' changed between major build. Please revert"
                                        + " change and/or bump up DB version.\n",
                                baseFile.getName()));
            }
        }

        if (errors.length() != 0) {
            throw new IllegalStateException(errors.toString());
        }
    }

    private ZipFile getTestPackageFromFile(String lib) throws IOException {
        File testPackage = mHostUtils.getTestFile(lib);
        return new ZipFile(FileUtil.findFile(testPackage, mSchemaApkName));
    }
}
