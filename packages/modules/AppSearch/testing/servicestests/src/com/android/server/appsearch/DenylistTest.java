/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.appsearch;

import static com.google.common.truth.Truth.assertThat;

import android.util.ArraySet;

import com.android.server.appsearch.external.localstorage.stats.CallStats;

import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

public class DenylistTest {
    @Test
    public void testEmptyDenylistDeniesNothing() {
        for (Integer apiType : CallStats.getAllApiCallTypes()) {
            assertThat(Denylist.EMPTY_INSTANCE.checkDeniedPackageDatabase("foo", "bar",
                    apiType)).isFalse();
            assertThat(Denylist.EMPTY_INSTANCE.checkDeniedPackage("foo", apiType)).isFalse();
        }
    }

    @Test
    public void testDenyAllApis() {
        Denylist denylist = Denylist.create(
                "pkg=foo&apis=localSetSchema,globalGetSchema,localGetSchema,"
                        + "localGetNamespaces,localPutDocuments,globalGetDocuments,"
                        + "localGetDocuments,globalSearch,localSearch,globalGetNextPage,"
                        + "localGetNextPage,invalidateNextPageToken,"
                        + "localWriteSearchResultsToFile,localPutDocumentsFromFile,"
                        + "localSearchSuggestion,globalReportUsage,localReportUsage,"
                        + "localRemoveByDocumentId,localRemoveBySearch,localGetStorageInfo,flush,"
                        + "globalRegisterObserverCallback,globalUnregisterObserverCallback,"
                        + "initialize");
        for (Integer apiType : CallStats.getAllApiCallTypes()) {
            assertThat(denylist.checkDeniedPackageDatabase("foo", "bar", apiType)).isTrue();
            assertThat(denylist.checkDeniedPackageDatabase("bar", "foo", apiType)).isFalse();
            assertThat(denylist.checkDeniedPackage("foo", apiType)).isTrue();
            assertThat(denylist.checkDeniedPackage("bar", apiType)).isFalse();
        }
    }

    @Test
    public void testDenyNoApis() {
        Denylist denylist = Denylist.create(
                "pkg=foo&apis=");
        for (Integer apiType : CallStats.getAllApiCallTypes()) {
            assertThat(denylist.checkDeniedPackage("foo", apiType)).isFalse();
        }
    }

    @Test
    public void testDenySomeApis() {
        Denylist denylist = Denylist.create(
                "pkg=foo&apis=localSetSchema,localGetSchema,localGetNamespaces");
        Set<Integer> deniedApiTypes = new ArraySet<>(
                Arrays.asList(CallStats.CALL_TYPE_SET_SCHEMA, CallStats.CALL_TYPE_GET_SCHEMA,
                        CallStats.CALL_TYPE_GET_NAMESPACES));
        Set<Integer> apiTypes = CallStats.getAllApiCallTypes();
        apiTypes.removeAll(deniedApiTypes);
        for (Integer apiType : apiTypes) {
            assertThat(denylist.checkDeniedPackage("foo", apiType)).isFalse();
        }
        for (Integer apiType : deniedApiTypes) {
            assertThat(denylist.checkDeniedPackage("foo", apiType)).isTrue();
        }
    }

    @Test
    public void testDenyByPackage() {
        Denylist denylist = Denylist.create("pkg=foo&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "hello",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackage("foo", CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("bar", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackageDatabase("bar", "hello",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackage("bar", CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testDenyByPackageDatabase() {
        Denylist denylist = Denylist.create("pkg=foo&db=bar&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "hello",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackage("foo", CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testDenyByDatabase() {
        Denylist denylist = Denylist.create("db=bar&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "hello",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackage("foo", CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackageDatabase("bar", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("bar", "hello",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackage("bar", CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testWhitespaceSensitive() {
        Denylist denylist = Denylist.create("pkg=foo &db=bar &apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackageDatabase("foo ", "bar ",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();

        denylist = Denylist.create("pkg=foo&db= &apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", " ",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackage("foo", CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testKeysAreCaseSensitive() {
        Denylist denylist = Denylist.create("pkg=foo&DB=bar&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testValuesAreCaseSensitive() {
        Denylist denylist = Denylist.create("pkg=foo&db=BAR&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "BAR",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testApisAreCaseSensitive() {
        Denylist denylist = Denylist.create(
                "pkg=foo&apis=localsetschema,localGetSchema");
        assertThat(denylist.checkDeniedPackage("foo", CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackage("foo", CallStats.CALL_TYPE_GET_SCHEMA)).isTrue();
    }

    @Test
    public void testUnknownKeyInvalidatesEntry() {
        Denylist denylist = Denylist.create(
                "pkg=foo&db=bar&apis=localSetSchema&hello=world");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testDuplicateKeyTakesFirstOccurrence() {
        Denylist denylist = Denylist.create(
                "pkg=foo&db=bar&apis=localSetSchema&pkg=bar");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("bar", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testUnknownApiIsOkButIgnored() {
        Denylist denylist = Denylist.create(
                "pkg=foo&db=bar&apis=hello,localSetSchema,world,localGetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_GET_SCHEMA)).isTrue();
        // Assert actually ignored and didn't add a random api to the denylist
        Set<Integer> apiTypes = CallStats.getAllApiCallTypes();
        apiTypes.remove(CallStats.CALL_TYPE_SET_SCHEMA);
        apiTypes.remove(CallStats.CALL_TYPE_GET_SCHEMA);
        for (Integer apiType : apiTypes) {
            assertThat(denylist.checkDeniedPackageDatabase("foo", "bar", apiType)).isFalse();
        }
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_UNKNOWN)).isFalse();
    }

    @Test
    public void testBadParameterFormatInvalidatesEntry() {
        // parameters expected to be in the format key=value
        Denylist denylist = Denylist.create(
                "pkg=foo&db=bar&apis=localSetSchema&helloworld");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testEmptyParameterIsNotOk() {
        // empty parameters are not ignored
        Denylist denylist = Denylist.create(
                "pkg=foo&db=bar&apis=localSetSchema&&");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testMultipleDenylistEntries() {
        Denylist denylist = Denylist.create(
                "pkg=foo&db=bar&apis=localSetSchema;"
                        + "pkg=hello&apis=localSetSchema;"
                        + "db=baz&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackage("hello", CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "baz",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
    }

    @Test
    public void testMultipleSimilarDenylistEntriesAreAdditive() {
        Denylist denylist = Denylist.create(
                "pkg=foo&db=bar&apis=localSetSchema;"
                        + "pkg=foo&db=bar&apis=localGetSchema;"
                        + "pkg=foo&db=bar&apis=initialize;");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_GET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_INITIALIZE)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_FLUSH)).isFalse();
    }

    @Test
    public void testUrlEncodedPackageAndDatabaseNames() {
        Denylist denylist = Denylist.create(
                "pkg=pkg%3Dfoo&db=db%3Bbar&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("pkg=foo", "db;bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("pkg%3Dfoo", "db%3Bbar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testEmptyDatabaseIsValid() {
        Denylist denylist = Denylist.create(
                "pkg=foo&db=&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
    }

    @Test
    public void testEmptyDatabaseIsNotDenyByPackage() {
        Denylist denylist = Denylist.create(
                "pkg=foo&db=&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackage("foo", CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testEmptyPackageIsValid() {
        // Empty package name is valid but is unlikely to ever match an actual package
        Denylist denylist = Denylist.create("pkg=&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackage("", CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
    }

    @Test
    public void testEmptyPackageIsNotDenyByDatabase() {
        Denylist denylist = Denylist.create("pkg=&db=bar&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testEmptyWithAndWithoutEqualsIsEquivalent() {
        Denylist denylist = Denylist.create("pkg=&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackage("",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackage("foo",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();

        denylist = Denylist.create("pkg&apis=localSetSchema");
        assertThat(denylist.checkDeniedPackage("",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackageDatabase("", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(denylist.checkDeniedPackage("foo",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }

    @Test
    public void testMissingPackageAndDatabaseIsNotValid() {
        Denylist denylist = Denylist.create("apis=localSetSchema");
        assertThat(denylist.checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(denylist.checkDeniedPackageDatabase("", "",
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
    }
}
