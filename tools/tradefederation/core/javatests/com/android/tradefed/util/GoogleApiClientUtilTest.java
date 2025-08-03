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

package com.android.tradefed.util;

import com.android.tradefed.auth.ICredentialFactory;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.google.api.client.auth.oauth2.Credential;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Unit test for {@link GoogleApiClientUtil}. */
@RunWith(JUnit4.class)
public class GoogleApiClientUtilTest {

    private static final Collection<String> SCOPES = Collections.singleton("ascope");
    private static final String HOST_OPTION_JSON_KEY = "host-option-json-key";
    private File mRoot;
    private StubGoogleApiClientUtil mUtil;
    private File mKey;
    private File mOldValue;
    boolean mCredentialFactoryUsed = false;

    static class StubGoogleApiClientUtil extends GoogleApiClientUtil {

        List<File> mKeyFiles = new ArrayList<>();
        boolean mDefaultCredentialUsed = false;

        @Override
        Credential doCreateCredentialFromJsonKeyFile(File file, Collection<String> scopes)
                throws IOException, GeneralSecurityException {
            mKeyFiles.add(file);
            return Mockito.mock(Credential.class);
        }

        @Override
        Credential doCreateDefaultCredential(Collection<String> scopes) throws IOException {
            mDefaultCredentialUsed = true;
            return Mockito.mock(Credential.class);
        }
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        try {
            GlobalConfiguration.getInstance();
        } catch (IllegalStateException e) {
            GlobalConfiguration.createGlobalConfiguration(new String[] {});
        }
    }

    @Before
    public void setUp() throws Exception {
        mUtil = new StubGoogleApiClientUtil();
        mRoot = FileUtil.createTempDir(GoogleApiClientUtilTest.class.getName());
        mKey = new File(mRoot, "key.json");
        FileUtil.writeToFile("key", mKey);
        mOldValue =
                GlobalConfiguration.getInstance()
                        .getHostOptions()
                        .getServiceAccountJsonKeyFiles()
                        .get(HOST_OPTION_JSON_KEY);
        GlobalConfiguration.getInstance()
                .setConfigurationObject(
                        GlobalConfiguration.CREDENTIAL_FACTORY_TYPE_NAME,
                        new ICredentialFactory() {
                            @Override
                            public Credential createCredential(Collection<String> scopes)
                                    throws IOException {
                                mCredentialFactoryUsed = true;
                                return Mockito.mock(Credential.class);
                            }

                            @Override
                            public Map<String, String> getInfo() {
                                return null;
                            }
                        });
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mRoot);
        if (mOldValue != null) {
            GlobalConfiguration.getInstance()
                    .getHostOptions()
                    .getServiceAccountJsonKeyFiles()
                    .put(HOST_OPTION_JSON_KEY, mOldValue);
        }
    }

    @Test
    public void testCreateCredential() throws Exception {
        Credential credential = mUtil.doCreateCredential(SCOPES, mKey, null);
        Assert.assertNotNull(credential);
        Assert.assertEquals(1, mUtil.mKeyFiles.size());
        Assert.assertEquals(mKey, mUtil.mKeyFiles.get(0));
        Assert.assertFalse(mUtil.mDefaultCredentialUsed);
        Assert.assertFalse(mCredentialFactoryUsed);
    }

    @Test
    public void testCreateCredential_useHostOptions() throws Exception {
        OptionSetter optionSetter =
                new OptionSetter(GlobalConfiguration.getInstance().getHostOptions());
        optionSetter.setOptionValue(
                "host_options:service-account-json-key-file",
                HOST_OPTION_JSON_KEY,
                mKey.getAbsolutePath());
        Credential credential = mUtil.doCreateCredential(SCOPES, null, HOST_OPTION_JSON_KEY);
        Assert.assertNotNull(credential);
        Assert.assertEquals(1, mUtil.mKeyFiles.size());
        Assert.assertEquals(mKey, mUtil.mKeyFiles.get(0));
        Assert.assertFalse(mUtil.mDefaultCredentialUsed);
        Assert.assertFalse(mCredentialFactoryUsed);
    }

    @Test
    public void testCreateCredential_useFallbackKeyFile() throws Exception {
        Credential credential = mUtil.doCreateCredential(SCOPES, null, "not-exist-key", mKey);
        Assert.assertNotNull(credential);
        Assert.assertEquals(1, mUtil.mKeyFiles.size());
        Assert.assertEquals(mKey, mUtil.mKeyFiles.get(0));
        Assert.assertFalse(mUtil.mDefaultCredentialUsed);
        Assert.assertFalse(mCredentialFactoryUsed);
    }

    @Test
    public void testCreateCredential_useDefault() throws Exception {
        Credential credential = mUtil.doCreateCredential(SCOPES, null, "not-exist-key");
        Assert.assertNotNull(credential);
        Assert.assertEquals(0, mUtil.mKeyFiles.size());
        Assert.assertTrue(mUtil.mDefaultCredentialUsed);
        Assert.assertFalse(mCredentialFactoryUsed);
    }

    @Test
    public void testCreateCredential_useCredentialFactory() throws Exception {
        Credential credential = mUtil.doCreateCredentialFromCredentialFactory(SCOPES);
        Assert.assertNotNull(credential);
        Assert.assertEquals(0, mUtil.mKeyFiles.size());
        Assert.assertFalse(mUtil.mDefaultCredentialUsed);
        Assert.assertTrue(mCredentialFactoryUsed);
    }
}
