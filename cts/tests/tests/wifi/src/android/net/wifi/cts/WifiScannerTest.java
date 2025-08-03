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

package android.net.wifi.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiSsid;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.Parcel;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class WifiScannerTest extends WifiJUnit3TestBase {

    private static Context sContext;
    private static WifiScanner sWifiScanner;
    private static final long TEST_WAIT_DURATION_MS = 5000;
    private static final int POLL_WAIT_MSEC = 60;
    private static final String TEST_SSID = "TEST_SSID";
    public static final String TEST_BSSID = "04:ac:fe:45:34:10";
    public static final String TEST_CAPS = "CCMP";
    public static final int TEST_LEVEL = -56;
    public static final int TEST_FREQUENCY = 2412;
    public static final long TEST_TIMESTAMP = 4660L;

    private final Object mLock = new Object();
    private boolean mCachedScanDataReturned = false;
    private final HandlerThread mHandlerThread = new HandlerThread("WifiScannerTest");
    protected final Executor mExecutor;
    {
        mHandlerThread.start();
        mExecutor = new HandlerExecutor(new Handler(mHandlerThread.getLooper()));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        sWifiScanner =  sContext.getSystemService(WifiScanner.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private static WifiScanner.ScanSettings createRequest(WifiScanner.ChannelSpec[] channels,
            int period, int batch, int bssidsPerScan, int reportEvents) {
        WifiScanner.ScanSettings request = new WifiScanner.ScanSettings();
        request.band = WifiScanner.WIFI_BAND_UNSPECIFIED;
        request.channels = channels;
        request.periodInMs = period;
        request.numBssidsPerScan = bssidsPerScan;
        request.maxScansToCache = batch;
        request.reportEvents = reportEvents;
        return request;
    }

    private static WifiScanner.ScanSettings createRequest(int type, int band, int period, int batch,
            int bssidsPerScan, int reportEvents) {
        return createRequest(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, band, period, 0, 0,
                batch, bssidsPerScan, reportEvents);
    }

    private static WifiScanner.ScanSettings createRequest(int band, int period, int batch,
            int bssidsPerScan, int reportEvents) {
        return createRequest(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, band, period, 0, 0, batch,
                bssidsPerScan, reportEvents);
    }

    private static WifiScanner.ScanSettings createRequest(int type, int band, int period,
            int maxPeriod, int stepCount, int batch, int bssidsPerScan, int reportEvents) {
        WifiScanner.ScanSettings request = new WifiScanner.ScanSettings();
        request.type = type;
        request.band = band;
        request.channels = null;
        request.periodInMs = period;
        request.maxPeriodInMs = maxPeriod;
        request.stepCount = stepCount;
        request.numBssidsPerScan = bssidsPerScan;
        request.maxScansToCache = batch;
        request.reportEvents = reportEvents;
        return request;
    }

    /**
     * Verify WifiScanner ScanSettings setVendorIes() and getVendorIes() methods.
     * Test ScanSettings object being serialized and deserialized while vendorIes keeping the
     * values unchanged.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testVendorIesParcelable() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(
                WifiScanner.WIFI_BAND_BOTH_WITH_DFS, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        List<ScanResult.InformationElement> vendorIesList = new ArrayList<>();
        ScanResult.InformationElement vendorIe1 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, 0x11, 0x22, 0x33});
        ScanResult.InformationElement vendorIe2 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc});
        vendorIesList.add(vendorIe1);
        vendorIesList.add(vendorIe2);
        requestSettings.setVendorIes(vendorIesList);
        assertEquals(vendorIesList, requestSettings.getVendorIes());

        Parcel parcel = Parcel.obtain();
        requestSettings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        assertThat(
                WifiScanner.ScanSettings.CREATOR.createFromParcel(parcel).getVendorIes()).isEqualTo(
                requestSettings.getVendorIes());
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testPnoSettings() throws Exception {
        android.net.wifi.nl80211.PnoSettings pnoSettings =
                new android.net.wifi.nl80211.PnoSettings();
        pnoSettings.setScanIterations(3);
        pnoSettings.setScanIntervalMultiplier(4);
        assertEquals(3, pnoSettings.getScanIterations());
        assertEquals(4, pnoSettings.getScanIntervalMultiplier());
    }

    public void testParcelableScanData() {
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = TEST_SSID;
        scanResult.setWifiSsid(WifiSsid.fromBytes(TEST_SSID.getBytes(StandardCharsets.UTF_8)));
        scanResult.BSSID = TEST_BSSID;
        scanResult.capabilities = TEST_CAPS;
        scanResult.level = TEST_LEVEL;
        scanResult.frequency = TEST_FREQUENCY;
        scanResult.timestamp = TEST_TIMESTAMP;

        WifiScanner.ScanData scanData = new WifiScanner.ScanData(0, 0,
                new ScanResult[]{scanResult});
        WifiScanner.ParcelableScanData parcelableScanData = new WifiScanner
                .ParcelableScanData(new WifiScanner.ScanData[]{scanData});
        WifiScanner.ScanData[] result = parcelableScanData.getResults();
        assertThat(result.length).isEqualTo(1);
        ScanResult scanResult1 = result[0].getResults()[0];

        assertThat(scanResult1.SSID).isEqualTo(TEST_SSID);
        assertThat(scanResult1.getWifiSsid()).isEqualTo(scanResult.getWifiSsid());
        assertThat(scanResult1.BSSID).isEqualTo(TEST_BSSID);
        assertThat(scanResult1.capabilities).isEqualTo(TEST_CAPS);
        assertThat(scanResult1.level).isEqualTo(TEST_LEVEL);
        assertThat(scanResult1.frequency).isEqualTo(TEST_FREQUENCY);
        assertThat(scanResult1.timestamp).isEqualTo(TEST_TIMESTAMP);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public void testGetCachedScanData() throws Exception {
        mCachedScanDataReturned = false;
        Consumer<ScanData> listener = new Consumer<ScanData>() {
            @Override
            public void accept(ScanData scanData) {
                synchronized (mLock) {
                    mCachedScanDataReturned = true;
                    mLock.notify();
                }
            }
        };

        assertThrows(SecurityException.class,
                () -> sWifiScanner.getCachedScanData(mExecutor, listener));
        // null executor
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiScanner.getCachedScanData(null, listener));
        // null listener
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiScanner.getCachedScanData(mExecutor, null));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiScanner.getCachedScanData(mExecutor, listener);
            long timeout = System.currentTimeMillis() + TEST_WAIT_DURATION_MS;
            synchronized (mLock) {
                while (System.currentTimeMillis() < timeout && !mCachedScanDataReturned) {
                    mLock.wait(POLL_WAIT_MSEC);
                }
            }
            assertTrue(mCachedScanDataReturned);
        } catch (UnsupportedOperationException ex) {
            // Expected if the device does not support this API
        } catch (Exception e) {
            fail("getCachedScanData unexpected Exception " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
