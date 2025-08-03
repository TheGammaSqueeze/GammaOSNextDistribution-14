/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.net.wifi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.content.Context;
import android.net.wifi.WifiScanner.PnoSettings;
import android.net.wifi.WifiScanner.PnoSettings.PnoNetwork;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiScanner.ScanListener;
import android.net.wifi.WifiScanner.ScanSettings;
import android.os.Handler;
import android.os.Parcel;
import android.os.WorkSource;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Unit tests for {@link android.net.wifi.WifiScanner}.
 */
@SmallTest
public class WifiScannerTest {
    @Mock
    private Context mContext;
    @Mock
    private IWifiScanner mService;
    @Spy
    private Executor mExecutor = new SynchronousExecutor();
    @Mock
    private ScanListener mScanListener;
    @Mock
    private WifiScanner.ParcelableScanData mParcelableScanData;
    private ScanData[] mScanData = {};

    private static final boolean TEST_PNOSETTINGS_IS_CONNECTED = false;
    private static final int TEST_PNOSETTINGS_MIN_5GHZ_RSSI = -60;
    private static final int TEST_PNOSETTINGS_MIN_2GHZ_RSSI = -70;
    private static final int TEST_PNOSETTINGS_MIN_6GHZ_RSSI = -55;
    private static final String TEST_SSID_1 = "TEST1";
    private static final String TEST_SSID_2 = "TEST2";
    private static final int[] TEST_FREQUENCIES_1 = {};
    private static final int[] TEST_FREQUENCIES_2 = {2500, 5124, 6245};
    private static final String DESCRIPTION_NOT_AUTHORIZED = "Not authorized";
    private static final String TEST_PACKAGE_NAME = "com.test.123";
    private static final String TEST_FEATURE_ID = "test.feature";

    private WifiScanner mWifiScanner;
    private TestLooper mLooper;
    private Handler mHandler;

    /**
     * Setup before tests.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        mHandler = spy(new Handler(mLooper.getLooper()));
        mWifiScanner = new WifiScanner(mContext, mService, mLooper.getLooper());
        mLooper.dispatchAll();
        when(mParcelableScanData.getResults()).thenReturn(mScanData);
        when(mContext.getOpPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(mContext.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(mContext.getAttributionTag()).thenReturn(TEST_FEATURE_ID);
    }

    /**
     * Clean up after tests.
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    /**
     * Verify parcel read/write for ScanSettings.
     */
    @Test
    public void verifyScanSettingsParcelWithBand() throws Exception {
        ScanSettings writeSettings = new ScanSettings();
        writeSettings.type = WifiScanner.SCAN_TYPE_LOW_POWER;
        writeSettings.band = WifiScanner.WIFI_BAND_BOTH_WITH_DFS;

        ScanSettings readSettings = parcelWriteRead(writeSettings);
        assertEquals(readSettings.type, writeSettings.type);
        assertEquals(readSettings.band, writeSettings.band);
        assertEquals(0, readSettings.channels.length);
    }

    /**
     * Verify parcel read/write for ScanSettings.
     */
    @Test
    public void verifyScanSettingsParcelWithChannels() throws Exception {
        ScanSettings writeSettings = new ScanSettings();
        writeSettings.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;
        writeSettings.band = WifiScanner.WIFI_BAND_UNSPECIFIED;
        writeSettings.channels = new WifiScanner.ChannelSpec[] {
                new WifiScanner.ChannelSpec(5),
                new WifiScanner.ChannelSpec(7)
        };

        ScanSettings readSettings = parcelWriteRead(writeSettings);
        assertEquals(writeSettings.type, readSettings.type);
        assertEquals(writeSettings.band, readSettings.band);
        assertEquals(2, readSettings.channels.length);
        assertEquals(5, readSettings.channels[0].frequency);
        assertEquals(7, readSettings.channels[1].frequency);
    }

    /**
     * Write the provided {@link ScanSettings} to a parcel and deserialize it.
     */
    private static ScanSettings parcelWriteRead(ScanSettings writeSettings) throws Exception {
        Parcel parcel = Parcel.obtain();
        writeSettings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        return ScanSettings.CREATOR.createFromParcel(parcel);
    }

    /**
     *  PnoSettings object can be serialized and deserialized, while keeping the
     *  values unchanged.
     */
    @Test
    public void canSerializeAndDeserializePnoSettings() throws Exception {

        PnoSettings pnoSettings = new PnoSettings();

        PnoNetwork pnoNetwork1 = new PnoNetwork(TEST_SSID_1);
        PnoNetwork pnoNetwork2 = new PnoNetwork(TEST_SSID_2);
        pnoNetwork1.frequencies = TEST_FREQUENCIES_1;
        pnoNetwork2.frequencies = TEST_FREQUENCIES_2;

        pnoSettings.networkList = new PnoNetwork[]{pnoNetwork1, pnoNetwork2};
        pnoSettings.isConnected = TEST_PNOSETTINGS_IS_CONNECTED;
        pnoSettings.min5GHzRssi = TEST_PNOSETTINGS_MIN_5GHZ_RSSI;
        pnoSettings.min24GHzRssi = TEST_PNOSETTINGS_MIN_2GHZ_RSSI;
        pnoSettings.min6GHzRssi = TEST_PNOSETTINGS_MIN_6GHZ_RSSI;

        Parcel parcel = Parcel.obtain();
        pnoSettings.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        PnoSettings pnoSettingsDeserialized =
                pnoSettings.CREATOR.createFromParcel(parcel);

        assertNotNull(pnoSettingsDeserialized);
        assertEquals(TEST_PNOSETTINGS_IS_CONNECTED, pnoSettingsDeserialized.isConnected);
        assertEquals(TEST_PNOSETTINGS_MIN_5GHZ_RSSI, pnoSettingsDeserialized.min5GHzRssi);
        assertEquals(TEST_PNOSETTINGS_MIN_2GHZ_RSSI, pnoSettingsDeserialized.min24GHzRssi);
        assertEquals(TEST_PNOSETTINGS_MIN_6GHZ_RSSI, pnoSettingsDeserialized.min6GHzRssi);

        // Test parsing of PnoNetwork
        assertEquals(pnoSettings.networkList.length, pnoSettingsDeserialized.networkList.length);
        for (int i = 0; i < pnoSettings.networkList.length; i++) {
            PnoNetwork expected = pnoSettings.networkList[i];
            PnoNetwork actual = pnoSettingsDeserialized.networkList[i];
            assertEquals(expected.ssid, actual.ssid);
            assertEquals(expected.flags, actual.flags);
            assertEquals(expected.authBitField, actual.authBitField);
            assertTrue(Arrays.equals(expected.frequencies, actual.frequencies));
        }
    }

    /**
     *  Make sure that frequencies is not null by default.
     */
    @Test
    public void pnoNetworkFrequencyIsNotNull() throws Exception {
        PnoNetwork pnoNetwork = new PnoNetwork(TEST_SSID_1);
        assertNotNull(pnoNetwork.frequencies);
    }

    /**
     * Verify parcel read/write for ScanData.
     */
    @Test
    public void verifyScanDataParcel() throws Exception {
        ScanData writeScanData = new ScanData(2, 0, 3,
                WifiScanner.WIFI_BAND_BOTH_WITH_DFS, new ScanResult[0]);

        ScanData readScanData = parcelWriteRead(writeScanData);
        assertEquals(writeScanData.getId(), readScanData.getId());
        assertEquals(writeScanData.getFlags(), readScanData.getFlags());
        assertEquals(writeScanData.getBucketsScanned(), readScanData.getBucketsScanned());
        assertEquals(writeScanData.getScannedBandsInternal(),
                readScanData.getScannedBandsInternal());
        assertArrayEquals(writeScanData.getResults(), readScanData.getResults());
    }

    /**
     * Write the provided {@link ScanData} to a parcel and deserialize it.
     */
    private static ScanData parcelWriteRead(ScanData writeScanData) throws Exception {
        Parcel parcel = Parcel.obtain();
        writeScanData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        return ScanData.CREATOR.createFromParcel(parcel);
    }

    /**
     * Verify #setRnrSetting with valid and invalid inputs.
     */
    @Test
    public void testSetRnrSetting() throws Exception {
        // First verify IllegalArgumentException if an invalid input is passed in.
        assumeTrue(SdkLevel.isAtLeastS());
        try {
            WifiScanner.ScanSettings scanSettings = new WifiScanner.ScanSettings();
            scanSettings.setRnrSetting(-1);
            fail("Excepted IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        }

        // Then verify calling the API with a valid input.
        WifiScanner.ScanSettings scanSettings = new WifiScanner.ScanSettings();
        scanSettings.setRnrSetting(WifiScanner.WIFI_RNR_NOT_NEEDED);
        assertEquals(WifiScanner.WIFI_RNR_NOT_NEEDED, scanSettings.getRnrSetting());
    }

    @Test
    public void testIsScanning() throws Exception {
        mWifiScanner.isScanning();
        verify(mService).isScanning();
    }

    /**
     * Test behavior of {@link WifiScanner#startScan(ScanSettings, ScanListener)}
     * @throws Exception
     */
    @Test
    public void testStartScan() throws Exception {
        ScanSettings scanSettings = new ScanSettings();
        ScanListener scanListener = mock(ScanListener.class);

        mWifiScanner.startScan(scanSettings, scanListener);
        mLooper.dispatchAll();

        verify(mService).startScan(any(), eq(scanSettings),
                any(), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Test behavior of {@link WifiScanner#getSingleScanResults()}.
     */
    @Test
    public void testGetSingleScanResults() throws Exception {
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = TEST_SSID_1;
        ScanResult[] scanResults = {scanResult};

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public List<ScanResult> answer(String packageName, String featureId) {
                return new ArrayList<>(Arrays.asList(scanResults));
            }
        }).when(mService).getSingleScanResults(any(), any());

        List<ScanResult> results = mWifiScanner.getSingleScanResults();
        assertEquals(1, results.size());
        assertEquals(TEST_SSID_1, results.get(0).SSID);
    }

    /**
     * Test behavior of {@link WifiScanner#getSingleScanResults()} with an incorrect response from
     * the server.
     */
    @Test
    public void testGetSingleScanResultsIncorrectResponse() throws Exception {
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public List<ScanResult> answer(String packageName, String featureId) {
                return new ArrayList<>();
            }
        }).when(mService).getSingleScanResults(any(), any());
        List<ScanResult> results = mWifiScanner.getSingleScanResults();
        verify(mService).getSingleScanResults(eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        assertEquals(0, results.size());
    }

    /**
     * Test behavior of {@link WifiScanner#stopScan(ScanListener)}
     * @throws Exception
     */
    @Test
    public void testStopScan() throws Exception {
        ScanSettings scanSettings = new ScanSettings();
        ScanListener scanListener = mock(ScanListener.class);

        mWifiScanner.startScan(scanSettings, scanListener);
        mLooper.dispatchAll();

        mWifiScanner.stopScan(scanListener);
        mLooper.dispatchAll();

        verify(mService, times(1)).startScan(any(),
                eq(scanSettings), any(), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        verify(mService, times(1)).stopScan(any(),
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Test behavior of {@link WifiScanner#startScan(ScanSettings, ScanListener)}
     * @throws Exception
     */
    @Test
    public void testStartScanListenerOnSuccess() throws Exception {
        ScanSettings scanSettings = new ScanSettings();
        ScanListener scanListener = mock(ScanListener.class);

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiScannerListener listener, WifiScanner.ScanSettings settings,
                    WorkSource workSource, String packageName, String featureId) throws Exception {
                    listener.onSuccess();
            }
        }).when(mService).startScan(any(), any(), any(), any(), any());
        mWifiScanner.startScan(scanSettings, scanListener);
        mLooper.dispatchAll();

        verify(mService, times(1)).startScan(any(),
                eq(scanSettings), any(), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        verify(scanListener).onSuccess();
    }

    /**
     * Test behavior of {@link WifiScanner#startScan(ScanSettings, ScanListener)}
     * @throws Exception
     */
    @Test
    public void testStartScanListenerOnResults() throws Exception {
        ScanSettings scanSettings = new ScanSettings();
        ScanListener scanListener = mock(ScanListener.class);
        ScanResult scanResult = new ScanResult();
        ScanData[] scanDatas = new ScanData[]{
                new ScanData(0, 0, new ScanResult[]{scanResult})};

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiScannerListener listener, WifiScanner.ScanSettings settings,
                    WorkSource workSource, String packageName, String featureId) throws Exception {
                listener.onSuccess();
                listener.onResults(scanDatas);
            }
        }).when(mService).startScan(any(), any(), any(), any(), any());
        mWifiScanner.startScan(scanSettings, scanListener);
        mLooper.dispatchAll();
        verify(mService).startScan(any(), eq(scanSettings),
                any(), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        mLooper.dispatchAll();
        verify(scanListener).onResults(eq(scanDatas));
    }

    /**
     * Test behavior of {@link WifiScanner#startDisconnectedPnoScan(ScanSettings, PnoSettings,
     * Executor, WifiScanner.PnoScanListener)}
     * @throws Exception
     */
    @Test
    public void testStartDisconnectedPnoScan() throws Exception {
        ScanSettings scanSettings = new ScanSettings();
        PnoSettings pnoSettings = new PnoSettings();
        WifiScanner.PnoScanListener pnoScanListener = mock(WifiScanner.PnoScanListener.class);

        mWifiScanner.startDisconnectedPnoScan(
                scanSettings, pnoSettings, mock(Executor.class), pnoScanListener);
        mLooper.dispatchAll();
        verify(mService).startPnoScan(any(),
                eq(scanSettings), eq(pnoSettings),
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    /**
     * Test behavior of {@link WifiScanner#startConnectedPnoScan(ScanSettings, PnoSettings,
     * Executor, WifiScanner.PnoScanListener)}
     * @throws Exception
     */
    @Test
    public void testStartConnectedPnoScan() throws Exception {
        ScanSettings scanSettings = new ScanSettings();
        PnoSettings pnoSettings = new PnoSettings();
        WifiScanner.PnoScanListener pnoScanListener = mock(WifiScanner.PnoScanListener.class);

        mWifiScanner.startConnectedPnoScan(
                scanSettings, pnoSettings, mock(Executor.class), pnoScanListener);
        verify(mService).startPnoScan(any(), eq(scanSettings), eq(pnoSettings), any(), any());
        assertTrue(scanSettings.isPnoScan);
        assertTrue(pnoSettings.isConnected);
    }

    /**
     * Test behavior of {@link WifiScanner#stopPnoScan(ScanListener)}
     * Executor, WifiScanner.PnoScanListener)}
     * @throws Exception
     */
    @Test
    public void testStopPnoScan() throws Exception {
        ScanSettings scanSettings = new ScanSettings();
        PnoSettings pnoSettings = new PnoSettings();
        WifiScanner.PnoScanListener pnoScanListener = mock(WifiScanner.PnoScanListener.class);

        mWifiScanner.startDisconnectedPnoScan(
                scanSettings, pnoSettings, mock(Executor.class), pnoScanListener);
        mWifiScanner.stopPnoScan(pnoScanListener);

        verify(mService, times(1)).stopPnoScan(any(),
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
    }

    @Test
    public void testScanDataAddResults() throws Exception {
        ScanResult scanResult1 = new ScanResult();
        scanResult1.SSID = TEST_SSID_1;
        ScanData scanData = new ScanData(0, 0, new ScanResult[]{scanResult1});

        ScanResult scanResult2 = new ScanResult();
        scanResult2.SSID = TEST_SSID_2;
        scanData.addResults(new ScanResult[]{scanResult2});

        ScanResult[] consolidatedScanResults = scanData.getResults();
        assertEquals(2, consolidatedScanResults.length);
        assertEquals(TEST_SSID_1, consolidatedScanResults[0].SSID);
        assertEquals(TEST_SSID_2, consolidatedScanResults[1].SSID);
    }

    @Test
    public void testScanDataParcel() throws Exception {
        ScanResult scanResult1 = new ScanResult();
        scanResult1.SSID = TEST_SSID_1;
        ScanData scanData = new ScanData(5, 4, new ScanResult[]{scanResult1});

        Parcel parcel = Parcel.obtain();
        scanData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        ScanData readScanData = ScanData.CREATOR.createFromParcel(parcel);

        assertEquals(scanData.getId(), readScanData.getId());
        assertEquals(scanData.getFlags(), readScanData.getFlags());
        assertEquals(scanData.getResults().length, readScanData.getResults().length);
        assertEquals(scanData.getResults()[0].SSID, readScanData.getResults()[0].SSID);
    }

    /** Tests that upon registration success, {@link ScanListener#onSuccess()} is called. */
    @Test
    public void testRegisterScanListenerSuccess() throws Exception {
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiScannerListener listener, String packageName, String featureId)
                    throws Exception {
                listener.onSuccess();
            }
        }).when(mService).registerScanListener(any(), any(), any());
        mWifiScanner.registerScanListener(mExecutor, mScanListener);
        mLooper.dispatchAll();
        verify(mService).registerScanListener(any(), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        verify(mScanListener).onSuccess();
    }

    /**
     * Tests that upon registration failed, {@link ScanListener#onFailure(int, String)} is called.
     */
    @Test
    public void testRegisterScanListenerFailed() throws Exception {
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiScannerListener listener, String packageName, String featureId)
                    throws Exception {
                listener.onFailure(WifiScanner.REASON_NOT_AUTHORIZED, DESCRIPTION_NOT_AUTHORIZED);
            }
        }).when(mService).registerScanListener(any(), any(), any());
        mWifiScanner.registerScanListener(mExecutor, mScanListener);
        mLooper.dispatchAll();
        verify(mService).registerScanListener(any(), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        verify(mScanListener).onFailure(WifiScanner.REASON_NOT_AUTHORIZED,
                DESCRIPTION_NOT_AUTHORIZED);
    }

    /**
     * Tests that when the ScanListener is triggered, {@link ScanListener#onResults(ScanData[])}
     * is called.
     */
    @Test
    public void testRegisterScanListenerReceiveScanResults() throws Exception {
        ArgumentCaptor<IWifiScannerListener> listenerArgumentCaptor = ArgumentCaptor.forClass(
                IWifiScannerListener.class);
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiScannerListener listener, String packageName, String featureId)
                    throws Exception {
                listener.onSuccess();
            }
        }).when(mService).registerScanListener(any(), any(), any());
        mWifiScanner.registerScanListener(mExecutor, mScanListener);
        verify(mService).registerScanListener(listenerArgumentCaptor.capture(),
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        verify(mExecutor, times(1)).execute(any());
        verify(mScanListener).onSuccess();

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiScannerListener listener, WifiScanner.ScanSettings settings,
                    WorkSource workSource, String packageName, String featureId) throws Exception {
                listener.onResults(mScanData);
                listenerArgumentCaptor.getValue().onResults(mScanData);
            }
        }).when(mService).startScan(any(), any(), any(), any(), any());
        ScanSettings scanSettings = new ScanSettings();
        mWifiScanner.startScan(scanSettings, mock(ScanListener.class));
        verify(mExecutor, times(2)).execute(any());
        verify(mScanListener).onResults(mScanData);
    }

    /**
     * Tests that after unregistering a scan listener, {@link ScanListener#onResults(ScanData[])}
     * is not called.
     */
    @Test
    public void testUnregisterScanListener() throws Exception {
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiScannerListener listener, String packageName, String featureId)
                    throws Exception {
                listener.onSuccess();
            }
        }).when(mService).unregisterScanListener(any(), any(), any());

        mWifiScanner.registerScanListener(mExecutor, mScanListener);
        mWifiScanner.unregisterScanListener(mScanListener);
        verify(mService).registerScanListener(any(), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        verify(mService).unregisterScanListener(any(), eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID));
        verify(mScanListener).onSuccess();
        verify(mScanListener, never()).onResults(mScanData);
    }

    /**
     * Tests isFullBandScan() method with and without DFS check
     */
    @Test
    public void testIsFullBandScan() throws Exception {
        assertFalse(WifiScanner.isFullBandScan(WifiScanner.WIFI_BAND_24_GHZ, true));
        assertFalse(WifiScanner.isFullBandScan(WifiScanner.WIFI_BAND_5_GHZ, true));
        assertFalse(WifiScanner.isFullBandScan(WifiScanner.WIFI_BAND_6_GHZ, true));
        assertFalse(WifiScanner.isFullBandScan(
                WifiScanner.WIFI_BAND_6_GHZ | WifiScanner.WIFI_BAND_5_GHZ, true));
        assertTrue(WifiScanner.isFullBandScan(
                WifiScanner.WIFI_BAND_24_GHZ | WifiScanner.WIFI_BAND_5_GHZ, true));
        assertFalse(WifiScanner.isFullBandScan(
                WifiScanner.WIFI_BAND_24_GHZ | WifiScanner.WIFI_BAND_5_GHZ, false));
        assertTrue(WifiScanner.isFullBandScan(WifiScanner.WIFI_BAND_BOTH_WITH_DFS, true));
        assertTrue(WifiScanner.isFullBandScan(WifiScanner.WIFI_BAND_BOTH_WITH_DFS, false));
    }

    /**
     * Tests creating WifiScanner in multi threads, with async channel disconnects.
     */
    @Test
    public void testWifiScannerConcurrentServiceStart() {
        WifiScanner wifiScanner = new WifiScanner(
                mContext, mService, WifiFrameworkInitializer.getInstanceLooper());

        Thread thread1 = new Thread(() -> {
            try {
                WifiScanner wifiScanner1 = new WifiScanner(
                        mContext, mService, WifiFrameworkInitializer.getInstanceLooper());
            }  catch (NullPointerException e) {
                fail("WifiScanner can't be initialized! " + e);
            }
        });

        thread1.start();
        try {
            thread1.join();
        } catch (InterruptedException e) {
            fail("WifiScanner can't be initialized!" + e);
        }
    }

    /**
     * Verify #setVendorIes with valid and invalid inputs
     */
    @Test
    public void testSetVendorIes() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        WifiScanner.ScanSettings scanSettings = new WifiScanner.ScanSettings();
        List<ScanResult.InformationElement> vendorIesList = new ArrayList<>();
        ScanResult.InformationElement vendorIe1 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, 0x11, 0x22, 0x33});
        ScanResult.InformationElement vendorIe2 = new ScanResult.InformationElement(255, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc});
        ScanResult.InformationElement vendorIe3 = new ScanResult.InformationElement(221, 0,
                new byte[0]);
        vendorIe3.bytes = null;
        ScanResult.InformationElement vendorIe4 = new ScanResult.InformationElement(221, 0,
                new byte[256]);
        ScanResult.InformationElement vendorIe5 = new ScanResult.InformationElement(221, 0,
                new byte[256]);

        vendorIesList.add(vendorIe2);
        assertThrows(IllegalArgumentException.class,
                () -> scanSettings.setVendorIes(vendorIesList));

        vendorIesList.remove(vendorIe2);
        vendorIesList.add(vendorIe3);
        assertThrows(IllegalArgumentException.class,
                () -> scanSettings.setVendorIes(vendorIesList));

        vendorIesList.remove(vendorIe3);
        vendorIesList.add(vendorIe4);
        assertThrows(IllegalArgumentException.class,
                () -> scanSettings.setVendorIes(vendorIesList));

        vendorIesList.add(vendorIe5);
        assertThrows(IllegalArgumentException.class,
                () -> scanSettings.setVendorIes(vendorIesList));

        vendorIesList.remove(vendorIe4);
        vendorIesList.remove(vendorIe5);
        vendorIesList.add(vendorIe1);
        scanSettings.setVendorIes(vendorIesList);
        assertEquals(vendorIesList, scanSettings.getVendorIes());
    }
}
