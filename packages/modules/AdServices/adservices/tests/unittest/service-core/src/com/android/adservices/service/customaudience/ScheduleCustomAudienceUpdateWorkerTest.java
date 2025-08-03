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

package com.android.adservices.service.customaudience;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ScheduleCustomAudienceUpdateWorkerTest {

    private ScheduleCustomAudienceUpdateWorker mUpdateWorker;
    @Mock ScheduledUpdatesHandler mHandlerMock;
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Test
    public void testUpdateWorkerInvokedHandler()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mHandlerMock.performScheduledUpdates(any(Instant.class)))
                .thenReturn(FluentFuture.from(Futures.immediateVoidFuture()));
        mUpdateWorker = new ScheduleCustomAudienceUpdateWorker(mHandlerMock);
        Void ignored = mUpdateWorker.updateCustomAudience().get(5, TimeUnit.SECONDS);
        Mockito.verify(mHandlerMock).performScheduledUpdates(any(Instant.class));
    }
}
