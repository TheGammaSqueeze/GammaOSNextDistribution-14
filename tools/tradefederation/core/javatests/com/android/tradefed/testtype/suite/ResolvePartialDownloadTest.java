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
package com.android.tradefed.testtype.suite;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.invoker.TestInformation;

import com.google.common.truth.Truth;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link ResolvePartialDownload}. */
@RunWith(JUnit4.class)
public class ResolvePartialDownloadTest {

    private ResolvePartialDownload mResolveFeature;
    @Mock DynamicRemoteFileResolver mResolver;
    @Mock TestInformation mTestInformation;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mResolveFeature = new ResolvePartialDownload(mResolver);
        mResolveFeature.setTestInformation(mTestInformation);
    }

    @Test
    public void testRemotePaths() throws Exception {
        doReturn(new BuildInfo()).when(mTestInformation).getBuildInfo();

        FeatureRequest.Builder builder = FeatureRequest.newBuilder();
        Map<String, String> args = new HashMap<>();
        args.put(ResolvePartialDownload.DESTINATION_DIR, "dest_dir");
        args.put(ResolvePartialDownload.REMOTE_PATHS, "path1");
        builder.putAllArgs(args);

        FeatureResponse rep = mResolveFeature.execute(builder.build());
        Truth.assertThat(rep.hasErrorInfo()).isFalse();

        verify(mResolver)
                .resolvePartialDownloadZip(
                        Mockito.any(), Mockito.eq("path1"), Mockito.isNull(), Mockito.isNull());
    }

    @Test
    public void testRemotePaths_multi() throws Exception {
        doReturn(new BuildInfo()).when(mTestInformation).getBuildInfo();

        FeatureRequest.Builder builder = FeatureRequest.newBuilder();
        Map<String, String> args = new HashMap<>();
        args.put(ResolvePartialDownload.DESTINATION_DIR, "dest_dir");
        args.put(ResolvePartialDownload.REMOTE_PATHS, "path1;path2");
        builder.putAllArgs(args);

        FeatureResponse rep = mResolveFeature.execute(builder.build());
        Truth.assertThat(rep.hasErrorInfo()).isFalse();

        verify(mResolver)
                .resolvePartialDownloadZip(
                        Mockito.any(), Mockito.eq("path1"), Mockito.isNull(), Mockito.isNull());
        verify(mResolver)
                .resolvePartialDownloadZip(
                        Mockito.any(), Mockito.eq("path2"), Mockito.isNull(), Mockito.isNull());
    }
}
