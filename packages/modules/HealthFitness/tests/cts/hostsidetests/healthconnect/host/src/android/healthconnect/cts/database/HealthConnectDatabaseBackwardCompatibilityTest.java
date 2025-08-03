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

package android.healthconnect.cts.database;

import static android.healthconnect.cts.database.DatabaseTestUtils.assertInstallSucceeds;
import static android.healthconnect.cts.database.DatabaseTestUtils.checkColumnModification;
import static android.healthconnect.cts.database.DatabaseTestUtils.checkExistingTableDeletion;
import static android.healthconnect.cts.database.DatabaseTestUtils.checkForeignKeyModification;
import static android.healthconnect.cts.database.DatabaseTestUtils.checkIndexModification;
import static android.healthconnect.cts.database.DatabaseTestUtils.checkNewTableAddition;
import static android.healthconnect.cts.database.DatabaseTestUtils.checkPrimaryKeyModification;
import static android.healthconnect.cts.database.DatabaseTestUtils.deleteHcDatabase;
import static android.healthconnect.cts.database.DatabaseTestUtils.getCurrentHcDatabaseVersion;
import static android.healthconnect.cts.database.DatabaseTestUtils.isFilePresentInResources;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * HealthConnectDatabaseBackwardCompatibilityTest contains test cases that ensures backward
 * compatibility of the HealthConnect Database.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HealthConnectDatabaseBackwardCompatibilityTest extends BaseHostJUnit4Test {
    public static final String APEX_FILE_PREFIX = "HealthConnectVersion_";
    public static final String APEX_FILE_SUFFIX = ".apex";
    public static String sCurrentVersionFile;
    public static String sPreviousVersionFile;
    public static HashMap<String, TableInfo> sPreviousVersionSchema = new HashMap<>();
    public static HashMap<String, TableInfo> sCurrentVersionSchema = new HashMap<>();

    @Before
    /** Initial setUp to get the Schema Hashmaps of current and previous versions. */
    public void setUp() throws Exception {
        /** check for device availability. */
        ITestDevice device = getDevice();
        assertThat(device).isNotNull();
        Assume.assumeTrue(rebootAndEnableRoot());

        /**
         * Getting the current version of HealthConnect database and setting the current and
         * previous version file names.
         */
        int currentVersion = getCurrentHcDatabaseVersion(device);
        setVersionFileName(currentVersion);
    }

    public void setVersionFileName(int currentVersion) {
        sCurrentVersionFile = APEX_FILE_PREFIX + currentVersion + APEX_FILE_SUFFIX;
        sPreviousVersionFile = APEX_FILE_PREFIX + (currentVersion - 1) + APEX_FILE_SUFFIX;
    }

    /**
     * @return schema of the version that is installed on device.
     */
    public static String getSchema(ITestDevice device) throws DeviceNotAvailableException {
        return device.executeShellCommand(
                "cd ~; sqlite3 data/system_ce/0/healthconnect/healthconnect.db" + " \".schema\"");
    }

    private boolean rebootAndEnableRoot() throws DeviceNotAvailableException {
        getDevice().reboot();
        getDevice().waitForDeviceAvailable();
        /** Enable root for device. */
        if (!getDevice().isAdbRoot()) {
            return getDevice().enableAdbRoot();
        }
        return true;
    }

    /** test to check the backward compatibility of database versions. */
    @Test
    public void checkBackwardCompatibility() throws Exception {
        /** Checking for the availability of both version files in resources/ */
        Assume.assumeTrue(
                isFilePresentInResources(sCurrentVersionFile)
                        && isFilePresentInResources(sPreviousVersionFile));

        /** Getting the schema for current version and populating its hashmap. */
        String currentVersionSchema = getSchema(getDevice());
        HealthConnectDatabaseSchema currentSchema =
                new HealthConnectDatabaseSchema(currentVersionSchema);
        sCurrentVersionSchema = currentSchema.getTableInfo();

        /** Deleting the current version database from device. */
        deleteHcDatabase(getDevice());

        /** Previous version installation */
        assertInstallSucceeds(getDevice(), sPreviousVersionFile, true);
        rebootAndEnableRoot();

        /** Getting the schema for previous version and populating its hashmap. */
        String previousVersionSchema = getSchema(getDevice());
        HealthConnectDatabaseSchema previousSchema =
                new HealthConnectDatabaseSchema(previousVersionSchema);
        sPreviousVersionSchema = previousSchema.getTableInfo();

        List<String> incompatibleChanges = new ArrayList<>();

        checkTableDeletion(incompatibleChanges);
        checkColumnModifications(incompatibleChanges);
        checkIndexModifications(incompatibleChanges);
        checkPrimaryKeyModifications(incompatibleChanges);
        checkForeignKeyModifications(incompatibleChanges);
        checkNewTableModification(incompatibleChanges);

        Assert.assertTrue(
                "Changes made to the database are backward incompatible"
                        + "\n"
                        + incompatibleChanges,
                incompatibleChanges.isEmpty());
    }

    /** Checks for the deletion of existing tables. */
    public void checkTableDeletion(List<String> incompatibleChanges) {

        List<String> backwardIncompatibleChangeList = new ArrayList<>();
        checkExistingTableDeletion(
                sPreviousVersionSchema, sCurrentVersionSchema, backwardIncompatibleChangeList);

        if (!backwardIncompatibleChangeList.isEmpty()) {
            incompatibleChanges.add(
                    "Deletion of existing table is not allowed"
                            + "\n"
                            + backwardIncompatibleChangeList
                            + "\n");
        }
    }

    /** Checks for the modification in columns of existing tables. */
    public void checkColumnModifications(List<String> incompatibleChanges) {

        List<String> backwardIncompatibleChangeList = new ArrayList<>();
        checkColumnModification(
                sPreviousVersionSchema, sCurrentVersionSchema, backwardIncompatibleChangeList);

        if (!backwardIncompatibleChangeList.isEmpty()) {
            incompatibleChanges.add(
                    "Changes made to the columns are backward incompatible"
                            + "\n"
                            + backwardIncompatibleChangeList
                            + "\n");
        }
    }

    /** Checks for the modifications in the indexes of existing tables. */
    public void checkIndexModifications(List<String> incompatibleChanges) {

        List<String> backwardIncompatibleChangeList = new ArrayList<>();
        checkIndexModification(
                sPreviousVersionSchema, sCurrentVersionSchema, backwardIncompatibleChangeList);

        if (!backwardIncompatibleChangeList.isEmpty()) {
            incompatibleChanges.add(
                    "Changes made to the indexes are backward incompatible"
                            + "\n"
                            + backwardIncompatibleChangeList
                            + "\n");
        }
    }

    /** Checks for the modification in primary key columns of existing tables. */
    public void checkPrimaryKeyModifications(List<String> incompatibleChanges) {

        List<String> backwardIncompatibleChangeList = new ArrayList<>();
        checkPrimaryKeyModification(
                sPreviousVersionSchema, sCurrentVersionSchema, backwardIncompatibleChangeList);

        if (!backwardIncompatibleChangeList.isEmpty()) {
            incompatibleChanges.add(
                    "Changes made to the primary keys are backward incompatible"
                            + "\n"
                            + backwardIncompatibleChangeList
                            + "\n");
        }
    }

    /** Checks for the modifications in the foreign keys of existing tables. */
    public void checkForeignKeyModifications(List<String> incompatibleChanges) {

        List<String> backwardIncompatibleChangeList = new ArrayList<>();
        checkForeignKeyModification(
                sPreviousVersionSchema, sCurrentVersionSchema, backwardIncompatibleChangeList);

        if (!backwardIncompatibleChangeList.isEmpty()) {
            incompatibleChanges.add(
                    "Changes made to the foreign keys are backward incompatible"
                            + "\n"
                            + backwardIncompatibleChangeList
                            + "\n");
        }
    }

    /** Checks for the backward incompatible actions in new tables. */
    public void checkNewTableModification(List<String> incompatibleChanges) {

        List<String> backwardIncompatibleChangeList = new ArrayList<>();
        checkNewTableAddition(
                sPreviousVersionSchema, sCurrentVersionSchema, backwardIncompatibleChangeList);

        if (!backwardIncompatibleChangeList.isEmpty()) {
            incompatibleChanges.add(
                    "Foreign keys of the new table are backward incompatible"
                            + "\n"
                            + backwardIncompatibleChangeList
                            + "\n");
        }
    }

    @After
    public void tearDown() throws Exception {

        if (sCurrentVersionFile != null && isFilePresentInResources(sCurrentVersionFile)) {
            /** Deleting the previous version database from device. */
            deleteHcDatabase(getDevice());

            /** Current version re-installation */
            assertInstallSucceeds(getDevice(), sCurrentVersionFile, true);
            rebootAndEnableRoot();
        }
        getDevice().disableAdbRoot();
    }
}
