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

package com.android.server.telecom.tests;

import android.net.Uri;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;


import com.android.server.telecom.Call;
import com.android.server.telecom.Ringer;
import com.android.server.telecom.callfiltering.CallFilteringResult;
import com.android.server.telecom.callfiltering.DndCallFilter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import junit.framework.Assert;

@RunWith(JUnit4.class)
public class DndCallFilteringTests extends TelecomTestCase {

    // mocks
    @Mock private Call mCall;
    @Mock private Ringer mRinger;
    // constants
    private final long FILTER_TIMEOUT = 2000;

    private final CallFilteringResult BASE_RESULT = new CallFilteringResult.Builder()
            .setShouldAllowCall(true)
            .setShouldAddToCallLog(true)
            .setShouldShowNotification(true)
            .build();


    private final CallFilteringResult CALL_SUPPRESSED_RESULT = new CallFilteringResult.Builder()
            .setShouldAllowCall(true)
            .setShouldAddToCallLog(true)
            .setShouldShowNotification(true)
            .setDndSuppressed(true)
            .build();

    private final CallFilteringResult CALL_NOT_SUPPRESSED_RESULT = new CallFilteringResult.Builder()
            .setShouldAllowCall(true)
            .setShouldAddToCallLog(true)
            .setShouldShowNotification(true)
            .setDndSuppressed(false)
            .build();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Dynamic variables
        Uri testHandle = Uri.parse("tel:1235551234");
        when(mCall.getHandle()).thenReturn(testHandle);
        when(mCall.wasDndCheckComputedForCall()).thenReturn(false);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test DndCallFilter suppresses a call and builds a CALL_SUPPRESSED_RESULT when given
     * a false shouldRingForContact answer.
     *
     * @throws Exception; should not throw
     */
    @Test
    public void testShouldSuppressCall() throws Exception {
        // GIVEN
        DndCallFilter filter = new DndCallFilter(mCall, mRinger);

        // WHEN
        assertNotNull(filter);
        when(mRinger.shouldRingForContact(mCall)).thenReturn(false);

        // THEN
        CompletionStage<CallFilteringResult> resultFuture = filter.startFilterLookup(BASE_RESULT);

        Assert.assertEquals(CALL_SUPPRESSED_RESULT, resultFuture.toCompletableFuture()
                .get(FILTER_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    /**
     * Test DndCallFilter allows a call to ring and builds a CALL_NOT_SUPPRESSED_RESULT when
     * given a true shouldRingForContact answer.
     *
     * @throws Exception; should not throw
     */
    @Test
    public void testCallShouldRingAndNotBeSuppressed() throws Exception {
        // GIVEN
        DndCallFilter filter = new DndCallFilter(mCall, mRinger);

        // WHEN
        assertNotNull(filter);
        when(mRinger.shouldRingForContact(mCall)).thenReturn(true);

        // THEN
        CompletionStage<CallFilteringResult> resultFuture = filter.startFilterLookup(BASE_RESULT);

        // ASSERT
        Assert.assertEquals(CALL_NOT_SUPPRESSED_RESULT, resultFuture.toCompletableFuture()
                .get(FILTER_TIMEOUT, TimeUnit.MILLISECONDS));
    }
}