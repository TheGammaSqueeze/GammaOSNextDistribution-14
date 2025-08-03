package test_package;

import test_package.Bar;
import test_package.ByteEnum;
import test_package.IntEnum;
import test_package.LongEnum;
import test_package.SimpleUnion;
import android.os.PersistableBundle;

parcelable Foo {
    const int kZero = 0;
    const int kOne = 1;
    const int kOnes = 0xffffffff;
    const byte kByteOne = 1;
    const long kLongOnes = 0xffffffffffffffff;
    const String kEmpty = "";
    const String kFoo = "foo";

    String a="FOO";
    int b=42;
    float c=3.14f;
    Bar d;
    Bar e;
    int f=3;
    ByteEnum shouldBeByteBar;
    IntEnum shouldBeIntBar;
    LongEnum shouldBeLongBar;
    ByteEnum[] shouldContainTwoByteFoos;
    IntEnum[] shouldContainTwoIntFoos;
    LongEnum[] shouldContainTwoLongFoos;
    @nullable String[] g;
    @nullable SimpleUnion u;

    // example: using int constants
    const int BIT0 = 0x1;
    const int BIT1 = 0x1 << 1;
    const int BIT2 = 0x1 << 2;
    int shouldSetBit0AndBit2;

    // example: using a String constant of union
    @nullable SimpleUnion shouldBeConstS1;

    PersistableBundle bundle1;
    @nullable PersistableBundle bundle2;
    PersistableBundle[] bundleArray;
}
