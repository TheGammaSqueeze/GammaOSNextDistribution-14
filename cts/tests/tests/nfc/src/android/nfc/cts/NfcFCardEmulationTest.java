package android.nfc.cts;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.INfcFCardEmulation;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.*;
import android.os.RemoteException;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldReader;
import org.mockito.internal.util.reflection.FieldSetter;

@RunWith(JUnit4.class)
public class NfcFCardEmulationTest {
    private NfcAdapter mAdapter;
    private static final ComponentName mService =
        new ComponentName("android.nfc.cts", "android.nfc.cts.CtsMyHostApduService");

    private INfcFCardEmulation mOldService;
    @Mock private INfcFCardEmulation mockEmulation;

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF);
    }

    @Before
    public void setUp() throws NoSuchFieldException, RemoteException {
        MockitoAnnotations.initMocks(this);
        assumeTrue(supportsHardware());
        Context mContext = InstrumentationRegistry.getContext();
        mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertNotNull(mAdapter);

        NfcFCardEmulation instance = NfcFCardEmulation.getInstance(mAdapter);
        FieldReader serviceField = new FieldReader(instance,
                instance.getClass().getDeclaredField("sService"));
        mOldService = (INfcFCardEmulation) serviceField.read();
    }

    @After
    public void tearDown() throws Exception {
        if (!supportsHardware()) return;
        NfcFCardEmulation instance = NfcFCardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance,
                instance.getClass().getDeclaredField("sService"), mOldService);
    }

    @Test
    public void getNonNullInstance() {
        NfcFCardEmulation instance = NfcFCardEmulation.getInstance(mAdapter);
        Assert.assertNotNull(instance);
    }

    @Test
    public void testGetSystemCodeForService() throws NoSuchFieldException, RemoteException {
        NfcFCardEmulation instance = createMockedInstance();
        String code = "System Code";
        when(mockEmulation.getSystemCodeForService(anyInt(),any(ComponentName.class)))
            .thenReturn(code);
        String result = instance.getSystemCodeForService(mService);
        Assert.assertEquals(result, code);
    }

    @Test
    public void testRegisterCodeForService() throws NoSuchFieldException, RemoteException {
        NfcFCardEmulation instance = createMockedInstance();
        String code = "4000";
        when(mockEmulation.registerSystemCodeForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.registerSystemCodeForService(mService, code);
        Assert.assertTrue(result);
    }

    @Test
    public void testUnregisterSystemCodeForService() throws NoSuchFieldException, RemoteException {
        NfcFCardEmulation instance = createMockedInstance();
        when(mockEmulation.removeSystemCodeForService(anyInt(), any(ComponentName.class)))
            .thenReturn(true);
        boolean result = instance.unregisterSystemCodeForService(mService);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetNfcid2ForService() throws NoSuchFieldException, RemoteException {
        NfcFCardEmulation instance = createMockedInstance();
        String testNfcid2 = "02FE000000000000";
        when(mockEmulation.getNfcid2ForService(anyInt(), any(ComponentName.class)))
            .thenReturn(testNfcid2);
        String result = instance.getNfcid2ForService(mService);
        Assert.assertEquals(result, testNfcid2);
    }

    @Test
    public void testSetNfcid2ForService() throws NoSuchFieldException, RemoteException {
        NfcFCardEmulation instance = createMockedInstance();
        String testNfcid2 = "02FE000000000000";
        when(mockEmulation.setNfcid2ForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.setNfcid2ForService(mService, testNfcid2);
        Assert.assertTrue(result);
    }

    @Test
    public void testEnableService() throws NoSuchFieldException, RemoteException {
        NfcFCardEmulation instance = createMockedInstance();
        Activity activity = createAndResumeActivity();
        when(mockEmulation.enableNfcFForegroundService(any(ComponentName.class))).thenReturn(true);
        boolean result = instance.enableService(activity, mService);
        Assert.assertTrue(result);
    }

    @Test
    public void testDisableService() throws NoSuchFieldException, RemoteException {
        NfcFCardEmulation instance = createMockedInstance();
        Activity activity = createAndResumeActivity();
        when(mockEmulation.disableNfcFForegroundService()).thenReturn(true);
        boolean result = instance.disableService(activity);
        Assert.assertTrue(result);
    }

    private Activity createAndResumeActivity() {
        Intent intent
            = new Intent(ApplicationProvider.getApplicationContext(),
                NfcFCardEmulationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);
        return activity;
    }

    private NfcFCardEmulation createMockedInstance() throws NoSuchFieldException {
        NfcFCardEmulation instance = NfcFCardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance, instance.getClass().getDeclaredField("sService"), mockEmulation);
        return instance;
    }
}
