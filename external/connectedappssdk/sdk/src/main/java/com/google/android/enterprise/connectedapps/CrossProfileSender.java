/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps;

import static com.google.android.enterprise.connectedapps.CrossProfileSDKUtilities.filterUsersByAvailabilityRestrictions;
import static com.google.android.enterprise.connectedapps.CrossProfileSDKUtilities.selectUserHandleToBind;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedSet;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.CrossProfileApps;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.exceptions.MissingApiException;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.internal.BundleCallReceiver;
import com.google.android.enterprise.connectedapps.internal.BundleUtilities;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.CrossProfileBundleCallSender;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This class is used internally by the Connected Apps SDK to send messages across users and
 * profiles.
 */
public final class CrossProfileSender {

  private static final class CrossProfileCall implements ExceptionCallback {
    private final long crossProfileTypeIdentifier;
    private final int methodIdentifier;
    private final Bundle params;
    private final LocalCallback callback;

    CrossProfileCall(
        long crossProfileTypeIdentifier,
        int methodIdentifier,
        Bundle params,
        LocalCallback callback) {
      if (params == null || callback == null) {
        throw new NullPointerException();
      }
      this.crossProfileTypeIdentifier = crossProfileTypeIdentifier;
      this.methodIdentifier = methodIdentifier;
      this.params = params;
      this.callback = callback;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CrossProfileCall that = (CrossProfileCall) o;
      return crossProfileTypeIdentifier == that.crossProfileTypeIdentifier
          && methodIdentifier == that.methodIdentifier
          && params.equals(that.params)
          && callback.equals(that.callback);
    }

    @Override
    public int hashCode() {
      return Objects.hash(crossProfileTypeIdentifier, methodIdentifier, params, callback);
    }

    @Override
    public void onException(Throwable throwable) {
      callback.onException(createThrowableBundle(throwable));
    }
  }

  private static final class OngoingCrossProfileCall extends ICrossProfileCallback.Stub {

    private final CrossProfileSender sender;
    private final CrossProfileCall call;
    private final BundleCallReceiver bundleCallReceiver = new BundleCallReceiver();

    private OngoingCrossProfileCall(CrossProfileSender sender, CrossProfileCall call) {
      if (sender == null || call == null) {
        throw new NullPointerException();
      }
      this.sender = sender;
      this.call = call;
    }

    @Override
    public void prepareResult(long callId, int blockId, int numBytes, byte[] params) {
      bundleCallReceiver.prepareCall(callId, blockId, numBytes, params);
    }

    @Override
    public void prepareBundle(long callId, int bundleId, Bundle bundle) {
      bundleCallReceiver.prepareBundle(callId, bundleId, bundle);
    }

    @Override
    public void onResult(long callId, int blockId, int methodIdentifier, byte[] paramsBytes) {
      sender.removeConnectionHolder(call);

      Bundle bundle = bundleCallReceiver.getPreparedCall(callId, blockId, paramsBytes);

      call.callback.onResult(methodIdentifier, bundle);
    }

    @Override
    public void onException(long callId, int blockId, byte[] paramsBytes) {
      Bundle bundle = bundleCallReceiver.getPreparedCall(callId, blockId, paramsBytes);

      onException(bundle);
    }

    public void onException(Bundle exception) {
      sender.removeConnectionHolder(call);

      call.callback.onException(exception);

      sender.scheduledExecutorService.execute(sender::maybeScheduleAutomaticDisconnection);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      OngoingCrossProfileCall that = (OngoingCrossProfileCall) o;
      return sender.equals(that.sender) && call.equals(that.call);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sender, call);
    }
  }

  // Temporary variable until deprecated methods are removed
  public static final Object MANUAL_MANAGEMENT_CONNECTION_HOLDER = new Object();

  public static final int MAX_BYTES_PER_BLOCK = 250000;

  private static final String LOG_TAG = "CrossProfileSender";
  private static final long INITIAL_BIND_RETRY_DELAY_MS = 500;
  private static final int DEFAULT_AUTOMATIC_DISCONNECTION_TIMEOUT_SECONDS = 30;

  private static final int NONE = 0;
  private static final int UNAVAILABLE = 1;
  private static final int AVAILABLE = 2;
  private static final int DISCONNECTED = UNAVAILABLE;
  private static final int CONNECTED = AVAILABLE;

  private final ScheduledExecutorService scheduledExecutorService;
  private final Context context;
  private final ComponentName bindToService;
  private final boolean canUseReflectedApis;
  private final ConnectionListener connectionListener;
  private final AvailabilityListener availabilityListener;
  private final ConnectionBinder binder;
  private final AvailabilityRestrictions availabilityRestrictions;

  private final AtomicReference<@Nullable ICrossProfileService> iCrossProfileService =
      new AtomicReference<>();
  private final AtomicReference<@Nullable ScheduledFuture<?>> scheduledTryBind =
      new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> scheduledBindTimeout = new AtomicReference<>();

  // Interaction with connectionHolders, and connectionHolderAliases must
  //  take place on the scheduled executor thread
  private final Set<Object> connectionHolders = Collections.newSetFromMap(new WeakHashMap<>());
  private final Map<Object, Set<Object>> connectionHolderAliases = new WeakHashMap<>();
  private final Set<ExceptionCallback> unavailableProfileExceptionWatchers =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final ConcurrentLinkedDeque<CrossProfileCall> asyncCallQueue =
      new ConcurrentLinkedDeque<>();

  private final ServiceConnection connection =
      new ServiceConnection() {

        @Override
        public void onBindingDied(ComponentName name) {
          Log.e(LOG_TAG, "onBindingDied for component " + name);
          scheduledExecutorService.execute(
              () -> onBindingAttemptFailed("onBindingDied", /* terminal= */ true));
        }

        @Override
        public void onNullBinding(ComponentName name) {
          Log.e(LOG_TAG, "onNullBinding for component " + name);
          scheduledExecutorService.execute(
              () -> onBindingAttemptFailed("onNullBinding", /* terminal= */ true));
        }

        // Called when the connection with the service is established
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          Log.i(LOG_TAG, "onServiceConnected for component " + name);
          scheduledExecutorService.execute(
              () -> {
                if (connectionHolders.isEmpty()) {
                  Log.i(LOG_TAG, "Connected but no holders. Disconnecting.");
                  unbind();
                  return;
                }
                iCrossProfileService.set(ICrossProfileService.Stub.asInterface(service));

                tryMakeAsyncCalls();
                checkConnected();
                onBindingAttemptSucceeded();
              });
        }

        // Called when the connection with the service disconnects unexpectedly
        @Override
        public void onServiceDisconnected(ComponentName name) {
          Log.e(LOG_TAG, "Unexpected disconnection for component " + name);
          attemptReconnect();
        }

        private void attemptReconnect() {
          scheduledExecutorService.execute(
              () -> {
                unbind();
                throwUnavailableException(
                    new UnavailableProfileException("Lost connection to other profile"));
                // These disconnections can be temporary - so to avoid an exception on an async
                // call leading to bad user experience - we send the availability update again
                // to prompt a retry/refresh
                updateAvailability();
                checkConnected();
                cancelAutomaticDisconnection();
                bind();
              });
        }
      };

  // This is synchronized which isn't massively performant but it only gets accessed once straight
  // after creating a Sender, and once each time availability changes
  private static final Set<CrossProfileSender> senders =
     synchronizedSet(newSetFromMap(new WeakHashMap<>()));

  private static final BroadcastReceiver profileAvailabilityReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      synchronized (senders) {
        for (CrossProfileSender sender : senders) {
          sender.scheduledExecutorService.execute(sender::checkAvailability);
        }
      }
    }
  };

  private final AtomicReference<ScheduledFuture<Void>> automaticDisconnectionFuture =
      new AtomicReference<>();
  private volatile @Nullable CountDownLatch manuallyBindLatch;

  private long bindRetryDelayMs = INITIAL_BIND_RETRY_DELAY_MS;
  private int lastReportedAvailabilityStatus = NONE;
  private int lastReportedConnectedStatus = NONE;

  CrossProfileSender(
      Context context,
      String connectedAppsServiceClassName,
      ConnectionBinder binder,
      ConnectionListener connectionListener,
      AvailabilityListener availabilityListener,
      ScheduledExecutorService scheduledExecutorService,
      AvailabilityRestrictions availabilityRestrictions) {
    this.context = context.getApplicationContext();
    if (connectionListener == null
        || availabilityListener == null
        || availabilityRestrictions == null
        || binder == null
        || scheduledExecutorService == null) {
      throw new NullPointerException();
    }
    this.binder = binder;
    this.connectionListener = connectionListener;
    this.availabilityListener = availabilityListener;
    bindToService = new ComponentName(context.getPackageName(), connectedAppsServiceClassName);
    canUseReflectedApis = ReflectionUtilities.canUseReflectedApis();
    this.scheduledExecutorService = scheduledExecutorService;
    this.availabilityRestrictions = availabilityRestrictions;

    senders.add(this);
    beginMonitoringAvailabilityChanges();
  }

  private void cancelAutomaticDisconnection() {
    ScheduledFuture<?> disconnectionFuture = automaticDisconnectionFuture.getAndSet(null);
    if (disconnectionFuture != null) {
      disconnectionFuture.cancel(/* mayInterruptIfRunning= */ true);
    }
  }

  private void maybeScheduleAutomaticDisconnection() {
    // Always called on scheduled executor service thread
    if (connectionHolders.isEmpty() && isBound()) {
      Log.i(LOG_TAG, "Scheduling automatic disconnection");
      ScheduledFuture<Void> scheduledDisconnection =
          scheduledExecutorService.schedule(
              this::automaticallyDisconnect,
              DEFAULT_AUTOMATIC_DISCONNECTION_TIMEOUT_SECONDS,
              SECONDS);

      if (!automaticDisconnectionFuture.compareAndSet(null, scheduledDisconnection)) {
        Log.i(LOG_TAG, "Already scheduled");
        scheduledDisconnection.cancel(/* mayInterruptIfRunning= */ true);
      }
    }
  }

  private Void automaticallyDisconnect() {
    // Always called on scheduled executor service thread
    if (connectionHolders.isEmpty() && isBound()) {
      unbind();
    }
    return null;
  }

  private static final AtomicBoolean isMonitoringAvailabilityChanges = new AtomicBoolean(false);

  private void beginMonitoringAvailabilityChanges() {
    if (isMonitoringAvailabilityChanges.getAndSet(true)) {
      return;
    }

    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
    filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
    filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
    context.registerReceiver(profileAvailabilityReceiver, filter);
  }

  void manuallyBind(Object connectionHolder) throws UnavailableProfileException {
    Log.e(LOG_TAG, "Calling manuallyBind");
    if (isRunningOnUIThread()) {
      throw new IllegalStateException("connect()/manuallyBind() cannot be called from UI thread");
    }

    if (!isBindingPossible()) {
      throw new UnavailableProfileException("Profile not available");
    }

    if (!binder.hasPermissionToBind(context)) {
      throw new UnavailableProfileException("Permission not granted");
    }

    cancelAutomaticDisconnection();

    scheduledExecutorService.execute(
        () -> {
          connectionHolders.add(connectionHolder);
        });

    if (isBound()) {
      // If we're already bound there's no need to block the thread
      return;
    }

    if (manuallyBindLatch == null) {
      synchronized (this) {
        if (manuallyBindLatch == null) {
          manuallyBindLatch = new CountDownLatch(1);
        }
      }
    }

    bind();

    Log.i(LOG_TAG, "Blocking for bind");
    try {
      if (manuallyBindLatch != null) {
        manuallyBindLatch.await();
      }
    } catch (InterruptedException e) {
      Log.e(LOG_TAG, "Interrupted waiting for manually bind", e);
    }

    if (!isBound()) {
      unbind();
      scheduledExecutorService.execute(() -> removeConnectionHolderAndAliases(connectionHolder));
      throw new UnavailableProfileException("Profile not available");
    }
  }

  private static boolean isRunningOnUIThread() {
    return Looper.myLooper() == Looper.getMainLooper();
  }

  private void bind() {
    bindRetryDelayMs = INITIAL_BIND_RETRY_DELAY_MS;
    scheduledExecutorService.execute(this::tryBind);
  }

  private void onBindingAttemptSucceeded() {
    clearScheduledBindTimeout();
    Log.i(LOG_TAG, "Binding attempt succeeded");
    checkTriggerManualConnectionLock();
  }

  private void onBindingAttemptFailed(String reason) {
    onBindingAttemptFailed(reason, /* exception= */ null, /* terminal= */ false);
  }

  private void onBindingAttemptFailed(Exception exception) {
    onBindingAttemptFailed(exception.getMessage(), exception, /* terminal= */ false);
  }

  private void onBindingAttemptFailed(String reason, Exception exception) {
    onBindingAttemptFailed(reason, exception, /* terminal= */ false);
  }

  private void onBindingAttemptFailed(String reason, boolean terminal) {
    onBindingAttemptFailed(reason, /* exception= */ null, terminal);
  }

  private void onBindingAttemptFailed(
      String reason, @Nullable Exception exception, boolean terminal) {
    // Always called on scheduled executor service thread
    clearScheduledBindTimeout();
    if (exception == null) {
      Log.i(LOG_TAG, "Binding attempt failed: " + reason);
      throwUnavailableException(new UnavailableProfileException(reason));
    } else {
      Log.i(LOG_TAG, "Binding attempt failed: " + reason, exception);
      throwUnavailableException(new UnavailableProfileException(reason, exception));
    }

    if (terminal || connectionHolders.isEmpty() || manuallyBindLatch != null) {
      unbind();
      checkTriggerManualConnectionLock();
    } else {
      scheduleBindAttempt();
    }
  }

  private void clearScheduledBindTimeout() {
    ScheduledFuture<?> scheduledTimeout = scheduledBindTimeout.getAndSet(null);
    if (scheduledTimeout != null) {
      scheduledTimeout.cancel(/* mayInterruptIfRunning= */ true);
    }
  }

  private void checkTriggerManualConnectionLock() {
    if (manuallyBindLatch != null) {
      synchronized (this) {
        if (manuallyBindLatch != null) {
          manuallyBindLatch.countDown();
          manuallyBindLatch = null;
        }
      }
    }
  }

  /**
   * Stop attempting to bind to the other profile.
   *
   * <p>If there is already a binding present, it will be killed.
   */
  private void unbind() {
    Log.i(LOG_TAG, "Unbind");
    if (isBound()) {
      context.unbindService(connection);
      iCrossProfileService.set(null);
      checkConnected();
      cancelAutomaticDisconnection();
    }
    clearScheduledBindTimeout();
    throwUnavailableException(new UnavailableProfileException("No profile available"));
    checkTriggerManualConnectionLock();
  }

  boolean isBindingPossible() {
    return binder.bindingIsPossible(context, availabilityRestrictions);
  }

  private void tryBind() {
    // Always called on scheduled executor service thread
    Log.i(LOG_TAG, "Attempting to bind");

    ScheduledFuture<?> scheduledFuture = scheduledTryBind.getAndSet(null);
    if (scheduledFuture != null) {
      scheduledFuture.cancel(/* mayInterruptIfRunning= */ false);
    }

    if (!canUseReflectedApis) {
      onBindingAttemptFailed("Required APIs are unavailable. Binding is not possible.");
      return;
    }

    if (isBound()) {
      Log.i(LOG_TAG, "Already bound");
      onBindingAttemptSucceeded();
      return;
    }

    if (connectionHolders.isEmpty()) {
      onBindingAttemptFailed("Not trying to bind");
      return;
    }

    if (!binder.hasPermissionToBind(context)) {
      onBindingAttemptFailed("Permission not granted");
      return;
    }

    if (!isBindingPossible()) {
      onBindingAttemptFailed("No profile available");
      return;
    }

    if (scheduledBindTimeout.get() != null) {
      Log.i(LOG_TAG, "Already waiting to bind");
      return;
    }

    try {
      // Schedule a timeout in case something happens and we never reach onServiceConnected
      scheduledBindTimeout.set(scheduledExecutorService.schedule(this::timeoutBinding, 1, MINUTES));
      if (!binder.tryBind(context, bindToService, connection, availabilityRestrictions)) {
        onBindingAttemptFailed(
            "No profile available, app not installed in other profile, or service not included in"
                + " manifest");
      } else {
        Log.i(LOG_TAG, "binder.tryBind returned true, expecting onServiceConnected");
      }
    } catch (MissingApiException e) {
      Log.e(LOG_TAG, "MissingApiException when trying to bind", e);
      onBindingAttemptFailed("Missing API", e);
    } catch (UnavailableProfileException e) {
      Log.e(LOG_TAG, "Error while trying to bind", e);
      onBindingAttemptFailed(e);
    }
  }

  private void timeoutBinding() {
    onBindingAttemptFailed("Timed out while waiting for onServiceConnected");
  }

  private void scheduleBindAttempt() {
    ScheduledFuture<?> scheduledFuture = scheduledTryBind.get();
    if (scheduledFuture != null && !scheduledFuture.isDone()) {
      return;
    }

    bindRetryDelayMs *= 2;
    scheduledTryBind.set(
        scheduledExecutorService.schedule(this::tryBind, bindRetryDelayMs, MILLISECONDS));
  }

  boolean isBound() {
    return iCrossProfileService.get() != null;
  }

  /**
   * Make a synchronous cross-profile call.
   *
   * @return A {@link Bundle} containing the return value under the key \"return\".
   * @throws UnavailableProfileException if a connection is not already established
   */
  public Bundle call(long crossProfileTypeIdentifier, int methodIdentifier, Bundle params)
      throws UnavailableProfileException {
    try {
      return callWithExceptions(crossProfileTypeIdentifier, methodIdentifier, params);
    } catch (UnavailableProfileException | RuntimeException | Error e) {
      StackTraceElement[] remoteStack = e.getStackTrace();
      StackTraceElement[] localStack = Thread.currentThread().getStackTrace();
      StackTraceElement[] totalStack =
          Arrays.copyOf(remoteStack, remoteStack.length + localStack.length - 1);
      // We cut off the first element of localStack as it is just getting the stack trace
      System.arraycopy(localStack, 1, totalStack, remoteStack.length, localStack.length - 1);
      e.setStackTrace(totalStack);
      throw e;
    } catch (Throwable e) {
      throw new UnavailableProfileException("Unexpected checked exception", e);
    }
  }

  /**
   * Make a synchronous cross-profile call which expects some checked exceptions to be thrown.
   *
   * <p>Behaves the same as {@link #call(long, int, Bundle)} except that it deals with checked
   * exceptions by throwing {@link Throwable}.
   *
   * @return A {@link Bundle} containing the return value under the "return" key.
   * @throws UnavailableProfileException if a connection is not already established
   */
  public Bundle callWithExceptions(
      long crossProfileTypeIdentifier, int methodIdentifier, Bundle params) throws Throwable {
    ICrossProfileService service = iCrossProfileService.get();
    if (service == null) {
      throw new UnavailableProfileException("Could not access other profile");
    }

    CrossProfileBundleCallSender callSender =
        new CrossProfileBundleCallSender(
            service, crossProfileTypeIdentifier, methodIdentifier, /* callback= */ null);
    Bundle returnBundle = callSender.makeBundleCall(params);

    if (returnBundle.containsKey("throwable")) {
      Throwable t = BundleUtilities.readThrowableFromBundle(returnBundle, "throwable");
      if (t instanceof RuntimeException) {
        throw new ProfileRuntimeException(t);
      }
      throw t;
    }

    return returnBundle;
  }

  /** Make an asynchronous cross-profile call. */
  public void callAsync(
      long crossProfileTypeIdentifier,
      int methodIdentifier,
      Bundle params,
      LocalCallback callback,
      Object connectionHolderAlias) {
    if (!isBindingPossible()) {
      throwUnavailableException(new UnavailableProfileException("Profile not available"));
    }

    scheduledExecutorService.execute(
        () -> {
          CrossProfileCall crossProfileCall =
              new CrossProfileCall(crossProfileTypeIdentifier, methodIdentifier, params, callback);
          connectionHolders.add(crossProfileCall);
          cancelAutomaticDisconnection();
          addConnectionHolderAlias(connectionHolderAlias, crossProfileCall);
          unavailableProfileExceptionWatchers.add(crossProfileCall);

          asyncCallQueue.add(crossProfileCall);

          tryMakeAsyncCalls();
          bind();
        });
  }

  private void throwUnavailableException(Throwable throwable) {
    for (ExceptionCallback callback : unavailableProfileExceptionWatchers) {
      removeConnectionHolder(callback);
      callback.onException(throwable);
    }
  }

  private void tryMakeAsyncCalls() {
    Log.i(LOG_TAG, "tryMakeAsyncCalls");
    if (!isBound()) {
      return;
    }

    scheduledExecutorService.execute(this::drainAsyncQueue);
  }

  private void drainAsyncQueue() {
    Log.i(LOG_TAG, "drainAsyncQueue");
    while (true) {
      CrossProfileCall call = asyncCallQueue.pollFirst();
      if (call == null) {
        return;
      }
      OngoingCrossProfileCall ongoingCall = new OngoingCrossProfileCall(this, call);

      try {
        ICrossProfileService service = iCrossProfileService.get();
        if (service == null) {
          Log.w(LOG_TAG, "OngoingCrossProfileCall: not bound anymore, adding back to queue");
          asyncCallQueue.add(call);
          return;
        }
        CrossProfileBundleCallSender callSender =
            new CrossProfileBundleCallSender(
                service, call.crossProfileTypeIdentifier, call.methodIdentifier, ongoingCall);

        Bundle p = callSender.makeBundleCall(call.params);

        if (p.containsKey("throwable")) {
          RuntimeException exception =
              (RuntimeException) BundleUtilities.readThrowableFromBundle(p, "throwable");
          removeConnectionHolder(ongoingCall.call);
          throw new ProfileRuntimeException(exception);
        }
      } catch (UnavailableProfileException e) {
        Log.w(
            LOG_TAG, "OngoingCrossProfileCall: UnavailableProfileException, adding back to queue");
        asyncCallQueue.add(call);
        return;
      }
    }
  }

  private void checkAvailability() {
    if (isBindingPossible() && (lastReportedAvailabilityStatus != AVAILABLE)) {
      updateAvailability();
    } else if (!isBindingPossible() && (lastReportedAvailabilityStatus != UNAVAILABLE)) {
      updateAvailability();
    }
  }

  private void updateAvailability() {
    // This is only executed on the executor thread
    availabilityListener.availabilityChanged();
    lastReportedAvailabilityStatus = isBindingPossible() ? AVAILABLE : UNAVAILABLE;
  }

  private void checkConnected() {
    // This is only executed on the executor thread
    if (isBound() && lastReportedConnectedStatus != CONNECTED) {
      connectionListener.connectionChanged();
      lastReportedConnectedStatus = CONNECTED;
    } else if (!isBound() && lastReportedConnectedStatus != DISCONNECTED) {
      connectionListener.connectionChanged();
      lastReportedConnectedStatus = DISCONNECTED;
    }
  }

  /** Create a {@link Bundle} containing a {@link Throwable}. */
  private static Bundle createThrowableBundle(Throwable throwable) {
    Bundle bundle = new Bundle(Bundler.class.getClassLoader());
    BundleUtilities.writeThrowableToBundle(bundle, "throwable", throwable);
    return bundle;
  }

  static @Nullable UserHandle getOtherUserHandle(
      Context context, AvailabilityRestrictions availabilityRestrictions) {
    if (VERSION.SDK_INT < VERSION_CODES.P) {
      // CrossProfileApps was introduced in P
      return findDifferentRunningUser(
          context, android.os.Process.myUserHandle(), availabilityRestrictions);
    }

    CrossProfileApps crossProfileApps = context.getSystemService(CrossProfileApps.class);
    List<UserHandle> otherUsers =
        filterUsersByAvailabilityRestrictions(
            context, crossProfileApps.getTargetUserProfiles(), availabilityRestrictions);

    return selectUserHandleToBind(context, otherUsers);
  }

  private static @Nullable UserHandle findDifferentRunningUser(
      Context context,
      UserHandle ignoreUserHandle,
      AvailabilityRestrictions availabilityRestrictions) {
    UserManager userManager = context.getSystemService(UserManager.class);
    List<UserHandle> otherUsers = new ArrayList<>();

    for (UserHandle userHandle : userManager.getUserProfiles()) {
      if (!userHandle.equals(ignoreUserHandle)) {
        otherUsers.add(userHandle);
      }
    }

    otherUsers =
        filterUsersByAvailabilityRestrictions(context, otherUsers, availabilityRestrictions);

    return selectUserHandleToBind(context, otherUsers);
  }

  void addConnectionHolder(Object o) {
    scheduledExecutorService.execute(
        () -> {
          connectionHolders.add(o);

          cancelAutomaticDisconnection();
          bind();
        });
  }

  void removeConnectionHolder(Object o) {
    if (o == null) {
        throw new NullPointerException("Connection holder cannot be null");
    }

    scheduledExecutorService.execute(
        () -> {
          removeConnectionHolderAndAliases(o);

          maybeScheduleAutomaticDisconnection();
        });
  }

  void clearConnectionHolders() {
    scheduledExecutorService.execute(
        () -> {
          connectionHolders.clear();
          connectionHolderAliases.clear();

          maybeScheduleAutomaticDisconnection();
        });
  }

  private void removeConnectionHolderAndAliases(Object o) {
    // Always called on scheduled executor thread
    Set<Object> aliases = connectionHolderAliases.get(o);
    if (aliases != null) {
      connectionHolderAliases.remove(o);
      for (Object alias : aliases) {
        removeConnectionHolderAndAliases(alias);
      }
    }

    connectionHolders.remove(o);
    unavailableProfileExceptionWatchers.remove(o);
  }

  /**
   * Registers a connection holder alias.
   *
   * <p>This means that if the key is removed, then the value will also be removed. If the value is
   * removed, the key will not be removed.
   */
  void addConnectionHolderAlias(Object key, Object value) {
    scheduledExecutorService.execute(
        () -> {
          Set<Object> aliases = connectionHolderAliases.get(key);
          if (aliases == null) {
            aliases = Collections.newSetFromMap(new WeakHashMap<>());
          }

          aliases.add(value);

          connectionHolderAliases.put(key, aliases);
        });
  }

  /**
   * Clear static state.
   *
   * <p>This should not be required in production.
   */
  public static void clearStaticState() {
    isMonitoringAvailabilityChanges.set(false);
    senders.clear();
  }
}
