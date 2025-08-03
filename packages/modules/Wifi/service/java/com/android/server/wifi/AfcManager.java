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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiContext;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.hal.WifiChip;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that handles interactions with the AFC server and passing its response to the driver
 */
public class AfcManager {
    private static final String TAG = "WifiAfcManager";
    private static final long MINUTE_IN_MILLIS = 60 * 1000;
    private static final long LOCATION_MIN_TIME_MILLIS = MINUTE_IN_MILLIS;
    private static final float LOCATION_MIN_DISTANCE_METERS = 200;
    private final HandlerThread mWifiHandlerThread;
    private final WifiContext mContext;
    private final WifiNative mWifiNative;
    private final WifiGlobals mWifiGlobals;
    private final Clock mClock;
    private final LocationListener mLocationListener;
    private final AfcClient mAfcClient;
    private final AfcLocationUtil mAfcLocationUtil;
    private final AfcClient.Callback mCallback;
    private Location mLastKnownLocation;
    private String mLastKnownCountryCode;
    private LocationManager mLocationManager;
    private long mLastAfcServerQueryTime;
    private String mProviderForLocationRequest = "";
    private boolean mIsAfcSupportedForCurrentCountry = false;
    private boolean mVerboseLoggingEnabled = false;
    private AfcLocation mLastAfcLocationInSuccessfulQuery;
    private AfcServerResponse mLatestAfcServerResponse;
    private String mAfcServerUrl;
    private String mServerUrlSetFromShellCommand;
    private Map<String, String> mServerRequestPropertiesSetFromShellCommand;

    public AfcManager(WifiContext context, WifiInjector wifiInjector) {
        mContext = context;
        mClock = wifiInjector.getClock();

        mWifiHandlerThread = wifiInjector.getWifiHandlerThread();
        mWifiGlobals = wifiInjector.getWifiGlobals();
        mWifiNative = wifiInjector.getWifiNative();
        mAfcLocationUtil = wifiInjector.getAfcLocationUtil();
        mAfcClient = wifiInjector.getAfcClient();

        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                onLocationChange(location, false);
            }
        };

        mCallback = new AfcClient.Callback() {
            // Cache the server response and pass the AFC channel allowance to the driver.
            @Override
            public void onResult(AfcServerResponse serverResponse, AfcLocation afcLocation) {
                mLatestAfcServerResponse = serverResponse;
                mLastAfcLocationInSuccessfulQuery = afcLocation;

                boolean allowanceSetSuccessfully = setAfcChannelAllowance(mLatestAfcServerResponse
                        .getAfcChannelAllowance());

                if (mVerboseLoggingEnabled) {
                    Log.i(TAG, "The AFC Client Query was successful and had the response:\n"
                            + serverResponse);
                }

                if (!allowanceSetSuccessfully) {
                    Log.e(TAG, "The AFC allowed channels and frequencies were not set "
                            + "successfully in the driver.");
                }
            }

            @Override
            public void onFailure(int reasonCode, String description) {
                Log.e(TAG, "Reason Code: " + reasonCode + ", Description: " + description);
            }
        };
    }

    /**
     * This method starts listening to location changes which help determine whether to query the
     * AFC server. This should only be called when AFC is available in the country that the device
     * is in.
     */
    private void listenForLocationChanges() {
        List<String> locationProviders = mLocationManager.getProviders(true);

        // find a provider we can use for location updates, then request to listen for updates
        for (String provider : locationProviders) {
            if (isAcceptableProviderForLocationUpdates(provider)) {
                try {
                    mLocationManager.requestLocationUpdates(
                            provider,
                            LOCATION_MIN_TIME_MILLIS,
                            LOCATION_MIN_DISTANCE_METERS,
                            mLocationListener,
                            mWifiHandlerThread.getLooper()
                    );
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                break;
            }
        }
    }

    /**
     * Stops listening to location changes for the current location listener.
     */
    private void stopListeningForLocationChanges() {
        mLocationManager.removeUpdates(mLocationListener);
    }

    /**
     * Returns whether this is a location provider we want to use when requesting location updates.
     */
    private boolean isAcceptableProviderForLocationUpdates(String provider) {
        // We don't want to actively initiate a location fix here (with gps or network providers).
        return LocationManager.PASSIVE_PROVIDER.equals(provider);
    }

    /**
     * Perform a re-query if the server hasn't been queried before, if the expiration time
     * of the last successful AfcResponse has expired, or if the location parameter is outside the
     * bounds of the AfcLocation from the last successful AFC server query.
     *
     * @param location the device's current location.
     * @param isCalledFromShellCommand whether this method is being called from a shell command.
     *                                 Used to bypass the flag in the overlay for AFC being enabled
     *                                 or disabled.
     */
    public void onLocationChange(Location location, boolean isCalledFromShellCommand) {
        mLastKnownLocation = location;

        if (!mIsAfcSupportedForCurrentCountry && !isCalledFromShellCommand) {
            return;
        }

        if (location == null) {
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Location is null");
            }
            return;
        }

        // If there was no prior successful query, then query the server.
        if (mLastAfcLocationInSuccessfulQuery == null) {
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "There is no prior successful query so a new query of the server is"
                        + " executed.");
            }
            queryServerAndInformDriver(location, isCalledFromShellCommand);
            return;
        }

        // If the expiration time of the last successful Afc response has expired, then query the
        // server.
        if (mClock.getWallClockMillis() >= mLatestAfcServerResponse.getAfcChannelAllowance()
                .availabilityExpireTimeMs) {
            queryServerAndInformDriver(location, isCalledFromShellCommand);
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "The availability expiration time of the last query has expired"
                        + " so a new query of the AFC server is executed.");
            }
            return;
        }

        AfcLocationUtil.InBoundsCheckResult inBoundsResult = mAfcLocationUtil.checkLocation(
                mLastAfcLocationInSuccessfulQuery, location);

        // Query the AFC server if the new parameter location is outside the AfcLocation
        // boundary.
        if (inBoundsResult == AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION) {
            queryServerAndInformDriver(location, isCalledFromShellCommand);

            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "The location is outside the bounds of the Afc location object so a"
                        + " query of the AFC server is executed with a new AfcLocation object.");
            }
        } else {
            // Don't query since the location parameter is either inside the AfcLocation boundary
            // or on the border.
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "The current location is " + inBoundsResult + " so a query "
                        + "will not be executed.");
            }
        }
    }

    /**
     * Sends the allowed AFC channels and frequencies to the driver.
     */
    private boolean setAfcChannelAllowance(WifiChip.AfcChannelAllowance afcChannelAllowance) {
        return mWifiNative.setAfcChannelAllowance(afcChannelAllowance);
    }

    /**
     * Query the AFC server to get allowed AFC frequencies and channels, then update the driver with
     * these values.
     *
     * @param location the location used to construct the location boundary sent to the server.
     * @param isCalledFromShellCommand whether this method is being called from a shell command.
     */
    private void queryServerAndInformDriver(Location location, boolean isCalledFromShellCommand) {
        mLastAfcServerQueryTime = mClock.getElapsedSinceBootMillis();

        if (isCalledFromShellCommand) {
            if (mServerUrlSetFromShellCommand == null) {
                Log.e(TAG, "The AFC server URL has not been set. Please use the "
                        + "configure-afc-server shell command to set the server URL before "
                        + "attempting to query the server from a shell command.");
                return;
            }

            mAfcClient.setServerURL(mServerUrlSetFromShellCommand);
            mAfcClient.setRequestPropertyPairs(mServerRequestPropertiesSetFromShellCommand);
        } else {
            mAfcClient.setServerURL(mAfcServerUrl);
        }

        // Convert the Location object to an AfcLocation object
        AfcLocation afcLocationForQuery = mAfcLocationUtil.createAfcLocation(location);

        mAfcClient.queryAfcServer(afcLocationForQuery, new Handler(mWifiHandlerThread.getLooper()),
                mCallback);
    }

    /**
     * On a country code change, check if AFC is supported in this country. If it is, start
     * listening to location updates if we aren't already, and query the AFC server. If it isn't,
     * stop listening to location updates and send an AfcChannelAllowance object with empty
     * frequency and channel lists to the driver.
     */
    public void onCountryCodeChange(String countryCode) {
        if (!mWifiGlobals.isAfcSupportedOnDevice() || countryCode.equals(mLastKnownCountryCode)) {
            return;
        }
        mLastKnownCountryCode = countryCode;
        List<String> afcServerUrlsForCountry = mWifiGlobals.getAfcServerUrlsForCountry(countryCode);

        if (afcServerUrlsForCountry == null || afcServerUrlsForCountry.size() == 0) {
            mAfcServerUrl = null;
        } else {
            mAfcServerUrl = afcServerUrlsForCountry.get(0);
        }

        // if AFC support has not changed, we do not need to do anything else
        if (mIsAfcSupportedForCurrentCountry == (afcServerUrlsForCountry != null)) return;

        mIsAfcSupportedForCurrentCountry = !mIsAfcSupportedForCurrentCountry;
        getLocationManager();

        if (mLocationManager == null) {
            Log.e(TAG, "Location Manager should not be null.");
            return;
        }

        if (!mIsAfcSupportedForCurrentCountry) {
            stopListeningForLocationChanges();

            // send driver AFC allowance with empty frequency and channel arrays
            WifiChip.AfcChannelAllowance afcChannelAllowance = new WifiChip.AfcChannelAllowance();
            afcChannelAllowance.availableAfcFrequencyInfos = new ArrayList<>();
            afcChannelAllowance.availableAfcChannelInfos = new ArrayList<>();
            setAfcChannelAllowance(afcChannelAllowance);
            return;
        }

        listenForLocationChanges();
        getProviderForLocationRequest();
        if (mProviderForLocationRequest.isEmpty()) return;

        mLocationManager.getCurrentLocation(
                mProviderForLocationRequest, null,
                new HandlerExecutor(new Handler(mWifiHandlerThread.getLooper())),
                currentLocation -> {
                    mLastKnownLocation = currentLocation;

                    if (currentLocation == null) {
                        Log.e(TAG, "Current location is null.");
                        return;
                    }

                    queryServerAndInformDriver(currentLocation, false);
                });
    }

    /**
     * Returns the preferred provider to use for getting the current location, or an empty string
     * if none are present.
     */
    private String getProviderForLocationRequest() {
        if (!mProviderForLocationRequest.isEmpty() || mLocationManager == null) {
            return mProviderForLocationRequest;
        }

        // Order in which location providers are preferred. A lower index means a higher preference.
        String[] preferredProvidersInOrder;
        // FUSED_PROVIDER is only available from API level 31 onwards
        if (SdkLevel.isAtLeastS()) {
            preferredProvidersInOrder = new String[] { LocationManager.FUSED_PROVIDER,
                    LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER };
        } else {
            preferredProvidersInOrder = new String[] { LocationManager.NETWORK_PROVIDER,
                    LocationManager.GPS_PROVIDER };
        }

        List<String> availableLocationProviders = mLocationManager.getProviders(true);

        // return the first preferred provider that is available
        for (String preferredProvider : preferredProvidersInOrder) {
            if (availableLocationProviders.contains(preferredProvider)) {
                mProviderForLocationRequest = preferredProvider;
                break;
            }
        }
        return mProviderForLocationRequest;
    }

    /**
     * Set the server URL and request properties map used to query the AFC server. This is called
     * from the configure-afc-server Wi-Fi shell command.
     *
     * @param url the URL of the AFC server
     * @param requestProperties A map with key and value Strings for the HTTP header's request
     *                          property fields.
     */
    public void setServerUrlAndRequestPropertyPairs(@NonNull String url,
            @NonNull Map<String, String> requestProperties) {
        mServerUrlSetFromShellCommand = url;
        mServerRequestPropertiesSetFromShellCommand = new HashMap<>();
        mServerRequestPropertiesSetFromShellCommand.putAll(requestProperties);
    }

    /**
     * Dump the internal state of AfcManager.
     */
    public void dump(PrintWriter pw) {
        pw.println("Dump of AfcManager");
        if (!mWifiGlobals.isAfcSupportedOnDevice()) {
            pw.println("AfcManager - AFC is not supported on this device.");
            return;
        }
        pw.println("AfcManager - AFC is supported on this device.");

        if (!mIsAfcSupportedForCurrentCountry) {
            pw.println("AfcManager - AFC is not available in this country, with country code: "
                    + mLastKnownCountryCode);
        } else {
            pw.println("AfcManager - AFC is available in this country, with country code: "
                    + mLastKnownCountryCode);
        }

        pw.println("AfcManager - Last time the server was queried: " + mLastAfcServerQueryTime);
    }

    /**
     * Enable verbose logging in AfcManager.
     */
    public void enableVerboseLogging(boolean verboseLoggingEnabled) {
        mVerboseLoggingEnabled = verboseLoggingEnabled;
    }

    @VisibleForTesting
    LocationManager getLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = mContext.getSystemService(LocationManager.class);
        }
        return mLocationManager;
    }

    @VisibleForTesting
    Location getLastKnownLocation() {
        return mLastKnownLocation;
    }

    @VisibleForTesting
    long getLastAfcServerQueryTime() {
        return mLastAfcServerQueryTime;
    }

    @VisibleForTesting
    AfcLocation getLastAfcLocationInSuccessfulQuery() {
        return mLastAfcLocationInSuccessfulQuery;
    }

    @VisibleForTesting
    public void setIsAfcSupportedInCurrentCountry(boolean isAfcSupported) {
        mIsAfcSupportedForCurrentCountry = isAfcSupported;
    }

    @VisibleForTesting
    @Nullable
    String getAfcServerUrl() {
        return mAfcServerUrl;
    }
}
