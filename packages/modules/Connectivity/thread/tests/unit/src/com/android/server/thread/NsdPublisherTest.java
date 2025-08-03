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

package com.android.server.thread;

import static android.net.nsd.NsdManager.FAILURE_INTERNAL_ERROR;
import static android.net.nsd.NsdManager.PROTOCOL_DNS_SD;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.test.TestLooper;

import com.android.server.thread.openthread.DnsTxtAttribute;
import com.android.server.thread.openthread.INsdStatusReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/** Unit tests for {@link NsdPublisher}. */
public final class NsdPublisherTest {
    @Mock private NsdManager mMockNsdManager;

    @Mock private INsdStatusReceiver mRegistrationReceiver;
    @Mock private INsdStatusReceiver mUnregistrationReceiver;

    private TestLooper mTestLooper;
    private NsdPublisher mNsdPublisher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void registerService_nsdManagerSucceeds_serviceRegistrationSucceeds() throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mTestLooper.dispatchAll();

        assertThat(actualServiceInfo.getServiceName()).isEqualTo("MyService");
        assertThat(actualServiceInfo.getServiceType()).isEqualTo("_test._tcp");
        assertThat(actualServiceInfo.getSubtypes()).isEqualTo(Set.of("_subtype1", "_subtype2"));
        assertThat(actualServiceInfo.getPort()).isEqualTo(12345);
        assertThat(actualServiceInfo.getAttributes().size()).isEqualTo(2);
        assertThat(actualServiceInfo.getAttributes().get("key1"))
                .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x02});
        assertThat(actualServiceInfo.getAttributes().get("key2"))
                .isEqualTo(new byte[] {(byte) 0x03});

        verify(mRegistrationReceiver, times(1)).onSuccess();
    }

    @Test
    public void registerService_nsdManagerFails_serviceRegistrationFails() throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onRegistrationFailed(actualServiceInfo, FAILURE_INTERNAL_ERROR);
        mTestLooper.dispatchAll();

        assertThat(actualServiceInfo.getServiceName()).isEqualTo("MyService");
        assertThat(actualServiceInfo.getServiceType()).isEqualTo("_test._tcp");
        assertThat(actualServiceInfo.getSubtypes()).isEqualTo(Set.of("_subtype1", "_subtype2"));
        assertThat(actualServiceInfo.getPort()).isEqualTo(12345);
        assertThat(actualServiceInfo.getAttributes().size()).isEqualTo(2);
        assertThat(actualServiceInfo.getAttributes().get("key1"))
                .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x02});
        assertThat(actualServiceInfo.getAttributes().get("key2"))
                .isEqualTo(new byte[] {(byte) 0x03});

        verify(mRegistrationReceiver, times(1)).onError(FAILURE_INTERNAL_ERROR);
    }

    @Test
    public void registerService_nsdManagerThrows_serviceRegistrationFails() throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        doThrow(new IllegalArgumentException("NsdManager fails"))
                .when(mMockNsdManager)
                .registerService(any(), anyInt(), any(Executor.class), any());

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);
        mTestLooper.dispatchAll();

        verify(mRegistrationReceiver, times(1)).onError(FAILURE_INTERNAL_ERROR);
    }

    @Test
    public void unregisterService_nsdManagerSucceeds_serviceUnregistrationSucceeds()
            throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mNsdPublisher.unregister(mUnregistrationReceiver, 16 /* listenerId */);
        mTestLooper.dispatchAll();
        verify(mMockNsdManager, times(1)).unregisterService(actualRegistrationListener);

        actualRegistrationListener.onServiceUnregistered(actualServiceInfo);
        mTestLooper.dispatchAll();
        verify(mUnregistrationReceiver, times(1)).onSuccess();
    }

    @Test
    public void unregisterService_nsdManagerFails_serviceUnregistrationFails() throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mNsdPublisher.unregister(mUnregistrationReceiver, 16 /* listenerId */);
        mTestLooper.dispatchAll();
        verify(mMockNsdManager, times(1)).unregisterService(actualRegistrationListener);

        actualRegistrationListener.onUnregistrationFailed(
                actualServiceInfo, FAILURE_INTERNAL_ERROR);
        mTestLooper.dispatchAll();
        verify(mUnregistrationReceiver, times(1)).onError(0);
    }

    @Test
    public void onOtDaemonDied_unregisterAll() {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);
        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());
        NsdManager.RegistrationListener actualListener1 =
                actualRegistrationListenerCaptor.getValue();
        actualListener1.onServiceRegistered(actualServiceInfoCaptor.getValue());

        mNsdPublisher.registerService(
                null,
                "MyService2",
                "_test._udp",
                Collections.emptyList(),
                11111,
                Collections.emptyList(),
                mRegistrationReceiver,
                17 /* listenerId */);

        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(2))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());
        NsdManager.RegistrationListener actualListener2 =
                actualRegistrationListenerCaptor.getAllValues().get(1);
        actualListener2.onServiceRegistered(actualServiceInfoCaptor.getValue());

        mNsdPublisher.onOtDaemonDied();
        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(1)).unregisterService(actualListener1);
        verify(mMockNsdManager, times(1)).unregisterService(actualListener2);
    }

    private static DnsTxtAttribute makeTxtAttribute(String name, List<Integer> value) {
        DnsTxtAttribute txtAttribute = new DnsTxtAttribute();

        txtAttribute.name = name;
        txtAttribute.value = new byte[value.size()];

        for (int i = 0; i < value.size(); ++i) {
            txtAttribute.value[i] = value.get(i).byteValue();
        }

        return txtAttribute;
    }

    // @Before and @Test run in different threads. NsdPublisher requires the jobs are run on the
    // thread looper, so TestLooper needs to be created inside each test case to install the
    // correct looper.
    private void prepareTest() {
        mTestLooper = new TestLooper();
        Handler handler = new Handler(mTestLooper.getLooper());
        mNsdPublisher = new NsdPublisher(mMockNsdManager, handler);
    }
}
