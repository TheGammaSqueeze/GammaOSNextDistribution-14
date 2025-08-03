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

package com.android.art;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.server.art.IArtd;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.IllegalStateException;

/**
 * Integration tests for Artd.
 *
 * Run with "atest ArtdIntegrationTests".
 */
@RunWith(JUnit4.class)
public final class ArtdIntegrationTests {

    private static final String TAG = "ArtdTests";

    private static IArtd mArtd;

    @Before
    public void setUp() {
        IBinder rawBinderService = ServiceManager.getService("artd");
        if (rawBinderService == null) {
            throw new IllegalStateException("Unable to fetch artd service from binder.");
        }

        mArtd = IArtd.Stub.asInterface(rawBinderService);
    }

    // Test basic build and publish functionality for Artd.
    @Test
    public void testLiveness() throws RemoteException {
        Assert.assertTrue(mArtd.isAlive());
    }
}
