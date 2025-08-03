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

package com.android.adservices.service.measurement.registration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AsyncRegistrationContentProviderTest {
    private static final Uri TRIGGER_URI_T_PLUS =
            Uri.parse("content://com.android.adservices.provider.asyncregistration");
    private static final Uri TRIGGER_URI_S_MINUS =
            Uri.parse("content://com.android.ext.adservices.provider.asyncregistration");

    @Mock private Context mContext;
    @Mock private ContentResolver mContentResolver;
    private AsyncRegistrationContentProvider mSpyContentProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSpyContentProvider = Mockito.spy(new AsyncRegistrationContentProvider());
    }

    @Test
    public void testOnCreate() {
        assertTrue(mSpyContentProvider.onCreate());
    }

    @Test
    public void testQuery_throwsUnsupportedOperationException() {
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mSpyContentProvider.query(
                                TRIGGER_URI_T_PLUS,
                                /* projection= */ new String[0],
                                /* selection= */ "",
                                /* selectionArgs= */ new String[0],
                                /* sortOrder= */ ""));
    }

    @Test
    public void testGetType_throwsUnsupportedOperationException() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> mSpyContentProvider.getType(TRIGGER_URI_T_PLUS));
    }

    @Test
    public void testInsert_ignoresPassedInArgs_onTPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        doReturn(mContentResolver).when(mContext).getContentResolver();
        doReturn(mContext).when(mSpyContentProvider).getContext();
        doNothing().when(mContentResolver).notifyChange(any(), any());

        Uri returnedUri = mSpyContentProvider.insert(TRIGGER_URI_S_MINUS, new ContentValues());

        verify(mContentResolver, times(1)).notifyChange(TRIGGER_URI_T_PLUS, /* observer= */ null);
        assertEquals(TRIGGER_URI_T_PLUS, returnedUri);
    }

    @Test
    public void testInsert_ignoresPassedInArgs_onSMinus() {
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        doReturn(mContentResolver).when(mContext).getContentResolver();
        doReturn(mContext).when(mSpyContentProvider).getContext();
        doNothing().when(mContentResolver).notifyChange(any(), any());

        Uri returnedUri = mSpyContentProvider.insert(TRIGGER_URI_T_PLUS, new ContentValues());

        verify(mContentResolver, times(1)).notifyChange(TRIGGER_URI_S_MINUS, /* observer= */ null);
        assertEquals(TRIGGER_URI_S_MINUS, returnedUri);
    }

    @Test
    public void testDelete_throwsUnsupportedOperationException() {
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mSpyContentProvider.delete(
                                TRIGGER_URI_T_PLUS,
                                /* selection= */ "",
                                /* selectionArgs= */ new String[0]));
    }

    @Test
    public void testUpdate_throwsUnsupportedOperationException() {
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mSpyContentProvider.update(
                                TRIGGER_URI_T_PLUS,
                                new ContentValues(),
                                /* selection= */ "",
                                /* selectionArgs= */ new String[0]));
    }
}
