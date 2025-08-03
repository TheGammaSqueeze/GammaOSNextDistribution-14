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

package com.android.telephony.tools.sats2;

import com.android.telephony.sats2range.read.SatS2RangeFileReader;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;

import java.io.File;

/** A util class for checking if a location is in the input satellite S2 file. */
public final class SatS2LocationLookup {
    /**
     *  A util method for checking if a location is in the input satellite S2 file.
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);

        try (SatS2RangeFileReader satS2RangeFileReader =
                     SatS2RangeFileReader.open(new File(arguments.inputFile))) {
            S2CellId s2CellId = getS2CellId(arguments.latDegrees, arguments.lngDegrees,
                    satS2RangeFileReader.getS2Level());
            System.out.println("s2CellId=" + Long.toUnsignedString(s2CellId.id()));
            if (satS2RangeFileReader.findEntryByCellId(s2CellId.id()) == null) {
                System.out.println("The input file does not contain the input location");
            } else {
                System.out.println("The input file contains the input location");
            }
        }
    }

    private static S2CellId getS2CellId(double latDegrees, double lngDegrees, int s2Level) {
        // Create the leaf S2 cell containing the given S2LatLng
        S2CellId cellId = S2CellId.fromLatLng(S2LatLng.fromDegrees(latDegrees, lngDegrees));

        // Return the S2 cell at the expected S2 level
        return cellId.parent(s2Level);
    }

    private static class Arguments {
        @Parameter(names = "--input-file",
                description = "sat s2 file",
                required = true)
        public String inputFile;

        @Parameter(names = "--lat-degrees",
                description = "lat degress of the location",
                required = true)
        public double latDegrees;

        @Parameter(names = "--lng-degrees",
                description = "lng degress of the location",
                required = true)
        public double lngDegrees;
    }
}
