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

import static org.junit.Assert.assertEquals;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.Map;

/** Unit tests for {@link ServiceAccountKeyCredentialFactory}. */
@RunWith(JUnit4.class)
public class ServiceAccountKeyCredentialFactoryTest {

    private static final String KEY =
            "{\"type\": \"service_account\",\n"
                    + "\"project_id\": \"aproject\",\n"
                    + "\"private_key_id\": \"0a123456789\",\n"
                    + "\"private_key\": \"aprivatekey\",\n"
                    + "\"client_email\": \"sa@sa.com\",\n"
                    + "\"client_id\": \"12345\",\n"
                    + "\"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n"
                    + "\"token_uri\": \"https://oauth2.googleapis.com/token\",\n"
                    + "\"auth_provider_x509_cert_url\":"
                    + " \"https://www.googleapis.com/oauth2/v1/certs\",\n"
                    + "\"client_x509_cert_url\":"
                    + " \"https://www.googleapis.com/robot/v1/metadata/x509/as%40sa.com\"\n"
                    + "}";

    private File mKeyFile;
    private ServiceAccountKeyCredentialFactory mFactory;
    private OptionSetter mOptionSetter;

    @Before
    public void setUp() throws Exception {
        mKeyFile = FileUtil.createTempFile("test_key", "json");
        mFactory = new ServiceAccountKeyCredentialFactory();
        mOptionSetter = new OptionSetter(mFactory);
        FileUtil.writeToFile(KEY, mKeyFile);
        mOptionSetter.setOptionValue(
                "service-account-key-credential-factory:json-key-file", mKeyFile.getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mKeyFile);
    }

    /** Test getInfo will parse the service account key correctly. */
    @Test
    public void testGetInfo() {
        Map<String, String> info = mFactory.getInfo();
        assertEquals("sa@sa.com", info.get("client_email"));
        assertEquals("service_account", info.get("type"));
        assertEquals("0a123456789", info.get("private_key_id"));
    }

    /** Test getInfo when there is a error. */
    @Test
    public void testGetInfo_error() throws Exception {
        mOptionSetter.setOptionValue(
                "service-account-key-credential-factory:json-key-file", "invalid.json");
        Map<String, String> info = mFactory.getInfo();
        assertEquals("invalid.json (No such file or directory)", info.get("error"));
    }
}
