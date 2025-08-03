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

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static androidx.core.content.ContextCompat.checkSelfPermission;
import static androidx.core.content.ContextCompat.getSystemService;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.annotation.RequiresPermission;
import androidx.core.net.ConnectivityManagerCompat;
import com.google.android.downloader.DownloadConstraints.NetworkType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.GoogleLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * Default implementation of {@link ConnectivityHandler}, relying on Android's {@link
 * ConnectivityManager}.
 */
public class AndroidConnectivityHandler implements ConnectivityHandler {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final Context context;
  private final ScheduledExecutorService scheduledExecutorService;
  private final ConnectivityManager connectivityManager;
  private final long timeoutMillis;

  /**
   * Creates a new AndroidConnectivityHandler to handle connectivity checks for the Downloader.
   *
   * @param context the context to use for perform Android API checks. Will be retained, so should
   *     not be a UI context.
   * @param scheduledExecutorService a scheduled executor used to timeout operations waiting for
   *     connectivity. Beware that there are problems with this, see go/executors-timing for
   *     details.
   * @param timeoutMillis how long to wait before timing out a connectivity check. If more than this
   *     amount of time elapses, the connectivity check will timeout, and the {@link
   *     ListenableFuture} returned by {@link #checkConnectivity} will resolve with a {@link
   *     TimeoutException}.
   */
  public AndroidConnectivityHandler(
      Context context, ScheduledExecutorService scheduledExecutorService, long timeoutMillis) {
    if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(context, ACCESS_NETWORK_STATE)) {
      throw new IllegalStateException(
          "AndroidConnectivityHandler requires the ACCESS_NETWORK_STATE permission.");
    }

    this.context = context;
    this.scheduledExecutorService = scheduledExecutorService;
    this.connectivityManager = checkNotNull(getSystemService(context, ConnectivityManager.class));
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  @RequiresPermission(ACCESS_NETWORK_STATE)
  public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
    if (connectivitySatisfied(constraints)) {
      return Futures.immediateVoidFuture();
    }

    ListenableFutureTask<Void> futureTask = ListenableFutureTask.create(() -> null);
    // TODO: Using a receiver here isn't great. Ideally we'd use
    // ConnectivityManager.requestNetwork(request, callback, timeout), but that's only available
    // on SDK 26+, so we'd still need a fallback on older versions of Android.
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(constraints, futureTask);
    context.registerReceiver(receiver, new IntentFilter(CONNECTIVITY_ACTION));
    futureTask.addListener(() -> context.unregisterReceiver(receiver), directExecutor());
    return Futures.withTimeout(futureTask, timeoutMillis, MILLISECONDS, scheduledExecutorService);
  }

  @RequiresPermission(ACCESS_NETWORK_STATE)
  private boolean connectivitySatisfied(DownloadConstraints downloadConstraints) {
    // Special case the NONE value - if that is specified then skip all further checks.
    if (downloadConstraints == DownloadConstraints.NONE) {
      return true;
    }

    NetworkType networkType;

    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      Network network = connectivityManager.getActiveNetwork();
      if (network == null) {
        logger.atFine().log("No current network, connectivity cannot be satisfied.");
        return false;
      }

      NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
      if (networkCapabilities == null) {
        logger.atFine().log(
            "Can't determine network capabilities, connectivity cannot be satisfied");
        return false;
      }

      if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        logger.atFine().log(
            "Network does not have internet capabilities, connectivity cannot be satisfied.");
        return false;
      }

      if (downloadConstraints.requireUnmeteredNetwork()
          && ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager)) {
        logger.atFine().log("Network is metered, connectivity cannot be satisfied.");
        return false;
      }

      if (downloadConstraints.requiredNetworkTypes().contains(NetworkType.ANY)) {
        // If the request doesn't care about the network type (by way of having NetworkType.ANY in
        // its set of allowed network types), then stop checking now.
        return true;
      }

      networkType = computeNetworkType(networkCapabilities);
    } else {
      @SuppressLint("MissingPermission") // We just checked the permission above.
      NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
      if (networkInfo == null) {
        logger.atFine().log("No current network, connectivity cannot be satisfied.");
        return false;
      }

      if (!networkInfo.isConnected()) {
        // Regardless of which type of network we have right now, if it's not connected then all
        // downloads will fail, so just queue up all downloads in this case.
        logger.atFine().log("Network disconnected, connectivity cannot be satisfied.");
        return false;
      }

      if (downloadConstraints.requireUnmeteredNetwork()
          && ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager)) {
        logger.atFine().log("Network is metered, connectivity cannot be satisfied.");
        return false;
      }

      if (downloadConstraints.requiredNetworkTypes().contains(NetworkType.ANY)) {
        // If the request doesn't care about the network type (by way of having NetworkType.ANY in
        // its set of allowed network types), then stop checking now.
        return true;
      }

      networkType = computeNetworkType(networkInfo.getType());
    }

    if (networkType == null) {
      // If for some reason we couldn't determine the network type from Android (unexpected value?),
      // then we can't validate it against the set of constraints, so fail the check.
      return false;
    }

    // Otherwise, just make sure that the current network type is allowed by this request.
    return downloadConstraints.requiredNetworkTypes().contains(networkType);
  }

  @Nullable
  private static NetworkType computeNetworkType(int networkType) {
    if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB_MR2
        && networkType == ConnectivityManager.TYPE_BLUETOOTH) {
      return NetworkType.BLUETOOTH;
    } else if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB_MR2
        && networkType == ConnectivityManager.TYPE_ETHERNET) {
      return NetworkType.ETHERNET;
    } else if (networkType == ConnectivityManager.TYPE_MOBILE
        || networkType == ConnectivityManager.TYPE_MOBILE_MMS
        || networkType == ConnectivityManager.TYPE_MOBILE_SUPL
        || networkType == ConnectivityManager.TYPE_MOBILE_DUN
        || networkType == ConnectivityManager.TYPE_MOBILE_HIPRI) {
      return NetworkType.CELLULAR;
    } else if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP
        && networkType == ConnectivityManager.TYPE_VPN) {
      // There's no way to determine the underlying transport used by a VPN, so it's best to
      // be conservative and treat it is a cellular network.
      return NetworkType.CELLULAR;
    } else if (networkType == ConnectivityManager.TYPE_WIFI) {
      return NetworkType.WIFI;
    } else if (networkType == ConnectivityManager.TYPE_WIMAX) {
      // WiMAX and Cellular aren't really the same thing, but in practice they can be treated
      // the same, as they are both typically available over long distances and are often metered.
      return NetworkType.CELLULAR;
    }

    return null;
  }

  @Nullable
  @TargetApi(VERSION_CODES.LOLLIPOP)
  private static NetworkType computeNetworkType(NetworkCapabilities networkCapabilities) {
    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
      return NetworkType.CELLULAR;
    } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
      return NetworkType.WIFI;
    } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
      return NetworkType.BLUETOOTH;
    } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
      return NetworkType.ETHERNET;
    } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
      // There's no way to determine the underlying transport used by a VPN, so it's best to
      // be conservative and treat it is a cellular network.
      return NetworkType.CELLULAR;
    }
    return null;
  }

  @VisibleForTesting
  class NetworkBroadcastReceiver extends BroadcastReceiver {
    private final DownloadConstraints constraints;
    private final Runnable completionRunnable;

    public NetworkBroadcastReceiver(DownloadConstraints constraints, Runnable completionRunnable) {
      this.constraints = constraints;
      this.completionRunnable = completionRunnable;
    }

    @Override
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public void onReceive(Context context, Intent intent) {
      if (!CONNECTIVITY_ACTION.equals(intent.getAction())) {
        logger.atSevere().log(
            "NetworkBroadcastReceiver received an unexpected intent action: %s",
            intent.getAction());
        return;
      }

      if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
        logger.atInfo().log("NetworkBroadcastReceiver updated but NO_CONNECTIVITY extra set");
        return;
      }

      logger.atInfo().log(
          "NetworkBroadcastReceiver received intent: %s %s",
          intent.getAction(), intent.getExtras());

      if (connectivitySatisfied(constraints)) {
        logger.atInfo().log("Connectivity satisfied in BroadcastReceiver, running completion");
        completionRunnable.run();
      } else {
        logger.atInfo().log("Connectivity NOT satisfied in BroadcastReceiver");
      }
    }
  }
}
