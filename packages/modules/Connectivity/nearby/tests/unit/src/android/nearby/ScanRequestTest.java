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

package android.nearby;

import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;
import static android.nearby.ScanRequest.SCAN_MODE_BALANCED;
import static android.nearby.ScanRequest.SCAN_MODE_LOW_POWER;
import static android.nearby.ScanRequest.SCAN_TYPE_FAST_PAIR;
import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import android.os.Parcel;
import android.os.WorkSource;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Units tests for {@link ScanRequest}. */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class ScanRequestTest {

    private static final int RSSI = -40;
    private static final int UID = 1001;
    private static final String APP_NAME = "android.nearby.tests";

    private static WorkSource getWorkSource() {
        return new WorkSource(UID, APP_NAME);
    }

    /** Test creating a scan request. */
    @Test
    public void testScanRequestBuilder() {
        final int scanType = SCAN_TYPE_FAST_PAIR;
        ScanRequest request = new ScanRequest.Builder().setScanType(scanType).build();

        assertThat(request.getScanType()).isEqualTo(scanType);
        assertThat(request.getScanMode()).isEqualTo(SCAN_MODE_LOW_POWER);
        // Work source is null if not set.
        assertThat(request.getWorkSource().isEmpty()).isTrue();
    }

    /** Verify RuntimeException is thrown when creating scan request with invalid scan type. */
    @Test(expected = RuntimeException.class)
    public void testScanRequestBuilder_invalidScanType() {
        final int invalidScanType = -1;
        ScanRequest.Builder builder = new ScanRequest.Builder().setScanType(invalidScanType);

        builder.build();
    }

    /** Verify RuntimeException is thrown when creating scan mode with invalid scan mode. */
    @Test(expected = RuntimeException.class)
    public void testScanModeBuilder_invalidScanType() {
        final int invalidScanMode = -5;
        ScanRequest.Builder builder = new ScanRequest.Builder().setScanType(
                SCAN_TYPE_FAST_PAIR).setScanMode(invalidScanMode);
        builder.build();
    }

    /** Verify setting work source in the scan request. */
    @Test
    public void testSetWorkSource() {
        WorkSource workSource = getWorkSource();
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_FAST_PAIR)
                .setWorkSource(workSource)
                .build();

        assertThat(request.getWorkSource()).isEqualTo(workSource);
    }

    /** Verify setting work source with null value in the scan request. */
    @Test
    public void testSetWorkSource_nullValue() {
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_FAST_PAIR)
                .setWorkSource(null)
                .build();

        // Null work source is allowed.
        assertThat(request.getWorkSource().isEmpty()).isTrue();
    }

    /** Verify toString returns expected string. */
    @Test
    @SdkSuppress(minSdkVersion = 34)
    public void testToString() {
        WorkSource workSource = getWorkSource();
        ScanRequest request = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_FAST_PAIR)
                .setScanMode(SCAN_MODE_BALANCED)
                .setBleEnabled(true)
                .setWorkSource(workSource)
                .build();

        assertThat(request.toString()).isEqualTo(
                "Request[scanType=1, scanMode=SCAN_MODE_BALANCED, "
                        + "bleEnabled=true, offloadOnly=false, "
                        + "workSource=WorkSource{" + UID + " " + APP_NAME + "}, scanFilters=[]]");
    }

    /** Verify toString works correctly with null WorkSource. */
    @Test
    @SdkSuppress(minSdkVersion = 34)
    public void testToString_nullWorkSource_offloadOnly() {
        ScanRequest request = new ScanRequest.Builder().setScanType(
                SCAN_TYPE_FAST_PAIR).setWorkSource(null).setOffloadOnly(true).build();

        assertThat(request.toString()).isEqualTo("Request[scanType=1, "
                + "scanMode=SCAN_MODE_LOW_POWER, bleEnabled=true, offloadOnly=true, "
                + "workSource=WorkSource{}, scanFilters=[]]");
    }

    /** Verify writing and reading from parcel for scan request. */
    @Test
    public void testParceling() {
        WorkSource workSource = getWorkSource();
        ScanRequest originalRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .setScanMode(SCAN_MODE_BALANCED)
                .setBleEnabled(true)
                .setWorkSource(workSource)
                .addScanFilter(getPresenceScanFilter())
                .build();

        // Write the scan request to parcel, then read from it.
        ScanRequest request = writeReadFromParcel(originalRequest);

        // Verify the request read from parcel equals to the original request.
        assertThat(request).isEqualTo(originalRequest);
    }

    /** Verify parceling with null WorkSource. */
    @Test
    public void testParceling_nullWorkSource() {
        final int scanType = SCAN_TYPE_NEARBY_PRESENCE;
        ScanRequest originalRequest = new ScanRequest.Builder()
                .setScanType(scanType).build();

        ScanRequest request = writeReadFromParcel(originalRequest);

        assertThat(request).isEqualTo(originalRequest);
    }

    private ScanRequest writeReadFromParcel(ScanRequest originalRequest) {
        Parcel parcel = Parcel.obtain();
        originalRequest.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return ScanRequest.CREATOR.createFromParcel(parcel);
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
