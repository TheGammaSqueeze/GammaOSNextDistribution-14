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

package com.android.car.qc.provider;

import static com.android.car.qc.provider.BaseQCProvider.EXTRA_ITEM;
import static com.android.car.qc.provider.BaseQCProvider.EXTRA_URI;
import static com.android.car.qc.provider.BaseQCProvider.METHOD_BIND;
import static com.android.car.qc.provider.BaseQCProvider.METHOD_DESTROY;
import static com.android.car.qc.provider.BaseQCProvider.METHOD_SUBSCRIBE;
import static com.android.car.qc.provider.BaseQCProvider.METHOD_UNSUBSCRIBE;
import static com.android.car.qc.testutils.TestQCProvider.IS_DESTROYED_KEY;
import static com.android.car.qc.testutils.TestQCProvider.IS_SUBSCRIBED_KEY;
import static com.android.car.qc.testutils.TestQCProvider.KEY_DEFAULT;
import static com.android.car.qc.testutils.TestQCProvider.KEY_SLOW;
import static com.android.car.qc.testutils.TestQCProvider.METHOD_IS_DESTROYED;
import static com.android.car.qc.testutils.TestQCProvider.METHOD_IS_SUBSCRIBED;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCItem;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BaseQCProviderTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final Uri mDefaultUri = Uri.parse(
            "content://com.android.car.qc.testutils.AllowedTestQCProvider/" + KEY_DEFAULT);
    private final Uri mSlowUri =
            Uri.parse("content://com.android.car.qc.testutils.AllowedTestQCProvider/" + KEY_SLOW);
    private final Uri mDeniedUri =
            Uri.parse("content://com.android.car.qc.testutils.DeniedTestQCProvider");

    @Test
    public void callOnBind_allowed_returnsItem() throws RemoteException {
        ContentProviderClient provider = getClient(mDefaultUri);
        assertThat(provider).isNotNull();
        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mDefaultUri);
        Bundle res = provider.call(METHOD_BIND, null, extras);
        assertThat(res).isNotNull();
        res.setClassLoader(QCItem.class.getClassLoader());
        Parcelable parcelable = res.getParcelable(EXTRA_ITEM);
        assertThat(parcelable).isNotNull();
        assertThat(parcelable instanceof QCItem).isTrue();
    }

    @Test
    public void callOnBind_noUri_throwsIllegalArgumentException() throws RemoteException {
        ContentProviderClient provider = getClient(mDefaultUri);
        assertThat(provider).isNotNull();
        Bundle extras = new Bundle();
        assertThrows(IllegalArgumentException.class,
                () -> provider.call(METHOD_BIND, null, extras));
    }

    @Test
    public void callOnBind_slowOperation_throwsRuntimeException() {
        ContentProviderClient provider = getClient(mSlowUri);
        assertThat(provider).isNotNull();
        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mSlowUri);
        assertThrows(RuntimeException.class,
                () -> provider.call(METHOD_BIND, null, extras));
    }

    @Test
    public void callOnBind_notAllowed_throwsSecurityException() {
        ContentProviderClient provider = getClient(mDeniedUri);
        assertThat(provider).isNotNull();
        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mDeniedUri);
        assertThrows(SecurityException.class,
                () -> provider.call(METHOD_BIND, null, extras));
    }

    @Test
    public void callOnSubscribed_isSubscribed() throws RemoteException {
        ContentProviderClient provider = getClient(mDefaultUri);
        assertThat(provider).isNotNull();
        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mDefaultUri);
        provider.call(METHOD_SUBSCRIBE, null, extras);

        Bundle res = provider.call(METHOD_IS_SUBSCRIBED, null, extras);
        assertThat(res).isNotNull();
        boolean isSubscribed = res.getBoolean(IS_SUBSCRIBED_KEY, false);
        assertThat(isSubscribed).isTrue();
    }

    @Test
    public void callOnUnsubscribed_isUnsubscribed() throws RemoteException {
        ContentProviderClient provider = getClient(mDefaultUri);
        assertThat(provider).isNotNull();
        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mDefaultUri);
        provider.call(METHOD_SUBSCRIBE, null, extras);
        provider.call(METHOD_UNSUBSCRIBE, null, extras);

        Bundle res = provider.call(METHOD_IS_SUBSCRIBED, null, extras);
        assertThat(res).isNotNull();
        boolean isSubscribed = res.getBoolean(IS_SUBSCRIBED_KEY, true);
        assertThat(isSubscribed).isFalse();
    }

    @Test
    public void callDestroy_isDestroyed() throws RemoteException {
        ContentProviderClient provider = getClient(mDefaultUri);
        assertThat(provider).isNotNull();
        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_URI, mDefaultUri);
        provider.call(METHOD_SUBSCRIBE, null, extras);
        provider.call(METHOD_UNSUBSCRIBE, null, extras);
        provider.call(METHOD_DESTROY, null, extras);

        Bundle res = provider.call(METHOD_IS_DESTROYED, null, extras);
        assertThat(res).isNotNull();
        boolean isDestroyed = res.getBoolean(IS_DESTROYED_KEY, false);
        assertThat(isDestroyed).isTrue();
    }

    private ContentProviderClient getClient(Uri uri) {
        return mContext.getContentResolver().acquireContentProviderClient(uri);
    }
}
