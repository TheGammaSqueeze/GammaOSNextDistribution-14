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

package com.android.adservices.service.adselection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.service.devapi.DevContext;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class DebugReportSenderStrategyBatchImplTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final DevContext DEV_CONTEXT = DevContext.createForDevOptionsDisabled();
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    private DebugReportSenderStrategyBatchImpl mDebugReportSender;
    private MockitoSession mStaticMockSession;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ExtendedMockito.doNothing()
                .when(mAdSelectionDebugReportDao)
                .persistAdSelectionDebugReporting(anyList());
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DebugReportSenderJobService.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        ExtendedMockito.doNothing()
                .when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), anyBoolean()));
        mDebugReportSender =
                new DebugReportSenderStrategyBatchImpl(
                        CONTEXT, mAdSelectionDebugReportDao, DEV_CONTEXT);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testConstructorWithInvalidParamsThrowsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        new DebugReportSenderStrategyBatchImpl(
                                null, mAdSelectionDebugReportDao, DEV_CONTEXT));
        Assert.assertThrows(
                NullPointerException.class,
                () -> new DebugReportSenderStrategyBatchImpl(CONTEXT, null, DEV_CONTEXT));
        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        new DebugReportSenderStrategyBatchImpl(
                                CONTEXT, mAdSelectionDebugReportDao, null));
    }

    @Test
    public void testEnqueue() {
        Uri uri = Uri.parse("https://example.com/reportWin");
        mDebugReportSender.enqueue(uri);
    }

    @Test
    public void testEnqueueNullUriThrowsException() {
        Assert.assertThrows(NullPointerException.class, () -> mDebugReportSender.enqueue(null));
    }

    @Test
    public void testBatchEnqueue() {
        Uri uri1 = Uri.parse("https://example.com/reportWin");
        Uri uri2 = Uri.parse("https://example.com/reportLoss");
        mDebugReportSender.batchEnqueue(List.of(uri1, uri2));
    }

    @Test
    public void testBatchEnqueueNullUriThrowsException() {
        Assert.assertThrows(
                NullPointerException.class, () -> mDebugReportSender.batchEnqueue(null));
    }

    @Test
    public void testSend_withSingleReport_allRequestsAreSuccessful()
            throws ExecutionException, InterruptedException {
        Uri uri = Uri.parse("https://example.com/reportWin");
        mDebugReportSender.enqueue(uri);

        mDebugReportSender.flush().get();

        ExtendedMockito.verify(mAdSelectionDebugReportDao, times(1))
                .persistAdSelectionDebugReporting(anyList());
    }

    @Test
    public void testSend_withMultipleReports_allRequestsAreSuccessful()
            throws ExecutionException, InterruptedException {

        Uri uri1 = Uri.parse("https://example.com/reportWin");
        Uri uri2 = Uri.parse("https://example.com/reportLoss");
        mDebugReportSender.batchEnqueue(List.of(uri1, uri2));

        mDebugReportSender.flush().get();

        ExtendedMockito.verify(mAdSelectionDebugReportDao, times(1))
                .persistAdSelectionDebugReporting(anyList());
    }

    @Test
    public void testSend_withMultipleReports_schedulesSenderJob()
            throws ExecutionException, InterruptedException {
        Uri uri1 = Uri.parse("https://example.com/reportWin");
        Uri uri2 = Uri.parse("https://example.com/reportLoss");
        mDebugReportSender.batchEnqueue(List.of(uri1, uri2));

        mDebugReportSender.flush().get();

        ExtendedMockito.verify(
                () ->
                        DebugReportSenderJobService.scheduleIfNeeded(
                                any(Context.class), anyBoolean()),
                times(1));
    }

    @Test
    public void testSend_withNoReports_doesNotScheduleJob()
            throws ExecutionException, InterruptedException {
        mDebugReportSender.batchEnqueue(List.of());

        mDebugReportSender.flush().get();

        ExtendedMockito.verify(mAdSelectionDebugReportDao, never())
                .persistAdSelectionDebugReporting(anyList());
        ExtendedMockito.verify(
                () ->
                        DebugReportSenderJobService.scheduleIfNeeded(
                                any(Context.class), anyBoolean()),
                never());
    }
}
