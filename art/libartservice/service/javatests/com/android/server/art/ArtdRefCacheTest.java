/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.art;

import static android.os.IBinder.DeathRecipient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.IBinder;

import androidx.test.filters.SmallTest;

import com.android.server.art.testing.MockClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ArtdRefCacheTest {
    @Mock private ArtdRefCache.Injector mInjector;
    @Mock private IArtd mArtd;
    @Mock private IBinder mBinder;
    private MockClock mMockClock;
    private ArtdRefCache mArtdRefCache;

    @Before
    public void setUp() throws Exception {
        mMockClock = new MockClock();

        lenient()
                .when(mInjector.createScheduledExecutor())
                .thenAnswer(invocation -> mMockClock.createScheduledExecutor());
        lenient().when(mInjector.getArtd()).thenReturn(mArtd);

        lenient().when(mArtd.asBinder()).thenReturn(mBinder);

        mArtdRefCache = new ArtdRefCache(mInjector);
    }

    @Test
    public void testNoGetArtd() throws Exception {
        try (var pin = mArtdRefCache.new Pin()) {
        }

        verify(mInjector, never()).getArtd();
    }

    @Test
    public void testNoPin() throws Exception {
        // Cache miss.
        mArtdRefCache.getArtd();
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS - 1);
        mArtdRefCache.getArtd();
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS - 1);
        mArtdRefCache.getArtd();
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
        // Cache miss.
        mArtdRefCache.getArtd();

        verify(mInjector, times(2)).getArtd();
    }

    @Test
    public void testSingleScope() throws Exception {
        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
            mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
            mArtdRefCache.getArtd();
            mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
            mArtdRefCache.getArtd();
        }

        verify(mInjector, times(1)).getArtd();
    }

    @Test
    public void testMultipleScopesCacheTimeout() throws Exception {
        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
        }
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
        }
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
        }

        verify(mInjector, times(3)).getArtd();
    }

    @Test
    public void testMultipleScopesCacheHit() throws Exception {
        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
        }
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS - 1);
        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
        }
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS - 1);
        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
        }

        verify(mInjector, times(1)).getArtd();
    }

    @Test
    public void testMultipleScopesNoUnpinAfterTimeout() throws Exception {
        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
        }
        try (var pin = mArtdRefCache.new Pin()) {
            mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
            mArtdRefCache.getArtd();
        }
        try (var pin = mArtdRefCache.new Pin()) {
            mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
            mArtdRefCache.getArtd();
        }

        verify(mInjector, times(1)).getArtd();
    }

    @Test
    public void testBinderDied() throws Exception {
        var deathRecipient = ArgumentCaptor.forClass(DeathRecipient.class);
        doAnswer(invocation -> null)
                .when(mBinder)
                .linkToDeath(deathRecipient.capture(), eq(0) /* flags */);

        try (var pin = mArtdRefCache.new Pin()) {
            mArtdRefCache.getArtd();
            deathRecipient.getValue().binderDied(mBinder);
            mArtdRefCache.getArtd();
            deathRecipient.getValue().binderDied(mBinder);
            mArtdRefCache.getArtd();

            // It should not clear the cache when called with a different binder instance.
            var differentBinder = mock(IBinder.class);
            deathRecipient.getValue().binderDied(differentBinder);
            mArtdRefCache.getArtd();
        }

        verify(mInjector, times(3)).getArtd();
    }

    @Test
    public void testComplex() throws Exception {
        var deathRecipient = ArgumentCaptor.forClass(DeathRecipient.class);
        doAnswer(invocation -> null)
                .when(mBinder)
                .linkToDeath(deathRecipient.capture(), eq(0) /* flags */);

        try (var pin = mArtdRefCache.new Pin()) {
            // Cache miss.
            mArtdRefCache.getArtd();
            mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
            try (var pin2 = mArtdRefCache.new Pin()) {
                mArtdRefCache.getArtd();
                try (var pin3 = mArtdRefCache.new Pin()) {
                    mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
                    mArtdRefCache.getArtd();
                }
                mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
            }
            mArtdRefCache.getArtd();
            deathRecipient.getValue().binderDied(mBinder);
            // Cache miss.
            mArtdRefCache.getArtd();
            mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS - 1);
        }
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS - 1);
        try (var pin = mArtdRefCache.new Pin()) {
            mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS - 1);
            mArtdRefCache.getArtd();
        }
        mMockClock.advanceTime(ArtdRefCache.CACHE_TIMEOUT_MS);
        try (var pin = mArtdRefCache.new Pin()) {
            // Cache miss.
            mArtdRefCache.getArtd();
        }

        verify(mInjector, times(3)).getArtd();
    }
}
