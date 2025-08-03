/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.content.cts;

import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.AttributionSource;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextParams;
import android.content.IContentProvider;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ICancellationSignal;
import android.os.OperationCanceledException;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;

import org.mockito.stubbing.Answer;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple delegation test for {@link ContentProviderClient}, checking the right methods are called.
 * Actual {@link ContentProvider} functionality is tested in {@link ContentProviderTest}.
 */
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ContentProviderClientTest extends AndroidTestCase {

    private static final Answer ANSWER_SLEEP = invocation -> {
        // Sleep long enough to trigger ANR
        Thread.sleep(100);
        return null;
    };

    private static final String PACKAGE_NAME = "android.content.cts";
    private static final String FEATURE_ID = "testFeature";
    private static final String MODE = "mode";
    private static final String AUTHORITY = "authority";
    private static final String METHOD = "method";
    private static final String ARG = "arg";
    private static final Uri URI = Uri.parse("com.example.app://path");
    private static final Bundle ARGS = new Bundle();
    private static final Bundle EXTRAS = new Bundle();
    private static final ContentValues VALUES = new ContentValues();
    private static final ContentValues[] VALUES_ARRAY = {VALUES};
    private static final ArrayList<ContentProviderOperation> OPS = new ArrayList<>();

    private ContentResolver mContentResolver;
    private IContentProvider mIContentProvider;
    private ContentProviderClient mContentProviderClient;
    private AttributionSource mAttributionSource;

    private CancellationSignal mCancellationSignal = new CancellationSignal();
    private ICancellationSignal mICancellationSignal;
    private boolean mCalledCancel = false;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mIContentProvider = mock(IContentProvider.class, RETURNS_DEFAULTS);
        mICancellationSignal = mock(ICancellationSignal.class);

        doReturn(mICancellationSignal).when(mIContentProvider).createCancellationSignal();

        final Context attributionContext = getContext().createContext(
                new ContextParams.Builder()
                        .setAttributionTag(FEATURE_ID)
                        .build());

        mAttributionSource = attributionContext.getAttributionSource();
        mContentResolver = spy(new MockContentResolver(attributionContext));
        mContentProviderClient = spy(new ContentProviderClient(mContentResolver, mIContentProvider,
                false));

        mCalledCancel = false;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (!mCalledCancel) {
            // Client should never cancel unless the test called cancel
            assertFalse(mCancellationSignal.isCanceled());
            verify(mICancellationSignal, never()).cancel();
        }
    }

    public void testQuery() throws RemoteException {
        mContentProviderClient.query(URI, null, ARGS, mCancellationSignal);
        verify(mIContentProvider).query(mAttributionSource, URI, null, ARGS,
                mICancellationSignal);
    }

    public void testQueryTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).query(mAttributionSource, URI, null,
                ARGS, mICancellationSignal);

        testTimeout(() -> mContentProviderClient.query(URI, null, ARGS, mCancellationSignal));

        verify(mIContentProvider, after(150)).query(mAttributionSource, URI, null, ARGS,
                mICancellationSignal);
    }

    public void testQueryAlreadyCancelled() throws Exception {
        testAlreadyCancelled(
                () -> mContentProviderClient.query(URI, null, ARGS, mCancellationSignal));
        verify(mIContentProvider, never()).query(mAttributionSource, URI, null, ARGS,
                mICancellationSignal);
    }

    public void testGetType() throws RemoteException {
        mContentProviderClient.getType(URI);
        verify(mIContentProvider).getType(mAttributionSource, URI);
    }

    public void testGetTypeTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).getType(mAttributionSource, URI);

        testTimeout(() -> mContentProviderClient.getType(URI));

        verify(mIContentProvider, after(150)).getType(mAttributionSource, URI);
    }

    public void testGetStreamTypes() throws RemoteException {
        mContentProviderClient.getStreamTypes(URI, "");
        verify(mIContentProvider).getStreamTypes(mAttributionSource, URI, "");
    }

    public void testGetStreamTypesTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).getStreamTypes(mAttributionSource,
                URI, "");

        testTimeout(() -> mContentProviderClient.getStreamTypes(URI, ""));

        verify(mIContentProvider, after(150)).getStreamTypes(mAttributionSource, URI, "");
    }

    public void testCanonicalize() throws RemoteException {
        mContentProviderClient.canonicalize(URI);
        verify(mIContentProvider).canonicalize(mAttributionSource, URI);
    }

    public void testCanonicalizeTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).canonicalize(mAttributionSource, URI);

        testTimeout(() -> mContentProviderClient.canonicalize(URI));

        verify(mIContentProvider, after(150)).canonicalize(mAttributionSource, URI);
    }

    public void testUncanonicalize() throws RemoteException {
        mContentProviderClient.uncanonicalize(URI);
        verify(mIContentProvider).uncanonicalize(mAttributionSource, URI);
    }

    public void testUncanonicalizeTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).uncanonicalize(mAttributionSource, URI);

        testTimeout(() -> mContentProviderClient.uncanonicalize(URI));

        verify(mIContentProvider, after(150)).uncanonicalize(mAttributionSource, URI);
    }

    public void testRefresh() throws RemoteException {
        mContentProviderClient.refresh(URI, ARGS, mCancellationSignal);
        verify(mIContentProvider).refresh(mAttributionSource, URI, ARGS,
                mICancellationSignal);
    }

    public void testRefreshTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).refresh(mAttributionSource, URI, ARGS,
                mICancellationSignal);

        testTimeout(() -> mContentProviderClient.refresh(URI, ARGS, mCancellationSignal));

        verify(mIContentProvider, after(150)).refresh(mAttributionSource, URI, ARGS,
                mICancellationSignal);
    }

    public void testRefreshAlreadyCancelled() throws Exception {
        testAlreadyCancelled(() -> mContentProviderClient.refresh(URI, ARGS, mCancellationSignal));
        verify(mIContentProvider, never()).refresh(mAttributionSource, URI, ARGS,
                mICancellationSignal);
    }

    public void testInsert() throws RemoteException {
        mContentProviderClient.insert(URI, VALUES, EXTRAS);
        verify(mIContentProvider).insert(mAttributionSource, URI, VALUES, EXTRAS);
    }

    public void testInsertTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).insert(mAttributionSource, URI,
                VALUES, EXTRAS);

        testTimeout(() -> mContentProviderClient.insert(URI, VALUES, EXTRAS));

        verify(mIContentProvider, after(150)).insert(mAttributionSource, URI, VALUES, EXTRAS);
    }

    public void testBulkInsert() throws RemoteException {
        mContentProviderClient.bulkInsert(URI, VALUES_ARRAY);
        verify(mIContentProvider).bulkInsert(mAttributionSource, URI, VALUES_ARRAY);
    }

    public void testBulkInsertTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).bulkInsert(mAttributionSource, URI,
                VALUES_ARRAY);

        testTimeout(() -> mContentProviderClient.bulkInsert(URI, VALUES_ARRAY));

        verify(mIContentProvider, after(150)).bulkInsert(mAttributionSource, URI, VALUES_ARRAY);
    }

    public void testDelete() throws RemoteException {
        mContentProviderClient.delete(URI, EXTRAS);
        verify(mIContentProvider).delete(mAttributionSource, URI, EXTRAS);
    }

    public void testDeleteTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).delete(mAttributionSource, URI, EXTRAS);

        testTimeout(() -> mContentProviderClient.delete(URI, EXTRAS));

        verify(mIContentProvider, after(150)).delete(mAttributionSource, URI, EXTRAS);
    }

    public void testUpdate() throws RemoteException {
        mContentProviderClient.update(URI, VALUES, EXTRAS);
        verify(mIContentProvider).update(mAttributionSource, URI, VALUES, EXTRAS);
    }

    public void testUpdateTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).update(mAttributionSource, URI,
                VALUES, EXTRAS);

        testTimeout(() -> mContentProviderClient.update(URI, VALUES, EXTRAS));

        verify(mIContentProvider, after(150)).update(mAttributionSource, URI, VALUES, EXTRAS);
    }

    public void testOpenFile() throws RemoteException, FileNotFoundException {
        mContentProviderClient.openFile(URI, MODE, mCancellationSignal);

        verify(mIContentProvider).openFile(mAttributionSource, URI, MODE, mICancellationSignal);
    }

    public void testOpenFileTimeout()
            throws RemoteException, InterruptedException, FileNotFoundException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).openFile(mAttributionSource,
                URI, MODE, mICancellationSignal);

        testTimeout(() -> mContentProviderClient.openFile(URI, MODE, mCancellationSignal));

        verify(mIContentProvider, after(150)).openFile(mAttributionSource, URI, MODE,
                mICancellationSignal);
    }

    public void testOpenFileAlreadyCancelled() throws Exception {
        testAlreadyCancelled(() -> mContentProviderClient.openFile(URI, MODE, mCancellationSignal));

        verify(mIContentProvider, never()).openFile(mAttributionSource, URI, MODE,
                mICancellationSignal);
    }

    public void testOpenAssetFile() throws RemoteException, FileNotFoundException {
        mContentProviderClient.openAssetFile(URI, MODE, mCancellationSignal);

        verify(mIContentProvider).openAssetFile(mAttributionSource, URI, MODE, mICancellationSignal);
    }

    public void testOpenAssetFileTimeout()
            throws RemoteException, InterruptedException, FileNotFoundException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).openAssetFile(mAttributionSource,
                URI, MODE, mICancellationSignal);

        testTimeout(() -> mContentProviderClient.openAssetFile(URI, MODE, mCancellationSignal));

        verify(mIContentProvider, after(150)).openAssetFile(mAttributionSource, URI, MODE,
                mICancellationSignal);
    }

    public void testOpenAssetFileAlreadyCancelled() throws Exception {
        testAlreadyCancelled(
                () -> mContentProviderClient.openAssetFile(URI, MODE, mCancellationSignal));

        verify(mIContentProvider, never()).openAssetFile(mAttributionSource, URI, MODE,
                mICancellationSignal);
    }

    public void testOpenTypedAssetFileDescriptor() throws RemoteException, FileNotFoundException {
        mContentProviderClient.openTypedAssetFileDescriptor(URI, MODE, ARGS, mCancellationSignal);

        verify(mIContentProvider).openTypedAssetFile(mAttributionSource, URI, MODE, ARGS,
                mICancellationSignal);
    }

    public void testOpenTypedAssetFile() throws RemoteException, FileNotFoundException {
        mContentProviderClient.openTypedAssetFile(URI, MODE, ARGS, mCancellationSignal);

        verify(mIContentProvider).openTypedAssetFile(mAttributionSource, URI, MODE, ARGS,
                mICancellationSignal);
    }

    public void testOpenTypedAssetFileTimeout()
            throws RemoteException, InterruptedException, FileNotFoundException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).openTypedAssetFile(mAttributionSource,
                URI, MODE, ARGS, mICancellationSignal);

        testTimeout(() -> mContentProviderClient.openTypedAssetFile(URI, MODE, ARGS,
                mCancellationSignal));

        verify(mIContentProvider, after(150)).openTypedAssetFile(mAttributionSource, URI, MODE,
                ARGS, mICancellationSignal);
    }

    public void testOpenTypedAssetFileAlreadyCancelled() throws Exception {
        testAlreadyCancelled(
                () -> mContentProviderClient.openTypedAssetFile(URI, MODE, ARGS,
                        mCancellationSignal));

        verify(mIContentProvider, never()).openTypedAssetFile(mAttributionSource, URI, MODE,
                ARGS, mICancellationSignal);
    }

    public void testApplyBatch() throws RemoteException, OperationApplicationException {
        mContentProviderClient.applyBatch(AUTHORITY, OPS);

        verify(mIContentProvider).applyBatch(mAttributionSource, AUTHORITY, OPS);
    }

    public void testApplyBatchTimeout()
            throws RemoteException, InterruptedException, OperationApplicationException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).applyBatch(mAttributionSource,
                AUTHORITY, OPS);

        testTimeout(() -> mContentProviderClient.applyBatch(AUTHORITY, OPS));

        verify(mIContentProvider, after(150)).applyBatch(mAttributionSource, AUTHORITY, OPS);
    }

    public void testCall() throws RemoteException {
        mContentProviderClient.call(AUTHORITY, METHOD, ARG, ARGS);

        verify(mIContentProvider).call(mAttributionSource, AUTHORITY, METHOD, ARG, ARGS);
    }

    public void testCallTimeout() throws RemoteException, InterruptedException {
        doAnswer(ANSWER_SLEEP).when(mIContentProvider).call(mAttributionSource, AUTHORITY,
                METHOD, ARG, ARGS);

        testTimeout(() -> mContentProviderClient.call(AUTHORITY, METHOD, ARG, ARGS));

        verify(mIContentProvider, after(150)).call(mAttributionSource, AUTHORITY, METHOD, ARG,
                ARGS);
    }

    private void testTimeout(Function function) throws InterruptedException {
        mContentProviderClient.setDetectNotResponding(1);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        })
                .when(mContentResolver)
                .appNotRespondingViaProvider(mIContentProvider);

        new Thread(() -> {
            try {
                function.run();
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        }).start();

        latch.await(100, TimeUnit.MILLISECONDS);
        assertEquals(0, latch.getCount());

        verify(mContentResolver).appNotRespondingViaProvider(mIContentProvider);
    }

    private void testAlreadyCancelled(Function function) throws Exception {
        mCancellationSignal.cancel();
        mCalledCancel = true;

        try {
            function.run();
            fail("Expected OperationCanceledException");
        } catch (OperationCanceledException expected) {
        }
    }

    private interface Function {
        void run() throws Exception;
    }
}
