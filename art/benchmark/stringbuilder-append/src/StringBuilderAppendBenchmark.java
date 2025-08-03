/*
 * Copyright (C) 2019 The Android Open Source Project
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

public class StringBuilderAppendBenchmark {
    public static String string1 = "s1";
    public static String string2 = "s2";
    public static String longString1 = "This is a long string 1";
    public static String longString2 = "This is a long string 2";
    public static int int1 = 42;
    public static double double1 = 42.0;
    public static double double2 = 1.0E308;
    public static float float1 = 42.0f;
    public static float float2 = 1.0E38f;

    public void timeAppendStrings(int count) {
        String s1 = string1;
        String s2 = string2;
        int sum = 0;
        for (int i = 0; i < count; ++i) {
            String result = s1 + s2;
            sum += result.length();  // Make sure the append is not optimized away.
        }
        if (sum != count * (s1.length() + s2.length())) {
            throw new AssertionError();
        }
    }

    public void timeAppendLongStrings(int count) {
        String s1 = longString1;
        String s2 = longString2;
        int sum = 0;
        for (int i = 0; i < count; ++i) {
            String result = s1 + s2;
            sum += result.length();  // Make sure the append is not optimized away.
        }
        if (sum != count * (s1.length() + s2.length())) {
            throw new AssertionError();
        }
    }

    public void timeAppendStringAndInt(int count) {
        String s1 = string1;
        int i1 = int1;
        int sum = 0;
        for (int i = 0; i < count; ++i) {
            String result = s1 + i1;
            sum += result.length();  // Make sure the append is not optimized away.
        }
        if (sum != count * (s1.length() + Integer.toString(i1).length())) {
            throw new AssertionError();
        }
    }

    public void timeAppendStringAndDouble(int count) {
        String s1 = string1;
        double d1 = double1;
        int sum = 0;
        for (int i = 0; i < count; ++i) {
            String result = s1 + d1;
            sum += result.length();  // Make sure the append is not optimized away.
        }
        if (sum != count * (s1.length() + Double.toString(d1).length())) {
            throw new AssertionError();
        }
    }

    public void timeAppendStringAndHugeDouble(int count) {
        String s1 = string1;
        double d2 = double2;
        int sum = 0;
        for (int i = 0; i < count; ++i) {
            String result = s1 + d2;
            sum += result.length();  // Make sure the append is not optimized away.
        }
        if (sum != count * (s1.length() + Double.toString(d2).length())) {
            throw new AssertionError();
        }
    }

    public void timeAppendStringAndFloat(int count) {
        String s1 = string1;
        float f1 = float1;
        int sum = 0;
        for (int i = 0; i < count; ++i) {
            String result = s1 + f1;
            sum += result.length();  // Make sure the append is not optimized away.
        }
        if (sum != count * (s1.length() + Float.toString(f1).length())) {
            throw new AssertionError();
        }
    }

    public void timeAppendStringAndHugeFloat(int count) {
        String s1 = string1;
        float f2 = float2;
        int sum = 0;
        for (int i = 0; i < count; ++i) {
            String result = s1 + f2;
            sum += result.length();  // Make sure the append is not optimized away.
        }
        if (sum != count * (s1.length() + Float.toString(f2).length())) {
            throw new AssertionError();
        }
    }

    public void timeAppendStringDoubleStringAndFloat(int count) {
        String s1 = string1;
        String s2 = string2;
        double d1 = double1;
        float f1 = float1;
        int sum = 0;
        for (int i = 0; i < count; ++i) {
            String result = s1 + d1 + s2 + f1;
            sum += result.length();  // Make sure the append is not optimized away.
        }
        if (sum != count * (s1.length() +
                            Double.toString(d1).length() +
                            s2.length() +
                            Float.toString(f1).length())) {
            throw new AssertionError();
        }
    }
}
