package com.android.server.deviceconfig;

import android.content.Context;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.common.collect.ImmutableList;

/**
 * If device contains a SIM PIN, must prepare <a
 * href="https://source.android.com/docs/core/ota/resume-on-reboot#sim-pin-replay">Sim Pin
 * Replay</a> to unlock the device post reboot.
 *
 * @hide
 */
public class SimPinReplayManager {

  private static final String TAG = "UnattendedRebootManager";

  // The identifier of the system resource value that determines whether auto-sim-unlock feature is
  // enabled/disabled for the device.
  private static final String SYSTEM_ENABLE_SIM_PIN_STORAGE_KEY =
      "config_allow_pin_storage_for_unattended_reboot";
  // This is a copy of the hidden field
  // CarrierConfigManager#KEY_STORE_SIM_PIN_FOR_UNATTENDED_REBOOT_BOOL. Phonesky uses this key to
  // read the boolean value in carrier configs specifying whether to enable/disable auto-sim-unlock.
  private static final String CARRIER_ENABLE_SIM_PIN_STORAGE_KEY =
      "store_sim_pin_for_unattended_reboot_bool";

  private Context mContext;

  SimPinReplayManager(Context context) {
    mContext = context;
  }

  /** Returns true, if no SIM PIN present or prepared SIM PIN Replay. */
  public boolean prepareSimPinReplay() {
    // Is SIM Pin present?
    ImmutableList<Integer> pinLockedSubscriptionIds = getPinLockedSubscriptionIds(mContext);
    if (pinLockedSubscriptionIds.isEmpty()) {
      return true;
    }

    if (!isSimPinStorageEnabled(mContext, pinLockedSubscriptionIds)) {
      Log.w(TAG, "SIM PIN storage is disabled");
      return false;
    }

    TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
    if (telephonyManager == null) {
      Log.e(TAG, "Failed to prepare SIM PIN Replay, TelephonyManager is null");
      return false;
    }

    int prepareUnattendedRebootResult = telephonyManager.prepareForUnattendedReboot();
    if (prepareUnattendedRebootResult == TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS) {
      Log.i(TAG, "SIM PIN replay prepared");
      return true;
    }
    Log.w(TAG, "Failed to prepare SIM PIN Replay, " + prepareUnattendedRebootResult);
    return false;
  }

  /** Returns a list of telephony subscription IDs (SIM IDs) locked by PIN. */
  private static ImmutableList<Integer> getPinLockedSubscriptionIds(Context context) {
    SubscriptionManager subscriptionManager = context.getSystemService(SubscriptionManager.class);
    int[] subscriptionIds = subscriptionManager.getActiveSubscriptionIdList();
    if (subscriptionIds.length == 0) {
      return ImmutableList.of();
    }

    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    ImmutableList.Builder<Integer> pinLockedSubscriptionIdsBuilder = ImmutableList.builder();
    for (int subscriptionId : subscriptionIds) {
      if (telephonyManager.createForSubscriptionId(subscriptionId).isIccLockEnabled()) {
        pinLockedSubscriptionIdsBuilder.add(subscriptionId);
      }
    }
    return pinLockedSubscriptionIdsBuilder.build();
  }

  /**
   * Returns true, if SIM PIN storage is enabled.
   *
   * <p>The SIM PIN storage might be disabled by OEM or by carrier, subscription (SIM) Id is
   * required when checking if the corresponding SIM PIN storage is disabled by the carrier.
   *
   * <p>Both the OEM and carrier enable SIM PIN storage by default. If fails to read the OEM/carrier
   * configs, it assume SIM PIN storage is enabled.
   */
  private static boolean isSimPinStorageEnabled(
      Context context, ImmutableList<Integer> pinLockedSubscriptionIds) {
    if (!isSystemEnableSimPin()) {
      return false;
    }

    // If the carrier enables SIM PIN.
    CarrierConfigManager carrierConfigManager =
        context.getSystemService(CarrierConfigManager.class);
    if (carrierConfigManager == null) {
      Log.w(TAG, "CarrierConfigManager is null");
      return true;
    }
    for (int pinLockedSubscriptionId : pinLockedSubscriptionIds) {
      PersistableBundle subscriptionConfig =
          carrierConfigManager.getConfigForSubId(
              pinLockedSubscriptionId, CARRIER_ENABLE_SIM_PIN_STORAGE_KEY);
      // Only disable if carrier explicitly disables sim pin storage.
      if (!subscriptionConfig.isEmpty()
          && !subscriptionConfig.getBoolean(
              CARRIER_ENABLE_SIM_PIN_STORAGE_KEY, /* defaultValue= */ true)) {
        Log.w(
            TAG,
            "The carrier disables SIM PIN storage on subscription ID " + pinLockedSubscriptionId);
        return false;
      }
    }
    Log.v(TAG, "SIM PIN Storage is enabled");
    return true;
  }

  private static boolean isSystemEnableSimPin() {
    try {
      boolean value =
          Resources.getSystem()
              .getBoolean(
                  Resources.getSystem()
                      .getIdentifier(
                          SYSTEM_ENABLE_SIM_PIN_STORAGE_KEY,
                          /* defType= */ "bool",
                          /* defPackage= */ "android"));
      Log.i(TAG, SYSTEM_ENABLE_SIM_PIN_STORAGE_KEY + " = " + value);
      return value;
    } catch (Resources.NotFoundException e) {
      Log.e(TAG, "Could not read system resource value ," + SYSTEM_ENABLE_SIM_PIN_STORAGE_KEY);
      // When not explicitly disabled, assume SIM PIN storage functions properly.
      return true;
    }
  }
}
