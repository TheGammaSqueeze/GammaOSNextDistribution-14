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

package com.android.car.internal.evs;

import static android.car.evs.CarEvsManager.SERVICE_TYPE_REARVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_SURROUNDVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_FRONTVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_LEFTVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_RIGHTVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_DRIVERVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_FRONT_PASSENGERSVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_REAR_PASSENGERSVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_USER_DEFINED;

import android.car.builtin.util.Slogf;
import android.car.evs.CarEvsManager.CarEvsServiceType;

/**
 * This class provide utility methods for CarEvsService clients.
 *
 * @hide
 */
public final class CarEvsUtils {
    private static final String TAG = CarEvsUtils.class.getSimpleName();

    // To identify the origin of frame buffers and stream events, CarEvsService tags them with their
    // origin service type in 8-MSB of the frame buffer id and the stream event id. These constants
    // are used to implement this tagging operation.
    private static final int TAG_BIT_LEFT_SHIFT = 24;
    private static final int DATA_BIT_MASK = ~(0xFF << TAG_BIT_LEFT_SHIFT);

    private CarEvsUtils() {}

    public static @CarEvsServiceType int convertToServiceType(String type) {
        switch (type) {
            case "REARVIEW":
                return SERVICE_TYPE_REARVIEW;
            case "SURROUNDVIEW":
                return SERVICE_TYPE_SURROUNDVIEW;
            case "FRONTVIEW":
                return SERVICE_TYPE_FRONTVIEW;
            case "LEFTVIEW":
                return SERVICE_TYPE_LEFTVIEW;
            case "RIGHTVIEW":
                return SERVICE_TYPE_RIGHTVIEW;
            case "DRIVERVIEW":
                return SERVICE_TYPE_DRIVERVIEW;
            case "FRONT_PASSENGERSVIEW":
                return SERVICE_TYPE_FRONT_PASSENGERSVIEW;
            case "REAR_PASSENGERSVIEW":
                return SERVICE_TYPE_REAR_PASSENGERSVIEW;
            default:
                Slogf.w(TAG, "USER_DEFINED will be returned for a unknown service type " + type);
                // fall through
            case "USER_DEFINED":
                return SERVICE_TYPE_USER_DEFINED;
        }
    }

    public static String convertToString(@CarEvsServiceType int type) {
        switch (type) {
              case SERVICE_TYPE_REARVIEW:
                  return "REARVIEW";
              case SERVICE_TYPE_SURROUNDVIEW:
                  return "SURROUNDVIEW";
              case SERVICE_TYPE_FRONTVIEW:
                  return "FRONTVIEW";
              case SERVICE_TYPE_LEFTVIEW:
                  return "LEFTVIEW";
              case SERVICE_TYPE_RIGHTVIEW:
                  return "RIGHTVIEW";
              case SERVICE_TYPE_DRIVERVIEW:
                  return "DRIVERVIEW";
              case SERVICE_TYPE_FRONT_PASSENGERSVIEW:
                  return "FRONT_PASSENGERVIEW";
              case SERVICE_TYPE_REAR_PASSENGERSVIEW:
                  return "REAR_PASSENGERVIEW";
              case SERVICE_TYPE_USER_DEFINED:
                  return "USER_DEFINED";
              default:
                  return "Unknown type= + type";
        }
    }

    /**
     * Extracts a service type from a given value and returns it.
     *
     * @param value This should be either an event or CarEvsBufferDescriptor id that are sent by
     *              ICarEvsStreamCallback.onStreamEvent() and ICarEvsStreamCallback.onNewFrame()
     *              callbacks respectively.
     * @return A service type embedded in 8-MSB of a given value.
     */
    public static @CarEvsServiceType int getTag(int value) {
        return value >> TAG_BIT_LEFT_SHIFT;
    }

    /**
     * Extracts an actual buffer id or an event id from a given value and returns it.
     *
     * @param value This should be either an event or CarEvsBufferDescriptor id that are sent by
     *              ICarEvsStreamCallback.onStreamEvent() and ICarEvsStreamCallback.onNewFrame()
     *              callbacks respectively.
     * @return A buffer id or an event.
     */
    public static int getValue(int value) {
        return value &= DATA_BIT_MASK;
    }

    /**
     * Embeds a given tag in 8-MSB of a given value and returns it.
     *
     * @param tag Additional information to identify the origin of a given value that is either a
     *            buffer id or an event.
     * @param value This should be either an event or CarEvsBufferDescriptor id that are sent by
     *              ICarEvsStreamCallback.onStreamEvent() and ICarEvsStreamCallback.onNewFrame()
     *              callbacks respectively.
     * @return 32-bit integer that contains a tag in 8-MSB and a value in the rest.
     */
    public static int putTag(int tag, int value) {
        if (tag > 0xFF) {
            Slogf.w(TAG, "A given tag %d is greater than 0xFF. Only 8-LSB will be effective.", tag);
        }
        return ((tag & 0xFF) << TAG_BIT_LEFT_SHIFT) | (value & DATA_BIT_MASK);
    }
}
