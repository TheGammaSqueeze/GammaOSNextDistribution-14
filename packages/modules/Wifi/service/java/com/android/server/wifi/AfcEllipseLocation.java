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

import android.location.Location;
import android.util.Log;

import org.json.JSONObject;

import java.util.Random;

/**
 * Helper class to store a coarse ellipse location used to send to the AFC server. Randomizes the
 * center point of the ellipse with a center leeway parameter.
 */
public class AfcEllipseLocation extends AfcLocation {
    private static final String TAG = "WifiAfcEllipseLocation";
    static final double ONE_DEGREE_LONGITUDE_IN_METERS = 111139.0; // Used in double division
    static final int DEFAULT_SEMI_MINOR_AXIS_METERS = 500;
    static final int DEFAULT_SEMI_MAJOR_AXIS_METERS = 500;
    static final float DEFAULT_ORIENTATION = 90f;
    static final double DEFAULT_CENTER_LEEWAY_DEGREES = 0.001;
    static final int MIN_LONGITUDE = -180;
    static final int MAX_LONGITUDE = 180;
    static final int MIN_LATITUDE = -90;
    static final int MAX_LATITUDE = 90;
    double mLatitude;
    double mLongitude;
    int mSemiMajorAxis;
    int mSemiMinorAxis;
    double mOrientation;
    double mCenterLeeway;

    /**
     * @param semiMinorAxisMeters The length of the semi major axis of an ellipse which is a
     *                            positive integer in meters.
     * @param semiMajorAxisMeters The length of the semi minor axis of an ellipse which is a
     *                            positive integer in meters.
     * @param orientation         The orientation of the majorAxis field in decimal degrees,
     *                            measured clockwise from True North.
     * @param centerLeeway        The max amount in degrees that the longitude and latitude
     *                            coordinates of the generated ellipse will be randomly shifted +-
     *                            by. This variable should be reasonably smaller than the longitude
     *                            and latitude ranges. Each 0.001 degree is roughly 111 meters.
     * @param random              The random variable to randomize the center ellipse coordinates.
     * @param location            The geographic coordinates within which the AP or Fixed Client
     *                            Device is located and the AfcEllipseLocation is centered around.
     */
    public AfcEllipseLocation(int semiMinorAxisMeters, int semiMajorAxisMeters, double orientation,
            double centerLeeway, Random random, Location location) {
        super(location);

        mSemiMajorAxis = semiMajorAxisMeters;
        mSemiMinorAxis = semiMinorAxisMeters;
        mCenterLeeway = centerLeeway;
        mOrientation = orientation;

        // The ellipse longitude point is in between the location longitude point +- mCenterLeeway
        mLongitude = random.nextDouble() * (2 * mCenterLeeway) + (location.getLongitude()
                - mCenterLeeway);

        // Adjust for allowed range for longitude which is -180 to 180.
        if (mLongitude < MIN_LONGITUDE) {
            mLongitude = MIN_LONGITUDE;
        } else if (mLongitude > MAX_LONGITUDE) {
            mLongitude = MAX_LONGITUDE;
        }

        // The ellipse latitude point is in between the location latitude point +- mCenterLeeway
        mLatitude = random.nextDouble() * (2 * mCenterLeeway) + (location.getLatitude()
                - mCenterLeeway);

        // Adjust for allowed range for latitude which is -90 to 90.
        if (mLatitude < MIN_LATITUDE) {
            mLatitude = MIN_LATITUDE;
        } else if (mLatitude > MAX_LATITUDE) {
            mLatitude = MAX_LATITUDE;
        }
    }

    /**
     * Create a location JSONObject that has the ellipse fields for AFC server queries.
     */
    @Override
    public JSONObject toJson() {
        try {
            JSONObject location = new JSONObject();
            JSONObject ellipse = new JSONObject();
            JSONObject center = new JSONObject();

            center.put("latitude", mLatitude);
            center.put("longitude", mLongitude);
            ellipse.put("center", center);
            ellipse.put("majorAxis", mSemiMajorAxis);
            ellipse.put("minorAxis", mSemiMinorAxis);
            ellipse.put("orientation", mOrientation);
            location.put("ellipse", ellipse);

            JSONObject elevation = new JSONObject();
            elevation.put("height", mHeight);
            elevation.put("verticalUncertainty", mVerticalUncertainty);
            elevation.put("height_type", mHeightType);
            location.put("elevation", elevation);
            location.put("indoorDeployment", mLocationType);

            return location;
        } catch (Exception e) {
            Log.e(TAG, "Encountered error when building JSON object: " + e);
            return null;
        }
    }

    /**
     * Determine if location comparingLocation is within bounds of this AfcEllipseLocation object.
     */
    @Override
    public AfcLocationUtil.InBoundsCheckResult checkLocation(Location comparingLocation) {
        double comparingLatitude = comparingLocation.getLatitude();
        double comparingLongitude = comparingLocation.getLongitude();
        AfcLocationUtil.InBoundsCheckResult inBoundsResult;

        double semiMinorAxisDegrees = mSemiMinorAxis / ONE_DEGREE_LONGITUDE_IN_METERS;
        double semiMajorAxisDegrees = mSemiMajorAxis / ONE_DEGREE_LONGITUDE_IN_METERS;

        // Adjust comparingLongitude if the ellipse goes above the maximum longitude or below the
        // minimum longitude
        if (mLongitude + semiMajorAxisDegrees > MAX_LONGITUDE && comparingLongitude < 0) {
            comparingLongitude = comparingLongitude + 360;
        } else if (mLongitude - semiMajorAxisDegrees < MIN_LONGITUDE && comparingLongitude > 0) {
            comparingLongitude = comparingLongitude - 360;
        }

        // Equation for circles and ellipses on the cartesian plane that have the major axis
        // centered on the x-axis. Divide axes by 111139 to convert from meters to degrees which is
        // the same units as coordinates.
        double checkPoint = Math.pow((comparingLongitude - mLongitude) / semiMajorAxisDegrees, 2)
                + Math.pow((comparingLatitude - mLatitude) / semiMinorAxisDegrees, 2);

        if (checkPoint > 1.001) {
            inBoundsResult = AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION;
        } else if (checkPoint < 0.999) {
            inBoundsResult = AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION;
        } else {
            inBoundsResult = AfcLocationUtil.InBoundsCheckResult.ON_BORDER;
        }
        return inBoundsResult;
    }
}
