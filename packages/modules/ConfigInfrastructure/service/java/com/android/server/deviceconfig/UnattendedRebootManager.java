package com.android.server.deviceconfig;

import static com.android.server.deviceconfig.Flags.enableChargerDependencyForReboot;
import static com.android.server.deviceconfig.Flags.enableCustomRebootTimeConfigurations;
import static com.android.server.deviceconfig.Flags.enableSimPinReplay;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.RecoverySystem;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.deviceconfig.resources.R;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reboot scheduler for applying aconfig flags.
 *
 * <p>If device is password protected, uses <a
 * href="https://source.android.com/docs/core/ota/resume-on-reboot">Resume on Reboot</a> to reboot
 * the device, otherwise proceeds with regular reboot.
 *
 * @hide
 */
final class UnattendedRebootManager {
  private static final int DEFAULT_REBOOT_WINDOW_START_TIME_HOUR = 3;
  private static final int DEFAULT_REBOOT_WINDOW_END_TIME_HOUR = 5;

  private static final int DEFAULT_REBOOT_FREQUENCY_DAYS = 2;

  private static final String TAG = "UnattendedRebootManager";

  static final String REBOOT_REASON = "unattended,flaginfra";

  @VisibleForTesting
  static final String ACTION_RESUME_ON_REBOOT_LSKF_CAPTURED =
      "com.android.server.deviceconfig.RESUME_ON_REBOOOT_LSKF_CAPTURED";

  @VisibleForTesting
  static final String ACTION_TRIGGER_REBOOT = "com.android.server.deviceconfig.TRIGGER_REBOOT";

  private final Context mContext;

  @Nullable
  private final RebootTimingConfiguration mRebootTimingConfiguration;

  private boolean mLskfCaptured;

  private final UnattendedRebootManagerInjector mInjector;

  private final SimPinReplayManager mSimPinReplayManager;

  private boolean mChargingReceiverRegistered;

  private final BroadcastReceiver mChargingReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      mChargingReceiverRegistered = false;
      mContext.unregisterReceiver(mChargingReceiver);
      tryRebootOrSchedule();
    }
  };

  private static class InjectorImpl implements UnattendedRebootManagerInjector {
    InjectorImpl() {
      /*no op*/
    }

    public long now() {
      return System.currentTimeMillis();
    }

    public ZoneId zoneId() {
      return ZoneId.systemDefault();
    }

    @Override
    public long elapsedRealtime() {
      return SystemClock.elapsedRealtime();
    }

    public int getRebootStartTime() {
      return DEFAULT_REBOOT_WINDOW_START_TIME_HOUR;
    }

    public int getRebootEndTime() {
      return DEFAULT_REBOOT_WINDOW_END_TIME_HOUR;
    }

    public int getRebootFrequency() {
      return DEFAULT_REBOOT_FREQUENCY_DAYS;
    }

    public void setRebootAlarm(Context context, long rebootTimeMillis) {
      AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
      alarmManager.setExact(
          AlarmManager.RTC_WAKEUP, rebootTimeMillis, createTriggerRebootPendingIntent(context));
    }

    public void triggerRebootOnNetworkAvailable(Context context) {
      final ConnectivityManager connectivityManager =
          context.getSystemService(ConnectivityManager.class);
      NetworkRequest request =
          new NetworkRequest.Builder()
              .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
              .build();
      connectivityManager.requestNetwork(request, createTriggerRebootPendingIntent(context));
    }

    public int rebootAndApply(@NonNull Context context, @NonNull String reason, boolean slotSwitch)
        throws IOException {
      return RecoverySystem.rebootAndApply(context, reason, slotSwitch);
    }

    public void prepareForUnattendedUpdate(
        @NonNull Context context, @NonNull String updateToken, @Nullable IntentSender intentSender)
        throws IOException {
      RecoverySystem.prepareForUnattendedUpdate(context, updateToken, intentSender);
    }

    public boolean isPreparedForUnattendedUpdate(@NonNull Context context) throws IOException {
      return RecoverySystem.isPreparedForUnattendedUpdate(context);
    }

    @Override
    public boolean requiresChargingForReboot(Context context) {
      return context.getResources().getBoolean(R.bool.config_requireChargingForUnattendedReboot);
    }

    public void regularReboot(Context context) {
      PowerManager powerManager = context.getSystemService(PowerManager.class);
      powerManager.reboot(REBOOT_REASON);
    }

    private static PendingIntent createTriggerRebootPendingIntent(Context context) {
      return PendingIntent.getBroadcast(
          context,
          /* requestCode= */ 0,
          new Intent(ACTION_TRIGGER_REBOOT),
          PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
    }
  }

  @VisibleForTesting
  UnattendedRebootManager(
      Context context,
      UnattendedRebootManagerInjector injector,
      SimPinReplayManager simPinReplayManager,
      @Nullable RebootTimingConfiguration rebootTimingConfiguration) {
    mContext = context;
    mInjector = injector;
    mSimPinReplayManager = simPinReplayManager;
    mRebootTimingConfiguration = rebootTimingConfiguration;

    mContext.registerReceiver(
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            mLskfCaptured = true;
          }
        },
        new IntentFilter(ACTION_RESUME_ON_REBOOT_LSKF_CAPTURED),
        Context.RECEIVER_EXPORTED);

    // Do not export receiver so that tests don't trigger reboot.
    mContext.registerReceiver(
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            tryRebootOrSchedule();
          }
        },
        new IntentFilter(ACTION_TRIGGER_REBOOT),
        Context.RECEIVER_NOT_EXPORTED);
  }

  UnattendedRebootManager(Context context) {
    this(context,
        new InjectorImpl(),
        new SimPinReplayManager(context),
        enableCustomRebootTimeConfigurations()
            ? new RebootTimingConfiguration(context)
            : null);
  }

  public void prepareUnattendedReboot() {
    Log.i(TAG, "Preparing for Unattended Reboot");
    // RoR only supported on devices with screen lock.
    if (!isDeviceSecure(mContext)) {
      return;
    }
    PendingIntent pendingIntent =
        PendingIntent.getBroadcast(
            mContext,
            /* requestCode= */ 0,
            new Intent(ACTION_RESUME_ON_REBOOT_LSKF_CAPTURED),
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

    try {
      mInjector.prepareForUnattendedUpdate(
          mContext, /* updateToken= */ "", pendingIntent.getIntentSender());
    } catch (IOException e) {
      Log.i(TAG, "prepareForUnattendedReboot failed with exception" + e.getLocalizedMessage());
    }
  }

  public void scheduleReboot() {
    // Reboot the next day at the reboot start time.
    final int rebootHour;
    if (enableCustomRebootTimeConfigurations()) {
      Optional<Pair<Integer, Integer>> rebootWindowStartEndHour =
          mRebootTimingConfiguration.getRebootWindowStartEndHour();
      rebootHour = rebootWindowStartEndHour.isEmpty() ? 0 : rebootWindowStartEndHour.get().first;
    } else {
      rebootHour = mInjector.getRebootStartTime();
    }
    LocalDateTime timeToReboot =
        Instant.ofEpochMilli(mInjector.now())
            .atZone(mInjector.zoneId())
            .toLocalDate()
            .plusDays(getRebootFrequencyDays())
            .atTime(rebootHour, /* minute= */ 12);
    long rebootTimeMillis = timeToReboot.atZone(mInjector.zoneId()).toInstant().toEpochMilli();
    Log.v(TAG, "Scheduling unattended reboot at time " + timeToReboot);

    if (timeToReboot.isBefore(
        LocalDateTime.ofInstant(Instant.ofEpochMilli(mInjector.now()), mInjector.zoneId()))) {
      Log.w(TAG, "Reboot time has already passed.");
      return;
    }

    mInjector.setRebootAlarm(mContext, rebootTimeMillis);
  }

  @VisibleForTesting
  void tryRebootOrSchedule() {
    Log.v(TAG, "Attempting unattended reboot");

    final int rebootFrequencyDays = getRebootFrequencyDays();
    // Has enough time passed since reboot?
    if (TimeUnit.MILLISECONDS.toDays(mInjector.elapsedRealtime()) < rebootFrequencyDays) {
      Log.v(TAG, "Device has already been rebooted in that last " + rebootFrequencyDays + " days.");
      scheduleReboot();
      return;
    }
    // Is RoR is supported?
    if (!isDeviceSecure(mContext)) {
      Log.v(TAG, "Device is not secure. Proceed with regular reboot");
      mInjector.regularReboot(mContext);
      return;
    }
    // Is RoR prepared?
    if (!isPreparedForUnattendedReboot()) {
      Log.v(TAG, "Lskf is not captured, reschedule reboot.");
      prepareUnattendedReboot();
      scheduleReboot();
      return;
    }
    // Is network connected?
    // TODO(b/305259443): Use after-boot network connectivity projection
    if (!isNetworkConnected(mContext)) {
      Log.i(TAG, "Network is not connected, reschedule reboot.");
      mInjector.triggerRebootOnNetworkAvailable(mContext);
      return;
    }
    // Is current time between reboot window?
    int currentHour =
        Instant.ofEpochMilli(mInjector.now())
            .atZone(mInjector.zoneId())
            .toLocalDateTime()
            .getHour();
    final boolean isHourWithinRebootHourWindow;
    if (enableCustomRebootTimeConfigurations()) {
      isHourWithinRebootHourWindow =
          mRebootTimingConfiguration.isHourWithinRebootHourWindow(currentHour);
    } else {
      isHourWithinRebootHourWindow =
          currentHour >= mInjector.getRebootStartTime()
          && currentHour < mInjector.getRebootEndTime();
    }
    if (!isHourWithinRebootHourWindow) {
      Log.v(TAG, "Reboot requested outside of reboot window, reschedule reboot.");
      prepareUnattendedReboot();
      scheduleReboot();
      return;
    }
    // Is preparing for SIM PIN replay successful?
    if (enableSimPinReplay() && !mSimPinReplayManager.prepareSimPinReplay()) {
      Log.w(TAG, "Sim Pin Replay failed, reschedule reboot");
      scheduleReboot();
    }

    if (enableChargerDependencyForReboot()
        && mInjector.requiresChargingForReboot(mContext)
        && !isCharging(mContext)) {
      triggerRebootOnCharging();
      return;
    }

    // Proceed with RoR.
    Log.v(TAG, "Rebooting device to apply device config flags.");
    try {
      int success = mInjector.rebootAndApply(mContext, REBOOT_REASON, /* slotSwitch= */ false);
      if (success != 0) {
        // If reboot is not successful, reschedule.
        Log.w(TAG, "Unattended reboot failed, reschedule reboot.");
        scheduleReboot();
      }
    } catch (IOException e) {
      Log.e(TAG, e.getLocalizedMessage());
      scheduleReboot();
    }
  }

  private int getRebootFrequencyDays() {
    return enableCustomRebootTimeConfigurations()
        ? mRebootTimingConfiguration.getRebootFrequencyDays()
        : mInjector.getRebootFrequency();
  }

  private boolean isPreparedForUnattendedReboot() {
    try {
      boolean isPrepared = mInjector.isPreparedForUnattendedUpdate(mContext);
      if (isPrepared != mLskfCaptured) {
        Log.w(TAG, "isPrepared != mLskfCaptured. Received " + isPrepared);
      }
      return isPrepared;
    } catch (IOException e) {
      Log.w(TAG, e.getLocalizedMessage());
      return mLskfCaptured;
    }
  }

  private void triggerRebootOnCharging() {
    if (!mChargingReceiverRegistered) {
      mChargingReceiverRegistered = true;
      mContext.registerReceiver(
          mChargingReceiver,
          new IntentFilter(BatteryManager.ACTION_CHARGING),
          Context.RECEIVER_EXPORTED);
    }
  }

  /** Returns true if the device has screen lock. */
  private static boolean isDeviceSecure(Context context) {
    KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
    if (keyguardManager == null) {
      // Unknown if device is locked, proceed with RoR anyway.
      Log.w(TAG, "Keyguard manager is null, proceeding with RoR anyway.");
      return true;
    }
    return keyguardManager.isDeviceSecure();
  }

  private static boolean isCharging(Context context) {
    BatteryManager batteryManager =
        (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    return batteryManager.isCharging();
  }

  private static boolean isNetworkConnected(Context context) {
    final ConnectivityManager connectivityManager =
        context.getSystemService(ConnectivityManager.class);
    if (connectivityManager == null) {
      Log.w(TAG, "ConnectivityManager is null");
      return false;
    }
    Network activeNetwork = connectivityManager.getActiveNetwork();
    NetworkCapabilities networkCapabilities =
        connectivityManager.getNetworkCapabilities(activeNetwork);
    return networkCapabilities != null
        && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  }
}
