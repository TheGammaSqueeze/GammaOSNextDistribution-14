package android.nfc.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.*;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldReader;
import org.mockito.internal.util.reflection.FieldSetter;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class NfcAdapterTest {

    @Mock private INfcAdapter mService;
    private INfcAdapter mSavedService;
    private Context mContext;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private boolean supportsHardware() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    @Before
    public void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getContext();
        assumeTrue(supportsHardware());
        // Backup the original service. It is being overridden
        // when creating a mocked adapter.
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assume.assumeNotNull(adapter);
        mSavedService = (INfcAdapter) (
            new FieldReader(adapter, adapter.getClass().getDeclaredField("sService")).read());
    }

    @After
    public void tearDown() throws NoSuchFieldException {
        if (!supportsHardware()) return;
        // Restore the original service.
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        if (adapter != null) {
            FieldSetter.setField(adapter,
                    adapter.getClass().getDeclaredField("sService"), mSavedService);
        }
    }

    @Test
    public void testGetDefaultAdapter() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertNotNull(adapter);
    }

    @Test
    public void testAddNfcUnlockHandler() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            adapter.addNfcUnlockHandler(new CtsNfcUnlockHandler(), new String[]{"IsoDep"});
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testDisableWithNoParams() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.disable(anyBoolean())).thenReturn(true);
        boolean result = adapter.disable();
        Assert.assertTrue(result);
    }

    @Test
    public void testDisableWithParam() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.disable(anyBoolean())).thenReturn(true);
        boolean result = adapter.disable(true);
        Assert.assertTrue(result);
    }

    @Test
    public void testDisableForegroundDispatch() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.disableForegroundDispatch(activity);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testDisableReaderMode() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.disableReaderMode(activity);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testEnable() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.enable()).thenReturn(true);
        boolean result = adapter.enable();
        Assert.assertTrue(result);
    }

    @Test
    public void testEnableForegroundDispatch() {
        try {
            NfcAdapter adapter = createMockedInstance();
            Activity activity = createAndResumeActivity();
            Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NfcFCardEmulationActivity.class);
            PendingIntent pendingIntent
                = PendingIntent.getActivity(ApplicationProvider.getApplicationContext(),
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            String[][] techLists = new String[][]{new String[]{}};
            doNothing().when(mService).setForegroundDispatch(any(PendingIntent.class),
                any(IntentFilter[].class), any(TechListParcel.class));
            adapter.enableForegroundDispatch(activity, pendingIntent, null, techLists);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testEnableReaderMode() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.enableReaderMode(activity, new CtsReaderCallback(),
                NfcAdapter.FLAG_READER_NFC_A, new Bundle());
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testEnableReaderOption() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.enableReaderOption(anyBoolean())).thenReturn(true);
        boolean result = adapter.enableReaderOption(true);
        Assert.assertTrue(result);
    }

    @Test
    public void testEnableSecureNfc() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.setNfcSecure(anyBoolean())).thenReturn(true);
        boolean result = adapter.enableSecureNfc(true);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetNfcAntennaInfo() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        NfcAntennaInfo info = new NfcAntennaInfo(0, 0, false,
            new ArrayList<AvailableNfcAntenna>());
        when(mService.getNfcAntennaInfo()).thenReturn(info);
        NfcAntennaInfo result = adapter.getNfcAntennaInfo();
        Assert.assertEquals(info, result);
    }

    @Test
    public void testIgnore() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        Tag tag = new Tag(new byte[]{0x00}, new int[]{}, new Bundle[]{}, 0, 0L, null);
        when(mService.ignore(anyInt(), anyInt(), eq(null))).thenReturn(true);
        boolean result = adapter.ignore(tag, 0, null, null);
        Assert.assertTrue(result);
    }

    @Test
    public void testIsControllerAlwaysOn() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isControllerAlwaysOn()).thenReturn(true);
        boolean result = adapter.isControllerAlwaysOn();
        Assert.assertTrue(result);
    }

    @Test
    public void testIsControllerAlwaysOnSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isControllerAlwaysOnSupported()).thenReturn(true);
        boolean result = adapter.isControllerAlwaysOnSupported();
        Assert.assertTrue(result);
    }

    @Test
    public void testIsEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.getState()).thenReturn(NfcAdapter.STATE_ON);
        boolean result = adapter.isEnabled();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_READER_OPTION)
    public void testIsReaderOptionEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isReaderOptionEnabled()).thenReturn(true);
        boolean result = adapter.isReaderOptionEnabled();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_READER_OPTION)
    public void testIsReaderOptionSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isReaderOptionSupported()).thenReturn(true);
        boolean result = adapter.isReaderOptionSupported();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testAdapterState() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.getState()).thenReturn(NfcAdapter.STATE_ON);
        Assert.assertEquals(adapter.getAdapterState(), NfcAdapter.STATE_ON);
    }

    @Test
    public void testIsSecureNfcEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isNfcSecureEnabled()).thenReturn(true);
        boolean result = adapter.isSecureNfcEnabled();
        Assert.assertTrue(result);
    }

    @Test
    public void testIsSecureNfcSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.deviceSupportsNfcSecure()).thenReturn(true);
        boolean result = adapter.isSecureNfcSupported();
        Assert.assertTrue(result);
    }

    @Test
    public void testRemoveNfcUnlockHandler() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        boolean result = adapter.removeNfcUnlockHandler(new CtsNfcUnlockHandler());
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testResetDiscoveryTechnology() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.resetDiscoveryTechnology(activity);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testSetDiscoveryTechnology() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.setDiscoveryTechnology(activity,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F,
                    NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_A | NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                    | NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_F);
            adapter.resetDiscoveryTechnology(activity);
            adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_DISABLE,
                    NfcAdapter.FLAG_LISTEN_KEEP);
            adapter.resetDiscoveryTechnology(activity);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testSetReaderMode() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        // Verify the API does not crash or throw any exceptions.
        adapter.setReaderModePollingEnabled(true);
        adapter.setReaderModePollingEnabled(false);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testAllowTransaction() {
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            assumeTrue(adapter.isObserveModeSupported());
            boolean result = adapter.setObserveModeEnabled(false);
            Assert.assertTrue(result);
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDisallowTransaction() {
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            assumeTrue(adapter.isObserveModeSupported());
            boolean result = adapter.setObserveModeEnabled(true);
            Assert.assertTrue(result);
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }
    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDefaultObserveModePaymentDynamic() {
        ComponentName originalDefault = null;
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), true);
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            CardEmulationTest.ensurePreferredService(CustomHostApduService.class, mContext);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            setDefaultPaymentService(CtsMyHostApduService.class);
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            Assert.assertFalse(adapter.isObserveModeEnabled());
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDefaultObserveModeForegroundDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            Activity activity = createAndResumeActivity();
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), true);
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CustomHostApduService.class)));
            CardEmulationTest.ensurePreferredService(CustomHostApduService.class, mContext);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            Assert.assertFalse(adapter.isObserveModeEnabled());
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), false);
        }
    }
    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDefaultObserveModePayment() {
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(BackgroundHostApduService.class);
            CardEmulationTest.ensurePreferredService(BackgroundHostApduService.class, mContext);
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            setDefaultPaymentService(CtsMyHostApduService.class);
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            Assert.assertFalse(adapter.isObserveModeEnabled());
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDefaultObserveModeForeground() {
        Activity activity = createAndResumeActivity();
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Assert.assertTrue(cardEmulation.setPreferredService(activity,
                new ComponentName(mContext, BackgroundHostApduService.class)));
        CardEmulationTest.ensurePreferredService(BackgroundHostApduService.class, mContext);
        Assert.assertTrue(adapter.isObserveModeEnabled());
        Assert.assertTrue(cardEmulation.setPreferredService(activity,
                new ComponentName(mContext, CtsMyHostApduService.class)));
        CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
        Assert.assertFalse(adapter.isObserveModeEnabled());
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAllowTransaction_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            assumeTrue(adapter.isObserveModeSupported());
            adapter.setObserveModeEnabled(false);
            Assert.assertFalse(adapter.isObserveModeEnabled());
        });
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testDisallowTransaction_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            assumeTrue(adapter.isObserveModeSupported());
            adapter.setObserveModeEnabled(true);
            Assert.assertTrue(adapter.isObserveModeEnabled());
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_CHARGING)
    public void testEnableNfcCharging() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.setWlcEnabled(anyBoolean())).thenReturn(true);
        boolean result = adapter.setWlcEnabled(true);
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_CHARGING)
    public void testIsNfcChargingEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isWlcEnabled()).thenReturn(true);
        boolean result = adapter.isWlcEnabled();
        Assert.assertTrue(result);
    }

    @Test
    public void testSendVendorCmd() throws InterruptedException {
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertNotNull(nfcAdapter);
        NfcVendorNciCallback cb =
                new NfcVendorNciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            nfcAdapter.registerNfcVendorNciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Android GET_CAPS command
            int gid = 0xF;
            int oid = 0xC;
            byte[] payload = new byte[1];
            payload[0] = 0;
            nfcAdapter.sendVendorNciMessage(NfcAdapter.MESSAGE_TYPE_COMMAND, gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } finally {
            nfcAdapter.unregisterNfcVendorNciCallback(cb);
        }
    }

    private class NfcVendorNciCallback implements NfcAdapter.NfcVendorNciCallback {
        private final CountDownLatch mRspCountDownLatch;
        private final CountDownLatch mNtfCountDownLatch;

        public int gid;
        public int oid;
        public byte[] payload;

        NfcVendorNciCallback(CountDownLatch rspCountDownLatch, CountDownLatch ntfCountDownLatch) {
            mRspCountDownLatch = rspCountDownLatch;
            mNtfCountDownLatch = ntfCountDownLatch;
        }

        @Override
        public void onVendorNciResponse(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mRspCountDownLatch.countDown();
        }

        @Override
        public void onVendorNciNotification(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mNtfCountDownLatch.countDown();
        }
    }

    private class CtsReaderCallback implements NfcAdapter.ReaderCallback {
        @Override
        public void onTagDiscovered(Tag tag) {}
    }

    private class CtsNfcUnlockHandler implements NfcAdapter.NfcUnlockHandler {
        @Override
        public boolean onUnlockAttempted(Tag tag) {
            return true;
        }
    }

    private Activity createAndResumeActivity() {
        CardEmulationTest.ensureUnlocked();
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
            NfcFCardEmulationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);
        return activity;
    }

    private NfcAdapter createMockedInstance() throws NoSuchFieldException {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        FieldSetter.setField(adapter, adapter.getClass().getDeclaredField("sService"), mService);
        return adapter;
    }

    private ComponentName setDefaultPaymentService(Class serviceClass) {
        ComponentName componentName = setDefaultPaymentService(
                new ComponentName(mContext, serviceClass));
        if (componentName == null) {
            return null;
        }
        return componentName;
    }

    private ComponentName setDefaultPaymentService(ComponentName serviceName) {
        return DefaultPaymentProviderTestUtils.setDefaultPaymentService(serviceName, mContext);
    }
}
