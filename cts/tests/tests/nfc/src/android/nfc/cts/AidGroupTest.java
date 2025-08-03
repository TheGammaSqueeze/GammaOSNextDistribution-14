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

import static android.nfc.cardemulation.CardEmulation.CATEGORY_PAYMENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.nfc.Flags;
import android.nfc.cardemulation.AidGroup;
import android.os.Parcel;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Xml;
import android.util.proto.ProtoOutputStream;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
@RunWith(AndroidJUnit4.class)
public class AidGroupTest {
    private static final String AID_1 = "00000000000000";
    private static final String AID_2 = "00000000000001";
    private static final String DESCRIPTION = "Description";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final List<String> mAids = new ArrayList<>();

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
    }

    @Before
    public void setUp() throws NoSuchFieldException, RemoteException {
        MockitoAnnotations.initMocks(this);
        assumeTrue(supportsHardware());

        mAids.add(AID_1);
        mAids.add(AID_2);
    }

    @Test
    public void test_Constructor() {
        AidGroup aidGroup = new AidGroup(mAids, CATEGORY_PAYMENT);

        assertEquals(aidGroup.getAids(), mAids);
        assertEquals(aidGroup.getCategory(), CATEGORY_PAYMENT);

    }

    @Test
    public void test_ReadWriteParcel() {
        AidGroup aidGroup = new AidGroup(mAids, CATEGORY_PAYMENT);
        Parcel p = Parcel.obtain();
        aidGroup.writeToParcel(p, 0);
        p.setDataPosition(0);

        AidGroup newAidGroup = AidGroup.CREATOR.createFromParcel(p);

        assertEquals(aidGroup.getAids(), newAidGroup.getAids());
        assertEquals(aidGroup.getCategory(), newAidGroup.getCategory());
    }

    @Test
    public void test_ReadWriteXml() throws Exception {
        AidGroup aidGroup = new AidGroup(mAids, CATEGORY_PAYMENT);

        XmlSerializer serializer = Xml.newSerializer();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.setOutput(out, StandardCharsets.UTF_8.name());
        aidGroup.writeAsXml(serializer);
        serializer.flush();

        final XmlPullParser parser = Xml.newPullParser();
        final InputStream in = new ByteArrayInputStream(out.toByteArray());
        parser.setInput(in, StandardCharsets.UTF_8.name());
        AidGroup newAidGroup = AidGroup.createFromXml(parser);

        assertEquals(aidGroup.getAids(), newAidGroup.getAids());
        assertEquals(aidGroup.getCategory(), newAidGroup.getCategory());
    }

    @Test
    public void test_Dump() throws Exception {
        AidGroup aidGroup = new AidGroup(mAids, CATEGORY_PAYMENT);
        ProtoOutputStream po = new ProtoOutputStream();
        aidGroup.dump(po);
    }
}
