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

package android.test.wifi.nsd;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Helper class for NsdManager. */
public final class NsdHelper {
  private final NsdManager nsdManager;
  private final WifiManager wifi;
  private WifiManager.MulticastLock multicastLock;

  private static final String SERVICE_NAME = "WifiNsdTest";
  private static final String SERVICE_TYPE = "_wifi_nsd_test._tcp.";
  private static final String TAG = "WifiTest-NsdInstrumentationTest";

  public NsdHelper(Context context) {
    this.wifi = context.getSystemService(WifiManager.class);
    this.multicastLock = this.wifi.createMulticastLock("multicastLock");
    this.nsdManager = context.getSystemService(NsdManager.class);
  }

  private void await(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(60, TimeUnit.SECONDS));
  }

  public void serviceTest() throws InterruptedException {
    // TODO: Use service port.
    RegistrationListener listener = registerService(12345);
    await(listener.serviceRegistered);

    // TODO: Replace with connection and send PING PONG messages.
    TimeUnit.SECONDS.sleep(10);

    unregisterService(listener);
    await(listener.serviceUnregistered);
  }

  private static class RegistrationListener implements NsdManager.RegistrationListener {
    CountDownLatch serviceRegistered;
    CountDownLatch serviceUnregistered;

    RegistrationListener() {
      serviceRegistered = new CountDownLatch(1);
      serviceUnregistered = new CountDownLatch(1);
    }

    @Override
    public void onServiceRegistered(NsdServiceInfo serviceInfo) {
      Log.d(TAG, "Service registered. NsdServiceInfo:" + serviceInfo);
      serviceRegistered.countDown();
    }

    @Override
    public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
      Log.d(TAG, "Service unregistered");
      serviceUnregistered.countDown();
    }

    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
      fail("Registration failed");
    }

    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
      fail("Unregistration failed");
    }
  }

  private RegistrationListener registerService(int port) {
    RegistrationListener listener = new RegistrationListener();
    NsdServiceInfo serviceInfo = new NsdServiceInfo();
    serviceInfo.setPort(port);
    serviceInfo.setServiceName(SERVICE_NAME);
    serviceInfo.setServiceType(SERVICE_TYPE);

    nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener);
    return listener;
  }

  private CountDownLatch unregisterService(RegistrationListener listener) {
    nsdManager.unregisterService(listener);
    return listener.serviceUnregistered;
  }

  public void discoverTest() throws InterruptedException {
    DiscoveryListener listener = discoverServices();
    await(listener.serviceFound);
    CountDownLatch serviceResolved = resolveServices(listener.service);
    await(serviceResolved);
    CountDownLatch discoveryStopped = stopDiscovery(listener);
    await(discoveryStopped);
  }

  private static class DiscoveryListener implements NsdManager.DiscoveryListener {
    CountDownLatch serviceFound;
    CountDownLatch discoveryStopped;
    NsdServiceInfo service;

    DiscoveryListener() {
      serviceFound = new CountDownLatch(1);
      discoveryStopped = new CountDownLatch(1);
    }

    @Override
    public void onDiscoveryStarted(String regType) {}

    @Override
    public void onServiceFound(NsdServiceInfo serviceInfo) {
      if (serviceInfo.getServiceType().equals(SERVICE_TYPE)
          && serviceInfo.getServiceName().equals(SERVICE_NAME)) {
        service = serviceInfo;
        serviceFound.countDown();
      }
    }

    @Override
    public void onServiceLost(NsdServiceInfo nsdServiceInfo) {}

    @Override
    public void onDiscoveryStopped(String serviceType) {
      discoveryStopped.countDown();
    }

    @Override
    public void onStartDiscoveryFailed(String serviceType, int errorCode) {
      fail("Failed to start discovery");
    }

    @Override
    public void onStopDiscoveryFailed(String serviceType, int errorCode) {
      fail("Failed to stop discovery");
    }
  }

  private DiscoveryListener discoverServices() {
    DiscoveryListener discoveryListener = new DiscoveryListener();

    multicastLock.setReferenceCounted(true);
    multicastLock.acquire();

    nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);

    return discoveryListener;
  }

  private CountDownLatch resolveServices(NsdServiceInfo service) {
    ResolveListener listener = new ResolveListener();

    // TODO: Deprecated as of API level 34. For API levels 34 and above, use
    // registerServiceInfoCallback().
    nsdManager.resolveService(service, listener);

    return listener.serviceResolved;
  }

  private static class ResolveListener implements NsdManager.ResolveListener {
    CountDownLatch serviceResolved;

    ResolveListener() {
      serviceResolved = new CountDownLatch(1);
    }

    @Override
    public void onServiceResolved(NsdServiceInfo serviceInfo) {
      if (serviceInfo.getServiceName().equals(SERVICE_NAME)) {
        serviceResolved.countDown();
      }
    }

    @Override
    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
      fail("Resolve failed");
    }
  }

  private CountDownLatch stopDiscovery(DiscoveryListener listener) {
    nsdManager.stopServiceDiscovery(listener);
    if (multicastLock.isHeld()) {
      multicastLock.release(); // release after browsing
    }
    return listener.discoveryStopped;
  }
}
