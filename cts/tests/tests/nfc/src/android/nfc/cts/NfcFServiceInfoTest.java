/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.nfc.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.nfc.Flags;
import android.nfc.cardemulation.AidGroup;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.NfcFServiceInfo;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.proto.ProtoOutputStream;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
@RunWith(AndroidJUnit4.class)
public class NfcFServiceInfoTest {
    private static final String AID_1 = "00000000000000";
    private static final String AID_2 = "00000000000001";
    private static final String SERVICE_PACKAGE_NAME = "com.nfc.test";
    private static final String SERVICE_NAME = "hce_service";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final ArrayList<AidGroup> mDynamicAidGroups = new ArrayList<>();
    private final ResolveInfo mResolveInfo = new ResolveInfo();

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
    }

    @Before
    public void setUp() throws NoSuchFieldException, RemoteException {
        MockitoAnnotations.initMocks(this);
        assumeTrue(supportsHardware());

        ArrayList<String> aids = new ArrayList<String>();
        aids.add(AID_1);
        aids.add(AID_2);
        AidGroup aidGroup = new AidGroup(aids, CardEmulation.CATEGORY_PAYMENT);
        mDynamicAidGroups.add(aidGroup);

        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = SERVICE_PACKAGE_NAME;
        serviceInfo.name = SERVICE_NAME;
        serviceInfo.applicationInfo = new ApplicationInfo();
        mResolveInfo.serviceInfo = serviceInfo;
    }

    @Test
    public void test_ConstructorWithXml() {
        try {
            NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                    InstrumentationRegistry.getContext().getPackageManager(), mResolveInfo);
            fail();
        } catch (XmlPullParserException | IOException e) {
            // pass
        }
    }

    @Test
    public void test_Constructor() throws Exception {
        NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");

        assertEquals(nfcFServiceInfo.getComponent(),
                new ComponentName(SERVICE_PACKAGE_NAME, SERVICE_NAME));
        assertEquals(nfcFServiceInfo.getSystemCode(), "");
        assertEquals(nfcFServiceInfo.getNfcid2(), "");
        assertEquals(nfcFServiceInfo.getDescription(), "");
        assertEquals(nfcFServiceInfo.getUid(), 0);
        assertEquals(nfcFServiceInfo.getT3tPmm(), "");
    }

    @Test
    public void test_ReadWriteParcel() {
        NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");
        Parcel p = Parcel.obtain();
        nfcFServiceInfo.writeToParcel(p, 0);
        p.setDataPosition(0);

        NfcFServiceInfo newNfcFServiceInfo = NfcFServiceInfo.CREATOR.createFromParcel(p);
        assertEquals(newNfcFServiceInfo.getComponent(),
                new ComponentName(SERVICE_PACKAGE_NAME, SERVICE_NAME));
        assertEquals(newNfcFServiceInfo.getSystemCode(), "");
        assertEquals(newNfcFServiceInfo.getNfcid2(), "");
        assertEquals(newNfcFServiceInfo.getDescription(), "");
        assertEquals(newNfcFServiceInfo.getUid(), 0);
        assertEquals(newNfcFServiceInfo.getT3tPmm(), "");
    }

    @Test
    public void test_Equals() throws Exception {
        NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");
        NfcFServiceInfo newNfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");

        assertEquals(nfcFServiceInfo, newNfcFServiceInfo);
    }

    @Test
    public void test_Dump() throws Exception {
        NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");
        ParcelFileDescriptor pfd = new ParcelFileDescriptor(Parcel.obtain().readFileDescriptor());
        PrintWriter pw = new PrintWriter(new ByteArrayOutputStream());
        nfcFServiceInfo.dump(pfd, pw, new String[0]);
    }

    @Test
    public void test_DumpDebug() throws Exception {
        NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");
        ProtoOutputStream po = new ProtoOutputStream();
        nfcFServiceInfo.dumpDebug(po);
    }

    @Test
    public void test_SetDynamicSystemCode() throws Exception {
        NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");

        assertEquals(nfcFServiceInfo.getSystemCode(), "");

        nfcFServiceInfo.setDynamicSystemCode("test");
        assertEquals(nfcFServiceInfo.getSystemCode(), "test");
    }

    @Test
    public void test_SetNfcId2() throws Exception {
        NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");

        assertEquals(nfcFServiceInfo.getNfcid2(), "");

        nfcFServiceInfo.setDynamicNfcid2("test");
        assertEquals(nfcFServiceInfo.getNfcid2(), "test");
    }

    @Test
    public void test_LoadLabel() throws Exception {
        NfcFServiceInfo nfcFServiceInfo = new NfcFServiceInfo(
                mResolveInfo, "", "", "", "", "", 0, "");

        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        assertEquals(nfcFServiceInfo.loadLabel(pm), SERVICE_NAME);
        assertNotNull(nfcFServiceInfo.loadIcon(pm));
    }
}
