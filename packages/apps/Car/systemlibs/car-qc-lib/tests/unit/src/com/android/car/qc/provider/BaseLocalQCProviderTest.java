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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BaseLocalQCProviderTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private TestBaseLocalQCProvider mProvider;

    @Before
    public void setUp() {
        mProvider = new TestBaseLocalQCProvider(mContext);
    }

    @Test
    public void getQCItem_returnsItem() {
        QCItem item = mProvider.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCTile).isTrue();
    }

    @Test
    public void listen_callsOnSubscribed() {
        mProvider.shouldListen(true);
        assertThat(mProvider.isSubscribed()).isTrue();
    }

    @Test
    public void stopListening_callsOnUnsubscribed() {
        mProvider.shouldListen(true);
        mProvider.shouldListen(false);
        assertThat(mProvider.isSubscribed()).isFalse();
    }

    @Test
    public void notifyChange_updateNotified() {
        BaseLocalQCProvider.Notifier notifier = mock(BaseLocalQCProvider.Notifier.class);
        mProvider.setNotifier(notifier);
        mProvider.notifyChange();
        verify(notifier).notifyUpdate();
    }

    private static class TestBaseLocalQCProvider extends BaseLocalQCProvider {

        private boolean mIsSubscribed;

        TestBaseLocalQCProvider(Context context) {
            super(context);
        }

        @Override
        public QCItem getQCItem() {
            return new QCTile.Builder().build();
        }

        @Override
        protected void onSubscribed() {
            mIsSubscribed = true;
        }

        @Override
        protected void onUnsubscribed() {
            mIsSubscribed = false;
        }

        boolean isSubscribed() {
            return mIsSubscribed;
        }
    }
}
