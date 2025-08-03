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

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DatabaseTestUtils {
    public static final String INSTALL_ARG_FORCE_QUERYABLE = "--force-queryable";

    public static final String HC_APEX_RESOURCE_PATH_PREFIX = "/HealthConnectApexFiles/";
    public static final String HC_CTS_RESOURCE_PATH_PREFIX = "/HealthConnectCtsApkFiles/";
    public static final String TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    public static int getCurrentHcDatabaseVersion(ITestDevice device)
            throws DeviceNotAvailableException {
        String currentDbVersion =
                device.executeShellCommand(
                        "cd ~; sqlite3 data/system_ce/0/healthconnect/healthconnect.db"
                                + " \"PRAGMA user_version;\"");
        // To remove any extra white spaces on the sides.
        currentDbVersion = currentDbVersion.strip();
        LogUtil.CLog.d("Current Database version  " + currentDbVersion);
        return Integer.parseInt(currentDbVersion);
    }

    public static void deleteHcDatabase(ITestDevice device) throws DeviceNotAvailableException {
        String result =
                device.executeShellCommand(
                        "cd ~; rm /data/system_ce/0/healthconnect/healthconnect.db;");
        // Deleted successfully.
        if (!result.isBlank()) {
            throw new IllegalArgumentException("Failed to remove healthconnect.db : " + result);
        }
    }

    /**
     * Installs package using the packageFilename in Resources.
     *
     * <p>Since this method can be used for both HC apex files and CTS apk files, pass true in
     * {@code isHcApex} to notify that the resource to be installed is an apex otherwise pass false.
     */
    public static void assertInstallSucceeds(
            ITestDevice device, String packageFilenameInResources, boolean isHcApex)
            throws Exception {
        String installResult =
                installPackageFromResource(device, packageFilenameInResources, isHcApex);
        if (installResult != null) {
            throw new IllegalArgumentException(
                    "Failed to install " + packageFilenameInResources + ": " + installResult);
        }
    }

    public static void assertUninstallSucceeds(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        String uninstallResult = device.uninstallPackage(packageName);
        if (uninstallResult != null) {
            throw new IllegalArgumentException(
                    "Failed to install " + uninstallResult + ": " + uninstallResult);
        }
    }

    /** Fetches the package from resources and installs it for the current user. */
    public static String installPackageFromResource(
            ITestDevice device, String apkFilenameInResources, boolean isHcApex)
            throws IOException {
        // ITestDevice.installPackage API requires the APK to be installed to be a File. We thus
        // copy the requested resource into a temporary file, attempt to install it, and delete the
        // file during cleanup.
        File apkFile = null;
        try {
            apkFile = getFileFromResource(apkFilenameInResources, isHcApex);
            // Install package for current user.
            return device.installPackageForUser(
                    apkFile, true, device.getCurrentUser(), INSTALL_ARG_FORCE_QUERYABLE);
        } catch (DeviceNotAvailableException e) {
            throw new RemoteException("Device is not available, please connect a device.", e);
        } finally {
            cleanUpFile(apkFile);
        }
    }

    public static File getFileFromResource(String filenameInResources, boolean isHcApex)
            throws IOException, IllegalArgumentException {
        final String fullResourceName;
        if (isHcApex) {
            fullResourceName = HC_APEX_RESOURCE_PATH_PREFIX + filenameInResources;
        } else {
            fullResourceName = HC_CTS_RESOURCE_PATH_PREFIX + filenameInResources;
        }
        File tempDir = FileUtil.createTempDir("HcHostSideTests");
        File file = new File(tempDir, filenameInResources);
        InputStream in = DatabaseTestUtils.class.getResourceAsStream(fullResourceName);
        if (in == null) {
            throw new IllegalArgumentException("Resource not found: " + fullResourceName);
        }
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buf = new byte[65536];
        int chunkSize;
        while ((chunkSize = in.read(buf)) != -1) {
            out.write(buf, 0, chunkSize);
        }
        out.close();
        return file;
    }

    static void cleanUpFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    /**
     * Run a device side test.
     *
     * @param pkgName Test package name, such as "android.healthconnect.cts".
     * @param testClassName Test class name; either a fully qualified name, or "." + a class name.
     * @param testMethodName Test method name.
     */
    public static String runDeviceTests(
            ITestDevice device, String pkgName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }

        RemoteAndroidTestRunner testRunner =
                new RemoteAndroidTestRunner(pkgName, TEST_RUNNER, device.getIDevice());
        testRunner.setMaxTimeout(1800, TimeUnit.SECONDS);
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        CollectingTestListener listener = new CollectingTestListener();
        assert (device.runInstrumentationTests(testRunner, listener));

        final TestRunResult result = listener.getCurrentRunResults();
        if (result.isRunFailure()) {
            throw new AssertionError(
                    "Failed to successfully run device tests for "
                            + result.getName()
                            + ": "
                            + result.getRunFailureMessage());
        }
        if (result.getNumTests() == 0) {
            throw new AssertionError("No tests were run on the device");
        }

        if (result.hasFailedTests()) {
            // build a meaningful error message
            StringBuilder errorBuilder = new StringBuilder("On-device tests failed:\n");
            for (Map.Entry<TestDescription, TestResult> resultEntry :
                    result.getTestResults().entrySet()) {
                if (!resultEntry
                        .getValue()
                        .getStatus()
                        .equals(com.android.ddmlib.testrunner.TestResult.TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }
        return result.getTextSummary();
    }

    /**
     * Checks the deletion of tables of the previous version of the database in the current version
     * of the database.
     */
    public static void checkExistingTableDeletion(
            HashMap<String, TableInfo> mTableListPreviousVersion,
            HashMap<String, TableInfo> mTableListCurrentVersion,
            List<String> deletionOfTable) {

        for (String tableName : mTableListPreviousVersion.keySet()) {

            if (!mTableListCurrentVersion.containsKey(tableName)) {
                deletionOfTable.add("Table: " + tableName + " has been deleted from the database");
            }
        }
    }

    /**
     * Checks for the modifications in the primary keys of the database between previous and current
     * version.
     */
    public static void checkPrimaryKeyModification(
            HashMap<String, TableInfo> mTableListPreviousVersion,
            HashMap<String, TableInfo> mTableListCurrentVersion,
            List<String> modificationOfPrimaryKey) {

        for (String tableName : mTableListPreviousVersion.keySet()) {

            if (mTableListCurrentVersion.containsKey(tableName)) {

                List<String> primaryKeyPreviousVersion =
                        mTableListPreviousVersion.get(tableName).getPrimaryKey();
                List<String> primaryKeyCurrentVersion =
                        mTableListCurrentVersion.get(tableName).getPrimaryKey();

                for (String pk : primaryKeyPreviousVersion) {
                    if (!primaryKeyCurrentVersion.contains(pk)) {
                        modificationOfPrimaryKey.add(
                                "Primary key column: "
                                        + pk
                                        + " has been deleted from the table: "
                                        + tableName);
                    }
                }

                for (String pk : primaryKeyCurrentVersion) {
                    if (!primaryKeyPreviousVersion.contains(pk)) {
                        modificationOfPrimaryKey.add(
                                "Primary key column: "
                                        + pk
                                        + " has been added to the table: "
                                        + tableName);
                    }
                }
            }
        }
    }

    /**
     * Checks for the modifications in the columns of each table of the database between previous
     * and current version.
     */
    public static void checkColumnModification(
            HashMap<String, TableInfo> mTableListPreviousVersion,
            HashMap<String, TableInfo> mTableListCurrentVersion,
            List<String> modificationOfColumn) {

        for (String tableName : mTableListPreviousVersion.keySet()) {

            if (mTableListCurrentVersion.containsKey(tableName)) {

                HashMap<String, ColumnInfo> columnInfoPreviousVersion =
                        mTableListPreviousVersion.get(tableName).getColumnInfoMapping();
                HashMap<String, ColumnInfo> columnInfoCurrentVersion =
                        mTableListCurrentVersion.get(tableName).getColumnInfoMapping();

                for (String columnName : columnInfoPreviousVersion.keySet()) {
                    ColumnInfo column1 = columnInfoPreviousVersion.get(columnName);

                    if (columnInfoCurrentVersion.containsKey(columnName)) {
                        ColumnInfo column2 = columnInfoCurrentVersion.get(columnName);
                        column1.checkColumnDiff(column2, modificationOfColumn, tableName);
                    } else {
                        modificationOfColumn.add(
                                "Column: "
                                        + columnName
                                        + " has been deleted from the table: "
                                        + tableName);
                    }
                }

                for (String columnName : columnInfoCurrentVersion.keySet()) {

                    if (!columnInfoPreviousVersion.containsKey(columnName)) {
                        ColumnInfo columnInfo = columnInfoCurrentVersion.get(columnName);

                        if (columnInfo.getConstraints().contains(ColumnInfo.UNIQUE_CONSTRAINT)) {
                            modificationOfColumn.add(
                                    "UNIQUE constraint is not allowed for the new column: "
                                            + columnName
                                            + " of table: "
                                            + tableName);
                        }

                        if (columnInfo.getConstraints().contains(ColumnInfo.NOT_NULL_CONSTRAINT)) {
                            modificationOfColumn.add(
                                    "NOT NULL constraint is not allowed for the new column: "
                                            + columnName
                                            + " of table: "
                                            + tableName);
                        }

                        if (columnInfo
                                .getConstraints()
                                .contains(ColumnInfo.AUTO_INCREMENT_CONSTRAINT)) {
                            modificationOfColumn.add(
                                    "AUTOINCREMENT constraint is not allowed for the new column: "
                                            + columnName
                                            + " of table: "
                                            + tableName);
                        }

                        if (!columnInfo.getCheckConstraints().isEmpty()) {
                            modificationOfColumn.add(
                                    "Check constraints are not allowed for the new column: "
                                            + columnName
                                            + " of table: "
                                            + tableName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks for the modifications in the foreign keys of each table of the database between
     * previous and current version.
     */
    public static void checkForeignKeyModification(
            HashMap<String, TableInfo> mTableListPreviousVersion,
            HashMap<String, TableInfo> mTableListCurrentVersion,
            List<String> modificationOfForeignKey) {

        for (String tableName : mTableListPreviousVersion.keySet()) {

            if (mTableListCurrentVersion.containsKey(tableName)) {

                HashMap<String, ForeignKeyInfo> foreignKeyListPreviousVersion =
                        mTableListPreviousVersion.get(tableName).getForeignKeyMapping();
                HashMap<String, ForeignKeyInfo> foreignKeyListCurrentVersion =
                        mTableListCurrentVersion.get(tableName).getForeignKeyMapping();

                for (String foreignKeyName : foreignKeyListPreviousVersion.keySet()) {

                    if (foreignKeyListCurrentVersion.containsKey(foreignKeyName)) {

                        ForeignKeyInfo foreignInfo1 =
                                foreignKeyListPreviousVersion.get(foreignKeyName);
                        ForeignKeyInfo foreignInfo2 =
                                foreignKeyListCurrentVersion.get(foreignKeyName);

                        foreignInfo1.checkForeignKeyDiff(
                                foreignInfo2, modificationOfForeignKey, tableName);
                    } else {
                        modificationOfForeignKey.add(
                                "Foreign Key: "
                                        + foreignKeyName
                                        + " has been deleted from the table: "
                                        + tableName);
                    }
                }

                for (String foreignKeyName : foreignKeyListCurrentVersion.keySet()) {

                    if (!foreignKeyListPreviousVersion.containsKey(foreignKeyName)) {

                        ForeignKeyInfo foreignKeyInfo =
                                foreignKeyListCurrentVersion.get(foreignKeyName);
                        String referTableName = foreignKeyInfo.getForeignKeyTableName();
                        List<Integer> constraintListOfReferencedColumn =
                                mTableListCurrentVersion
                                        .get(referTableName)
                                        .getColumnInfoMapping()
                                        .get(foreignKeyInfo.getForeignKeyReferredColumnName())
                                        .getConstraints();
                        if (!mTableListCurrentVersion
                                        .get(referTableName)
                                        .getPrimaryKey()
                                        .contains(foreignKeyInfo.getForeignKeyReferredColumnName())
                                && !constraintListOfReferencedColumn.contains(
                                        ColumnInfo.UNIQUE_CONSTRAINT)) {
                            modificationOfForeignKey.add(
                                    "New Foreign key : "
                                            + foreignKeyName
                                            + " of  table: "
                                            + tableName
                                            + " has neither been made on primary key of "
                                            + "referenced table: "
                                            + referTableName
                                            + " nor UNIQUE constraint ");
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks for the modifications in the indexes of each table of the database between previous
     * and current version.
     */
    public static void checkIndexModification(
            HashMap<String, TableInfo> mTableListPreviousVersion,
            HashMap<String, TableInfo> mTableListCurrentVersion,
            List<String> modificationOfIndex) {

        for (String tableName : mTableListPreviousVersion.keySet()) {

            if (mTableListCurrentVersion.containsKey(tableName)) {

                HashMap<String, IndexInfo> indexInfoPreviousVersion =
                        mTableListPreviousVersion.get(tableName).getIndexInfoMapping();
                HashMap<String, IndexInfo> indexInfoCurrentVersion =
                        mTableListCurrentVersion.get(tableName).getIndexInfoMapping();

                for (String indexName : indexInfoPreviousVersion.keySet()) {
                    IndexInfo index1 = indexInfoPreviousVersion.get(indexName);

                    if (indexInfoCurrentVersion.containsKey(indexName)) {

                        IndexInfo index2 = indexInfoCurrentVersion.get(indexName);
                        index1.checkIndexDiff(index2, modificationOfIndex, tableName);
                    } else {
                        modificationOfIndex.add(
                                "Index : "
                                        + indexName
                                        + " has been deleted from table "
                                        + tableName);
                    }
                }
            }
        }
    }

    /**
     * Checks for the addition of new tables in the current version of the database.
     *
     * <p>The only way by which a new table can interact with older ones is with the help of foreign
     * key So, we need a check to ensure that the table to which the foreign key is being mapped is
     * primary key of that table.
     */
    public static void checkNewTableAddition(
            HashMap<String, TableInfo> mTableListPreviousVersion,
            HashMap<String, TableInfo> mTableListCurrentVersion,
            List<String> additionOfTable) {

        for (String tableName : mTableListCurrentVersion.keySet()) {

            if (!mTableListPreviousVersion.containsKey(tableName)) {

                HashMap<String, ForeignKeyInfo> foreignKeyList =
                        mTableListCurrentVersion.get(tableName).getForeignKeyMapping();

                for (String foreignKeyName : foreignKeyList.keySet()) {

                    ForeignKeyInfo foreignKeyInfo = foreignKeyList.get(foreignKeyName);
                    String referTableName = foreignKeyInfo.getForeignKeyTableName();
                    List<Integer> constraintListOfReferencedColumn =
                            mTableListCurrentVersion
                                    .get(referTableName)
                                    .getColumnInfoMapping()
                                    .get(foreignKeyInfo.getForeignKeyReferredColumnName())
                                    .getConstraints();
                    /**
                     * Checking whether the column to which foreign key has been mapped is primary
                     * key of the referenced table or not.
                     */
                    if (!mTableListCurrentVersion
                                    .get(referTableName)
                                    .getPrimaryKey()
                                    .contains(foreignKeyInfo.getForeignKeyReferredColumnName())
                            && !constraintListOfReferencedColumn.contains(
                                    ColumnInfo.UNIQUE_CONSTRAINT)) {
                        additionOfTable.add(
                                "Foreign key : "
                                        + foreignKeyName
                                        + " of new table: "
                                        + tableName
                                        + " has neither been made on primary key of referenced"
                                        + " table: "
                                        + referTableName
                                        + " nor UNIQUE constraint ");
                    }
                }
            }
        }
    }

    /**
     * @return true if apex version file is present in resources otherwise false.
     */
    public static boolean isFilePresentInResources(String filenameInResources) {
        final String fullResourceName = HC_APEX_RESOURCE_PATH_PREFIX + filenameInResources;
        InputStream in = DatabaseTestUtils.class.getResourceAsStream(fullResourceName);
        return in != null;
    }
}

