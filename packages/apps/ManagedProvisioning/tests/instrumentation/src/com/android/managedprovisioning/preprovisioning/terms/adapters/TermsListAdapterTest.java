/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.terms.adapters;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.preprovisioning.terms.TermsDocument;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

@SmallTest
public class TermsListAdapterTest {
    private static final TermsDocument GENERIC_TERMS =
            TermsDocument.createInstance("h0", "c0");
    private static final TermsDocument DOC_1 = TermsDocument.createInstance("h1", "c1");
    private static final TermsDocument DOC_2 = TermsDocument.createInstance("h2", "c2");
    private static final TermsDocument DOC_3 = TermsDocument.createInstance("h3", "c3");
    private static final List<TermsDocument> DOCS = Arrays.asList(DOC_1, DOC_2, DOC_3);

    @Mock private LayoutInflater mLayoutInflater;
    @Mock private AccessibilityContextMenuMaker mContextMenuMaker;
    @Mock private Context mContext;

    private final TermsListAdapter.TermsBridge mTermsBridge =
            new TermsListAdapter.TermsBridge() {
                @Override
                public boolean isTermExpanded(int groupPosition) {
                    return false;
                }

                @Override
                public void onTermExpanded(int groupPosition, boolean expanded) {}

                @Override
                public void onLinkClicked(Intent intent) {}
            };
    private final Utils mUtils = new Utils();
    private final StylerHelper mStylerHelper = new StylerHelper();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getItemCount_hasExpectedNumberOfDocuments() {
        TermsListAdapter adapter = new TermsListAdapter(mContext, GENERIC_TERMS, DOCS,
                mLayoutInflater, mContextMenuMaker, mTermsBridge, mUtils, mStylerHelper);

        assertThat(adapter.getItemCount()).isEqualTo(4);
    }
}
