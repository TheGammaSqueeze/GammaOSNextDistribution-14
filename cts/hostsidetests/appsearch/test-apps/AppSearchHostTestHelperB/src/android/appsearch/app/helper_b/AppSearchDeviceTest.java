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

package android.appsearch.app.helper_b;

import static android.app.appsearch.testutil.AppSearchTestUtils.convertSearchResultsToDocuments;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppSearchDeviceTest {

    private static final String NAMESPACE = "namespace";
    private static final String ID = "id";
    private static final GenericDocument DOCUMENT =
            new GenericDocument.Builder<>(NAMESPACE, ID, "testSchema")
                    .setPropertyString("subject", "testPut example1")
                    .setCreationTimestampMillis(12345L)
                    .build();

    private GlobalSearchSessionShim mGlobalSearch;

    @Before
    public void setUp() throws Exception {
        mGlobalSearch = GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get();
    }

    @Test
    public void testGlobalGetDocuments_exist() throws Exception {
        List<GenericDocument> outDocuments = convertSearchResultsToDocuments(
                mGlobalSearch.search(/*queryExpression=*/"",
                        new SearchSpec.Builder().setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                                .build()));
        assertThat(outDocuments).containsExactly(DOCUMENT);
    }

    @Test
    public void testGlobalGetDocuments_nonexist() throws Exception {
        List<GenericDocument> outDocuments = convertSearchResultsToDocuments(
                mGlobalSearch.search(/*queryExpression=*/"",
                        new SearchSpec.Builder().setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                                .build()));
        assertThat(outDocuments).isEmpty();
    }
}
