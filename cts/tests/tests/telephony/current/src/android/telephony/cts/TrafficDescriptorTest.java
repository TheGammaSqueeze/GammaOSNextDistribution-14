/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.TrafficDescriptor.OsAppId;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

public class TrafficDescriptorTest {
    private static final String DNN = "DNN";
    // 97a498e3fc925c9489860333d06e4e470a454e5445525052495345.
    // [OsAppId.ANDROID_OS_ID, "ENTERPRISE", 1]
    private static final byte[] ENTERPRISE_OS_APP_ID = {-105, -92, -104, -29, -4, -110, 92,
            -108, -119, -122, 3, 51, -48, 110, 78, 71, 10, 69, 78, 84, 69,
            82, 80, 82, 73, 83, 69};

    // 97a498e3fc925c9489860333d06e4e47125052494f524954495a455f4c4154454e4359.
    // [OsAppId.ANDROID_OS_ID, "PRIORITIZE_LATENCY", 1]
    private static final byte[] URLLC_OS_APP_ID = {-105, -92, -104, -29, -4, -110, 92,
            -108, -119, -122, 3, 51, -48, 110, 78, 71, 18, 80, 82, 73, 79, 82, 73, 84, 73, 90, 69,
            95, 76, 65, 84, 69, 78, 67, 89};

    // 97a498e3fc925c9489860333d06e4e47145052494f524954495a455f42414e445749445448.
    // [OsAppId.ANDROID_OS_ID, "PRIORITIZE_BANDWIDTH", 1]
    private static final byte[] EMBB_OS_APP_ID = {-105, -92, -104, -29, -4, -110, 92,
            -108, -119, -122, 3, 51, -48, 110, 78, 71, 20, 80, 82, 73, 79, 82, 73, 84, 73, 90, 69,
            95, 66, 65, 78, 68, 87, 73, 68, 84, 72};

    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        mPackageManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getPackageManager();
    }

    @Test
    public void testConstructorAndGetters() {
        TrafficDescriptor td = new TrafficDescriptor(DNN, ENTERPRISE_OS_APP_ID);
        assertThat(td.getDataNetworkName()).isEqualTo(DNN);
        assertThat(td.getOsAppId()).isEqualTo(ENTERPRISE_OS_APP_ID);

        td = new TrafficDescriptor(DNN, URLLC_OS_APP_ID);
        assertThat(td.getDataNetworkName()).isEqualTo(DNN);
        assertThat(td.getOsAppId()).isEqualTo(URLLC_OS_APP_ID);

        td = new TrafficDescriptor(DNN, EMBB_OS_APP_ID);
        assertThat(td.getDataNetworkName()).isEqualTo(DNN);
        assertThat(td.getOsAppId()).isEqualTo(EMBB_OS_APP_ID);
    }

    @Test
    public void testEquals() {
        TrafficDescriptor enterpriseTd = new TrafficDescriptor(DNN, ENTERPRISE_OS_APP_ID);
        TrafficDescriptor equalsTd = new TrafficDescriptor(DNN, ENTERPRISE_OS_APP_ID);
        assertThat(enterpriseTd).isEqualTo(equalsTd);

        TrafficDescriptor urllcTd = new TrafficDescriptor(DNN, URLLC_OS_APP_ID);
        equalsTd = new TrafficDescriptor(DNN, URLLC_OS_APP_ID);
        assertThat(urllcTd).isEqualTo(equalsTd);

        TrafficDescriptor embbTd = new TrafficDescriptor(DNN, EMBB_OS_APP_ID);
        equalsTd = new TrafficDescriptor(DNN, EMBB_OS_APP_ID);
        assertThat(embbTd).isEqualTo(equalsTd);

        assertThat(enterpriseTd).isNotEqualTo(urllcTd);
        assertThat(enterpriseTd).isNotEqualTo(embbTd);
        assertThat(urllcTd).isNotEqualTo(enterpriseTd);
        assertThat(urllcTd).isNotEqualTo(embbTd);
        assertThat(embbTd).isNotEqualTo(enterpriseTd);
        assertThat(embbTd).isNotEqualTo(urllcTd);
    }

    @Test
    public void testNotEquals() {
        TrafficDescriptor td = new TrafficDescriptor(DNN, ENTERPRISE_OS_APP_ID);
        TrafficDescriptor notEqualsTd = new TrafficDescriptor("NOT_DNN", ENTERPRISE_OS_APP_ID);
        assertThat(td).isNotEqualTo(notEqualsTd);
        assertThat(td).isNotEqualTo(null);
    }

    @Test
    public void testParcel() {
        TrafficDescriptor td = new TrafficDescriptor(DNN, ENTERPRISE_OS_APP_ID);

        Parcel parcel = Parcel.obtain();
        td.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        TrafficDescriptor parcelTd = TrafficDescriptor.CREATOR.createFromParcel(parcel);
        assertThat(td).isEqualTo(parcelTd);
        parcel.recycle();

        td = new TrafficDescriptor(null, URLLC_OS_APP_ID);

        parcel = Parcel.obtain();
        td.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        parcelTd = TrafficDescriptor.CREATOR.createFromParcel(parcel);
        assertThat(td).isEqualTo(parcelTd);
        parcel.recycle();

        td = new TrafficDescriptor(null, EMBB_OS_APP_ID);

        parcel = Parcel.obtain();
        td.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        parcelTd = TrafficDescriptor.CREATOR.createFromParcel(parcel);
        assertThat(td).isEqualTo(parcelTd);
        parcel.recycle();
    }

    @Test
    public void testBuilder() {
        TrafficDescriptor td = new TrafficDescriptor.Builder()
                .setDataNetworkName(DNN)
                .setOsAppId(ENTERPRISE_OS_APP_ID)
                .build();
        assertThat(td.getDataNetworkName()).isEqualTo(DNN);
        assertThat(td.getOsAppId()).isEqualTo(ENTERPRISE_OS_APP_ID);

        td = new TrafficDescriptor.Builder()
                .setOsAppId(URLLC_OS_APP_ID)
                .build();
        assertThat(td.getDataNetworkName()).isNull();
        assertThat(td.getOsAppId()).isEqualTo(URLLC_OS_APP_ID);

        td = new TrafficDescriptor.Builder()
                .setOsAppId(EMBB_OS_APP_ID)
                .build();
        assertThat(td.getDataNetworkName()).isNull();
        assertThat(td.getOsAppId()).isEqualTo(EMBB_OS_APP_ID);
    }

    // The purpose of this test is to ensure that no real package names are used as app id.
    // Traffic descriptor should throw exception for any app id not in the allowed list.
    @Test
    public void testRealAppIdThrowException() {
        for (PackageInfo packageInfo : mPackageManager.getInstalledPackages(0 /*flags*/)) {
            OsAppId osAppId = new OsAppId(OsAppId.ANDROID_OS_ID, packageInfo.packageName, 1);
            // IllegalArgumentException is expected when using a real package name as app id.
            assertThrows(IllegalArgumentException.class,
                    () -> new TrafficDescriptor.Builder()
                            .setDataNetworkName(DNN)
                            .setOsAppId(osAppId.getBytes())
                            .build());
        }
    }
}
