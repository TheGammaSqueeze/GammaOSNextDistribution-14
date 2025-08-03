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

package com.android.car.settings.qc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.telephony.SubscriptionManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public abstract class MobileDataBaseWorkerTestCase<E extends MobileDataBaseWorker> {
    private static final int DEFAULT_SUB_ID = 1;

    protected final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private E mWorker;
    private MockitoSession mSession;

    @Mock
    private ContentResolver mContentResolver;

    protected abstract E getWorker();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(SubscriptionManager.class, withSettings().lenient())
                .startMocking();

        when(mContext.getContentResolver()).thenReturn(mContentResolver);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onSubscribe_validSubId_registerObserver() {
        createWorker(DEFAULT_SUB_ID);
        mWorker.onQCItemSubscribe();
        verify(mContentResolver).registerContentObserver(any(Uri.class), eq(false),
                any(ContentObserver.class));
    }

    @Test
    public void onSubscribe_invalidSubId_doesNotRegisterObserver() {
        createWorker(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        mWorker.onQCItemSubscribe();
        verify(mContentResolver, never()).registerContentObserver(any(Uri.class), eq(false),
                any(ContentObserver.class));
    }

    @Test
    public void onUnsubscribe_unregistersObserver() {
        createWorker(DEFAULT_SUB_ID);
        ArgumentCaptor<ContentObserver> captor = ArgumentCaptor.forClass(ContentObserver.class);
        mWorker.onQCItemSubscribe();
        verify(mContentResolver).registerContentObserver(any(Uri.class), eq(false),
                captor.capture());
        mWorker.onQCItemUnsubscribe();
        verify(mContentResolver).unregisterContentObserver(captor.getValue());
    }

    private void createWorker(int subId) {
        when(SubscriptionManager.getDefaultDataSubscriptionId()).thenReturn(subId);
        mWorker = getWorker();
    }
}
