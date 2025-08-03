/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package java.lang;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class StrictMath {

StrictMath() { throw new RuntimeException("Stub!"); }

public static native double sin(double a);

public static native double cos(double a);

public static native double tan(double a);

public static native double asin(double a);

public static native double acos(double a);

public static native double atan(double a);

public static double toRadians(double angdeg) { throw new RuntimeException("Stub!"); }

public static double toDegrees(double angrad) { throw new RuntimeException("Stub!"); }

public static native double exp(double a);

public static native double log(double a);

public static native double log10(double a);

public static native double sqrt(double a);

public static native double cbrt(double a);

public static native double IEEEremainder(double f1, double f2);

public static double ceil(double a) { throw new RuntimeException("Stub!"); }

public static double floor(double a) { throw new RuntimeException("Stub!"); }

public static double rint(double a) { throw new RuntimeException("Stub!"); }

public static native double atan2(double y, double x);

public static native double pow(double a, double b);

public static int round(float a) { throw new RuntimeException("Stub!"); }

public static long round(double a) { throw new RuntimeException("Stub!"); }

public static double random() { throw new RuntimeException("Stub!"); }

public static int addExact(int x, int y) { throw new RuntimeException("Stub!"); }

public static long addExact(long x, long y) { throw new RuntimeException("Stub!"); }

public static int subtractExact(int x, int y) { throw new RuntimeException("Stub!"); }

public static long subtractExact(long x, long y) { throw new RuntimeException("Stub!"); }

public static int multiplyExact(int x, int y) { throw new RuntimeException("Stub!"); }

public static long multiplyExact(long x, int y) { throw new RuntimeException("Stub!"); }

public static long multiplyExact(long x, long y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static int divideExact(int x, int y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static long divideExact(long x, long y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static int floorDivExact(int x, int y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static long floorDivExact(long x, long y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static int ceilDivExact(int x, int y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static long ceilDivExact(long x, long y) { throw new RuntimeException("Stub!"); }

public static int incrementExact(int a) { throw new RuntimeException("Stub!"); }

public static long incrementExact(long a) { throw new RuntimeException("Stub!"); }

public static int decrementExact(int a) { throw new RuntimeException("Stub!"); }

public static long decrementExact(long a) { throw new RuntimeException("Stub!"); }

public static int negateExact(int a) { throw new RuntimeException("Stub!"); }

public static long negateExact(long a) { throw new RuntimeException("Stub!"); }

public static int toIntExact(long value) { throw new RuntimeException("Stub!"); }

public static long multiplyFull(int x, int y) { throw new RuntimeException("Stub!"); }

public static long multiplyHigh(long x, long y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static long unsignedMultiplyHigh(long x, long y) { throw new RuntimeException("Stub!"); }

public static int floorDiv(int x, int y) { throw new RuntimeException("Stub!"); }

public static long floorDiv(long x, int y) { throw new RuntimeException("Stub!"); }

public static long floorDiv(long x, long y) { throw new RuntimeException("Stub!"); }

public static int floorMod(int x, int y) { throw new RuntimeException("Stub!"); }

public static int floorMod(long x, int y) { throw new RuntimeException("Stub!"); }

public static long floorMod(long x, long y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static int ceilDiv(int x, int y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static long ceilDiv(long x, int y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static long ceilDiv(long x, long y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static int ceilMod(int x, int y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static int ceilMod(long x, int y) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static long ceilMod(long x, long y) { throw new RuntimeException("Stub!"); }

public static int abs(int a) { throw new RuntimeException("Stub!"); }

public static int absExact(int a) { throw new RuntimeException("Stub!"); }

public static long abs(long a) { throw new RuntimeException("Stub!"); }

public static long absExact(long a) { throw new RuntimeException("Stub!"); }

public static float abs(float a) { throw new RuntimeException("Stub!"); }

public static double abs(double a) { throw new RuntimeException("Stub!"); }

public static int max(int a, int b) { throw new RuntimeException("Stub!"); }

public static long max(long a, long b) { throw new RuntimeException("Stub!"); }

public static float max(float a, float b) { throw new RuntimeException("Stub!"); }

public static double max(double a, double b) { throw new RuntimeException("Stub!"); }

public static int min(int a, int b) { throw new RuntimeException("Stub!"); }

public static long min(long a, long b) { throw new RuntimeException("Stub!"); }

public static float min(float a, float b) { throw new RuntimeException("Stub!"); }

public static double min(double a, double b) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static int clamp(long value, int min, int max) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static long clamp(long value, long min, long max) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static double clamp(double value, double min, double max) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static float clamp(float value, float min, float max) { throw new RuntimeException("Stub!"); }

public static double fma(double a, double b, double c) { throw new RuntimeException("Stub!"); }

public static float fma(float a, float b, float c) { throw new RuntimeException("Stub!"); }

public static double ulp(double d) { throw new RuntimeException("Stub!"); }

public static float ulp(float f) { throw new RuntimeException("Stub!"); }

public static double signum(double d) { throw new RuntimeException("Stub!"); }

public static float signum(float f) { throw new RuntimeException("Stub!"); }

public static native double sinh(double x);

public static native double cosh(double x);

public static native double tanh(double x);

public static native double hypot(double x, double y);

public static native double expm1(double x);

public static native double log1p(double x);

public static double copySign(double magnitude, double sign) { throw new RuntimeException("Stub!"); }

public static float copySign(float magnitude, float sign) { throw new RuntimeException("Stub!"); }

public static int getExponent(float f) { throw new RuntimeException("Stub!"); }

public static int getExponent(double d) { throw new RuntimeException("Stub!"); }

public static double nextAfter(double start, double direction) { throw new RuntimeException("Stub!"); }

public static float nextAfter(float start, double direction) { throw new RuntimeException("Stub!"); }

public static double nextUp(double d) { throw new RuntimeException("Stub!"); }

public static float nextUp(float f) { throw new RuntimeException("Stub!"); }

public static double nextDown(double d) { throw new RuntimeException("Stub!"); }

public static float nextDown(float f) { throw new RuntimeException("Stub!"); }

public static double scalb(double d, int scaleFactor) { throw new RuntimeException("Stub!"); }

public static float scalb(float f, int scaleFactor) { throw new RuntimeException("Stub!"); }

public static final double E = 2.718281828459045;

public static final double PI = 3.141592653589793;

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static final double TAU = 6.283185307179586;
}

