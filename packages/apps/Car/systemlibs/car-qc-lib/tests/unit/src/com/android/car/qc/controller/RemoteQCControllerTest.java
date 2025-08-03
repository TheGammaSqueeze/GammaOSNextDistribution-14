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

package com.android.car.qc.controller;

import static com.android.car.qc.provider.BaseQCProvider.EXTRA_URI;
import static com.android.car.qc.testutils.TestQCProvider.IS_DESTROYED_KEY;
import static com.android.car.qc.testutils.TestQCProvider.IS_SUBSCRIBED_KEY;
import static com.android.car.qc.testutils.TestQCProvider.KEY_DEFAULT;
import static com.android.car.qc.testutils.TestQCProvider.METHOD_IS_DESTROYED;
import static com.android.car.qc.testutils.TestQCProvider.METHOD_IS_SUBSCRIBED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.qc.QCItem;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteQCControllerTest extends BaseQCControllerTestCase<RemoteQCController> {

    private final Uri mDefaultUri = Uri.parse(
            "content://com.android.car.qc.testutils.AllowedTestQCProvider/" + KEY_DEFAULT);

    private RemoteQCController mController;

    @Override
    protected RemoteQCController getController() {
        if (mController == null) {
            mController = new RemoteQCController(mContext, mDefaultUri, mContext.getMainExecutor());
        }
        return mController;
    }

    @Test
    public void onBind_updatesQCItem() {
        Observer<QCItem> observer = mock(Observer.class);
        RemoteQCController spiedController = spy(getController());
        spiedController.addObserver(observer);
        spiedController.bind();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(spiedController).onQCItemUpdated(notNull());
    }

    @Test
    public void updateListening_listen_updatesQCItem() {
        Observer<QCItem> observer = mock(Observer.class);
        RemoteQCController spiedController = spy(getController());
        spiedController.addObserver(observer);
        spiedController.listen(true);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(spiedController).onQCItemUpdated(notNull());
    }

    @Test
    public void updateListening_listen_providerSubscribed() throws RemoteException {
        Observer<QCItem> observer = mock(Observer.class);
        getController().addObserver(observer);
        getController().listen(true);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mDefaultUri);
        Bundle res = getController().getClient().call(METHOD_IS_SUBSCRIBED, null, extras);
        assertThat(res).isNotNull();
        boolean isSubscribed = res.getBoolean(IS_SUBSCRIBED_KEY, false);
        assertThat(isSubscribed).isTrue();
    }

    @Test
    public void updateListening_doNotListen_providerUnsubscribed() throws RemoteException {
        Observer<QCItem> observer = mock(Observer.class);
        getController().addObserver(observer);
        getController().listen(true);
        getController().listen(false);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mDefaultUri);
        Bundle res = getController().getClient().call(METHOD_IS_SUBSCRIBED, null, extras);
        assertThat(res).isNotNull();
        boolean isSubscribed = res.getBoolean(IS_SUBSCRIBED_KEY, true);
        assertThat(isSubscribed).isFalse();
    }

    @Test
    public void updateListening_listen_registerContentObserver() {
        ContentResolver resolver = mock(ContentResolver.class);
        when(mContext.getContentResolver()).thenReturn(resolver);
        when(resolver.acquireContentProviderClient(mDefaultUri)).thenReturn(
                mock(ContentProviderClient.class));
        Observer<QCItem> observer = mock(Observer.class);
        getController().addObserver(observer);
        getController().listen(true);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(resolver).registerContentObserver(eq(mDefaultUri), eq(true),
                any(ContentObserver.class));
    }

    @Test
    public void updateListening_doNotListen_unregisterContentObserver() {
        ContentResolver resolver = mock(ContentResolver.class);
        when(mContext.getContentResolver()).thenReturn(resolver);
        when(resolver.acquireContentProviderClient(mDefaultUri)).thenReturn(
                mock(ContentProviderClient.class));
        Observer<QCItem> observer = mock(Observer.class);
        getController().addObserver(observer);
        getController().listen(true);
        getController().listen(false);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(resolver).unregisterContentObserver(any(ContentObserver.class));
    }

    @Test
    public void onDestroy_callsProviderOnDestroy() throws RemoteException {
        Observer<QCItem> observer = mock(Observer.class);
        getController().addObserver(observer);
        getController().listen(true);
        getController().listen(false);
        getController().destroy();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mDefaultUri);
        Bundle res = getController().getClient().call(METHOD_IS_DESTROYED, null, extras);
        assertThat(res).isNotNull();
        boolean isDestroyed = res.getBoolean(IS_DESTROYED_KEY, false);
        assertThat(isDestroyed).isTrue();
    }
}
