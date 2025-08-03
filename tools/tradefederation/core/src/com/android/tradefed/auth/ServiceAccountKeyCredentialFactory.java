/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tradefed.auth;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** A credential factory to create service account key based oauth {@link Credential}. */
@OptionClass(alias = "service-account-key-credential-factory", global_namespace = false)
public class ServiceAccountKeyCredentialFactory implements ICredentialFactory {

    @Option(name = "json-key-file", description = "Service account json key file.")
    private File mKeyFile;

    private static final String CLIENT_EMAIL_KEY = "client_email";
    private static final String TYPE_KEY = "type";
    private static final String PRIVATE_KEY_ID_KEY = "private_key_id";
    private static final String ERROR_KEY = "error";
    private Map<String, String> mInfo = null;

    /** {@inheritDoc} */
    @Override
    public Credential createCredential(Collection<String> scopes) throws IOException {
        try {
            GoogleCredential credential =
                    GoogleCredential.fromStream(
                                    new FileInputStream(mKeyFile),
                                    GoogleNetHttpTransport.newTrustedTransport(),
                                    GsonFactory.getDefaultInstance())
                            .createScoped(scopes);
            return credential;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getInfo() {
        if (mInfo == null) {
            mInfo = parseKeyFile(mKeyFile);
        }
        return new HashMap<>(mInfo);
    }

    /**
     * Parse service account key json file to get information about the service account key.
     *
     * @param keyFile
     * @return
     */
    Map<String, String> parseKeyFile(File keyFile) {
        Map<String, String> info = new HashMap<>();
        try {
            String keyContent = FileUtil.readStringFromFile(keyFile);
            JSONObject jsonObject = new JSONObject(keyContent);
            info.put(CLIENT_EMAIL_KEY, jsonObject.getString(CLIENT_EMAIL_KEY));
            info.put(TYPE_KEY, jsonObject.getString(TYPE_KEY));
            info.put(PRIVATE_KEY_ID_KEY, jsonObject.getString(PRIVATE_KEY_ID_KEY));
        } catch (IOException | JSONException e) {
            CLog.e("Failed to read %s:", keyFile.getAbsolutePath());
            CLog.e(e);
            info.put(ERROR_KEY, e.getMessage());
        }
        return info;
    }
}
