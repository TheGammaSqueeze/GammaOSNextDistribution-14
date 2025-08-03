package com.android.server.deviceconfig;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.IntentSender;
import android.os.RecoverySystem;

import java.io.IOException;
import java.time.ZoneId;

/**
 * Dependency injectors for {@link com.android.server.deviceconfig.UnattendedRebootManager} to
 * enable unit testing.
 */
interface UnattendedRebootManagerInjector {

  /** Time injectors. */
  long now();

  ZoneId zoneId();

  long elapsedRealtime();

  /** Reboot time injectors. */
  int getRebootStartTime();

  int getRebootEndTime();

  int getRebootFrequency();

  /** Reboot Alarm injector. */
  void setRebootAlarm(Context context, long rebootTimeMillis);

  /** Connectivity injector. */
  void triggerRebootOnNetworkAvailable(Context context);

  /** {@link RecoverySystem} methods injectors. */
  int rebootAndApply(@NonNull Context context, @NonNull String reason, boolean slotSwitch)
      throws IOException;

  void prepareForUnattendedUpdate(
      @NonNull Context context, @NonNull String updateToken, @Nullable IntentSender intentSender)
      throws IOException;

  boolean isPreparedForUnattendedUpdate(@NonNull Context context) throws IOException;

  boolean requiresChargingForReboot(Context context);

  /** Regular reboot injector. */
  void regularReboot(Context context);
}
