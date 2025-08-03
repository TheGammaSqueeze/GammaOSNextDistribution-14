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

package android.federatedcompute;

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.federatedcompute.ExampleStoreQueryCallbackImpl.IteratorAdapter;
import android.federatedcompute.ExampleStoreQueryCallbackImpl.IteratorCallbackAdapter;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreIteratorCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.TransactionTooLargeException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
public final class ExampleStoreQueryCallbackImplTest {
    private IteratorAdapter mMockIteratorAdaptor;
    private ExampleStoreIterator mMockExampleStoreIterator;
    private IExampleStoreIteratorCallback mMockAidlExampleStoreIteratorCallback;
    private IExampleStoreCallback mMockAidlExampleStoreCallback;

    @Before
    public void setUp() {
        mMockIteratorAdaptor = mock(IteratorAdapter.class);
        mMockExampleStoreIterator = mock(ExampleStoreIterator.class);
        mMockAidlExampleStoreIteratorCallback = mock(IExampleStoreIteratorCallback.class);
        mMockAidlExampleStoreCallback = mock(IExampleStoreCallback.class);
    }

    @Test
    public void testStartQuerySuccessNullResultThrows() throws Exception {
        ExampleStoreQueryCallbackImpl adapter =
                new ExampleStoreQueryCallbackImpl(mMockAidlExampleStoreCallback);
        assertThrows(NullPointerException.class, () -> adapter.onStartQuerySuccess(null));
    }

    @Test
    public void testStartQueryFailureTwicePassThrough() throws Exception {
        ExampleStoreQueryCallbackImpl adapter =
                new ExampleStoreQueryCallbackImpl(mMockAidlExampleStoreCallback);
        adapter.onStartQueryFailure(STATUS_INTERNAL_ERROR);
        adapter.onStartQueryFailure(STATUS_INTERNAL_ERROR);
        verify(mMockAidlExampleStoreCallback, times(2))
                .onStartQueryFailure(eq(STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testStartQuerySuccessTwicePassThrough() throws Exception {
        ExampleStoreQueryCallbackImpl adapter =
                new ExampleStoreQueryCallbackImpl(mMockAidlExampleStoreCallback);
        adapter.onStartQuerySuccess(mMockExampleStoreIterator);
        adapter.onStartQuerySuccess(mMockExampleStoreIterator);
        verify(mMockAidlExampleStoreCallback, times(2)).onStartQuerySuccess(any());
    }

    @Test
    public void testStartQuerySuccessAfterFailurePassThrough() throws Exception {
        ExampleStoreQueryCallbackImpl adapter =
                new ExampleStoreQueryCallbackImpl(mMockAidlExampleStoreCallback);
        adapter.onStartQueryFailure(STATUS_INTERNAL_ERROR);
        adapter.onStartQuerySuccess(mMockExampleStoreIterator);
        verify(mMockAidlExampleStoreCallback).onStartQuerySuccess(any());
    }

    @Test
    public void testStartQueryFailureAfterSuccessPassThrough() throws Exception {
        ExampleStoreQueryCallbackImpl adapter =
                new ExampleStoreQueryCallbackImpl(mMockAidlExampleStoreCallback);
        adapter.onStartQuerySuccess(mMockExampleStoreIterator);
        adapter.onStartQueryFailure(STATUS_INTERNAL_ERROR);
        verify(mMockAidlExampleStoreCallback).onStartQueryFailure(eq(STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testIteratorCloseTwice() throws Exception {
        ExampleStoreQueryCallbackImpl adapter =
                new ExampleStoreQueryCallbackImpl(mMockAidlExampleStoreCallback);
        adapter.onStartQuerySuccess(mMockExampleStoreIterator);
        ArgumentCaptor<IteratorAdapter> iteratorCaptor =
                ArgumentCaptor.forClass(IteratorAdapter.class);
        verify(mMockAidlExampleStoreCallback).onStartQuerySuccess(iteratorCaptor.capture());
        IteratorAdapter iterator = iteratorCaptor.getValue();
        iterator.close();
        iterator.close();
        // The app's iterator should only have been called once.
        verify(mMockExampleStoreIterator).close();
    }

    @Test
    public void testIteratorAdapterCloseTwiceIgnoresSecondCall() throws Exception {
        IteratorAdapter iterator = new IteratorAdapter(mMockExampleStoreIterator);
        iterator.close();
        verify(mMockExampleStoreIterator).close();
        iterator.close();
        // The second call shouldn't result in another call to the app's close() method.
        verify(mMockExampleStoreIterator).close();
    }

    @Test
    public void testIteratorAdapterNextAfterCloseIgnore() throws Exception {
        IteratorAdapter iterator = new IteratorAdapter(mMockExampleStoreIterator);
        iterator.close();
        iterator.next(mMockAidlExampleStoreIteratorCallback);
        // The second call shouldn't result in another call to the app's close() method.
        verify(mMockExampleStoreIterator, never()).next(any());
    }

    /**
     * Tests that additional calls to a the callback are passed through to the proxy. It will be in
     * charge of ignoring all but the first call.
     */
    @Test
    public void testIteratorCallbackSuccessTwicePassThrough() throws Exception {
        IteratorCallbackAdapter adapter =
                new IteratorCallbackAdapter(
                        mMockAidlExampleStoreIteratorCallback, mMockIteratorAdaptor);
        assertThat(adapter.onIteratorNextSuccess(new Bundle())).isTrue();
        assertThat(adapter.onIteratorNextSuccess(new Bundle())).isTrue();
        verify(mMockAidlExampleStoreIteratorCallback, times(2)).onIteratorNextSuccess(any());
    }

    /**
     * Tests that additional calls to a the callback are passed through to the proxy. It will be in
     * charge of ignoring all but the first call.
     */
    @Test
    public void testIteratorCallbackFailureTwicePassThrough() throws Exception {
        IteratorCallbackAdapter adapter =
                new IteratorCallbackAdapter(
                        mMockAidlExampleStoreIteratorCallback, mMockIteratorAdaptor);
        adapter.onIteratorNextFailure(STATUS_INTERNAL_ERROR);
        adapter.onIteratorNextFailure(STATUS_INTERNAL_ERROR);
        verify(mMockAidlExampleStoreIteratorCallback, times(2))
                .onIteratorNextFailure(eq(STATUS_INTERNAL_ERROR));
    }

    /**
     * Tests that additional calls to a the callback are passed through to the proxy. It will be in
     * charge of ignoring all but the first call.
     */
    @Test
    public void testIteratorCallbackSuccessAfterFailurePassThrough() throws Exception {
        IteratorCallbackAdapter adapter =
                new IteratorCallbackAdapter(
                        mMockAidlExampleStoreIteratorCallback, mMockIteratorAdaptor);
        adapter.onIteratorNextFailure(STATUS_INTERNAL_ERROR);
        assertThat(adapter.onIteratorNextSuccess(new Bundle())).isTrue();
        assertThat(adapter.onIteratorNextSuccess(new Bundle())).isTrue();
        verify(mMockAidlExampleStoreIteratorCallback)
                .onIteratorNextFailure(eq(STATUS_INTERNAL_ERROR));
        verify(mMockAidlExampleStoreIteratorCallback, times(2)).onIteratorNextSuccess(any());
    }

    /**
     * Tests that additional calls to a the callback are passed through to the proxy. It will be in
     * charge of ignoring all but the first call.
     */
    @Test
    public void testIteratorCallbackFailureAfterSuccessPassThrough() throws Exception {
        IteratorCallbackAdapter adapter =
                new IteratorCallbackAdapter(
                        mMockAidlExampleStoreIteratorCallback, mMockIteratorAdaptor);
        assertThat(adapter.onIteratorNextSuccess(new Bundle())).isTrue();
        adapter.onIteratorNextFailure(STATUS_INTERNAL_ERROR);
        verify(mMockAidlExampleStoreIteratorCallback).onIteratorNextSuccess(any());
        verify(mMockAidlExampleStoreIteratorCallback)
                .onIteratorNextFailure(eq(STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testIteratorCallbackSuccessRemoteException() throws Exception {
        doThrow(new RemoteException())
                .when(mMockAidlExampleStoreIteratorCallback)
                .onIteratorNextSuccess(any());
        IteratorCallbackAdapter adapter =
                new IteratorCallbackAdapter(
                        mMockAidlExampleStoreIteratorCallback, mMockIteratorAdaptor);
        // Should not throw. Exception should be swallowed, but an error should be returned.
        assertThat(adapter.onIteratorNextSuccess(new Bundle())).isFalse();
        // The corresponding iterator should also be closed when a RemoteException occurs.
        verify(mMockIteratorAdaptor).close();
    }

    @Test
    public void testIteratorCallbackSuccessTransactionTooLargeException() throws Exception {
        doThrow(new TransactionTooLargeException())
                .when(mMockAidlExampleStoreIteratorCallback)
                .onIteratorNextSuccess(any());
        IteratorCallbackAdapter adapter =
                new IteratorCallbackAdapter(
                        mMockAidlExampleStoreIteratorCallback, mMockIteratorAdaptor);
        // Should return an error to the client so they can at least debug when examples are
        // dropped.
        assertThat(adapter.onIteratorNextSuccess(new Bundle())).isFalse();
        // The corresponding iterator should be closed automatically in this case.
        verify(mMockIteratorAdaptor).close();
    }

    @Test
    public void testIteratorCallbackFailureRemoteException() throws Exception {
        doThrow(new RemoteException())
                .when(mMockAidlExampleStoreIteratorCallback)
                .onIteratorNextFailure(anyInt());
        IteratorCallbackAdapter adapter =
                new IteratorCallbackAdapter(
                        mMockAidlExampleStoreIteratorCallback, mMockIteratorAdaptor);
        // Should not throw. Exception should be swallowed.
        adapter.onIteratorNextFailure(STATUS_INTERNAL_ERROR);
        // The corresponding iterator should also be closed when a RemoteException occurs.
        verify(mMockIteratorAdaptor).close();
    }
}
