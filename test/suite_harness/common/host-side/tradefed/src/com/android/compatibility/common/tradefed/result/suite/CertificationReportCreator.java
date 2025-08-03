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
package com.android.compatibility.common.tradefed.result.suite;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.ResultUploader;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IDisableable;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/** Package all the results into the zip and allow to upload it. */
@OptionClass(alias = "result-reporter")
public class CertificationReportCreator
        implements ILogSaverListener, ITestSummaryListener, IConfigurationReceiver, IDisableable {

    public static final String HTLM_REPORT_NAME = "test_result.html";
    public static final String REPORT_XSL_FILE_NAME = "compatibility_result.xsl";
    public static final String FAILURE_REPORT_NAME = "test_result_failures_suite.html";
    public static final String FAILURE_XSL_FILE_NAME = "compatibility_failures.xsl";

    public static final String INCLUDE_HTML_IN_ZIP = "html-in-zip";

    @Option(name = "disable", description = "Whether or not to disable this reporter.")
    private boolean mDisable = false;

    @Option(
            name = INCLUDE_HTML_IN_ZIP,
            description = "Whether failure summary report is included in the zip fie.")
    private boolean mIncludeHtml = false;

    @Option(name = "result-server", description = "Server to publish test results.")
    private String mResultServer;

    @Option(
            name = "disable-result-posting",
            description = "Disable result posting into report server.")
    private boolean mDisableResultPosting = false;

    @Option(name = "use-log-saver", description = "Also saves generated result with log saver")
    private boolean mUseLogSaver = false;

    /** Invocation level Log saver to receive when files are logged */
    private ILogSaver mLogSaver;
    private IConfiguration mConfiguration;

    private CompatibilityBuildHelper mBuildHelper;

    private String mReferenceUrl;

    private File mReportFile;

    /** {@inheritDoc} */
    @Override
    public void setLogSaver(ILogSaver saver) {
        mLogSaver = saver;
    }

    /** {@inheritDoc} */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        for (TestSummary summary : summaries) {
            if (mReferenceUrl == null && summary.getSummary().getString() != null) {
                mReferenceUrl = summary.getSummary().getString();
            }
        }
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    private IConfiguration getConfiguration() {
        return mConfiguration;
    }

    @Override
    public void invocationStarted(IInvocationContext context) {
        if (mBuildHelper == null) {
            mBuildHelper = new CompatibilityBuildHelper(context.getBuildInfos().get(0));
        }
    }

    public void setReportFile(File reportFile) {
        mReportFile = reportFile;
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        if (mReportFile == null) {
            CLog.w("Did not receive the report file to be packaged");
            return;
        }
        File resultDir;
        try {
            resultDir = mBuildHelper.getResultDir();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        File report = null;
        File failureReport = null;
        if (mIncludeHtml) {
            // Create the html reports before the zip file.
            report = createReport(mReportFile);
            failureReport = createFailureReport(mReportFile);
        }
        File zippedResults = zipResults(resultDir);
        if (!mIncludeHtml) {
            // Create html reports after zip file so extra data is not uploaded
            report = createReport(mReportFile);
            failureReport = createFailureReport(mReportFile);
        }
        if (report != null) {
            CLog.i("Viewable report: %s", report.getAbsolutePath());
        }
        try {
            if (failureReport.exists()) {
                CLog.i("Test Result: %s", failureReport.getCanonicalPath());
            } else {
                CLog.i("Test Result: %s", mReportFile.getCanonicalPath());
            }

            saveLog(mReportFile, zippedResults);
        } catch (IOException e) {
            CLog.e("Error when handling the post processing of results file:");
            CLog.e(e);
        }

        uploadResult(mReportFile);
    }

    /**
     * Zip the contents of the given results directory. CTS specific.
     *
     * @param resultsDir
     */
    private static File zipResults(File resultsDir) {
        File zipResultFile = null;
        try {
            // create a file in parent directory, with same name as resultsDir
            zipResultFile =
                    new File(resultsDir.getParent(), String.format("%s.zip", resultsDir.getName()));
            ZipUtil.createZip(resultsDir, zipResultFile);
        } catch (IOException e) {
            CLog.w("Failed to create zip for %s", resultsDir.getName());
        }
        return zipResultFile;
    }

    /** When enabled, upload the result to a server. CTS specific. */
    private void uploadResult(File resultFile) {
        if (mResultServer != null && !mResultServer.trim().isEmpty() && !mDisableResultPosting) {
            ResultUploader uploader =
                    new ResultUploader(mResultServer, mBuildHelper.getSuiteName());
            try {
                CLog.d("Result Server: %d", uploader.uploadResult(resultFile, mReferenceUrl));
            } catch (IOException ioe) {
                CLog.e("IOException while uploading result.");
                CLog.e(ioe);
            }
        }
    }

    /** When enabled, save log data using log saver */
    private void saveLog(File resultFile, File zippedResults) throws IOException {
        if (!mUseLogSaver) {
            return;
        }

        FileInputStream fis = null;
        LogFile logFile = null;
        try {
            fis = new FileInputStream(resultFile);
            logFile = mLogSaver.saveLogData("log-result", LogDataType.XML, fis);
            CLog.d("Result XML URL: %s", logFile.getUrl());
            logReportFiles(getConfiguration(), resultFile, resultFile.getName(),
                     LogDataType.XML);
        } catch (IOException ioe) {
            CLog.e("error saving XML with log saver");
            CLog.e(ioe);
        } finally {
            StreamUtil.close(fis);
        }
        // Save the full results folder.
        if (zippedResults != null) {
            FileInputStream zipResultStream = null;
            try {
                zipResultStream = new FileInputStream(zippedResults);
                logFile = mLogSaver.saveLogData("results", LogDataType.ZIP, zipResultStream);
                CLog.d("Result zip URL: %s", logFile.getUrl());
                logReportFiles(getConfiguration(), zippedResults, "results",
                        LogDataType.ZIP);
            } finally {
                StreamUtil.close(zipResultStream);
            }
        }
    }

    /** Generate html report. */
    private File createReport(File inputXml) {
        File report = new File(inputXml.getParentFile(), HTLM_REPORT_NAME);
        try (InputStream xslStream =
                        new FileInputStream(
                                new File(inputXml.getParentFile(), REPORT_XSL_FILE_NAME));
                OutputStream outputStream = new FileOutputStream(report)) {
            Transformer transformer =
                    TransformerFactory.newInstance().newTransformer(new StreamSource(xslStream));
            transformer.transform(new StreamSource(inputXml), new StreamResult(outputStream));
        } catch (IOException | TransformerException ignored) {
            CLog.e(ignored);
            FileUtil.deleteFile(report);
            return null;
        }
        return report;
    }

    /** Generate html report listing an failed tests. CTS specific. */
    private File createFailureReport(File inputXml) {
        File failureReport = new File(inputXml.getParentFile(), FAILURE_REPORT_NAME);
        try (InputStream xslStream =
                        CertificationReportCreator.class.getResourceAsStream(
                                String.format("/report/%s", FAILURE_XSL_FILE_NAME));
                OutputStream outputStream = new FileOutputStream(failureReport)) {

            Transformer transformer =
                    TransformerFactory.newInstance().newTransformer(new StreamSource(xslStream));
            transformer.transform(new StreamSource(inputXml), new StreamResult(outputStream));
        } catch (IOException | TransformerException ignored) {
            CLog.e(ignored);
        }
        return failureReport;
    }

    /** Re-log a result file to all reporters so they are aware of it. */
    private void logReportFiles(
            IConfiguration configuration, File resultFile, String dataName, LogDataType type) {
        if (configuration == null) {
            return;
        }
        ILogSaver saver = configuration.getLogSaver();
        List<ITestInvocationListener> listeners = configuration.getTestInvocationListeners();
        try (FileInputStreamSource source = new FileInputStreamSource(resultFile)) {
            LogFile loggedFile = null;
            try (InputStream stream = source.createInputStream()) {
                loggedFile = saver.saveLogData(dataName, type, stream);
            } catch (IOException e) {
                CLog.e(e);
            }
            for (ITestInvocationListener listener : listeners) {
                if (listener.equals(this)) {
                    // Avoid logging against itself
                    continue;
                }
                listener.testLog(dataName, type, source);
                if (loggedFile != null) {
                    if (listener instanceof ILogSaverListener) {
                        ((ILogSaverListener) listener).logAssociation(dataName, loggedFile);
                    }
                }
            }
        }
    }

    @Override
    public boolean isDisabled() {
        return mDisable;
    }
}
