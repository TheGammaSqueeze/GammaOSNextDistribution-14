/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nearby.cts;

import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;
import static android.nearby.ScanRequest.SCAN_MODE_BALANCED;
import static android.nearby.ScanRequest.SCAN_MODE_LOW_LATENCY;
import static android.nearby.ScanRequest.SCAN_MODE_LOW_POWER;
import static android.nearby.ScanRequest.SCAN_MODE_NO_POWER;
import static android.nearby.ScanRequest.SCAN_TYPE_FAST_PAIR;
import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanRequest;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class ScanRequestTest {

    private static final int RSSI = -40;

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testScanType() {
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .build();

        assertThat(request.getScanType()).isEqualTo(SCAN_TYPE_NEARBY_PRESENCE);
    }

    // Valid scan type must be set to one of ScanRequest#SCAN_TYPE_
    @Test(expected = IllegalStateException.class)
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testScanType_notSet_throwsException() {
        new ScanRequest.Builder().setScanMode(SCAN_MODE_BALANCED).build();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testScanMode_defaultLowPower() {
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_FAST_PAIR)
                .build();

        assertThat(request.getScanMode()).isEqualTo(SCAN_MODE_LOW_POWER);
    }

    /** Verify setting work source with null value in the scan request is allowed */
    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testSetWorkSource_nullValue() {
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_FAST_PAIR)
                .setWorkSource(null)
                .build();

        // Null work source is allowed.
        assertThat(request.getWorkSource().isEmpty()).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testIsEnableBle_defaultTrue() {
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_FAST_PAIR)
                .build();

        assertThat(request.isBleEnabled()).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testIsOffloadOnly_defaultFalse() {
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_FAST_PAIR)
                .build();

        assertThat(request.isOffloadOnly()).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testSetOffloadOnly_isOffloadOnlyTrue() {
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .setOffloadOnly(true)
                .build();

        assertThat(request.isOffloadOnly()).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void test_isValidScanType() {
        assertThat(ScanRequest.isValidScanType(SCAN_TYPE_FAST_PAIR)).isTrue();
        assertThat(ScanRequest.isValidScanType(SCAN_TYPE_NEARBY_PRESENCE)).isTrue();

        assertThat(ScanRequest.isValidScanType(0)).isFalse();
        assertThat(ScanRequest.isValidScanType(5)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void test_isValidScanMode() {
        assertThat(ScanRequest.isValidScanMode(SCAN_MODE_LOW_LATENCY)).isTrue();
        assertThat(ScanRequest.isValidScanMode(SCAN_MODE_BALANCED)).isTrue();
        assertThat(ScanRequest.isValidScanMode(SCAN_MODE_LOW_POWER)).isTrue();
        assertThat(ScanRequest.isValidScanMode(SCAN_MODE_NO_POWER)).isTrue();

        assertThat(ScanRequest.isValidScanMode(3)).isFalse();
        assertThat(ScanRequest.isValidScanMode(-2)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void test_scanModeToString() {
        assertThat(ScanRequest.scanModeToString(2)).isEqualTo("SCAN_MODE_LOW_LATENCY");
        assertThat(ScanRequest.scanModeToString(1)).isEqualTo("SCAN_MODE_BALANCED");
        assertThat(ScanRequest.scanModeToString(0)).isEqualTo("SCAN_MODE_LOW_POWER");
        assertThat(ScanRequest.scanModeToString(-1)).isEqualTo("SCAN_MODE_NO_POWER");

        assertThat(ScanRequest.scanModeToString(3)).isEqualTo("SCAN_MODE_INVALID");
        assertThat(ScanRequest.scanModeToString(-2)).isEqualTo("SCAN_MODE_INVALID");
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testScanFilter() {
        ScanRequest request = new ScanRequest.Builder().setScanType(
                SCAN_TYPE_NEARBY_PRESENCE).addScanFilter(getPresenceScanFilter()).build();

        assertThat(request.getScanFilters()).isNotEmpty();
        assertThat(request.getScanFilters().get(0).getMaxPathLoss()).isEqualTo(RSSI);
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void describeContents() {
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_FAST_PAIR)
                .build();
        assertThat(request.describeContents()).isEqualTo(0);
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testCreatorNewArray() {
        ScanRequest[] requests =
                ScanRequest.CREATOR.newArray(2);
        assertThat(requests.length).isEqualTo(2);
    }

    private static PresenceScanFilter getPresenceScanFilter() {
        final byte[] secretId = new byte[]{1, 2, 3, 4};
        final byte[] authenticityKey = new byte[]{0, 1, 1, 1};
        final byte[] publicKey = new byte[]{1, 1, 2, 2};
        final byte[] encryptedMetadata = new byte[]{1, 2, 3, 4, 5};
        final byte[] metadataEncryptionKeyTag = new byte[]{1, 1, 3, 4, 5};

        PublicCredential credential = new PublicCredential.Builder(
                secretId, authenticityKey, publicKey, encryptedMetadata, metadataEncryptionKeyTag)
                .setIdentityType(IDENTITY_TYPE_PRIVATE)
                .build();

        final int action = 123;
        return new PresenceScanFilter.Builder()
                .addCredential(credential)
                .setMaxPathLoss(RSSI)
                .addPresenceAction(action)
                .build();
    }
}
