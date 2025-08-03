// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.downloader;

import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest.permission;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.downloader.DownloadConstraints.NetworkType;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;

/** Unit tests for AndroidConnectivityHandler. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = VERSION_CODES.KITKAT)
public final class AndroidConnectivityHandlerTest {
  private static final long DEFAULT_TIMEOUT_MILLIS = 1000;

  @Rule
  public TestExecutorRule testExecutorRule =
      new TestExecutorRule(Duration.ofMillis(2 * DEFAULT_TIMEOUT_MILLIS));

  private Application application;
  private ConnectivityManager connectivityManager;
  private ScheduledExecutorService scheduledExecutorService;

  @Before
  public void setUp() {
    application = ApplicationProvider.getApplicationContext();
    scheduledExecutorService = testExecutorRule.newSingleThreadScheduledExecutor();
    connectivityManager =
        (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
  }

  @Test
  public void checkConnectivity_permissionNotGranted() {
    assertThrows(IllegalStateException.class, this::createConnectivityHandler);
  }

  @Test
  public void checkConnectivity_noConnectivityRequired() throws Exception {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();
    ListenableFuture<Void> result = connectivityHandler.checkConnectivity(DownloadConstraints.NONE);

    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isNull();
  }

  @Test
  public void checkConnectivity_noNetwork() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager).setActiveNetworkInfo(null);

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(DownloadConstraints.NETWORK_CONNECTED);

    assertThat(result.isDone()).isFalse();
  }

  @Test
  public void checkConnectivity_networkNotConnected() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.DISCONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                false,
                State.DISCONNECTED));

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(DownloadConstraints.NETWORK_CONNECTED);

    assertThat(result.isDone()).isFalse();
  }

  @Test
  public void checkConnectivity_wrongNetworkType() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.CONNECTED,
                ConnectivityManager.TYPE_MOBILE,
                0,
                true,
                State.CONNECTED));

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(DownloadConstraints.NETWORK_UNMETERED);

    assertThat(result.isDone()).isFalse();
  }

  @Test
  public void checkConnectivity_anyNetworkType() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.CONNECTED,
                ConnectivityManager.TYPE_MOBILE,
                0,
                true,
                State.CONNECTED));

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(
            DownloadConstraints.builder()
                .setRequireUnmeteredNetwork(false)
                .setRequiredNetworkTypes(ImmutableSet.of(NetworkType.ANY))
                .build());
    assertThat(result.isDone()).isTrue();
  }

  @Test
  public void checkConnectivity_unknownNetworkType() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.CONNECTED,
                100, // Invalid network type
                0,
                true,
                State.CONNECTED));

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(
            DownloadConstraints.builder()
                .setRequiredNetworkTypes(EnumSet.of(NetworkType.WIFI))
                .setRequireUnmeteredNetwork(false)
                .build());

    assertThat(result.isDone()).isFalse();
  }

  @Test
  public void checkConnectivity_requiredNetworkConnected_wifiOnly() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, State.CONNECTED));

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(DownloadConstraints.NETWORK_UNMETERED);

    assertThat(result.isDone()).isTrue();
  }

  @Test
  public void checkConnectivity_requiredNetworkConnected_wifiOrCellular() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.CONNECTED,
                ConnectivityManager.TYPE_MOBILE,
                0,
                true,
                State.CONNECTED));

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(DownloadConstraints.NETWORK_CONNECTED);

    assertThat(result.isDone()).isTrue();
  }

  @Config(sdk = VERSION_CODES.M)
  @Test
  public void checkConnectivity_requiredNetworkConnected_sdk23() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    ShadowConnectivityManager shadowConnectivityManager = shadowOf(connectivityManager);
    Network network = ShadowNetwork.newInstance(0);
    shadowConnectivityManager.addNetwork(
        network,
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, State.CONNECTED));
    NetworkCapabilities networkCapabilities = ShadowNetworkCapabilities.newInstance();
    shadowOf(networkCapabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    shadowConnectivityManager.setNetworkCapabilities(network, networkCapabilities);
    shadowConnectivityManager.setDefaultNetworkActive(true);

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(DownloadConstraints.NETWORK_CONNECTED);

    assertThat(result.isDone()).isTrue();
  }

  @Test
  public void checkConnectivity_networkChange() throws Exception {
    ShadowApplication shadowApplication = shadowOf(application);
    shadowApplication.grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.DISCONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                false,
                State.DISCONNECTED));

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(DownloadConstraints.NETWORK_CONNECTED);
    assertThat(result.isDone()).isFalse();
    assertThat(shadowApplication.getRegisteredReceivers()).hasSize(1);
    assertThat(shadowApplication.getRegisteredReceivers().get(0).getBroadcastReceiver())
        .isInstanceOf(AndroidConnectivityHandler.NetworkBroadcastReceiver.class);
    assertThat(
            shadowApplication
                .getRegisteredReceivers()
                .get(0)
                .getIntentFilter()
                .hasAction(CONNECTIVITY_ACTION))
        .isTrue();

    // Change state to be available.
    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, State.CONNECTED));

    application.sendBroadcast(new Intent(CONNECTIVITY_ACTION));

    shadowOf(getMainLooper()).idle();

    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isNull();
    assertThat(shadowApplication.getRegisteredReceivers()).isEmpty();
  }

  @Test
  public void checkConnectivity_timeout() {
    shadowOf(application).grantPermissions(permission.ACCESS_NETWORK_STATE);

    AndroidConnectivityHandler connectivityHandler = createConnectivityHandler();

    shadowOf(connectivityManager)
        .setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                DetailedState.DISCONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                false,
                State.DISCONNECTED));

    ListenableFuture<Void> result =
        connectivityHandler.checkConnectivity(DownloadConstraints.NETWORK_CONNECTED);
    assertThat(result.isDone()).isFalse();

    ExecutionException exception = assertThrows(ExecutionException.class, result::get);
    assertThat(exception).hasCauseThat().isInstanceOf(TimeoutException.class);
  }

  private AndroidConnectivityHandler createConnectivityHandler() {
    return new AndroidConnectivityHandler(
        application, scheduledExecutorService, DEFAULT_TIMEOUT_MILLIS);
  }
}
