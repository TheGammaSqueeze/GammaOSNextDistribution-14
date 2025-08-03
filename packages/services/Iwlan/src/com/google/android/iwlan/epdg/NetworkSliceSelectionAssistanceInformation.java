package com.google.android.iwlan.epdg;

import android.support.annotation.NonNull;
import android.telephony.data.NetworkSliceInfo;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class NetworkSliceSelectionAssistanceInformation {
    private static final String TAG =
            NetworkSliceSelectionAssistanceInformation.class.getSimpleName();
    static final List<Integer> VALID_LENGTHS = Arrays.asList(1, 2, 4, 5, 8);

    public static NetworkSliceInfo getSliceInfo(byte[] snssai) {
        if (snssai == null) {
            return null;
        }
        /**
         * From 3GPP TS 24.501 Section 9.11.2.8, Content structure of the Value of S-NSSAI
         *
         * <p>Slice Service Type - 1 byte
         * Slice Differentiator - 3 bytes
         * Mapped HPLMN Slice Service Type - 1 byte
         * Mapped HPLMN Slice Differentiator - 3 bytes
         *
         * <p>Depending on the value of the length field the following S-NSSAI contents are
         * included:
         * Bits 8 7 6 5 4 3 2 1
         * 0 0 0 0 0 0 0 1 SST
         * 0 0 0 0 0 0 1 0 SST and mapped HPLMN SST
         * 0 0 0 0 0 1 0 0 SST and SD
         * 0 0 0 0 0 1 0 1 SST, SD and mapped HPLMN SST
         * 0 0 0 0 1 0 0 0 SST, SD, mapped HPLMN SST and mapped HPLMN SD
         * All other values are reserved.
         */
        NetworkSliceInfo.Builder siBuilder = new NetworkSliceInfo.Builder();
        int len = snssai.length;
        // validity checks. See TS 24.501 Section 9.11.2.8
        // length should be valid
        if (!VALID_LENGTHS.contains(len)) {
            Log.d(TAG, "Invalid S-NSSAI length : " + snssai.length);
            return null;
        }

        switch (len) {
            case 1: // get SST
                siBuilder.setSliceServiceType(getSST(snssai, 0));
                break;
            case 2: // get SST and mapped SST
                siBuilder.setSliceServiceType(getSST(snssai, 0));
                siBuilder.setMappedHplmnSliceServiceType(getSST(snssai, 1));
                break;
            case 4: // get SST and SD
                siBuilder.setSliceServiceType(getSST(snssai, 0));
                siBuilder.setSliceDifferentiator(getSD(snssai, 1));
                break;
            case 5: // get SST, SD and mapped SST
                siBuilder.setSliceServiceType(getSST(snssai, 0));
                siBuilder.setSliceDifferentiator(getSD(snssai, 1));
                siBuilder.setMappedHplmnSliceServiceType(getSST(snssai, 4));
                break;
            case 8: // get SST, SD, mapped SST, mapped SD
                siBuilder.setSliceServiceType(getSST(snssai, 0));
                siBuilder.setSliceDifferentiator(getSD(snssai, 1));
                siBuilder.setMappedHplmnSliceServiceType(getSST(snssai, 4));
                siBuilder.setMappedHplmnSliceDifferentiator(getSD(snssai, 5));
                break;
        }

        return siBuilder.build();
    }

    private static int getSST(@NonNull byte[] snssai, int offset) {
        if (offset < 0 || snssai.length < offset + 1) {
            return NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE;
        }
        /**
         * From 3GPP TS 23.003: Values 0 to 127 belong to the standardized SST range and they are
         * defined in 3GPP TS 23.501. Values 128 to 255 belong to the Operator-specific range
         */
        return Byte.toUnsignedInt(snssai[offset]);
    }

    private static int getSD(byte[] snssai, int offset) {
        int sliceDescriptor = NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE;
        /*
         * Slice Descriptor is 3 bytes long
         * The SD field has a reserved value "no SD value associated with the SST"
         * defined as hexadecimal FFFFFF
         */
        if (offset >= 0 && snssai.length >= offset + 3) {
            int sd = 0;
            sd =
                    (sd | snssai[offset + 2])
                            | ((sd | snssai[offset + 1]) << 8)
                            | ((sd | snssai[offset]) << 16);
            if (sd != 0xFFFFFF) {
                sliceDescriptor = sd;
            }
        }
        return sliceDescriptor;
    }
}
