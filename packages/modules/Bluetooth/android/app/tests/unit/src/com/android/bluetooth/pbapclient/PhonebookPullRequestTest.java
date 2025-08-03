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

import static org.mockito.Mockito.mock;

import android.accounts.Account;
import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.vcard.VCardConstants;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardProperty;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PhonebookPullRequestTest {

    private PhonebookPullRequest mRequest;
    private Context mTargetContext;

    @Before
    public void setUp() {
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mRequest = new PhonebookPullRequest(mTargetContext, mock(Account.class));
    }

    @Test
    public void onPullComplete_whenResultsAreNull() {
        mRequest.setResults(null);

        mRequest.onPullComplete();

        // No operation has been done.
        assertThat(mRequest.complete).isFalse();
    }

    @Test
    public void onPullComplete_success() {
        List<VCardEntry> results = new ArrayList<>();
        results.add(createEntry(200));
        results.add(createEntry(200));
        results.add(createEntry(PhonebookPullRequest.MAX_OPS));
        mRequest.setResults(results);

        mRequest.onPullComplete();

        assertThat(mRequest.complete).isTrue();
    }

    private VCardProperty createProperty(String name, String value) {
        VCardProperty property = new VCardProperty();
        property.setName(name);
        property.setValues(value);
        return property;
    }

    private VCardEntry createEntry(int propertyCount) {
        VCardEntry entry = new VCardEntry();
        for (int i = 0; i < propertyCount; i++) {
            entry.addProperty(createProperty(VCardConstants.PROPERTY_TEL, Integer.toString(i)));
        }
        return entry;
    }
}
