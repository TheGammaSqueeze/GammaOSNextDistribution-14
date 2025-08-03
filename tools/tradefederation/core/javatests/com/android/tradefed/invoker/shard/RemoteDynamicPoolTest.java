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
package com.android.tradefed.invoker.shard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.shard.token.ITokenRequest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.ITestSuite;

import com.google.internal.android.engprod.v1.RequestTestTargetResponse;
import com.google.internal.android.engprod.v1.SerializedTestTarget;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link RemoteDynamicPool} */
@RunWith(JUnit4.class)
public class RemoteDynamicPoolTest {
    private IDynamicShardingClient mMockClient;
    private String mFakePoolId = "testPoolId123";
    private Map<String, ITestSuite> mMockModuleMapping;
    private RemoteDynamicPool mPool;

    @Before
    public void setUp() {
        mMockClient = Mockito.mock(IDynamicShardingClient.class);
        mMockModuleMapping = new HashMap<>();
        mMockModuleMapping.put("FakeTFUnitTests", Mockito.mock(ITestSuite.class));
        mPool = RemoteDynamicPool.newInstance(mMockClient, mFakePoolId, mMockModuleMapping);
    }

    @Test
    public void testPollRejectedTokenModule() {
        ITokenRequest result = mPool.pollRejectedTokenModule();
        assertEquals(null, result);
    }

    @Test
    public void testPollNormal() {
        RequestTestTargetResponse fakeResponse =
                RequestTestTargetResponse.newBuilder()
                        .addTestTargets(
                                SerializedTestTarget.newBuilder().setTargetName("FakeTFUnitTests"))
                        .build();
        Mockito.doReturn(fakeResponse).when(mMockClient).requestTestTarget(Mockito.any());
        TestInformation fakeInfo = Mockito.mock(TestInformation.class);
        IRemoteTest test = mPool.poll(fakeInfo, true);
        assertNotNull(test);
    }
}
