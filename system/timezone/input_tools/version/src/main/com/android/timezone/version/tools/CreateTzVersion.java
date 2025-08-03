/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.timezone.version.tools;

import com.android.i18n.timezone.TzDataSetVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * A command-line tool for creating a time zone data set version file.
 *
 * <p>Args:
 * <dl>
 *     <dt>input properties file</dt>
 * </dl>
 *
 * <p>The input properties file must have the entries:
 * <dl>
 *     <dt>rules.version</dt>
 *     <dd>The IANA rules version.</dd>
 *     <dt>revision</dt>
 *     <dd>IANA data revision (typically 1).</dd>
 *     <dt>output.version.file</dt>
 *     <dd>The location to write the version file to.</dd>
 * </dl>
 *
 * <p>The output consists of:
 * <ul>
 *     <li>A version file.</li>
 * </ul>
 */
public class CreateTzVersion {

    private CreateTzVersion() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            printUsage();
            System.exit(1);
        }
        File f = new File(args[0]);
        if (!f.exists()) {
            System.err.println("Properties file " + f + " not found");
            printUsage();
            System.exit(2);
        }
        Properties properties = loadProperties(f);
        String ianaRulesVersion = getMandatoryProperty(properties, "rules.version");
        int revision = Integer.parseInt(getMandatoryProperty(properties, "revision"));

        // Create an object to hold version metadata for the tz data.
        TzDataSetVersion tzDataSetVersion = new TzDataSetVersion(
                TzDataSetVersion.currentFormatMajorVersion(),
                TzDataSetVersion.currentFormatMinorVersion(),
                ianaRulesVersion,
                revision);
        byte[] tzDataSetVersionBytes = tzDataSetVersion.toBytes();

        File outputVersionFile = new File(getMandatoryProperty(properties, "output.version.file"));

        // Write the tz data set version file.
        try (OutputStream os = new FileOutputStream(outputVersionFile)) {
            os.write(tzDataSetVersionBytes);
        }
        System.out.println("Wrote " + outputVersionFile);
    }

    private static String getMandatoryProperty(Properties p, String propertyName) {
        String value = p.getProperty(propertyName);
        if (value == null) {
            System.out.println("Missing property: " + propertyName);
            printUsage();
            System.exit(3);
        }
        return value;
    }

    private static Properties loadProperties(File f) throws IOException {
        Properties p = new Properties();
        try (Reader reader = new InputStreamReader(new FileInputStream(f))) {
            p.load(reader);
        }
        return p;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("\t" + CreateTzVersion.class.getName() +
                " <tzupdate.properties file>");
    }
}
