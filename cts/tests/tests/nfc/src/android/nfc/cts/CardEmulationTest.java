package android.nfc.cts;

import static android.nfc.cts.WalletRoleTestUtils.CTS_PACKAGE_NAME;
import static android.nfc.cts.WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME;
import static android.nfc.cts.WalletRoleTestUtils.WALLET_HOLDER_SERVICE_DESC;
import static android.nfc.cts.WalletRoleTestUtils.getWalletRoleHolderService;
import static android.nfc.cts.WalletRoleTestUtils.runWithRole;
import static android.nfc.cts.WalletRoleTestUtils.runWithRoleNone;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.nfc.Flags;
import android.nfc.INfcCardEmulation;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.AidGroup;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.PollingFrame;
import android.nfc.cardemulation.PollingFrame.PollingFrameType;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
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
import java.util.HexFormat;
import java.util.List;

@RunWith(JUnit4.class)
public class CardEmulationTest {
    private NfcAdapter mAdapter;

    private static final ComponentName mService =
        new ComponentName("android.nfc.cts", "android.nfc.cts.CtsMyHostApduService");

    private Context mContext;
    private INfcCardEmulation mOldService;
    @Mock private INfcCardEmulation mEmulation;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
    }

    private boolean supportsHardwareForEse() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_ESE);
    }

    @Before
    public void setUp() throws NoSuchFieldException, RemoteException {
        MockitoAnnotations.initMocks(this);
        assumeTrue(supportsHardware());
        mContext = InstrumentationRegistry.getContext();
        mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertNotNull(mAdapter);

        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldReader serviceField = new FieldReader(instance,
                instance.getClass().getDeclaredField("sService"));
        mOldService = (INfcCardEmulation) serviceField.read();
    }

    @After
    public void tearDown() throws Exception {
        if (!supportsHardware()) return;
        restoreOriginalService();
    }

    private void restoreOriginalService() throws NoSuchFieldException {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance,
                instance.getClass().getDeclaredField("sService"), mOldService);
    }

    private void setMockService() throws NoSuchFieldException {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance, instance.getClass().getDeclaredField("sService"),
                mEmulation);
    }

    @Test
    public void getNonNullInstance() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        Assert.assertNotNull(instance);
    }

    @Test
    public void testIsDefaultServiceForCategory() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultServiceForCategory(anyInt(), any(ComponentName.class),
            anyString())).thenReturn(true);
        boolean result = instance.isDefaultServiceForCategory(mService,
            CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testIsDefaultServiceForAid() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        String aid = "00000000000000";
        when(mEmulation.isDefaultServiceForAid(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.isDefaultServiceForAid(mService, aid);
        Assert.assertTrue(result);
    }

    @Test
    public void testCategoryAllowsForegroundPreferenceWithCategoryPayment() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        boolean result
            = instance.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testCategoryAllowsForegroundPrefenceWithCategoryOther() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        boolean result
            = instance.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryPaymentAndPaymentRegistered()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultPaymentRegistered()).thenReturn(true);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_PREFER_DEFAULT, result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryPaymentAndWithoutPaymentRegistered()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultPaymentRegistered()).thenReturn(false);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_ALWAYS_ASK, result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryOther() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_OTHER);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_ASK_IF_CONFLICT, result);
    }

    @Test
    public void testRegisterAidsForService() throws NoSuchFieldException, RemoteException {
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        CardEmulation cardEmulation = createMockedInstance();
        when(mEmulation.registerAidGroupForService(anyInt(), any(ComponentName.class), any()))
                .thenReturn(true);
        Assert.assertTrue(cardEmulation.registerAidsForService(mService,
                CardEmulation.CATEGORY_PAYMENT, aids));
    }

    @Test
    public void testUnsetOffHostForService() throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        when(mEmulation.unsetOffHostForService(anyInt(), any(ComponentName.class)))
            .thenReturn(true);
        boolean result = instance.unsetOffHostForService(mService);
        Assert.assertTrue(result);
    }

    @Test
    public void testSetOffHostForService() throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        String offHostSecureElement = "eSE";
        when(mEmulation.setOffHostForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.setOffHostForService(mService, offHostSecureElement);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetAidsForService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        AidGroup aidGroup = new AidGroup(aids, CardEmulation.CATEGORY_PAYMENT);
        when(mEmulation.getAidGroupForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(aidGroup);
        List<String> result = instance.getAidsForService(mService, CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(aids, result);
    }

    @Test
    public void testRemoveAidsForService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.removeAidGroupForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.removeAidsForService(mService, CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testSetPreferredService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        Activity activity = createAndResumeActivity();
        when(mEmulation.setPreferredService(any(ComponentName.class))).thenReturn(true);
        boolean result = instance.setPreferredService(activity, mService);
        Assert.assertTrue(result);
    }

    @Test
    public void testUnsetPreferredService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        Activity activity = createAndResumeActivity();
        when(mEmulation.unsetPreferredService()).thenReturn(true);
        boolean result = instance.unsetPreferredService(activity);
        Assert.assertTrue(result);
    }

    @Test
    public void testSupportsAidPrefixRegistration() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.supportsAidPrefixRegistration()).thenReturn(true);
        boolean result = instance.supportsAidPrefixRegistration();
        Assert.assertTrue(result);
    }

    @Test
    public void testGetAidsForPreferredPaymentService() throws NoSuchFieldException,
        RemoteException {
        CardEmulation instance = createMockedInstance();
        ArrayList<AidGroup> dynamicAidGroups = new ArrayList<AidGroup>();
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        AidGroup aidGroup = new AidGroup(aids, CardEmulation.CATEGORY_PAYMENT);
        dynamicAidGroups.add(aidGroup);
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false, "",
            new ArrayList<AidGroup>(), dynamicAidGroups, false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        List<String> result = instance.getAidsForPreferredPaymentService();
        Assert.assertEquals(aids, result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOnHost()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ true,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals("Host", result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOffHostAndNoSecureElement()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ false,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "",
            /* offHost = */ null, "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals("OffHost", result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOffHostAndSecureElement()
        throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        String offHostSecureElement = "OffHost Secure Element";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ false,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "",
            /* offHost = */ offHostSecureElement, "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals(offHostSecureElement, result);
    }

    @Test
    public void testGetDescriptionForPreferredPaymentService() throws NoSuchFieldException,
        RemoteException {
        CardEmulation instance = createMockedInstance();
        String description = "Preferred Payment Service Description";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false,
            /* description */ description, new ArrayList<AidGroup>(), new ArrayList<AidGroup>(),
            false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        CharSequence result = instance.getDescriptionForPreferredPaymentService();
        Assert.assertEquals(description, result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testGetServices() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        String description = "Preferred Payment Service Description";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false,
                /* description */ description, new ArrayList<AidGroup>(), new ArrayList<AidGroup>(),
                false, 0, 0, "", "", "");
        List<ApduServiceInfo> services = List.of(serviceInfo);
        when(mEmulation.getServices(anyInt(), anyString())).thenReturn(services);
        Assert.assertEquals(instance.getServices(CardEmulation.CATEGORY_PAYMENT, 0), services);
    }

    @Test
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testGetPreferredPaymentService() {
        final String expectedPaymentService = "foo.bar/foo.bar.baz.Service";
        Settings.Secure.putString(ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.NFC_PAYMENT_DEFAULT_COMPONENT, expectedPaymentService);

        ComponentName paymentService = CardEmulation.getPreferredPaymentService(
                ApplicationProvider.getApplicationContext());

        Assert.assertEquals(paymentService,
                ComponentName.unflattenFromString(expectedPaymentService));
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testTypeAPollingLoopToDefault() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            ensurePreferredService(CustomHostApduService.class);
            notifyPollingLoopAndWait(new ArrayList<PollingFrame>(frames),
                    CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testTypeAPollingLoopToWalletHolder() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                () -> {
                    NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
                    adapter.notifyHceDeactivated();
                    ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    ensurePreferredService(WalletRoleTestUtils.WALLET_HOLDER_SERVICE_DESC);
                    notifyPollingLoopAndWait(new ArrayList<PollingFrame>(frames),
                            WalletRoleTestUtils.getWalletRoleHolderService().getClassName());
                    adapter.notifyHceDeactivated();
                });
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testTypeAPollingLoopToForeground() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext,
                            CtsMyHostApduService.class)));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testTypeAPollingLoopToForegroundWithWalletHolder() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                () -> {
                    Assert.assertTrue(cardEmulation.setPreferredService(activity,
                            new ComponentName(mContext,
                                    CtsMyHostApduService.class)));
                    ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    ensurePreferredService(CtsMyHostApduService.class);
                    notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
                    Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
                    activity.finish();
                    adapter.notifyHceDeactivated();
                });
    }

    void ensurePreferredService(Class serviceClass) {
        ensurePreferredService(serviceClass, mContext);
    }

    static void ensurePreferredService(Class serviceClass, Context context) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        int resId = (serviceClass == CtsMyHostApduService.class
                ? R.string.CtsPaymentService
                : (serviceClass == CustomHostApduService.class
                        ? R.string.CtsCustomPaymentService : R.string.CtsBackgroundPaymentService));
        final String desc = context.getResources().getString(resId);
        DefaultPaymentProviderTestUtils.ensurePreferredService(desc, context);
    }

    void ensurePreferredService(String serviceDesc) {
        DefaultPaymentProviderTestUtils.ensurePreferredService(serviceDesc, mContext);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustomDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName customServiceName = new ComponentName(mContext, CustomHostApduService.class);
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                annotationStringHex, false));
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        adapter.notifyHceDeactivated();
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testThreeWayConflictPollingLoopToForegroundDynamic() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            ComponentName ctsMyServiceName = new ComponentName(mContext,
                    CtsMyHostApduService.class);
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsMyServiceName));
            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex, false));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex, false));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(ctsMyServiceName,
                    annotationStringHex, false));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testBackgroundForegroundConflictPollingLoopToForegroundDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        ComponentName ctsServiceName = new ComponentName(mContext,
                CtsMyHostApduService.class);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsServiceName));
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(ctsServiceName,
                    annotationStringHex, false));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex, false));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundPaymentConflictPollingLoopToPaymentDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        ComponentName originalDefault = null;
        try {
            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);
            originalDefault = setDefaultPaymentService(customServiceName);
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);

            Assert.assertTrue(cardEmulation.isDefaultServiceForCategory(customServiceName,
                    CardEmulation.CATEGORY_PAYMENT));
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex, false));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex, false));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CustomHostApduService.class);
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustom() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        adapter.notifyHceDeactivated();
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testThreeWayConflictPollingLoopToForeground() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testThreeWayConflictPollingLoopToForegroundWithWalletHolder() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                () -> {
                    Assert.assertTrue(cardEmulation.setPreferredService(activity,
                            new ComponentName(mContext, CtsMyHostApduService.class)));
                    String testName = new Object() {
                    }.getClass().getEnclosingMethod().getName();
                    String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
                    ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
                    frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                            HexFormat.of().parseHex(annotationStringHex)));
                    ensurePreferredService(CtsMyHostApduService.class);
                    notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
                    Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
                    activity.finish();
                    adapter.notifyHceDeactivated();
                });
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testBackgroundForegroundConflictPollingLoopToForeground() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundPaymentConflictPollingLoopToPayment() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CustomHostApduService.class);
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testBackgroundWalletConflictPollingLoopToWallet_walletRoleEnabled() {
        runWithRole(mContext, WALLET_HOLDER_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            adapter.notifyHceDeactivated();
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            android.util.Log.i("PLF", annotationStringHex);
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(WALLET_HOLDER_SERVICE_DESC);
            notifyPollingLoopAndWait(frames, getWalletRoleHolderService().getClassName());
            adapter.notifyHceDeactivated();
        });
    }


    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE})
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testAutoTransact() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        createAndResumeActivity();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        Assert.assertTrue(adapter.setObserveModeEnabled(true));
        Assert.assertTrue(adapter.isObserveModeEnabled());
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        Assert.assertFalse(adapter.isObserveModeEnabled());
        adapter.notifyHceDeactivated();
        Assert.assertTrue(adapter.isObserveModeEnabled());
        adapter.setObserveModeEnabled(false);
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAutoTransact_walletRoleEnabled() throws NoSuchFieldException {
        restoreOriginalService();
        runWithRole(mContext, CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            assumeTrue(adapter.isObserveModeSupported());
            adapter.notifyHceDeactivated();
            createAndResumeActivity();
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
            Assert.assertFalse(adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
            Assert.assertTrue(adapter.isObserveModeEnabled());
            adapter.setObserveModeEnabled(false);
        });
        setMockService();
    }


    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE})
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testAutoTransactDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        createAndResumeActivity();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName customServiceName = new ComponentName(mContext, CustomHostApduService.class);
        Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                annotationStringHex, true));
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        Assert.assertTrue(adapter.setObserveModeEnabled(true));
        Assert.assertTrue(adapter.isObserveModeEnabled());
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        Assert.assertFalse(adapter.isObserveModeEnabled());
        adapter.notifyHceDeactivated();
        Assert.assertTrue(adapter.isObserveModeEnabled());
        adapter.setObserveModeEnabled(false);
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAutoTransactDynamic_walletRoleEnabled() throws NoSuchFieldException {
        restoreOriginalService();
        runWithRole(mContext, CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            assumeTrue(adapter.isObserveModeSupported());
            adapter.notifyHceDeactivated();
            createAndResumeActivity();
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex, true));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
            Assert.assertFalse(adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
            Assert.assertTrue(adapter.isObserveModeEnabled());
            adapter.setObserveModeEnabled(false);
        });
        setMockService();
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP})
    public void testInvalidPollingLoopFilter() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName customServiceName = new ComponentName(mContext, CustomHostApduService.class);
        Assert.assertThrows(IllegalArgumentException.class,
                () -> cardEmulation.registerPollingLoopFilterForService(customServiceName,
                        "", false));
        Assert.assertThrows(IllegalArgumentException.class,
                () ->cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    "????", false));
        Assert.assertThrows(IllegalArgumentException.class,
                () ->cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    "123", false));

    }

    static void ensureUnlocked() {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final PowerManager pm = context.getSystemService(PowerManager.class);
        final KeyguardManager km = context.getSystemService(KeyguardManager.class);
        try {
            if (pm != null && !pm.isInteractive()) {
                runShellCommand("input keyevent KEYCODE_WAKEUP");
                CommonTestUtils.waitUntil("Device does not wake up after 5 seconds", 5,
                        () -> pm != null && pm.isInteractive());
            }
            if (km != null && km.isKeyguardLocked()) {
                CommonTestUtils.waitUntil("Device does not unlock after 3 seconds", 3,
                        () -> {
                        SystemUtil.runWithShellPermissionIdentity(
                                () -> instrumentation.sendKeyDownUpSync(
                                        (KeyEvent.KEYCODE_MENU)));
                        return km != null && !km.isKeyguardLocked();
                    }
                );
            }
        } catch (InterruptedException ie) {
        }
    }

    private PollingFrame createFrame(@PollingFrameType int type) {
        return new PollingFrame(type, null, 8, 0);
    }

    private PollingFrame createFrameWithData(@PollingFrameType int type, byte[] data) {
        return new PollingFrame(type, data, 8, 0);
    }

    private ComponentName setDefaultPaymentService(Class serviceClass) {
        ComponentName componentName = setDefaultPaymentService(
                new ComponentName(mContext, serviceClass));
        return componentName;
    }

    ComponentName setDefaultPaymentService(ComponentName serviceName) {
        return DefaultPaymentProviderTestUtils.setDefaultPaymentService(serviceName, mContext);
    }

    static final class SettingsObserver extends ContentObserver {
        boolean mSeenChange = false;

        SettingsObserver(Handler handler) {
            super(handler);
        }
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            mSeenChange = true;
            synchronized (this) {
                this.notify();
            }
        }
    }

    static PollLoopReceiver sCurrentPollLoopReceiver = null;

    static class PollLoopReceiver  {
        int mFrameIndex = 0;
        List<PollingFrame> mFrames;
        String mServiceName;
        List<PollingFrame> mReceivedFrames;
        String mReceivedServiceName;

        PollLoopReceiver(List<PollingFrame> frames, String serviceName) {
            mFrames = frames;
            mServiceName = serviceName;
            mReceivedFrames = new ArrayList<PollingFrame>(1);
        }

        void notifyPollingLoop(String className, List<PollingFrame> receivedFrames) {
            mReceivedFrames.addAll(receivedFrames);
            mReceivedServiceName = className;
            if (mReceivedFrames.size() < mFrames.size()) {
                return;
            }
            synchronized (this) {
                this.notify();
            }
        }

        void test() {
            for (PollingFrame receivedFrame : mReceivedFrames) {
                if (mFrameIndex >= mFrames.size()) {
                    Assert.fail("received more frames than sent: " + receivedFrame);
                }
                Assert.assertEquals(mFrames.get(mFrameIndex).getType(), receivedFrame.getType());
                Assert.assertEquals(mFrames.get(mFrameIndex).getGain(), receivedFrame.getGain());
                Assert.assertArrayEquals(mFrames.get(mFrameIndex).getData(),
                        receivedFrame.getData());
                mFrameIndex++;
            }
            Assert.assertEquals(mServiceName, mReceivedServiceName);
        }
    }

    private void notifyPollingLoopAndWait(List<PollingFrame> frames, String serviceName) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        sCurrentPollLoopReceiver = new PollLoopReceiver(frames, serviceName);
        for (PollingFrame frame : frames) {
            adapter.notifyPollingLoop(frame);
        }
        synchronized (sCurrentPollLoopReceiver) {
            try {
                sCurrentPollLoopReceiver.wait(5000);
            } catch (InterruptedException ie) {
                Assert.assertNull(ie);
            }
        }
        sCurrentPollLoopReceiver.test();
        Assert.assertEquals(frames.size(), sCurrentPollLoopReceiver.mFrameIndex);
        sCurrentPollLoopReceiver = null;
    }

    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testAidResolutionWithRoleHolder_anotherAppHoldsForeground()
            throws NoSuchFieldException {
        restoreOriginalService();
        Activity activity = createAndResumeActivity();
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        instance.setPreferredService(activity, WalletRoleTestUtils.getForegroundService());
        runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME, ()-> {
            /*
            * Aid Mapping:
            * Wallet Holder App: Service 1:     PAYMENT_AID_1, PAYMENT_AID_2
            * Wallet Holder App: Service 2:     PAYMENT_AID_1, PAYMENT_AID_2
            * Foreground App :   Only Service:  PAYMENT_AID_1
            * Non Payment App:   Only Service:  NON_PAYMENT_AID_1
            *
            * Scenario:
            * Wallet Role Holder is WalletRoleHolderApp
            * Foreground app: ForegroundApp
            *
            * Expected Outcome:
            * Both wallet role holder and the foreground app holds PAYMENT_AID_1.
            * So the foreground app is expected to get the routing for PAYMENT_AID_1.
            *
            * The foreground app does not have NON_PAYMENT_AID_1. Neither does the role holder.
            * So an app in the background (Non Payment App) gets the routing.
            **/
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getNonPaymentService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertTrue(instance.unsetPreferredService(activity));
            activity.finish();
        });
        setMockService();
    }

    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testAidResolutionWithRoleHolder() throws NoSuchFieldException {
        restoreOriginalService();
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME, ()-> {
            /*
             * Aid Mapping:
             * Wallet Holder App: Service 1:     PAYMENT_AID_1, PAYMENT_AID_2
             * Wallet Holder App: Service 2:     PAYMENT_AID_1, PAYMENT_AID_2
             * Foreground App :   Only Service:  PAYMENT_AID_1
             * Non Payment App:   Only Service:  NON_PAYMENT_AID_1
             *
             * Scenario:
             * Wallet Role Holder is WalletRoleHolderApp
             * Foreground app: None
             *
             * Expected Outcome:
             * Wallet role holder and a background app holds PAYMENT_AID_1.
             * So the Wallet role holder app is expected to get the routing for PAYMENT_AID_1.
             * The wallet role holder has two services holding PAYMENT_AID_1. Therefore the one
             * that has the priority based on alphabetical sorting of their names gets the routing
             * WalletRoleHolderService vs XWAlletRoleHolderService. WalletRoleHolderService gets it.
             *
             * Only the Wallet Role Holder holds PAYMENT_AID_2.
             * So the wallet role holder app gets the routing for PAYMENT_AID_2.
             *
             * A background app that is not the wallet role holder has the NON_PAYMENT_AID_1.
             * So that app gets the routing for NON_PAYMENT_AID_1.
             **/
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getNonPaymentService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
        });
        setMockService();
    }

    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testAidResolutionWithRoleHolderSetToNone() throws NoSuchFieldException {
        restoreOriginalService();
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        runWithRoleNone(mContext, ()-> {
            /*
             * Aid Mapping:
             * Wallet Holder App: Service 1:     PAYMENT_AID_1, PAYMENT_AID_2
             * Wallet Holder App: Service 2:     PAYMENT_AID_1, PAYMENT_AID_2
             * Foreground App :   Only Service:  PAYMENT_AID_1
             * Non Payment App:   Only Service:  NON_PAYMENT_AID_1
             *
             * Scenario:
             * Wallet Role Holder is Set to None
             *
             * Expected Outcome:
             * Wallet role holder does not exist therefore routing is handled on the basis of
             * supported AIDs and overlapping services.
             *
             * Non Payment App is the only map holding the NON_PAYMENT_AID_1 and will be set
             * as the default service for that AID.
             *
             *  The rest of the apps will always need to disambig and will not be set as defaults.
             *
             **/
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getNonPaymentService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
        });
    }

    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testAidResolutionWithRoleHolder_holderDoesNotSupportAid_overLappingAids()
            throws NoSuchFieldException {
        restoreOriginalService();
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        runWithRole(mContext, WalletRoleTestUtils.NON_PAYMENT_NFC_PACKAGE_NAME, ()-> {
            /*
             * Aid Mapping:
             * Wallet Holder App: Service 1:     PAYMENT_AID_1, PAYMENT_AID_2
             * Wallet Holder App: Service 2:     PAYMENT_AID_1, PAYMENT_AID_2
             * Foreground App :   Only Service:  PAYMENT_AID_1
             * Non Payment App:   Only Service:  NON_PAYMENT_AID_1
             *
             * Scenario:
             * Wallet Role Holder is Non Payment App
             * Foreground app: None
             *
             * Expected Outcome:
             * Wallet role holder holds NON_PAYMENT_AID_1
             * So wallet role holders gets the routing for NON_PAYMENT_AID_1.
             * The non wallet apps have overlapping aids and therefore no default services exist
             * for those AIDs.
             *
             **/
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getNonPaymentService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
        });
        setMockService();
    }

    private Activity createAndResumeActivity() {
        ensureUnlocked();
        Intent intent
            = new Intent(ApplicationProvider.getApplicationContext(),
                NfcFCardEmulationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);
        return activity;
    }

    private CardEmulation createMockedInstance() throws NoSuchFieldException {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance, instance.getClass().getDeclaredField("sService"), mEmulation);
        return instance;
    }
}
