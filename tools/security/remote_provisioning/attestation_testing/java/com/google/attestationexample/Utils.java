package com.google.attestationexample;

import android.util.Log;

import java.util.Arrays;

public class Utils {
    public static final String FAIL = "AttestationFail";
    public static final String FAIL_INFO = "AttestationFailInfo";

    public static void logError(String message, int expected, int actual) {
        Log.e(FAIL, message);
        Log.e(FAIL_INFO, "Expected: " + expected);
        Log.e(FAIL_INFO, "Actual: " + actual);
    }

    public static void logError(String message, byte[] expected, byte[] actual) {
        Log.e(FAIL, message);
        Log.e(FAIL_INFO, "Expected: " + Arrays.toString(expected));
        Log.e(FAIL_INFO, "Actual: " + Arrays.toString(actual));
    }
}
