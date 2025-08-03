/*
 * Copyright (C) 2007 The Android Open Source Project
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

/**
 * test add by a 16-bit constant
 */
public class Main {
    public static void math_013() {
        int a, b, res;

        a = 3;
        b = 7;

        // a 16-bit constant
        a += 32000;
        b -= 32000;
        System.out.println("a:" + a);
        System.out.println("b:" + b);

        long c = constWide32();
        System.out.println(String.format("c:0x%016x", c));

        long d = constWide32Neg();
        System.out.println(String.format("d:0x%016x", d));

        long e = constWide();
        System.out.println(String.format("e:0x%016x", e));

        long f = constWideHigh16();
        System.out.println(String.format("f:0x%016x", f));
    }
    public static long constWide32() {
        return 0x12345678;
    }
    public static long constWide32Neg() {
        return 0xfedcba98;
    }
    public static long constWide() {
        return 0x123456789abcdef0L;
    }
    public static long constWideHigh16() {
        return 0xabcd000000000000L;
    }
    public static void main(String args[]) {
        math_013();
    }
}
