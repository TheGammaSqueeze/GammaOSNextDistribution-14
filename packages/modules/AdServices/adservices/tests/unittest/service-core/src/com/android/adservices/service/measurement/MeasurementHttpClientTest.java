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

package com.android.adservices.service.measurement;

import static com.android.adservices.service.Flags.MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_NETWORK_READ_TIMEOUT_MS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import javax.net.ssl.SSLHandshakeException;

/** Unit tests for {@link MeasurementHttpClient} */
@RunWith(AndroidJUnit4.class)
public final class MeasurementHttpClientTest {

    private static final String ADSERVICES_PACKAGE_NAME = "com.google.android.adservices";
    private static final String EXTSERVICES_PACKAGE_NAME = "com.google.android.extservices";
    private static final String KEY_CONNECT_TIMEOUT = "measurement_network_connect_timeout_ms";
    private static final String KEY_READ_TIMEOUT = "measurement_network_read_timeout_ms";

    private static final String MAPPINGS =
            "341300000,341400000,202401"
                    + "|341400000,341500000,202402"
                    + "|341500000,341600000,202403"
                    + "|341600000,341700000,202404";
    private MeasurementHttpClient mNetworkConnection;
    @Mock Flags mMockFlags;
    @Mock PackageManager mMockPackageManager;
    @Mock Context mContext;

    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);
        setupPackageVersion(341500000L, ADSERVICES_PACKAGE_NAME);
    }

    @Test
    public void testOpenAndSetupConnectionDefaultTimeoutValues_success() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        final URL url = new URL("https://google.com");
        mNetworkConnection = new MeasurementHttpClient(mContext);
        final URLConnection urlConnection = mNetworkConnection.setup(url);

        Assert.assertEquals(
                MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS, urlConnection.getConnectTimeout());
        Assert.assertEquals(MEASUREMENT_NETWORK_READ_TIMEOUT_MS, urlConnection.getReadTimeout());
    }

    @Test
    public void testSetup_ForComputeFromMappingsFlagTrue_SetsCorrectHeader() throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn(MAPPINGS).when(mMockFlags).getAdservicesVersionMappings();
            setupPackageVersion(341410234L, ADSERVICES_PACKAGE_NAME);
            final URL url = new URL("https://test.com");
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URLConnection urlConnection = mNetworkConnection.setup(url);
            Assert.assertEquals("202402", urlConnection.getRequestProperty("Version"));
        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_ForComputeFromMappingsFlagTrueAndExtServices_SetsCorrectHeader()
            throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn(MAPPINGS).when(mMockFlags).getAdservicesVersionMappings();
            setupPackageVersion(341610234L, EXTSERVICES_PACKAGE_NAME);
            final URL url = new URL("https://test.com");
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URLConnection urlConnection = mNetworkConnection.setup(url);
            Assert.assertEquals("202404", urlConnection.getRequestProperty("Version"));
        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_ForComputeFromMappingsFlagTrueAndVersionAsStartRange_SetsCorrectHeader()
            throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn(MAPPINGS).when(mMockFlags).getAdservicesVersionMappings();
            setupPackageVersion(341500000L, ADSERVICES_PACKAGE_NAME);
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URL url = new URL("https://test.com");
            final URLConnection urlConnection = mNetworkConnection.setup(url);
            Assert.assertEquals("202403", urlConnection.getRequestProperty("Version"));
        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_ForComputeFromMappingsAndVersionAsStartRangeExtS_SetsCorrectHeader()
            throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn(MAPPINGS).when(mMockFlags).getAdservicesVersionMappings();
            final URL url = new URL("https://test.com");

            setupPackageVersion(341600000L, EXTSERVICES_PACKAGE_NAME);
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URLConnection urlConnection2 = mNetworkConnection.setup(url);
            Assert.assertEquals("202404", urlConnection2.getRequestProperty("Version"));
        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_ForComputeFromMappingsFlagTrueAndVersionAsEndRange_SetsCorrectHeader()
            throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn(MAPPINGS).when(mMockFlags).getAdservicesVersionMappings();
            setupPackageVersion(341400000L, ADSERVICES_PACKAGE_NAME);
            final URL url = new URL("https://test.com");
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URLConnection urlConnection = mNetworkConnection.setup(url);
            Assert.assertEquals("202402", urlConnection.getRequestProperty("Version"));
        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_ForComputeFromMappingsAndVersionAsEndRangeExtS_SetsCorrectHeader()
            throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn(MAPPINGS).when(mMockFlags).getAdservicesVersionMappings();
            final URL url = new URL("https://test.com");
            setupPackageVersion(341500000L, EXTSERVICES_PACKAGE_NAME);
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URLConnection urlConnection2 = mNetworkConnection.setup(url);
            Assert.assertEquals("202403", urlConnection2.getRequestProperty("Version"));
        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_ForComputeFromMappingsFlagTrueAndVersionNotInRange_SetsHeaderAsNotFound()
            throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn(MAPPINGS).when(mMockFlags).getAdservicesVersionMappings();
            setupPackageVersion(341800000L, ADSERVICES_PACKAGE_NAME);
            final URL url = new URL("https://test.com");
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URLConnection urlConnection = mNetworkConnection.setup(url);
            Assert.assertEquals("000000", urlConnection.getRequestProperty("Version"));
        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_ForComputeFromMappingsAndVersionNotInRangeExtS_SetsHeaderAsNotFound()
            throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(true).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn(MAPPINGS).when(mMockFlags).getAdservicesVersionMappings();
            final URL url = new URL("https://test.com");
            setupPackageVersion(341800000L, EXTSERVICES_PACKAGE_NAME);
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URLConnection urlConnection2 = mNetworkConnection.setup(url);
            Assert.assertEquals("000000", urlConnection2.getRequestProperty("Version"));
        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_ForComputeFromMappingsFlagFalse_SetsHeaderUsingVersionFlag()
            throws IOException {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            doReturn(false).when(mMockFlags).getEnableComputeVersionFromMappings();
            doReturn("202311").when(mMockFlags).getMainlineTrainVersion();
            setupPackageVersion(341800000L, ADSERVICES_PACKAGE_NAME);
            final URL url = new URL("https://test.com");
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final URLConnection urlConnection = mNetworkConnection.setup(url);
            Assert.assertEquals("202311", urlConnection.getRequestProperty("Version"));

        } finally {
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testSetup_headersLeakingInfoAreOverridden() throws Exception {
        final MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
        MockWebServer server = null;
        try {
            server =
                    mMockWebServerRule.startMockWebServer(
                            request -> {
                                Assert.assertNotNull(request);
                                final String userAgentHeader = request.getHeader("user-agent");
                                Assert.assertNotNull(userAgentHeader);
                                Assert.assertEquals("", userAgentHeader);
                                return new MockResponse().setResponseCode(200);
                            });

            final URL url = server.getUrl("/test");
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final HttpURLConnection urlConnection =
                    (HttpURLConnection) mNetworkConnection.setup(url);

            Assert.assertEquals(200, urlConnection.getResponseCode());
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    @Test
    public void testSetup_connectLocalhostToUntrustedServer_success() throws Exception {
        final MockWebServerRule mMockWebServerRule = createUntrustedForHttps();
        MockWebServer server = null;
        try {
            server =
                    mMockWebServerRule.startMockWebServer(
                            request -> {
                                Assert.assertNotNull(request);
                                final String userAgentHeader = request.getHeader("user-agent");
                                Assert.assertNotNull(userAgentHeader);
                                Assert.assertEquals("", userAgentHeader);
                                return new MockResponse().setResponseCode(200);
                            });

            final URL url = server.getUrl("/test");

            Assert.assertTrue(WebAddresses.isLocalhost(Uri.parse(url.toString())));
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final HttpURLConnection urlConnection =
                    (HttpURLConnection) mNetworkConnection.setup(url);

            Assert.assertEquals(200, urlConnection.getResponseCode());
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    @Test
    public void testSetup_connectNonLocalhostToUntrustedServer_throws() throws Exception {
        final MockWebServerRule mMockWebServerRule = createUntrustedForHttps();
        MockWebServer server = null;
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(WebAddresses.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(false).when(() -> WebAddresses.isLocalhost(any(Uri.class)));
            server =
                    mMockWebServerRule.startMockWebServer(
                            request -> {
                                Assert.assertNotNull(request);
                                final String userAgentHeader = request.getHeader("user-agent");
                                Assert.assertNotNull(userAgentHeader);
                                Assert.assertEquals("", userAgentHeader);
                                return new MockResponse().setResponseCode(200);
                            });

            final URL url = server.getUrl("/test");

            Assert.assertFalse(WebAddresses.isLocalhost(Uri.parse(url.toString())));
            mNetworkConnection = new MeasurementHttpClient(mContext);
            final HttpURLConnection urlConnection =
                    (HttpURLConnection) mNetworkConnection.setup(url);

            Assert.assertThrows(SSLHandshakeException.class, () -> urlConnection.getResponseCode());
        } finally {
            if (server != null) {
                server.shutdown();
            }
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testOpenAndSetupConnectionOverrideTimeoutValues_success() throws Exception {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CONNECT_TIMEOUT,
                "123456",
                /* makeDefault */ false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_READ_TIMEOUT,
                "654321",
                /* makeDefault */ false);

        final URL url = new URL("https://google.com");
        mNetworkConnection = new MeasurementHttpClient(mContext);
        final URLConnection urlConnection = mNetworkConnection.setup(url);

        Assert.assertEquals(123456, urlConnection.getConnectTimeout());
        Assert.assertEquals(654321, urlConnection.getReadTimeout());
    }

    @Test
    public void testOpenAndSetupConnectionONullUrl_failure() {
        mNetworkConnection = new MeasurementHttpClient(mContext);
        Assert.assertThrows(NullPointerException.class, () -> mNetworkConnection.setup(null));
    }

    @Test
    public void testOpenAndSetupConnectionOInvalidUrl_failure() {
        mNetworkConnection = new MeasurementHttpClient(mContext);
        Assert.assertThrows(
                MalformedURLException.class, () -> mNetworkConnection.setup(new URL("x")));
    }

    private static MockWebServerRule createUntrustedForHttps() {
        return MockWebServerRule.forHttps(
                ApplicationProvider.getApplicationContext(),
                "adservices_untrusted_test_server.p12",
                "adservices_test");
    }

    private void setupPackageVersion(long version, String packageName) {
        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = packageName;
        packageInfo.isApex = true;
        packageInfo.setLongVersionCode(version);
        ArrayList<PackageInfo> packageInfoList = new ArrayList<>();
        packageInfoList.add(packageInfo);
        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getInstalledPackages(eq(PackageManager.MATCH_APEX)))
                .thenReturn(packageInfoList);
    }
}
