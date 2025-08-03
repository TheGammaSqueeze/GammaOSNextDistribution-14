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

package com.android.keychain;

import android.annotation.Nullable;
import android.security.CredentialManagementApp;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;

import com.android.internal.util.FastXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class KeyChainStateStorage {

    private static final String TAG = "KeyChain";
    private static final String TAG_CREDENTIAL_MANAGEMENT_APP = "credential-management-app";

    private final File mDirectory;

    public KeyChainStateStorage(File directory) {
        mDirectory = directory;
    }

    @Nullable
    public CredentialManagementApp loadCredentialManagementApp() {
        CredentialManagementApp credentialManagementApp = null;
        AtomicFile file = getCredentialManagementFile();
        FileInputStream stream = null;
        try {
            stream = file.openRead();
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, StandardCharsets.UTF_8.name());
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
            }
            String tag = parser.getName();
            if (TAG_CREDENTIAL_MANAGEMENT_APP.equals(tag)) {
                credentialManagementApp = CredentialManagementApp.readFromXml(parser);
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Failed to load state", e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
            }
        }
        return credentialManagementApp;
    }

    public void saveCredentialManagementApp(
            @Nullable CredentialManagementApp credentialManagementApp) {
        AtomicFile file = getCredentialManagementFile();
        FileOutputStream stream;
        try {
            stream = file.startWrite();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write state " + e);
            return;
        }
        try {
            XmlSerializer out = new FastXmlSerializer();
            out.setOutput(stream, StandardCharsets.UTF_8.name());
            out.startDocument(null, true);

            if (credentialManagementApp != null) {
                out.startTag(null, TAG_CREDENTIAL_MANAGEMENT_APP);
                credentialManagementApp.writeToXml(out);
                out.endTag(null, TAG_CREDENTIAL_MANAGEMENT_APP);
            }
            out.endDocument();
            file.finishWrite(stream);
            stream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to store state");
            file.failWrite(stream);
        }
    }

    private AtomicFile getCredentialManagementFile() {
        File file = new File(mDirectory, "credential-management-app.xml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
            }
        }
        return new AtomicFile(file);
    }
}
