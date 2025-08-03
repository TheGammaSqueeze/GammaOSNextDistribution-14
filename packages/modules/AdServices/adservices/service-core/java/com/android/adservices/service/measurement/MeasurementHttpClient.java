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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Utility class related to network related activities
 *
 * @hide
 */
public class MeasurementHttpClient {
    private static final String APEX_VERSION_WHEN_NOT_FOUND = "000000";

    enum HttpMethod {
        GET,
        POST
    }

    private final Map<Long, String> mApexVersionToHeaderVersion = new HashMap<>();
    private Long mApexVersion;

    public MeasurementHttpClient(Context context) {
        if (mApexVersion == null) {
            setAdservicesApexVersion(context.getPackageManager());
        }
    }

    /**
     * Opens a {@link URLConnection} and sets the network connection & read timeout. The timeout
     * values are configurable using the name "measurement_network_connect_timeout_ms" and
     * "measurement_network_read_timeout_ms"
     */
    @NonNull
    public URLConnection setup(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);

        final HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        if (WebAddresses.isLocalhost(Uri.parse(url.toString()))) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "MeasurementHttpClient::setup : setting unsafe SSL for localhost, URI:"
                                    + " %s",
                            url.toString());
            urlConnection.setSSLSocketFactory(getUnsafeSslSocketFactory());
        }
        final Flags flags = FlagsFactory.getFlags();
        urlConnection.setConnectTimeout(flags.getMeasurementNetworkConnectTimeoutMs());
        urlConnection.setReadTimeout(flags.getMeasurementNetworkReadTimeoutMs());

        // Overriding default headers to avoid leaking information
        urlConnection.setRequestProperty("User-Agent", "");
        String version =
                flags.getEnableComputeVersionFromMappings()
                        ? getHeaderVersion()
                        : flags.getMainlineTrainVersion();
        urlConnection.setRequestProperty("Version", version);
        return urlConnection;
    }

    /**
     * Intentionally bypass SSL certificate verification -- called only when connecting to
     * localhost.
     */
    @SuppressLint({"TrustAllX509TrustManager", "CustomX509TrustManager"})
    private static SSLSocketFactory getUnsafeSslSocketFactory() {
        try {
            TrustManager[] bypassTrustManagers = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, bypassTrustManagers, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger().e(e, "getUnsafeSslSocketFactory caught exception");
            return null;
        }
    }

    private String getHeaderVersion() {
        return mApexVersionToHeaderVersion.computeIfAbsent(
                mApexVersion, headerVersion -> computeHeaderVersion());
    }

    private void setAdservicesApexVersion(PackageManager packageManager) {
        List<PackageInfo> installedPackages =
                packageManager.getInstalledPackages(PackageManager.MATCH_APEX);

        Optional<Long> apexVersion = Optional.empty();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            apexVersion =
                    installedPackages.stream()
                            .filter(s -> s.isApex && s.packageName.contains("android.adservices"))
                            .findFirst()
                            .map(PackageInfo::getLongVersionCode);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            apexVersion =
                    installedPackages.stream()
                            .filter(s -> s.isApex && s.packageName.contains("android.extservices"))
                            .findFirst()
                            .map(PackageInfo::getLongVersionCode);
        }
        apexVersion.ifPresent(aLong -> mApexVersion = aLong);
    }

    private String computeHeaderVersion() {
        String listOfMappings = FlagsFactory.getFlags().getAdservicesVersionMappings();
        String[] mappings = listOfMappings.split("\\|");
        for (String mapping : mappings) {
            String[] range = mapping.split(",");
            int startRange = Integer.parseInt(range[0]);
            int endRange = Integer.parseInt(range[1]);
            if (mApexVersion != null && (mApexVersion >= startRange && mApexVersion < endRange)) {
                return range[2];
            }
        }
        LoggerFactory.getMeasurementLogger()
                .d("Header version not found. " + "Apex version -  " + mApexVersion);
        return APEX_VERSION_WHEN_NOT_FOUND;
    }
}
