/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wifi;

import android.util.Log;
import android.util.Xml;

import com.android.internal.util.FastXmlSerializer;
import com.android.server.wifi.util.XmlUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Class used to backup/restore data.
 */
public class BackupRestoreController {
    private static final String TAG = "BackupRestoreController";

    private final WifiSettingsBackupRestore mWifiSettingsBackupRestore;
    private final Clock mClock;

    private static final String XML_TAG_DOCUMENT_HEADER = "WifiBackupDataUnion";
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    /**
     * Verbose logging flag.
     */
    private boolean mVerboseLoggingEnabled = false;
    /**
     * Store the dump of the backup/restore data for debugging. This is only stored when verbose
     * logging is enabled.
     */
    private byte[] mDebugLastBackupDataRetrieved;
    private long mLastBackupDataRetrievedTimestamp = 0;
    private byte[] mDebugLastBackupDataRestored;
    private long mLastBackupDataRestoredTimestamp = 0;

    public BackupRestoreController(WifiSettingsBackupRestore wifiSettingsBackupRestore,
            Clock clock) {
        mWifiSettingsBackupRestore = wifiSettingsBackupRestore;
        mClock = clock;
    }

    /**
     * Retrieve an XML byte stream representing the data that needs to be backed up.
     *
     * @return Raw byte stream of XML that needs to be backed up.
     */
    public byte[] retrieveBackupData() {
        try {
            final XmlSerializer out = new FastXmlSerializer();
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            out.setOutput(outputStream, StandardCharsets.UTF_8.name());
            XmlUtil.writeDocumentStart(out, XML_TAG_DOCUMENT_HEADER);

            mWifiSettingsBackupRestore.retrieveBackupDataFromSettingsConfigStore(out, outputStream);

            XmlUtil.writeDocumentEnd(out, XML_TAG_DOCUMENT_HEADER);
            byte[] backupData = outputStream.toByteArray();
            if (mVerboseLoggingEnabled) {
                mDebugLastBackupDataRetrieved = backupData;
            }
            mLastBackupDataRetrievedTimestamp = mClock.getWallClockMillis();
            return backupData;
        } catch (IOException e) {
            Log.e(TAG, "Error retrieving the backup data: " + e);
        }
        return new byte[0];
    }

    private void distpatchBackupData(String sectionName, XmlPullParser in, int depth)
            throws XmlPullParserException, IOException {
        switch (sectionName) {
            case WifiSettingsBackupRestore.XML_TAG_SECTION_HEADER_WIFI_SETTINGS_DATA:
                mWifiSettingsBackupRestore.restoreSettingsFromBackupData(in, depth);
                break;
            default:
                Log.i(TAG, "unknown tag: (backed up from newer version?)" + sectionName);
        }
    }

    /**
     * Split the back up data to retrieve each back up session.
     *
     * @param data raw byte stream representing the XML data.
     */
    public void parserBackupDataAndDispatch(byte[] data) {
        if (data == null || data.length == 0) {
            Log.e(TAG, "Invalid backup data received");
            return;
        }
        if (mVerboseLoggingEnabled) {
            mDebugLastBackupDataRestored = data;
        }
        try {
            final XmlPullParser in = Xml.newPullParser();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            in.setInput(inputStream, StandardCharsets.UTF_8.name());
            // Start parsing the XML stream.
            XmlUtil.gotoDocumentStart(in, XML_TAG_DOCUMENT_HEADER);
            int sectionDepth = in.getDepth();
            String[] sectionName = new String[1];
            while (XmlUtil.gotoNextSectionOrEnd(in, sectionName, sectionDepth)) {
                try {
                    if (sectionName[0] == null) {
                        throw new XmlPullParserException("Missing value name");
                    }
                    distpatchBackupData(sectionName[0], in, sectionDepth);
                } catch (XmlPullParserException | IOException ex) {
                    Log.e(TAG, "Error to parser tag: " + sectionName[0]);
                }
            }
            mLastBackupDataRestoredTimestamp = mClock.getWallClockMillis();
        } catch (XmlPullParserException | IOException ex) {
            Log.e(TAG, "Error :" + ex);
        }

    }

    /**
     * Enable verbose logging.
     *
     * @param verboseEnabled whether or not verbosity log level is enabled.
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    /**
     * Dump out the last backup/restore data if verbose logging is enabled.
     *
     * @param fd   unused
     * @param pw   PrintWriter for writing dump to
     * @param args unused
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of " + TAG);
        if (mDebugLastBackupDataRetrieved != null) {
            pw.println("Last backup data retrieved: "
                    + createLogFromBackupData(mDebugLastBackupDataRetrieved));

        }
        pw.println("mLastBackupDataRetrievedTimestamp: "
                + (mLastBackupDataRetrievedTimestamp != 0
                ? FORMATTER.format(new Date(mLastBackupDataRetrievedTimestamp)) : "N/A"));
        if (mDebugLastBackupDataRestored != null) {
            pw.println("Last backup data restored: "
                    + createLogFromBackupData(mDebugLastBackupDataRestored));
        }
        pw.println("mLastBackupDataRestoredTimestamp: "
                + (mLastBackupDataRestoredTimestamp != 0
                ? FORMATTER.format(new Date(mLastBackupDataRestoredTimestamp)) : "N/A"));
    }

    private String createLogFromBackupData(byte[] data) {
        if (data != null) {
            StringBuilder sb = new StringBuilder();
            try {
                String xmlString = new String(data, StandardCharsets.UTF_8.name());
                for (String line : xmlString.split("\n")) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "fail to create log from backup data. " + e);
            }
        }
        return "";
    }
}
