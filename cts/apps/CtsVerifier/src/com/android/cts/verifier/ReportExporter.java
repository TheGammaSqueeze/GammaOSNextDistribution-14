/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier;

import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.compatibility.common.util.FileUtil;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.ResultHandler;
import com.android.compatibility.common.util.ScreenshotsMetadataHandler;
import com.android.compatibility.common.util.ZipUtil;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Background task to generate a report and save it to external storage. */
public class ReportExporter extends AsyncTask<Void, Void, String> {
    private static final String TAG = ReportExporter.class.getSimpleName();
    private static final boolean DEBUG = true;

    public static final String REPORT_DIRECTORY = "VerifierReports";
    public static final String LOGS_DIRECTORY = "ReportLogFiles";

    private static final Logger LOG = Logger.getLogger(ReportExporter.class.getName());
    private static final String COMMAND_LINE_ARGS = "";
    private static final String LOG_URL = null;
    private static final String REFERENCE_URL = null;
    private static final String SUITE_NAME_METADATA_KEY = "SuiteName";
    // Default CTS-V suite_plan shown in test_result.xml.
    private static final String DEFAULT_SUITE_PLAN = "verifier";
    // CTS Verifier System suite_plan shown in test_result.xml.
    private static final String SYSTEM_SUITE_PLAN = "verifier-system";
    private static final String SUITE_BUILD = "0";
    private static final String ZIP_EXTENSION = ".zip";
    private final long START_MS = System.currentTimeMillis();
    private final long END_MS = START_MS;
    private final Context mContext;
    private final TestListAdapter mAdapter;

    ReportExporter(Context context, TestListAdapter adapter) {
        this.mContext = context;
        this.mAdapter = adapter;
    }

    //
    // Copy any ReportLog files created by XTS-Verifier tests into the temp report directory
    // so that they will get ZIPped into the transmitted file.
    //
    private void copyReportFiles(File tempDir) {
        if (DEBUG) {
            Log.d(TAG, "copyReportFiles(" + tempDir.getAbsolutePath() + ")");
        }

        File reportLogFolder =
                new File(
                        Environment.getExternalStorageDirectory().getAbsolutePath()
                                + File.separator
                                + LOGS_DIRECTORY);

        copyFilesRecursively(reportLogFolder, tempDir);
    }

    private void copyFilesRecursively(File source, File destFolder) {
        File[] files = source.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            Path src = Paths.get(file.getAbsolutePath());
            Path dest = Paths.get(destFolder.getAbsolutePath() + File.separator + file.getName());
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Error copying ReportLog file. IOException: " + ex);
            }
            if (file.isDirectory()) {
                copyFilesRecursively(file, dest.toFile());
            }
        }
    }

    @Override
    protected String doInBackground(Void... params) {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            LOG.log(Level.WARNING, "External storage is not writable.");
            return mContext.getString(R.string.no_storage);
        }
        IInvocationResult result;
        try {
            TestResultsReport report = new TestResultsReport(mContext, mAdapter);
            result = report.generateResult();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't create test results report", e);
            return mContext.getString(R.string.test_results_error);
        }
        // create a directory for XTS Verifier reports
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        File verifierReportsDir = new File(externalStorageDirectory, REPORT_DIRECTORY);
        verifierReportsDir.mkdirs();

        String suiteName = Version.getMetadata(mContext, SUITE_NAME_METADATA_KEY);
        // create a temporary directory for this particular report
        File tempDir = new File(verifierReportsDir, getReportName(suiteName));
        tempDir.mkdirs();

        // Pull in any ReportLogs
        copyReportFiles(tempDir);

        // create a File object for a report ZIP file
        File reportZipFile = new File(verifierReportsDir, getReportName(suiteName) + ZIP_EXTENSION);

        try {
            // Serialize the report
            String versionName = Version.getVersionName(mContext);
            ResultHandler.writeResults(
                    suiteName,
                    versionName,
                    TestListActivity.getIsSystemEnabled() ? SYSTEM_SUITE_PLAN : DEFAULT_SUITE_PLAN,
                    SUITE_BUILD,
                    result,
                    tempDir,
                    START_MS,
                    END_MS,
                    REFERENCE_URL,
                    LOG_URL,
                    COMMAND_LINE_ARGS,
                    null);

            // Serialize the screenshots metadata if at least one exists
            if (containsScreenshotMetadata(result)) {
                ScreenshotsMetadataHandler.writeResults(result, tempDir);
            }

            // copy formatting files to the temporary report directory
            copyFormattingFiles(tempDir);

            // create a compressed ZIP file containing the temporary report directory
            ZipUtil.createZip(tempDir, reportZipFile);
        } catch (IOException | XmlPullParserException e) {
            LOG.log(Level.WARNING, "I/O exception writing report to storage.", e);
            return mContext.getString(R.string.no_storage_io_parser_exception);
        } finally {
            // delete the temporary directory and its files made for the report
            FileUtil.recursiveDelete(tempDir);
        }
        saveReportOnInternalStorage(reportZipFile);
        return mContext.getString(R.string.report_saved, reportZipFile.getPath());
    }

    private boolean containsScreenshotMetadata(IInvocationResult result) {
        for (IModuleResult module : result.getModules()) {
            for (ICaseResult cr : module.getResults()) {
                for (ITestResult r : cr.getResults()) {
                    if (r.getResultStatus() == null) {
                        continue; // test was not executed, don't report
                    }
                    if (r.getTestScreenshotsMetadata() != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void saveReportOnInternalStorage(File reportZipFile) {
        if (DEBUG) {
            Log.d(TAG, "---- saveReportOnInternalStorage(" + reportZipFile.getAbsolutePath() + ")");
        }
        try {
            ParcelFileDescriptor pfd =
                    ParcelFileDescriptor.open(reportZipFile, ParcelFileDescriptor.MODE_READ_ONLY);
            InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pfd);

            File verifierDir = mContext.getDir(REPORT_DIRECTORY, Context.MODE_PRIVATE);
            File verifierReport = new File(verifierDir, reportZipFile.getName());
            FileOutputStream fos = new FileOutputStream(verifierReport);

            FileUtils.copy(is, fos);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "I/O exception writing report to internal storage.", e);
        }
    }

    /**
     * Copy the XML formatting files stored in the assets directory to the result output.
     *
     * @param resultsDir
     */
    private void copyFormattingFiles(File resultsDir) {
        for (String resultFileName : ResultHandler.RESULT_RESOURCES) {
            InputStream rawStream = null;
            try {
                rawStream = mContext.getAssets().open(String.format("report/%s", resultFileName));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load " + resultFileName + " from assets.");
            }
            if (rawStream != null) {
                File resultFile = new File(resultsDir, resultFileName);
                try {
                    FileUtil.writeToFile(rawStream, resultFile);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to write " + resultFileName + " to a file.");
                }
            }
        }
    }

    private String getReportName(String suiteName) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.ENGLISH);
        String date = dateFormat.format(new Date());
        return String.format(
                "%s-%s-%s-%s-%s-%s",
                date, suiteName, Build.MANUFACTURER, Build.PRODUCT, Build.DEVICE, Build.ID);
    }

    @Override
    protected void onPostExecute(String result) {
        new AlertDialog.Builder(mContext)
                .setMessage(result)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
