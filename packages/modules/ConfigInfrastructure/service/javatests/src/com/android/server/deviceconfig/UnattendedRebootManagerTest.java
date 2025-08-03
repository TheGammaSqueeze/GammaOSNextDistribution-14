package com.android.server.deviceconfig;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.android.server.deviceconfig.Flags.FLAG_ENABLE_CHARGER_DEPENDENCY_FOR_REBOOT;
import static com.android.server.deviceconfig.Flags.FLAG_ENABLE_CUSTOM_REBOOT_TIME_CONFIGURATIONS;
import static com.android.server.deviceconfig.Flags.FLAG_ENABLE_SIM_PIN_REPLAY;

import static com.android.server.deviceconfig.UnattendedRebootManager.ACTION_RESUME_ON_REBOOT_LSKF_CAPTURED;
import static com.android.server.deviceconfig.UnattendedRebootManager.ACTION_TRIGGER_REBOOT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.platform.test.flag.junit.SetFlagsRule;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContextWrapper;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.util.Log;

import androidx.test.filters.SmallTest;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

@SmallTest
public class UnattendedRebootManagerTest {

  private static final String TAG = "UnattendedRebootManagerTest";

  private static final int REBOOT_FREQUENCY = 1;
  private static final int REBOOT_START_HOUR = 2;
  private static final int REBOOT_END_HOUR = 3;

  private static final long CURRENT_TIME = 1696452549304L; // 2023-10-04T13:49:09.304
  private static final long REBOOT_TIME = 1696497120000L; // 2023-10-05T02:12:00
  private static final long RESCHEDULED_REBOOT_TIME = 1696583520000L; // 2023-10-06T02:12:00
  private static final long OUTSIDE_WINDOW_REBOOT_TIME = 1696587000000L; // 2023-10-06T03:10:00
  private static final long RESCHEDULED_OUTSIDE_WINDOW_REBOOT_TIME =
          1696669920000L; // 2023-10-07T02:12:00
  private static final long ELAPSED_REALTIME_1_DAY = 86400000L;

  private final List<BroadcastReceiverRegistration> mRegisteredReceivers = new ArrayList<>();

  private Context mContext;
  private BatteryManager mBatterManager;
  private KeyguardManager mKeyguardManager;
  private ConnectivityManager mConnectivityManager;
  private RebootTimingConfiguration mRebootTimingConfiguration;
  private FakeInjector mFakeInjector;
  private UnattendedRebootManager mRebootManager;
  private SimPinReplayManager mSimPinReplayManager;

  @Rule
  public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();


  @Before
  public void setUp() throws Exception {
    mSetFlagsRule.enableFlags(
        FLAG_ENABLE_SIM_PIN_REPLAY, FLAG_ENABLE_CHARGER_DEPENDENCY_FOR_REBOOT);

    mSimPinReplayManager = mock(SimPinReplayManager.class);
    mKeyguardManager = mock(KeyguardManager.class);
    mConnectivityManager = mock(ConnectivityManager.class);
    mBatterManager = mock(BatteryManager.class);

    mRebootTimingConfiguration =
        new RebootTimingConfiguration(REBOOT_START_HOUR, REBOOT_END_HOUR, REBOOT_FREQUENCY);

    mContext =
        new ContextWrapper(getInstrumentation().getTargetContext()) {
          @Override
          public Object getSystemService(String name) {
            if (name.equals(Context.KEYGUARD_SERVICE)) {
              return mKeyguardManager;
            } else if (name.equals(Context.CONNECTIVITY_SERVICE)) {
              return mConnectivityManager;
            } else if (name.equals(Context.BATTERY_SERVICE)) {
              return mBatterManager;
            }
            return super.getSystemService(name);
          }

          @Override
          public Intent registerReceiver(
              @Nullable BroadcastReceiver receiver, IntentFilter filter, int flags) {
            mRegisteredReceivers.add(new BroadcastReceiverRegistration(receiver, filter, flags));
            return super.registerReceiver(receiver, filter, flags);
          }
        };

    mFakeInjector = new FakeInjector();
    mRebootManager =
        new UnattendedRebootManager(
            mContext, mFakeInjector, mSimPinReplayManager, mRebootTimingConfiguration);

    // Need to register receiver in tests so that the test doesn't trigger reboot requested by
    // deviceconfig.
    mContext.registerReceiver(
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            mRebootManager.tryRebootOrSchedule();
          }
        },
        new IntentFilter(ACTION_TRIGGER_REBOOT),
        Context.RECEIVER_EXPORTED);

    mFakeInjector.setElapsedRealtime(ELAPSED_REALTIME_1_DAY);

    mFakeInjector.setRequiresChargingForReboot(true);
    when(mBatterManager.isCharging()).thenReturn(true);
  }

  @Test
  public void scheduleReboot() {
    Log.i(TAG, "scheduleReboot");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    assertTrue(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(REBOOT_TIME);
  }

  @Test
  public void scheduleReboot_requiresCharging_notCharging() {
    Log.i(TAG, "scheduleReboot_requiresCharging_notCharging");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);
    mSetFlagsRule.enableFlags(FLAG_ENABLE_CHARGER_DEPENDENCY_FOR_REBOOT);
    mFakeInjector.setRequiresChargingForReboot(true);
    when(mBatterManager.isCharging()).thenReturn(false);

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    // Charging is required and device is not charging, so reboot should not be triggered.
    assertFalse(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(REBOOT_TIME);
    List<BroadcastReceiverRegistration> chargingStateReceiverRegistrations =
        getRegistrationsForAction(BatteryManager.ACTION_CHARGING);
    assertThat(chargingStateReceiverRegistrations).hasSize(1);

    // Now mimic a change in a charging state changed, and verify that we do the reboot once device
    // is charging.
    when(mBatterManager.isCharging()).thenReturn(true);
    BroadcastReceiver chargingStateReceiver = chargingStateReceiverRegistrations.get(0).mReceiver;
    chargingStateReceiver.onReceive(mContext, new Intent(BatteryManager.ACTION_CHARGING));

    assertTrue(mFakeInjector.isRebootAndApplied());
  }

  @Test
  public void scheduleReboot_doesNotRequireCharging_notCharging() {
    Log.i(TAG, "scheduleReboot_doesNotRequireCharging_notCharging");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);
    mSetFlagsRule.enableFlags(FLAG_ENABLE_CHARGER_DEPENDENCY_FOR_REBOOT);
    mFakeInjector.setRequiresChargingForReboot(false);
    when(mBatterManager.isCharging()).thenReturn(false);

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    // Charging is not required, so reboot should be triggered despite the fact that the device
    // is not charging.
    assertTrue(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(REBOOT_TIME);
    assertThat(getRegistrationsForAction(BatteryManager.ACTION_CHARGING)).isEmpty();
  }

  @Test
  public void scheduleReboot_requiresCharging_flagNotEnabled() {
    Log.i(TAG, "scheduleReboot_requiresCharging_flagNotEnabled");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);
    mSetFlagsRule.disableFlags(FLAG_ENABLE_CHARGER_DEPENDENCY_FOR_REBOOT);
    mFakeInjector.setRequiresChargingForReboot(true);
    when(mBatterManager.isCharging()).thenReturn(false);

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    // Charging is required, but the flag that controls the feature to depend on charging is not
    // enabled, so eboot should be triggered despite the fact that the device is not charging.
    assertTrue(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(REBOOT_TIME);
    assertThat(getRegistrationsForAction(BatteryManager.ACTION_CHARGING)).isEmpty();
  }

  @Test
  public void scheduleReboot_noPinLock() {
    Log.i(TAG, "scheduleReboot_noPinLock");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(false);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    assertFalse(mFakeInjector.isRebootAndApplied());
    assertTrue(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(REBOOT_TIME);
  }

  @Test
  public void scheduleReboot_noPreparation() {
    Log.i(TAG, "scheduleReboot_noPreparation");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);

    mRebootManager.scheduleReboot();

    assertFalse(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(RESCHEDULED_REBOOT_TIME);
  }

  @Test
  public void scheduleReboot_simPinPreparationFailed() {
    Log.i(TAG, "scheduleReboot");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(false).thenReturn(true);

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    assertTrue(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(RESCHEDULED_REBOOT_TIME);
  }

  @Test
  public void scheduleReboot_noInternet() {
    Log.i(TAG, "scheduleReboot_noInternet");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any())).thenReturn(new NetworkCapabilities());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    assertFalse(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(REBOOT_TIME);
    assertTrue(mFakeInjector.isRequestedNetwork());
  }

  @Test
  public void scheduleReboot_noInternetValidation() {
    Log.i(TAG, "scheduleReboot_noInternetValidation");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    assertFalse(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(REBOOT_TIME);
    assertTrue(mFakeInjector.isRequestedNetwork());
  }

  @Test
  public void scheduleReboot_elapsedRealtimeLessThanFrequency_withDefaultTimeConfigurations() {
    scheduleReboot_elapsedRealtimeLessThanFrequency(/* enableCustomTimeConfig= */ false);
  }

  @Test
  public void scheduleReboot_elapsedRealtimeLessThanFrequency_withCustomTimeConfigurations() {
    scheduleReboot_elapsedRealtimeLessThanFrequency(/* enableCustomTimeConfig= */ true);
  }

  private void scheduleReboot_elapsedRealtimeLessThanFrequency(boolean enableCustomTimeConfig) {
    if (enableCustomTimeConfig) {
      mSetFlagsRule.enableFlags(FLAG_ENABLE_CUSTOM_REBOOT_TIME_CONFIGURATIONS);
    } else {
      mSetFlagsRule.disableFlags(FLAG_ENABLE_CUSTOM_REBOOT_TIME_CONFIGURATIONS);
    }
    Log.i(TAG, "scheduleReboot_elapsedRealtimeLessThanFrequency");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);
    mFakeInjector.setElapsedRealtime(82800000); // 23 hours

    mRebootManager.prepareUnattendedReboot();
    mRebootManager.scheduleReboot();

    assertFalse(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime()).isEqualTo(RESCHEDULED_REBOOT_TIME);
  }

  @Test
  public void tryRebootOrSchedule_outsideRebootWindow_withDefaultTimeConfigurations() {
    tryRebootOrSchedule_outsideRebootWindow(/* enableCustomTimeConfig= */ false);
  }

  @Test
  public void tryRebootOrSchedule_outsideRebootWindow_withCustomTimeConfigurations() {
    tryRebootOrSchedule_outsideRebootWindow(/* enableCustomTimeConfig= */ true);
  }

  private void tryRebootOrSchedule_outsideRebootWindow(boolean enableCustomTimeConfig) {
    if (enableCustomTimeConfig) {
      mSetFlagsRule.enableFlags(FLAG_ENABLE_CUSTOM_REBOOT_TIME_CONFIGURATIONS);
    }
    Log.i(TAG, "scheduleReboot_internetOutsideRebootWindow");
    when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
    when(mConnectivityManager.getNetworkCapabilities(any()))
        .thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());
    when(mSimPinReplayManager.prepareSimPinReplay()).thenReturn(true);
    mFakeInjector.setNow(OUTSIDE_WINDOW_REBOOT_TIME);

    mRebootManager.prepareUnattendedReboot();
    // Simulating case when reboot is tried after network connection is established outside the
    // reboot window.
    mRebootManager.tryRebootOrSchedule();

    assertTrue(mFakeInjector.isRebootAndApplied());
    assertFalse(mFakeInjector.isRegularRebooted());
    assertThat(mFakeInjector.getActualRebootTime())
        .isEqualTo(RESCHEDULED_OUTSIDE_WINDOW_REBOOT_TIME);
  }

  static class FakeInjector implements UnattendedRebootManagerInjector {

    private boolean isPreparedForUnattendedReboot;
    private boolean requiresChargingForReboot;
    private boolean rebootAndApplied;
    private boolean regularRebooted;
    private boolean requestedNetwork;
    private long actualRebootTime;
    private boolean scheduledReboot;

    private long nowMillis;

    private long elapsedRealtimeMillis;

    FakeInjector() {
      nowMillis = CURRENT_TIME;
    }

    @Override
    public void prepareForUnattendedUpdate(
        @NonNull Context context,
        @NonNull String updateToken,
        @Nullable IntentSender intentSender) {
      context.sendBroadcast(new Intent(ACTION_RESUME_ON_REBOOT_LSKF_CAPTURED));
      isPreparedForUnattendedReboot = true;
    }

    @Override
    public boolean isPreparedForUnattendedUpdate(@NonNull Context context) {
      return isPreparedForUnattendedReboot;
    }

    @Override
    public boolean requiresChargingForReboot(Context context) {
      return requiresChargingForReboot;
    }

    void setRequiresChargingForReboot(boolean requiresCharging) {
      requiresChargingForReboot = requiresCharging;
    }

    @Override
    public int rebootAndApply(
        @NonNull Context context, @NonNull String reason, boolean slotSwitch) {
      rebootAndApplied = true;
      return 0; // No error.
    }

    @Override
    public int getRebootFrequency() {
      return REBOOT_FREQUENCY;
    }

    @Override
    public void setRebootAlarm(Context context, long rebootTimeMillis) {
      // To prevent infinite loop, do not simulate another reboot if reboot was already scheduled.
      if (scheduledReboot) {
        actualRebootTime = rebootTimeMillis;
        return;
      }
      // Advance now to reboot time and reboot immediately.
      scheduledReboot = true;
      actualRebootTime = rebootTimeMillis;
      setNow(rebootTimeMillis);

      LatchingBroadcastReceiver rebootReceiver = new LatchingBroadcastReceiver();

      // Wait for reboot broadcast to be sent.
      context.sendOrderedBroadcast(
          new Intent(ACTION_TRIGGER_REBOOT), null, rebootReceiver, null, 0, null, null);

      rebootReceiver.await(20, TimeUnit.SECONDS);
    }

    @Override
    public void triggerRebootOnNetworkAvailable(Context context) {
      requestedNetwork = true;
    }

    public boolean isRequestedNetwork() {
      return requestedNetwork;
    }

    @Override
    public int getRebootStartTime() {
      return REBOOT_START_HOUR;
    }

    @Override
    public int getRebootEndTime() {
      return REBOOT_END_HOUR;
    }

    @Override
    public long now() {
      return nowMillis;
    }

    public void setNow(long nowMillis) {
      this.nowMillis = nowMillis;
    }

    @Override
    public ZoneId zoneId() {
      return ZoneId.of("America/Los_Angeles");
    }

    @Override
    public long elapsedRealtime() {
      return elapsedRealtimeMillis;
    }

    public void setElapsedRealtime(long elapsedRealtimeMillis) {
      this.elapsedRealtimeMillis = elapsedRealtimeMillis;
    }

    @Override
    public void regularReboot(Context context) {
      regularRebooted = true;
    }

    boolean isRebootAndApplied() {
      return rebootAndApplied;
    }

    boolean isRegularRebooted() {
      return regularRebooted;
    }

    public long getActualRebootTime() {
      return actualRebootTime;
    }
  }

  /**
   * A {@link BroadcastReceiver} with an internal latch that unblocks once any intent is received.
   */
  private static class LatchingBroadcastReceiver extends BroadcastReceiver {
    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onReceive(Context context, Intent intent) {
      latch.countDown();
    }

    public boolean await(long timeoutInMs, TimeUnit timeUnit) {
      try {
        return latch.await(timeoutInMs, timeUnit);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private List<BroadcastReceiverRegistration> getRegistrationsForAction(String action) {
    return mRegisteredReceivers
        .stream()
        .filter(r -> r.mFilter.hasAction(action))
        .collect(Collectors.toList());
  }

  /** Data class to store BroadcastReceiver registration info. */
  private static final class BroadcastReceiverRegistration {
    final BroadcastReceiver mReceiver;
    final IntentFilter mFilter;
    final int mFlags;

    BroadcastReceiverRegistration(BroadcastReceiver receiver, IntentFilter filter, int flags) {
      mReceiver = receiver;
      mFilter = filter;
      mFlags = flags;
    }
  }
}
