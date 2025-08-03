package com.android.server.deviceconfig;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class SimPinReplayManagerTest {

  private static final String TAG = "SimPinReplayManagerTest";

  // A copy of the hidden field CarrierConfigManager#KEY_STORE_SIM_PIN_FOR_UNATTENDED_REBOOT_BOOL.
  public static final String CARRIER_ENABLE_SIM_PIN_STORAGE_KEY =
      "store_sim_pin_for_unattended_reboot_bool";

  SimPinReplayManager mSimPinReplayManager;
  SubscriptionManager mSubscriptionManager;
  TelephonyManager mTelephonyManager;
  CarrierConfigManager mCarrierConfigManager;

  private Context mContext;

  @Before
  public void setUp() {

    mSubscriptionManager = mock(SubscriptionManager.class);
    mTelephonyManager = mock(TelephonyManager.class);
    mCarrierConfigManager = mock(CarrierConfigManager.class);

    mContext =
        new ContextWrapper(getInstrumentation().getTargetContext()) {
          @Override
          public Object getSystemService(String name) {
            if (name.equals(Context.TELEPHONY_SUBSCRIPTION_SERVICE)) {
              return mSubscriptionManager;
            } else if (name.equals(Context.TELEPHONY_SERVICE)) {
              return mTelephonyManager;
            } else if (name.equals(Context.CARRIER_CONFIG_SERVICE)) {
              return mCarrierConfigManager;
            }
            return super.getSystemService(name);
          }
        };

    mSimPinReplayManager = new SimPinReplayManager(mContext);
  }

  @Test
  public void prepareSimPinReplay_success() {
    Log.i(TAG, "prepareSimPinReplay_success");
    when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(new int[] {1}); // has sim
    TelephonyManager subIdManager = mock(TelephonyManager.class);
    when(mTelephonyManager.createForSubscriptionId(1)).thenReturn(subIdManager);
    when(subIdManager.isIccLockEnabled()).thenReturn(true); // has pin
    PersistableBundle config = new PersistableBundle(); // empty carrier config
    when(mCarrierConfigManager.getConfigForSubId(1, CARRIER_ENABLE_SIM_PIN_STORAGE_KEY))
        .thenReturn(config);
    when(mTelephonyManager.prepareForUnattendedReboot())
        .thenReturn(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

    boolean isPrepared = mSimPinReplayManager.prepareSimPinReplay();

    assertTrue(isPrepared);
  }

  @Test
  public void prepareSimPinReplay_noSim() {
    Log.i(TAG, "prepareSimPinReplay_noSim");
    when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(new int[] {}); // no sim

    boolean isPrepared = mSimPinReplayManager.prepareSimPinReplay();

    assertTrue(isPrepared);
  }

  @Test
  public void prepareSimPinReplay_noSimPin() {
    Log.i(TAG, "prepareSimPinReplay_noSimPin");
    when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(new int[] {1}); // has sim
    TelephonyManager subIdManager = mock(TelephonyManager.class);
    when(mTelephonyManager.createForSubscriptionId(1)).thenReturn(subIdManager);
    when(subIdManager.isIccLockEnabled()).thenReturn(false); // no pin

    boolean isPrepared = mSimPinReplayManager.prepareSimPinReplay();

    assertTrue(isPrepared);
  }

  @Test
  public void prepareSimPinReplay_carrierDisableSimPin() {
    Log.i(TAG, "prepareSimPinReplay_carrierDisableSimPin");
    when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(new int[] {1}); // has sim
    TelephonyManager subIdManager = mock(TelephonyManager.class);
    when(mTelephonyManager.createForSubscriptionId(1)).thenReturn(subIdManager);
    when(subIdManager.isIccLockEnabled()).thenReturn(true); // has pin
    PersistableBundle config = new PersistableBundle();
    config.putBoolean(CARRIER_ENABLE_SIM_PIN_STORAGE_KEY, false); // carrier disabled
    when(mCarrierConfigManager.getConfigForSubId(1, CARRIER_ENABLE_SIM_PIN_STORAGE_KEY))
        .thenReturn(config);

    boolean isPrepared = mSimPinReplayManager.prepareSimPinReplay();

    assertFalse(isPrepared);
  }

  @Test
  public void prepareSimPinReplay_carrierEnabled() {
    Log.i(TAG, "prepareSimPinReplay_carrierEnabled");
    when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(new int[] {1}); // has sim
    TelephonyManager subIdManager = mock(TelephonyManager.class);
    when(mTelephonyManager.createForSubscriptionId(1)).thenReturn(subIdManager);
    when(subIdManager.isIccLockEnabled()).thenReturn(true); // has pin
    PersistableBundle config = new PersistableBundle();
    config.putBoolean(CARRIER_ENABLE_SIM_PIN_STORAGE_KEY, true); // carrier enabled
    when(mCarrierConfigManager.getConfigForSubId(1, CARRIER_ENABLE_SIM_PIN_STORAGE_KEY))
        .thenReturn(config);
    when(mTelephonyManager.prepareForUnattendedReboot())
        .thenReturn(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

    boolean isPrepared = mSimPinReplayManager.prepareSimPinReplay();

    assertTrue(isPrepared);
  }

  @Test
  public void prepareSimPinReplay_prepareError() {
    Log.i(TAG, "prepareSimPinReplay_prepareError");
    when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(new int[] {1}); // has sim
    TelephonyManager subIdManager = mock(TelephonyManager.class);
    when(mTelephonyManager.createForSubscriptionId(1)).thenReturn(subIdManager);
    when(subIdManager.isIccLockEnabled()).thenReturn(true); // has pin
    PersistableBundle config = new PersistableBundle();
    config.putBoolean(CARRIER_ENABLE_SIM_PIN_STORAGE_KEY, true); // carrier enabled
    when(mCarrierConfigManager.getConfigForSubId(1, CARRIER_ENABLE_SIM_PIN_STORAGE_KEY))
        .thenReturn(config);
    when(mTelephonyManager.prepareForUnattendedReboot())
        .thenReturn(TelephonyManager.PREPARE_UNATTENDED_REBOOT_ERROR);

    boolean isPrepared = mSimPinReplayManager.prepareSimPinReplay();

    assertFalse(isPrepared);
  }

  @Test
  public void prepareSimPinReplay_preparePinRequired() {
    Log.i(TAG, "prepareSimPinReplay_preparePinRequired");
    when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(new int[] {1}); // has sim
    TelephonyManager subIdManager = mock(TelephonyManager.class);
    when(mTelephonyManager.createForSubscriptionId(1)).thenReturn(subIdManager);
    when(subIdManager.isIccLockEnabled()).thenReturn(true); // has pin
    PersistableBundle config = new PersistableBundle();
    config.putBoolean(CARRIER_ENABLE_SIM_PIN_STORAGE_KEY, true); // carrier enabled
    when(mCarrierConfigManager.getConfigForSubId(1, CARRIER_ENABLE_SIM_PIN_STORAGE_KEY))
        .thenReturn(config);
    when(mTelephonyManager.prepareForUnattendedReboot())
        .thenReturn(TelephonyManager.PREPARE_UNATTENDED_REBOOT_PIN_REQUIRED);

    boolean isPrepared = mSimPinReplayManager.prepareSimPinReplay();

    assertFalse(isPrepared);
  }
}
