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

public class Main {
    public static void main(String args[]) {
        Test227365246 test227365246 = new Test227365246();
        test227365246.$noinline$mainTest(args);

        Test216608614 test216608614 = new Test216608614();
        test216608614.$noinline$mainTest(args);

        Test216629762 test216629762 = new Test216629762();
        test216629762.$noinline$mainTest(args);
    }
}

class Test227365246 {
    int N = 400;
    int iFld;

    void $noinline$mainTest(String[] strArr1) {
        int i17, i18 = 5788, i19, i21, i22 = 127, i23;
        byte[] byArr = new byte[N];
        for (i17 = 14; 297 > i17; ++i17)
            for (int ax$2 = 151430; ax$2 < 235417; ax$2 += 2) {}
        try {
            for (i19 = 4; 179 > i19; ++i19) {
                i18 *= i18;
                for (i21 = 1; i21 < 58; i21++)
                    for (i23 = i21; 1 + 400 > i23; i23++) {
                        byArr[i23] -= i22;
                        i18 += i23;
                        switch (i19 % 5) {
                            case 107:
                                i19 >>= iFld;
                        }
                    }
            }
        } catch (ArrayIndexOutOfBoundsException exc1) {
        }
        System.out.println("i17 i18 b = " + i17 + "," + i18 + "," + 0);
    }
}

class Test216608614 {
    int N = 400;
    long lFld;
    double dFld;
    int iArrFld[]=new int[N];
    void $noinline$mainTest(String[] strArr1) {
        // Note: The original bug report started with `l=-1213929899L` but this took
        // too long when running with interpreter without JIT and we want to allow
        // this test to run for all configurations. Starting with `l=-1000000L` was
        // enough to allow JIT to compile the method for OSR and trigger the bug on host.
        long l=-1000000L;
        int i19= 46, i20=100, i21, i22=13, i25;
        try {
            do
                for (; i19 < 172; ++i19)
                    lFld = (long) dFld;
            while (++l < 146);
            for (i21 = 8;; ++i21)
                for (i25 = 1; i25 < 2; i25++) {
                    i20 = i22 % 1650388388;
                    i20 = iArrFld[i21];
                    i22 = 60;
                }
        } catch (ArrayIndexOutOfBoundsException exc1) {
        } finally {
        }
        System.out.println("l i19 i20 = " + l + "," + i19 + "," + i20);
    }
}

class Test216629762 {
    static int N = 400;
    int iFld=29275;
    volatile double dFld;
    static long lArrFld[][]=new long[N][N];

    void $noinline$mainTest(String[] strArr1) {
        int i8, i10=181, i11, i12=-57574, i13=69, i15= 6, i16= 186, i17= 227;
        try {
            for (i11 = 6; i11 < 278 + 400; ++i11)
                i12 *= iFld;
            for (;; i13++) {
                i10 /= i10;
                i16 += i15;
                lArrFld[i13][i15] >>= 31616;
                for (i17 = 1; i17 <  1 + 400; i17++)
                dFld += dFld;
            }
        }
        catch (ArrayIndexOutOfBoundsException exc2) {
            i16 += i12;
        }
        System.out.println("i16 b i17 = " + i16 + "," + 0  + "," + i17);
    }
}
