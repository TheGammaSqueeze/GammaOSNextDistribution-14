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

package com.android.adservices.service.adid;

import static org.mockito.Mockito.verify;

import android.adservices.adid.AdId;
import android.adservices.adid.IGetAdIdCallback;
import android.adservices.common.UpdateAdIdRequest;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Unit test for {@link com.android.adservices.service.adid.AdIdWorker}. */
public final class AdIdWorkerTest extends AdServicesMockitoTestCase {
    private static final String PACKAGE_NAME = "package_name";
    private static final int DUMMY_CALLER_UID = 0;
    private static final IGetAdIdCallback CALLBACK = new IGetAdIdCallback.Default();

    private AdIdWorker mAdIdWorker;

    @Mock private AdIdCacheManager mMockAdIdCacheManager;

    @Before
    public void setup() {
        mAdIdWorker = new AdIdWorker(mMockAdIdCacheManager);
    }

    @Test
    public void testGetAdId() {
        mAdIdWorker.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, CALLBACK);

        verify(mMockAdIdCacheManager).getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, CALLBACK);
    }

    @Test
    public void testUpdateAdId() {
        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build();

        mAdIdWorker.updateAdId(request);

        verify(mMockAdIdCacheManager).updateAdId(request);
    }
}
