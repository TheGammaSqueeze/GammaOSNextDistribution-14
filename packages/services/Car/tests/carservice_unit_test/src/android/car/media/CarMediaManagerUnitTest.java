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

package android.car.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.test.AbstractExpectableTestCase;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CarMediaManagerUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_MODE = CarMediaManager.MEDIA_SOURCE_MODE_BROWSE;
    private static final int TEST_USER_ID = 100;
    @Mock
    private Car mCarMock;
    @Mock
    private IBinder mBinderMock;
    @Mock
    private Context mContextMock;
    @Mock
    private ComponentName mComponentNameMock1;
    @Mock
    private ComponentName mComponentNameMock2;
    @Mock
    private ICarMedia mServiceMock;
    @Mock
    private CarMediaManager.MediaSourceChangedListener mListenerMock;

    private RemoteException mRemoteException = new RemoteException();
    private CarMediaManager mMediaManager;

    @Before
    public void setUp() {
        when(mBinderMock.queryLocalInterface(anyString())).thenReturn(mServiceMock);
        when(mCarMock.getContext()).thenReturn(mContextMock);
        when(mContextMock.getUser()).thenReturn(UserHandle.of(TEST_USER_ID));
        mMediaManager = new CarMediaManager(mCarMock, mBinderMock);
        doAnswer(invocation -> invocation.getArgument(1)).when(mCarMock)
                .handleRemoteExceptionFromCarService(any(RemoteException.class), any());
    }

    @Test
    public void testGetMediaSource() throws Exception {
        when(mServiceMock.getMediaSource(TEST_MODE, TEST_USER_ID)).thenReturn(mComponentNameMock1);

        expectWithMessage("Media source for browse mode").that(mMediaManager.getMediaSource(
                TEST_MODE)).isEqualTo(mComponentNameMock1);
    }

    @Test
    public void testGetMediaSource_whenServiceThrowsRemoteException_returnsNull() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getMediaSource(TEST_MODE, TEST_USER_ID);

        expectWithMessage("Media source when service throws remote exception")
                .that(mMediaManager.getMediaSource(TEST_MODE)).isNull();
        verify(mCarMock).handleRemoteExceptionFromCarService(mRemoteException, null);
    }

    @Test
    public void testSetMediaSource() throws Exception {
        mMediaManager.setMediaSource(mComponentNameMock1, TEST_MODE);

        verify(mServiceMock).setMediaSource(mComponentNameMock1, TEST_MODE, TEST_USER_ID);
    }

    @Test
    public void testSetMediaSource_whenServiceThrowsRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock)
                .setMediaSource(mComponentNameMock1, TEST_MODE, TEST_USER_ID);

        mMediaManager.setMediaSource(mComponentNameMock1, TEST_MODE);

        verify(mCarMock).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void testAddMediaSourceListener() throws Exception {
        mMediaManager.addMediaSourceListener(mListenerMock, TEST_MODE);

        verify(mServiceMock).registerMediaSourceListener(any(), eq(TEST_MODE), eq(TEST_USER_ID));
    }

    @Test
    public void testAddMediaSourceListener_whenServiceThrowsRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).registerMediaSourceListener(any(),
                eq(TEST_MODE), eq(TEST_USER_ID));

        mMediaManager.addMediaSourceListener(mListenerMock, TEST_MODE);

        verify(mCarMock).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void testOnMediaSourceChanged_forMediaSourceListener() throws Exception {
        ArgumentCaptor<ICarMediaSourceListener> serviceListenerCaptor = ArgumentCaptor.forClass(
                ICarMediaSourceListener.class);
        mMediaManager.addMediaSourceListener(mListenerMock, TEST_MODE);
        verify(mServiceMock).registerMediaSourceListener(serviceListenerCaptor.capture(),
                eq(TEST_MODE), eq(TEST_USER_ID));
        ICarMediaSourceListener serviceListener = serviceListenerCaptor.getValue();

        serviceListener.onMediaSourceChanged(mComponentNameMock2);

        verify(mListenerMock).onMediaSourceChanged(mComponentNameMock2);
    }

    @Test
    public void testRemoveMediaSourceListener() throws Exception {
        ArgumentCaptor<ICarMediaSourceListener> serviceListenerCaptor = ArgumentCaptor.forClass(
                ICarMediaSourceListener.class);
        mMediaManager.addMediaSourceListener(mListenerMock, TEST_MODE);
        verify(mServiceMock).registerMediaSourceListener(serviceListenerCaptor.capture(),
                eq(TEST_MODE), eq(TEST_USER_ID));

        mMediaManager.removeMediaSourceListener(mListenerMock, TEST_MODE);

        verify(mServiceMock).unregisterMediaSourceListener(serviceListenerCaptor.getValue(),
                TEST_MODE, TEST_USER_ID);
    }

    @Test
    public void testRemoveMediaSourceListener_whenServiceThrowsRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).unregisterMediaSourceListener(any(),
                eq(TEST_MODE), eq(TEST_USER_ID));

        mMediaManager.removeMediaSourceListener(mListenerMock, TEST_MODE);

        verify(mCarMock).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void testGetLastMediaSources() throws RemoteException {
        List<ComponentName> lastMediaSources = List.of(mComponentNameMock1, mComponentNameMock1);
        when(mServiceMock.getLastMediaSources(TEST_MODE, TEST_USER_ID))
                .thenReturn(lastMediaSources);

        expectWithMessage("Last media sources").that(mMediaManager.getLastMediaSources(
                TEST_MODE)).containsExactlyElementsIn(lastMediaSources);
    }

    @Test
    public void testGetLastMediaSources_whenServiceThrowsRemoteException_returnsNull() throws
            RemoteException {
        doThrow(mRemoteException).when(mServiceMock).getLastMediaSources(TEST_MODE, TEST_USER_ID);

        expectWithMessage("Last media sources").that(mMediaManager.getLastMediaSources(
                TEST_MODE)).isNull();
        verify(mCarMock).handleRemoteExceptionFromCarService(mRemoteException, null);
    }

    @Test
    public void testIsIndependentPlaybackConfig() throws RemoteException {
        when(mServiceMock.isIndependentPlaybackConfig(TEST_USER_ID)).thenReturn(true);

        expectWithMessage("Independent playback config").that(mMediaManager
                .isIndependentPlaybackConfig()).isTrue();
    }

    @Test
    public void testIsIndependentPlaybackConfig_whenServiceThrowsRemoteException_returnFalse()
            throws RemoteException {
        doThrow(mRemoteException).when(mServiceMock).isIndependentPlaybackConfig(TEST_USER_ID);

        expectWithMessage("Independent playback config when service throws remote exception")
                .that(mMediaManager.isIndependentPlaybackConfig()).isFalse();
        verify(mCarMock).handleRemoteExceptionFromCarService(mRemoteException, false);
    }

    @Test
    public void testSetIndependentPlaybackConfig() throws
            RemoteException {
        mMediaManager.setIndependentPlaybackConfig(true);

        verify(mServiceMock).setIndependentPlaybackConfig(true, TEST_USER_ID);
    }

    @Test
    public void testSetIndependentPlaybackConfig_whenServiceThrowsRemoteException() throws
            RemoteException {
        doThrow(mRemoteException).when(mServiceMock)
                .setIndependentPlaybackConfig(true, TEST_USER_ID);

        mMediaManager.setIndependentPlaybackConfig(true);

        verify(mCarMock).handleRemoteExceptionFromCarService(mRemoteException);
    }
}
