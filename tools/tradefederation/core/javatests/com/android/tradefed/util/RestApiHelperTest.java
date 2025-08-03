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

import static org.junit.Assert.assertEquals;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;

import java.util.Collections;

/** Unit tests for {@link RestApiHelper}. */
@RunWith(JUnit4.class)
public class RestApiHelperTest {
    private static final String BASE_URI = "https://www.googleapis.com/test/";

    private RestApiHelper mHelper = null;

    @Before
    public void setUp() throws Exception {

        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
        mHelper = new RestApiHelper(requestFactory, BASE_URI);
    }

    @After
    public void tearDown() throws Exception {}

    @Test
    public void testBuildQueryUri() {
        String[] uriParts = {"add", "new", "fox"};
        GenericUrl uri = new GenericUrl(BASE_URI + "add/new/fox");

        assertEquals(uri, mHelper.buildQueryUri(uriParts, Collections.<String, Object>emptyMap()));
    }
}
