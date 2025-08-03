/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.pbapclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.accounts.Account;
import android.content.Context;
import android.database.MatrixCursor;
import android.provider.ContactsContract;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.vcard.VCardConstants;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardProperty;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CallLogPullRequestTest {

    private final Account mAccount = mock(Account.class);
    private final HashMap<String, Integer> mCallCounter = new HashMap<>();

    private Context mTargetContext;
    @Spy
    private BluetoothMethodProxy mMapMethodProxy = BluetoothMethodProxy.getInstance();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mMapMethodProxy);
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @After
    public void tearDown() throws Exception {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void testToString() {
        final String path = PbapClientConnectionHandler.ICH_PATH;
        final CallLogPullRequest request = new CallLogPullRequest(
                mTargetContext, path, mCallCounter, mAccount);

        assertThat(request.toString()).isNotEmpty();
    }

    @Test
    public void onPullComplete_whenResultsAreNull() {
        final String path = PbapClientConnectionHandler.ICH_PATH;
        final CallLogPullRequest request = new CallLogPullRequest(
                mTargetContext, path, mCallCounter, mAccount);
        request.setResults(null);

        request.onPullComplete();

        // No operation has been done.
        assertThat(mCallCounter.size()).isEqualTo(0);
    }

    @Test
    public void onPullComplete_whenPathIsInvalid() {
        final String invalidPath = "invalidPath";
        final CallLogPullRequest request = new CallLogPullRequest(
                mTargetContext, invalidPath, mCallCounter, mAccount);
        List<VCardEntry> results = new ArrayList<>();
        request.setResults(results);

        request.onPullComplete();

        // No operation has been done.
        assertThat(mCallCounter.size()).isEqualTo(0);
    }

    @Test
    public void onPullComplete_whenResultsAreEmpty() {
        final String path = PbapClientConnectionHandler.ICH_PATH;
        final CallLogPullRequest request = new CallLogPullRequest(
                mTargetContext, path, mCallCounter, mAccount);
        List<VCardEntry> results = new ArrayList<>();
        request.setResults(results);

        request.onPullComplete();

        // Call counter should remain same.
        assertThat(mCallCounter.size()).isEqualTo(0);
    }

    @Test
    public void onPullComplete_whenThereIsNoPhoneProperty() {
        final String path = PbapClientConnectionHandler.MCH_PATH;
        final CallLogPullRequest request = new CallLogPullRequest(
                mTargetContext, path, mCallCounter, mAccount);

        // Add some property which is NOT a phone number
        VCardProperty property = new VCardProperty();
        property.setName(VCardConstants.PROPERTY_NOTE);
        property.setValues("Some random note");

        VCardEntry entry = new VCardEntry();
        entry.addProperty(property);

        List<VCardEntry> results = new ArrayList<>();
        results.add(entry);
        request.setResults(results);

        request.onPullComplete();

        // Call counter should remain same.
        assertThat(mCallCounter.size()).isEqualTo(0);
    }

    @Test
    public void onPullComplete_success() {
        final String path = PbapClientConnectionHandler.OCH_PATH;
        final CallLogPullRequest request = new CallLogPullRequest(
                mTargetContext, path, mCallCounter, mAccount);
        List<VCardEntry> results = new ArrayList<>();

        final String phoneNum = "tel:0123456789";

        VCardEntry entry1 = new VCardEntry();
        entry1.addProperty(createProperty(VCardConstants.PROPERTY_TEL, phoneNum));
        results.add(entry1);

        VCardEntry entry2 = new VCardEntry();
        entry2.addProperty(createProperty(VCardConstants.PROPERTY_TEL, phoneNum));
        entry2.addProperty(
                createProperty(CallLogPullRequest.TIMESTAMP_PROPERTY, "20220914T143305"));
        results.add(entry2);
        request.setResults(results);

        request.onPullComplete();

        assertThat(mCallCounter.size()).isEqualTo(1);
        for (String key : mCallCounter.keySet()) {
            assertThat(mCallCounter.get(key)).isEqualTo(2);
            break;
        }
    }

    @Test
    public void updateTimesContacted_cursorIsClosed() {
        final String path = PbapClientConnectionHandler.OCH_PATH;
        final CallLogPullRequest request = new CallLogPullRequest(
                mTargetContext, path, mCallCounter, mAccount);
        mCallCounter.put("key", 1);

        MatrixCursor cursor = new MatrixCursor(
                new String[] {ContactsContract.PhoneLookup.CONTACT_ID});
        cursor.addRow(new Object[] {"contact_id"});
        doReturn(cursor).when(mMapMethodProxy).contentResolverQuery(any(), any(), eq(null),
                eq(null), eq(null));
        assertThat(cursor.isClosed()).isFalse();

        request.updateTimesContacted();

        assertThat(cursor.isClosed()).isTrue();
    }

    private VCardProperty createProperty(String name, String value) {
        VCardProperty property = new VCardProperty();
        property.setName(name);
        property.setValues(value);
        return property;
    }
}
