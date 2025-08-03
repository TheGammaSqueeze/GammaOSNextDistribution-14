package test_package;

import test_package.Bar;
import test_package.ByteEnum;
import android.os.PersistableBundle;

union SimpleUnion {
    const int kZero = 0;
    const int kOne = 1;
    const int kOnes = 0xffffffff;
    const byte kByteOne = 1;
    const long kLongOnes = 0xffffffffffffffff;
    const String kEmpty = "";
    const String kFoo = "foo";

    int a = 42;
    int[] b;
    String c;
    ByteEnum d;
    ByteEnum[] e;
    @nullable Bar f;

    const String S1 = "a string constant";
    PersistableBundle g;
}
