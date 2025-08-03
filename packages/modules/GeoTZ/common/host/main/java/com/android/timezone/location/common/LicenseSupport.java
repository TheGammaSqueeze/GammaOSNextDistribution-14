/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.timezone.location.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;

/** Utilities to help with LICENSE files and associated headers. */
public class LicenseSupport {

    /**
     * The standard header to be put in files generated from OpenStreetMap data obtained from
     * https://opendatacommons.org/licenses/odbl/ on 2020-08-04.
     */
    private static final String TEXT_PROTO_ODBL_LICENSE_HEADER = ""
            + "# This time zone geo data is made available under the Open Database License:\n"
            + "# http://opendatacommons.org/licenses/odbl/1.0/.\n"
            + "# Any rights in individual contents of the database are licensed under the Database"
            + " Contents License:\n"
            + "# http://opendatacommons.org/licenses/dbcl/1.0/\n"
            + "\n";
    private static final String ODBL_LICENSE_SNIPPET = "Open Database License (ODbL) v1.0";

    public static final String LICENSE_FILE_NAME = "LICENSE";

    /** Individual licenses. */
    public enum License {
        ODBL(ODBL_LICENSE_SNIPPET, TEXT_PROTO_ODBL_LICENSE_HEADER);

        private final String mTextProtoHeader;

        private final String mLicenseFileSnippet;

        License(String licenseFileSnippet, String textProtoHeader) {
            this.mLicenseFileSnippet = Objects.requireNonNull(licenseFileSnippet);
            this.mTextProtoHeader = Objects.requireNonNull(textProtoHeader);
        }

        /** Returns the text proto file header for this license type. */
        public String getTextProtoHeader() {
            return mTextProtoHeader;
        }

        /**
         * Confirms a LICENSE file is present and contains a snippet of the expected license, or
         * throws an exception.
         */
        public void checkLicensePresentInDir(File dir) throws IOException {
            checkIsDir(dir);
            File licenseFile = new File(dir, LICENSE_FILE_NAME);
            checkIsFile(licenseFile);
            checkFileContains(licenseFile, mLicenseFileSnippet);
        }
    }

    private LicenseSupport() {
    }

    /**
     * Copies the LICENSE file from {@code inputDir} to {@code outputDir} as needed. If the file
     * already exists in the {@code outputDir}, it is checked to see if it is the same.
     */
    public static void copyLicenseFile(File inputDir, File outputDir) throws IOException {
        checkIsDir(inputDir);
        checkIsDir(outputDir);

        File licenseFileInput = new File(inputDir, LICENSE_FILE_NAME);
        if (!licenseFileInput.exists()) {
            throw new IllegalArgumentException(licenseFileInput + " does not exist");
        }
        File licenseOutputFile = new File(outputDir, LICENSE_FILE_NAME);
        if (licenseOutputFile.exists()) {
            System.out.println(licenseOutputFile + " already exists: checking content");
            // Just do a basic check for equality.
            checkFilesIdentical(licenseFileInput, licenseOutputFile);
        } else {
            System.out.println(
                    "Copying LICENSE from " + licenseFileInput + " to " + licenseOutputFile);
            Files.copy(licenseFileInput.toPath(), licenseOutputFile.toPath());
        }
    }

    private static void checkFilesIdentical(File one, File two) throws IOException {
        try (FileInputStream oneInput = new FileInputStream(one);
                FileInputStream twoInput = new FileInputStream(two)) {

            byte[] oneBytes = oneInput.readAllBytes();
            byte[] twoBytes = twoInput.readAllBytes();
            if (!Arrays.equals(oneBytes, twoBytes)) {
                throw new IllegalArgumentException("File " + one + " is not the same as " + two);
            }
        }
    }

    private static void checkFileContains(File file, String snippet) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(snippet)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException(file + " does not contain " + snippet);
    }

    private static void checkIsFile(File file) {
        if (!file.isFile()) {
            throw new IllegalArgumentException(file + " is not a file.");
        }
    }

    private static void checkIsDir(File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir + " is not a directory.");
        }
    }
}
