/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.discovery;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link DiscoveryProvider} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DiscoveryProviderTest {

    /** Fake implementation of the DiscoveryProvider for testing. */
    static class FakeDiscoveryProvider extends DiscoveryProvider {

        @Override
        public boolean start() {
            if (!super.start()) {
                return false;
            }
            mStarted = true;
            return true;
        }

        @Override
        public boolean stop() {
            if (!super.stop()) {
                return false;
            }
            mStarted = false;
            return true;
        }
    }

    private FakeDiscoveryProvider mFakeDiscoveryProvider;
    private DiscoveryProvider mDiscoveryProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFakeDiscoveryProvider = new FakeDiscoveryProvider();
        mDiscoveryProvider = mFakeDiscoveryProvider;
    }

    @Test
    public void testStartAndStop() {
        assertThat(mDiscoveryProvider.start()).isTrue();
        assertThat(mDiscoveryProvider.start()).isFalse();
        assertThat(mDiscoveryProvider.stop()).isTrue();
        assertThat(mDiscoveryProvider.stop()).isFalse();
        assertThat(mDiscoveryProvider.start()).isTrue();
    }
}
