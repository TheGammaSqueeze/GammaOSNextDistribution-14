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

package com.android.rkpdapp.unittest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;

import com.android.rkpdapp.GeekResponse;
import com.android.rkpdapp.RkpdException;
import com.android.rkpdapp.interfaces.ServerInterface;
import com.android.rkpdapp.metrics.ProvisioningAttempt;
import com.android.rkpdapp.testutil.FakeRkpServer;
import com.android.rkpdapp.utils.CborUtils;
import com.android.rkpdapp.utils.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class ServerInterfaceTest {
    private static final Duration TIME_TO_REFRESH_HOURS = Duration.ofHours(2);
    private static Context sContext;
    private ServerInterface mServerInterface;

    @BeforeClass
    public static void init() {
        sContext = Mockito.spy(ApplicationProvider.getApplicationContext());
    }

    @Before
    public void setUp() {
        Settings.clearPreferences(sContext);
        mServerInterface = new ServerInterface(sContext, false);
    }

    @After
    public void tearDown() {
        Settings.clearPreferences(sContext);
        Mockito.reset(sContext);
    }

    @Test
    public void testRetryOnServerFailure() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(FakeRkpServer.Response.INTERNAL_ERROR,
                FakeRkpServer.Response.INTERNAL_ERROR)) {
            Settings.setDeviceConfig(sContext, 1 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());
            Settings.setMaxRequestTime(sContext, 100);
            GeekResponse ignored = mServerInterface.fetchGeek(
                    ProvisioningAttempt.createScheduledAttemptMetrics(sContext));
            assertWithMessage("Expected RkpdException.").fail();
        } catch (RkpdException e) {
            assertThat(e.getErrorCode()).isEqualTo(RkpdException.ErrorCode.HTTP_SERVER_ERROR);
            assertThat(e).hasMessageThat().contains("HTTP error status encountered");
        }
    }

    @Test
    public void testFetchGeekRkpDisabled() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_RKP_DISABLED,
                FakeRkpServer.Response.INTERNAL_ERROR)) {
            Settings.setDeviceConfig(sContext, 1 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());
            GeekResponse response = mServerInterface.fetchGeek(
                    ProvisioningAttempt.createScheduledAttemptMetrics(sContext));

            assertThat(response.numExtraAttestationKeys).isEqualTo(0);
            assertThat(response.getChallenge()).isNotNull();
            assertThat(response.getGeekChain(2)).isNotNull();
        }
    }

    @Test
    public void testFetchGeekRkpEnabled() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            Settings.setDeviceConfig(sContext, 1 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());
            GeekResponse response = mServerInterface.fetchGeek(
                    ProvisioningAttempt.createScheduledAttemptMetrics(sContext));

            assertThat(response.numExtraAttestationKeys).isEqualTo(20);
            assertThat(response.getChallenge()).isNotNull();
            byte[] challenge = Base64.decode("AAABgEg1zGsBILStY/1VNI7st0AG9x2S/tba+H4=",
                    Base64.DEFAULT);
            assertThat(response.getChallenge()).isEqualTo(challenge);
            byte[] ed25519GeekChain = Base64.decode(
                    "g4RDoQEnoFgqpAEBAycgBiFYIJm57t1e5FL2hcZMYtw+YatXS"
                            + "H11NymtdoAy0rPLY1jZWEAeIghLpLekyNdOAw7+uK8UTKc7b6XN3Np5xitk"
                            + "/pk5r3bngPpmAIUNB5gqrJFcpyUUSQY0dcqKJ3rZ41pJ6wIDhEOhASegWCqk"
                            + "AQEDJyAGIVgg6i+FDp5qDFz3vdn6KDK/2lXpIKJRA8kDkxjOoBUp7NFYQIJr"
                            + "x12mNle3x3ESrRzCarMsIyrdFDDLghS2icXTHjG7uFAhSklNupEMbzNNg7xY"
                            + "Ky6E28VZD5hh4sHqifLQrgSEQ6EBJ6BYTqUBAQJYIG+S0QRtcdinjojY0VaB"
                            + "X5bReIPmMBuH7b8g0Uo7/mouAzgYIAQhWCC2XRxLmoM6nbUVWTehJvsP3+ec"
                            + "rAHVpOzIOikAiFglOVhAgLKf0DKenUr+sCXywtIiaEbGILCq6BasZKFFg5vM"
                            + "SVQlf6sWBVPwvTWT88a7WU5e+d4hBxSjtqSji4+Clpa6Aw==",
                    Base64.DEFAULT);
            byte[] p256GeekChain = Base64.decode(
                    "g4RDoQEmoFhNpQECAyYgASFYIPcUituX9MxT79JkEcTjdR9mH6Rx"
                            + "DGzP+glGgHSHVPKtIlggXn9b9uzk9hnM/xM3/Q+hyJPbGAZ2xF3m12p3hsMtr49YQC"
                            + "+XjkL7vgctlUeFR5NAsB/Um0ekxESp8qEHhxDHn8sR9L+f6Dvg5zRMFfx7w34zBfTR"
                            + "NDztAgRgehXgedOK/ySEQ6EBJqBYTaUBAgMmIAEhWCBRgKzPj5aM7A9Q4akbt5CGNI"
                            + "vjw6xlAk209jEOCEYyOSJYIFTrlJ3+trTkczolTi8fnZ29+mbBEYvploxD5DD22nar"
                            + "WECYOPs0OmXbc5ixJ6IVdPK+BueNIk7d8L/CAXTEtylrJBy12NJm+kTv9TAsBHTt6M"
                            + "Zg2s6fVlcndCHT3pOP47jNhEOhASagWHGmAQICWCCDn/j9EBwSn5JBx1uN5E70GROa"
                            + "xxttpw6V8mRTXacdwQM4GCABIVggFqRSEmOzhlZQ2N/yoKh9vNlup2hg6oxc8ZPllx"
                            + "kNrN4iWCCJvsxsP16wOTSvl7o40RYdocwdZNOMSE74coEbOz4x7lhA+trPLaulMAxz"
                            + "xeWrSZJZYET6xPIz5QSybBlk6RzjZDs0hgBlLfXdr6oBya+DyU74WpToZZNR4xgeOY"
                            + "CnaUszzQ==",
                    Base64.DEFAULT);
            assertThat(response.getGeekChain(CborUtils.EC_CURVE_25519)).isEqualTo(ed25519GeekChain);
            assertThat(response.getGeekChain(CborUtils.EC_CURVE_P256)).isEqualTo(p256GeekChain);
        }
    }

    @Test
    public void testFetchKeyAndUpdate() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            Settings.setDeviceConfig(sContext, 2 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());
            mServerInterface.fetchGeekAndUpdate(
                    ProvisioningAttempt.createScheduledAttemptMetrics(sContext));

            assertThat(Settings.getExtraSignedKeysAvailable(sContext)).isEqualTo(20);
            assertThat(Settings.getExpiringBy(sContext)).isEqualTo(Duration.ofHours(72));
        }
    }

    @Test
    public void testRequestSignedCertUnregistered() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_DEVICE_UNREGISTERED)) {
            Settings.setDeviceConfig(sContext, 2 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());
            ProvisioningAttempt metrics = ProvisioningAttempt.createScheduledAttemptMetrics(
                    sContext);
            mServerInterface.requestSignedCertificates(new byte[0], new byte[0], metrics);
            assertWithMessage("Should fail due to unregistered device.").fail();
        } catch (RkpdException e) {
            assertThat(e.getErrorCode()).isEqualTo(RkpdException.ErrorCode.DEVICE_NOT_REGISTERED);
        }
    }

    @Test
    public void testRequestSignedCertClientError() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_USER_UNAUTHORIZED)) {
            Settings.setDeviceConfig(sContext, 2 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());
            Settings.setMaxRequestTime(sContext, 100);
            ProvisioningAttempt metrics = ProvisioningAttempt.createScheduledAttemptMetrics(
                    sContext);
            mServerInterface.requestSignedCertificates(new byte[0], new byte[0], metrics);
            assertWithMessage("Should fail due to client error.").fail();
        } catch (RkpdException e) {
            assertThat(e.getErrorCode()).isEqualTo(RkpdException.ErrorCode.HTTP_CLIENT_ERROR);
        }
    }

    @Test
    public void testRequestSignedCertCborError() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_INVALID_CBOR)) {
            Settings.setDeviceConfig(sContext, 2 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());
            ProvisioningAttempt metrics = ProvisioningAttempt.createScheduledAttemptMetrics(
                    sContext);
            mServerInterface.requestSignedCertificates(new byte[0], new byte[0], metrics);
            assertWithMessage("Should fail due to invalid cbor.").fail();
        } catch (RkpdException e) {
            assertThat(e.getErrorCode()).isEqualTo(RkpdException.ErrorCode.INTERNAL_ERROR);
            assertThat(e).hasMessageThat().isEqualTo("Response failed to parse.");
        }
    }

    @Test
    public void testRequestSignedCertValid() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            Settings.setDeviceConfig(sContext, 2 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());
            ProvisioningAttempt metrics = ProvisioningAttempt.createScheduledAttemptMetrics(
                    sContext);
            List<byte[]> certChains = mServerInterface.requestSignedCertificates(new byte[0],
                    new byte[0], metrics);
            assertThat(certChains).isEmpty();
            assertThat(certChains).isNotNull();
        }
    }

    @Test
    public void testDataBudgetEmptyFetchGeekNetworkConnected() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            Settings.setDeviceConfig(sContext, 2 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());

            // Check the data budget in order to initialize a rolling window.
            assertThat(Settings.hasErrDataBudget(sContext, null /* curTime */)).isTrue();
            Settings.consumeErrDataBudget(sContext, Settings.FAILURE_DATA_USAGE_MAX);
            ProvisioningAttempt metrics = ProvisioningAttempt.createScheduledAttemptMetrics(
                    sContext);

            // We are okay in mocking connectivity failure since err data budget is the first thing
            // to be checked.
            mockConnectivityFailure(ConnectivityState.CONNECTED);
            mServerInterface.fetchGeek(metrics);
            assertWithMessage("Network transaction should not have proceeded.").fail();
        } catch (RkpdException e) {
            assertThat(e).hasMessageThat().contains("Out of data budget due to repeated errors");
            assertThat(e.getErrorCode()).isEqualTo(
                    RkpdException.ErrorCode.NETWORK_COMMUNICATION_ERROR);
        }
    }

    @Test
    public void testDataBudgetEmptyFetchGeekNetworkDisconnected() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            Settings.setDeviceConfig(sContext, 2 /* extraKeys */,
                    TIME_TO_REFRESH_HOURS /* expiringBy */, server.getUrl());

            // Check the data budget in order to initialize a rolling window.
            assertThat(Settings.hasErrDataBudget(sContext, null /* curTime */)).isTrue();
            Settings.consumeErrDataBudget(sContext, Settings.FAILURE_DATA_USAGE_MAX);
            ProvisioningAttempt metrics = ProvisioningAttempt.createScheduledAttemptMetrics(
                    sContext);

            // We are okay in mocking connectivity failure since err data budget is the first thing
            // to be checked.
            mockConnectivityFailure(ConnectivityState.DISCONNECTED);
            mServerInterface.fetchGeek(metrics);
            assertWithMessage("Network transaction should not have proceeded.").fail();
        } catch (RkpdException e) {
            assertThat(e).hasMessageThat().contains("Out of data budget due to repeated errors");
            assertThat(e.getErrorCode()).isEqualTo(RkpdException.ErrorCode.NO_NETWORK_CONNECTIVITY);
        }
    }

    @Test
    public void testReadErrorInvalidContentType() {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getContentType()).thenReturn("application/NOPE");
        assertThat(ServerInterface.readErrorFromConnection(connection))
                .isEqualTo("Unexpected content type from the server: application/NOPE");
    }

    @Test
    public void testReadTextErrorFromErrorStreamNoErrorData() throws Exception {
        final String expectedError = "No error data returned by server.";

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getContentType()).thenReturn("text");
        Mockito.when(connection.getInputStream()).thenThrow(new IOException());
        Mockito.when(connection.getErrorStream()).thenReturn(null);

        assertThat(ServerInterface.readErrorFromConnection(connection)).isEqualTo(expectedError);
    }

    @Test
    public void testReadTextErrorFromErrorStream() throws Exception {
        final String error = "Explanation for error goes here.";

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getContentType()).thenReturn("text");
        Mockito.when(connection.getInputStream()).thenThrow(new IOException());
        Mockito.when(connection.getErrorStream())
                .thenReturn(new ByteArrayInputStream(error.getBytes(StandardCharsets.UTF_8)));

        assertThat(ServerInterface.readErrorFromConnection(connection)).isEqualTo(error);
    }

    @Test
    public void testReadTextError() throws IOException {
        final String error = "This is an error.  Oh No.";
        final String[] textContentTypes = new String[]{
                "text",
                "text/ANYTHING",
                "text/what-is-this; charset=unknown",
                "text/lowercase; charset=utf-8",
                "text/uppercase; charset=UTF-8",
                "text/yolo; charset=ASCII"
        };

        for (String contentType : textContentTypes) {
            HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
            Mockito.when(connection.getContentType()).thenReturn(contentType);
            Mockito.when(connection.getInputStream())
                    .thenReturn(new ByteArrayInputStream(error.getBytes(StandardCharsets.UTF_8)));

            assertWithMessage("Failed on content type '" + contentType + "'")
                    .that(error)
                    .isEqualTo(ServerInterface.readErrorFromConnection(connection));
        }
    }

    @Test
    public void testReadJsonError() throws IOException {
        final String error = "Not really JSON.";

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getContentType()).thenReturn("application/json");
        Mockito.when(connection.getInputStream())
                .thenReturn(new ByteArrayInputStream(error.getBytes(StandardCharsets.UTF_8)));

        assertThat(ServerInterface.readErrorFromConnection(connection)).isEqualTo(error);
    }

    @Test
    public void testReadErrorStreamThrowsException() throws IOException {
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(stream.read(Mockito.any())).thenThrow(new IOException());

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getContentType()).thenReturn("text");
        Mockito.when(connection.getInputStream()).thenReturn(stream);

        final String error = ServerInterface.readErrorFromConnection(connection);
        assertWithMessage("Error string: '" + error + "'")
                .that(error).startsWith("Error reading error string from server: ");
    }

    @Test
    public void testReadErrorEmptyStream() throws IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getContentType()).thenReturn("text");
        Mockito.when(connection.getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[0]));

        assertThat(ServerInterface.readErrorFromConnection(connection))
                .isEqualTo("No error data returned by server.");
    }

    @Test
    public void testReadErrorStreamTooLarge() throws IOException {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2048; ++i) {
            sb.append(i % 100);
        }
        final String bigString = sb.toString();

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getContentType()).thenReturn("text");
        Mockito.when(connection.getInputStream())
                .thenReturn(new ByteArrayInputStream(bigString.getBytes(StandardCharsets.UTF_8)));

        sb.setLength(1024);
        assertThat(ServerInterface.readErrorFromConnection(connection)).isEqualTo(sb.toString());
    }

    private void mockConnectivityFailure(ConnectivityState state) {
        ConnectivityManager mockedConnectivityManager = Mockito.mock(ConnectivityManager.class);

        Mockito.when(sContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mockedConnectivityManager);
        NetworkCapabilities.Builder builder = new NetworkCapabilities.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (state == ConnectivityState.CONNECTED) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        Mockito.when(mockedConnectivityManager.getNetworkCapabilities(Mockito.any()))
                .thenReturn(builder.build());
    }

    private enum ConnectivityState {
        DISCONNECTED,
        CONNECTED
    }

}
