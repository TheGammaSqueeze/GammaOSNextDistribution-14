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

package com.android.adservices.service.encryptionkey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.encryptionkey.EncryptionKeyFetcher.JSONResponseContract;
import com.android.adservices.service.encryptionkey.EncryptionKeyFetcher.KeyResponseContract;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats;
import com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchJobType;
import com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchStatus;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.HttpsURLConnection;

/** Unit tests for {@link EncryptionKeyFetcher} */
@SmallTest
public class EncryptionKeyFetcherTest {

    private static final String ENROLLMENT_ID = "1";
    private static final String ENROLLED_APIS = "PRIVACY_SANDBOX_API_TOPICS";
    private static final String SITE = "https://test1.com";
    private static final String EMPTY_SITE = "";
    private static final String SINGLE_WHITESPACE_SITE = " ";
    private static final String PROTOCOL_TYPE1 = "HPKE";
    private static final String PROTOCOL_TYPE2 = "ECDSA";
    private static final int KEY_COMMITMENT_ID1 = 1;
    private static final int KEY_COMMITMENT_ID2 = 2;
    private static final String BODY1 = "WVZBTFVF";
    private static final String BODY2 = "VZBTFVFW";
    private static final long EXPIRATION1 = 100000L;
    private static final long EXPIRATION2 = 100001L;
    @Mock private HttpsURLConnection mURLConnection;
    @Mock private Flags mMockFlags;
    @Mock private AdServicesLogger mAdServicesLogger;
    private MockitoSession mStaticMockSession;

    EncryptionKeyFetcher mFetcher;
    EncryptionKeyFetcher mSpyFetcher;

    /** Unit test set up. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Start a mockitoSession to mock static method.
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        doReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS)
                .when(mMockFlags)
                .getEncryptionKeyNetworkConnectTimeoutMs();
        doReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS)
                .when(mMockFlags)
                .getEncryptionKeyNetworkReadTimeoutMs();
        mFetcher = new EncryptionKeyFetcher(mAdServicesLogger);
        mSpyFetcher = spy(mFetcher);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    private static JSONObject constructEncryptionKeyJSON() throws JSONException {
        JSONObject keyObject = new JSONObject();
        keyObject.put(KeyResponseContract.ID, KEY_COMMITMENT_ID1);
        keyObject.put(KeyResponseContract.BODY, BODY1);
        keyObject.put(KeyResponseContract.EXPIRY, EXPIRATION1);
        JSONArray keyArray = new JSONArray();
        keyArray.put(keyObject);

        JSONObject encryptionObject = new JSONObject();
        encryptionObject.put(JSONResponseContract.PROTOCOL_TYPE, PROTOCOL_TYPE1);
        encryptionObject.put(JSONResponseContract.KEYS, keyArray);
        return encryptionObject;
    }

    private static JSONObject constructSigningKeyJSON() throws JSONException {
        JSONObject keyObject = new JSONObject();
        keyObject.put(KeyResponseContract.ID, KEY_COMMITMENT_ID2);
        keyObject.put(KeyResponseContract.BODY, BODY2);
        keyObject.put(KeyResponseContract.EXPIRY, EXPIRATION2);
        JSONArray keyArray = new JSONArray();
        keyArray.put(keyObject);

        JSONObject signingObject = new JSONObject();
        signingObject.put(JSONResponseContract.PROTOCOL_TYPE, PROTOCOL_TYPE2);
        signingObject.put(JSONResponseContract.KEYS, keyArray);
        return signingObject;
    }

    private static EnrollmentData.Builder constructEnrollmentData() {
        return new EnrollmentData.Builder()
                .setEnrollmentId(ENROLLMENT_ID)
                .setEnrolledAPIs(ENROLLED_APIS)
                .setSdkNames("1sdk")
                .setAttributionSourceRegistrationUrl(List.of(SITE))
                .setAttributionTriggerRegistrationUrl(List.of(SITE))
                .setAttributionReportingUrl(List.of(SITE))
                .setRemarketingResponseBasedRegistrationUrl(List.of(SITE));
    }

    private static EncryptionKey constructEncryptionKey() {
        return new EncryptionKey.Builder()
                .setId("1")
                .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                .setEnrollmentId(ENROLLMENT_ID)
                .setReportingOrigin(Uri.parse(SITE))
                .setEncryptionKeyUrl(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT)
                .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                .setKeyCommitmentId(11)
                .setBody("AVZBTFVF")
                .setExpiration(100000L)
                .setLastFetchTime(100001L)
                .build();
    }

    private static JSONObject buildEncryptionKeyJSONResponse() throws JSONException {
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put(JSONResponseContract.ENCRYPTION_KEY, constructEncryptionKeyJSON());
        jsonResponse.put(JSONResponseContract.SIGNING_KEY, constructSigningKeyJSON());
        return jsonResponse;
    }

    private static void prepareMockFetchEncryptionKeyResult(
            EncryptionKeyFetcher fetcher, HttpsURLConnection urlConnection, String response)
            throws IOException {
        InputStream inputStream = new ByteArrayInputStream(response.getBytes());
        doReturn(urlConnection).when(fetcher).setUpURLConnection(any());
        when(urlConnection.getResponseCode()).thenReturn(200);
        when(urlConnection.getInputStream()).thenReturn(inputStream);
    }

    private static void prepareMockRefetchEncryptionKeyNoChange(
            EncryptionKeyFetcher fetcher, HttpsURLConnection urlConnection) throws IOException {
        doReturn(urlConnection).when(fetcher).setUpURLConnection(any());
        when(urlConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_MODIFIED);
    }

    /** Unit test for fetchEncryptionKeys() method. */
    @Test
    public void testFirstTimeFetchEncryptionKeysSucceed() throws JSONException, IOException {
        prepareMockFetchEncryptionKeyResult(
                mSpyFetcher, mURLConnection, buildEncryptionKeyJSONResponse().toString());
        EnrollmentData enrollmentData = constructEnrollmentData().setEncryptionKeyUrl(SITE).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(null, enrollmentData, true);

        assertTrue(encryptionKeyList.isPresent());
        assertEncryptionKeyListResult(encryptionKeyList.get());
        verify(mURLConnection).setRequestMethod("GET");
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.SUCCESS)
                        .setIsFirstTimeFetch(true)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    /** Unit test for fetch encryption key with bad encryption key url. */
    @Test
    public void testFirstTimeFetchEncryptionKeysNullEncryptionKeyUrl()
            throws JSONException, IOException {
        prepareMockFetchEncryptionKeyResult(
                mSpyFetcher, mURLConnection, buildEncryptionKeyJSONResponse().toString());
        EnrollmentData enrollmentData = constructEnrollmentData().setEncryptionKeyUrl(null).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(null, enrollmentData, true);

        assertFalse(encryptionKeyList.isPresent());
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.NULL_ENDPOINT)
                        .setIsFirstTimeFetch(true)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(null)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    /** Unit test for fetch encryption key with empty encryption key url. */
    @Test
    public void testFirstTimeFetchEncryptionKeysEmptyEncryptionKeyUrl()
            throws JSONException, IOException {
        prepareMockFetchEncryptionKeyResult(
                mSpyFetcher, mURLConnection, buildEncryptionKeyJSONResponse().toString());
        EnrollmentData enrollmentData =
                constructEnrollmentData().setEncryptionKeyUrl(EMPTY_SITE).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(null, enrollmentData, true);

        assertFalse(encryptionKeyList.isPresent());
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.NULL_ENDPOINT)
                        .setIsFirstTimeFetch(true)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(null)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    /** Unit test for fetch encryption key with single white space encryption key url. */
    @Test
    public void testFirstTimeFetchEncryptionKeysSingleWhiteSpaceEncryptionKeyUrl()
            throws JSONException, IOException {
        prepareMockFetchEncryptionKeyResult(
                mSpyFetcher, mURLConnection, buildEncryptionKeyJSONResponse().toString());
        EnrollmentData enrollmentData =
                constructEnrollmentData().setEncryptionKeyUrl(SINGLE_WHITESPACE_SITE).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(null, enrollmentData, true);

        assertFalse(encryptionKeyList.isPresent());
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.NULL_ENDPOINT)
                        .setIsFirstTimeFetch(true)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(null)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    /** Unit test for fetch encryption key with bad internet connection. */
    @Test
    public void testFirstTimeFetchEncryptionKeysBadConnection() throws Exception {
        doThrow(new IOException("Bad connection"))
                .when(mSpyFetcher)
                .setUpURLConnection(new URL(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT));
        EnrollmentData enrollmentData = constructEnrollmentData().setEncryptionKeyUrl(SITE).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(null, enrollmentData, true);

        assertFalse(encryptionKeyList.isPresent());
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.IO_EXCEPTION)
                        .setIsFirstTimeFetch(true)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    /** Unit test for fetch encryption key when server timeout. */
    @Test
    public void testFirstTimeFetchEncryptionKeysServerTimeOut() throws Exception {
        doReturn(mURLConnection)
                .when(mSpyFetcher)
                .setUpURLConnection(new URL(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT));
        doThrow(new IOException("Server timeout")).when(mURLConnection).getResponseCode();
        EnrollmentData enrollmentData = constructEnrollmentData().setEncryptionKeyUrl(SITE).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(null, enrollmentData, true);

        assertFalse(encryptionKeyList.isPresent());
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.IO_EXCEPTION)
                        .setIsFirstTimeFetch(true)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    /** Unit test for fetch encryption key when JSON response is invalid. */
    @Test
    public void testFirstTimeFetchEncryptionKeysInvalidJSONResponse() throws Exception {
        String response = "{" + buildEncryptionKeyJSONResponse();
        InputStream inputStream = new ByteArrayInputStream(response.getBytes());
        doReturn(mURLConnection)
                .when(mSpyFetcher)
                .setUpURLConnection(new URL(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT));
        when(mURLConnection.getResponseCode()).thenReturn(200);
        when(mURLConnection.getInputStream()).thenReturn(inputStream);
        EnrollmentData enrollmentData = constructEnrollmentData().setEncryptionKeyUrl(SITE).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(null, enrollmentData, true);

        assertFalse(encryptionKeyList.isPresent());
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.BAD_REQUEST_EXCEPTION)
                        .setIsFirstTimeFetch(true)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    /**
     * Unit test for re-fetch encryption key (previously fetched before), no change made to keys.
     */
    @Test
    public void testRefetchEncryptionKeysNoChangeMadeToKeys() throws IOException {
        prepareMockRefetchEncryptionKeyNoChange(mSpyFetcher, mURLConnection);
        EnrollmentData enrollmentData = constructEnrollmentData().setEncryptionKeyUrl(SITE).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(constructEncryptionKey(), enrollmentData, false);

        assertTrue(encryptionKeyList.isEmpty());
        verify(mURLConnection).setRequestMethod("GET");
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.KEY_NOT_MODIFIED)
                        .setIsFirstTimeFetch(false)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    /** Unit test for re-fetch encryption key (previously fetched before), return new keys. */
    @Test
    public void testRefetchEncryptionKeysReturnNewKeys() throws JSONException, IOException {
        prepareMockFetchEncryptionKeyResult(
                mSpyFetcher, mURLConnection, buildEncryptionKeyJSONResponse().toString());
        EnrollmentData enrollmentData = constructEnrollmentData().setEncryptionKeyUrl(SITE).build();
        Optional<List<EncryptionKey>> encryptionKeyList =
                mSpyFetcher.fetchEncryptionKeys(constructEncryptionKey(), enrollmentData, false);
        assertTrue(encryptionKeyList.isPresent());
        assertEncryptionKeyListResult(encryptionKeyList.get());

        verify(mURLConnection).setRequestMethod("GET");
        verify(mURLConnection).setRequestProperty(any(), any());
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(FetchStatus.SUCCESS)
                        .setIsFirstTimeFetch(false)
                        .setAdtechEnrollmentId(ENROLLMENT_ID)
                        .setEncryptionKeyUrl(SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT)
                        .build();
        verify(mAdServicesLogger).logEncryptionKeyFetchedStats(eq(stats));
    }

    private void assertEncryptionKeyListResult(List<EncryptionKey> encryptionKeys) {
        assertEquals(2, encryptionKeys.size());
        EncryptionKey encryptionKey1 = encryptionKeys.get(0);
        assertNotNull(encryptionKey1.getId());
        assertEquals(EncryptionKey.KeyType.ENCRYPTION, encryptionKey1.getKeyType());
        assertEquals(ENROLLMENT_ID, encryptionKey1.getEnrollmentId());
        assertEquals(Uri.parse(SITE), encryptionKey1.getReportingOrigin());
        assertEquals(
                SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT,
                encryptionKey1.getEncryptionKeyUrl());
        assertEquals(
                EncryptionKey.ProtocolType.valueOf(PROTOCOL_TYPE1),
                encryptionKey1.getProtocolType());
        assertEquals(KEY_COMMITMENT_ID1, encryptionKey1.getKeyCommitmentId());
        assertEquals(BODY1, encryptionKey1.getBody());
        assertEquals(EXPIRATION1, encryptionKey1.getExpiration());
        assertNotEquals(0L, encryptionKey1.getLastFetchTime());

        EncryptionKey encryptionKey2 = encryptionKeys.get(1);
        assertNotNull(encryptionKey2.getId());
        assertEquals(EncryptionKey.KeyType.SIGNING, encryptionKey2.getKeyType());
        assertEquals(ENROLLMENT_ID, encryptionKey2.getEnrollmentId());
        assertEquals(Uri.parse(SITE), encryptionKey2.getReportingOrigin());
        assertEquals(
                SITE + EncryptionKeyFetcher.ENCRYPTION_KEY_ENDPOINT,
                encryptionKey2.getEncryptionKeyUrl());
        assertEquals(
                EncryptionKey.ProtocolType.valueOf(PROTOCOL_TYPE2),
                encryptionKey2.getProtocolType());
        assertEquals(KEY_COMMITMENT_ID2, encryptionKey2.getKeyCommitmentId());
        assertEquals(BODY2, encryptionKey2.getBody());
        assertEquals(EXPIRATION2, encryptionKey2.getExpiration());
        assertNotEquals(0L, encryptionKey2.getLastFetchTime());
    }
}
