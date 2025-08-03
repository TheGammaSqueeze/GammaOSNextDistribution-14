/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.cluster;

import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.LegacySubprocessResultsReporter;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.common.base.Strings;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * A class to build a wrapper configuration file to use subprocess results reporter for a cluster
 * command.
 */
public class SubprocessReportingHelper {
    private static final String REPORTER_JAR_NAME = "subprocess-results-reporter.jar";
    private static final String CLASS_FILTER =
            String.format(
                    "(^%s|^%s|^%s|^%s|^%s|^%s).*class$",
                    "ErrorIdentifier",
                    "LegacySubprocessResultsReporter",
                    "SubprocessTestResultsParser",
                    "SubprocessEventHelper",
                    "SubprocessResultsReporter",
                    "ISupportGranularResults");

    private String mCommandLine;
    private String mClasspath;
    private File mWorkDir;
    private String mPort;

    public SubprocessReportingHelper(
            String commandLine, String classpath, File workDir, String port) {
        mCommandLine = commandLine;
        mClasspath = classpath;
        mWorkDir = workDir;
        mPort = port;
    }

    /**
     * Dynamically generate extract .class file from tradefed.jar and generate new subprocess
     * results reporter jar.
     *
     * @return a subprocess result reporter jar to inject.
     * @throws IOException
     */
    public File buildSubprocessReporterJar() throws IOException {
        // Generate a patched config file.
        final String[] tokens = QuotationAwareTokenizer.tokenizeLine(mCommandLine);
        final String configName = tokens[0];
        final SubprocessConfigBuilder builder = new SubprocessConfigBuilder();
        builder.setWorkingDir(mWorkDir)
                .setOriginalConfig(configName)
                .setClasspath(mClasspath)
                .setPort(mPort);
        final File patchedConfigFile = builder.build();
        LogUtil.CLog.i(
                "Generating new configuration:\n %s",
                FileUtil.readStringFromFile(patchedConfigFile));

        final File reporterJar = new File(mWorkDir, REPORTER_JAR_NAME);
        final File tfJar =
                new File(
                        LegacySubprocessResultsReporter.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .getPath());
        final String ext = FileUtil.getExtension(configName);
        final String configFileName = Strings.isNullOrEmpty(ext) ? configName + ".xml" : configName;
        // tfJar is directory of .class file when running JUnit test from Eclipse IDE
        if (tfJar.isDirectory()) {
            Set<File> classFiles = FileUtil.findFilesObject(tfJar, CLASS_FILTER);
            Manifest manifest = new Manifest();
            createJar(reporterJar, manifest, classFiles, configFileName, patchedConfigFile);
        } else {
            // tfJar is the tradefed.jar when running with tradefed.
            File extractedJar = ZipUtil2.extractZipToTemp(tfJar, "tmp-jar");
            try {
                Set<File> classFiles = FileUtil.findFilesObject(extractedJar, CLASS_FILTER);
                File mf = FileUtil.findFile(extractedJar, "MANIFEST.MF");
                Manifest manifest = new Manifest(new FileInputStream(mf));
                createJar(reporterJar, manifest, classFiles, configFileName, patchedConfigFile);
            } finally {
                FileUtil.recursiveDelete(extractedJar);
            }
        }
        return reporterJar;
    }

    /**
     * Create jar file.
     *
     * @param jar jar file to be created.
     * @param manifest manifest file.
     * @throws IOException
     */
    private void createJar(
            File jar, Manifest manifest, Set<File> classFiles, String configName, File configFile)
            throws IOException {
        try (JarOutputStream jarOutput = new JarOutputStream(new FileOutputStream(jar), manifest)) {
            for (File file : classFiles) {
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    String path = file.getPath();
                    JarEntry entry = new JarEntry(path.substring(path.indexOf("com")));
                    entry.setTime(file.lastModified());
                    jarOutput.putNextEntry(entry);
                    StreamUtil.copyStreams(in, jarOutput);
                    jarOutput.closeEntry();
                }
            }
            try (BufferedInputStream in =
                    new BufferedInputStream(new FileInputStream(configFile))) {
                JarEntry entry = new JarEntry(String.format("config/%s", configName));
                entry.setTime(configFile.lastModified());
                jarOutput.putNextEntry(entry);
                StreamUtil.copyStreams(in, jarOutput);
                jarOutput.closeEntry();
            }
        }
    }
}
