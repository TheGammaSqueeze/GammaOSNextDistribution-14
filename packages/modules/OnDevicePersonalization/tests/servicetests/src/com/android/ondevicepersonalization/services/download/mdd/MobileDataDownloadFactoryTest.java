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

package com.android.ondevicepersonalization.services.download.mdd;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MobileDataDownloadFactoryTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testSingletonInstance() {
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        MobileDataDownload mdd1 = MobileDataDownloadFactory
                .getMdd(mContext, executorService, executorService);
        MobileDataDownload mdd2 = MobileDataDownloadFactory.getMdd(mContext);
        assertEquals(mdd1, mdd2);
    }

}
