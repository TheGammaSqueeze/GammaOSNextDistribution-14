package android.aidl.test.trunk;

interface ITrunkStableTest {
    parcelable MyParcelable {
        int a;
        int b;
        // New in V2
        int c;
    }
    enum MyEnum {
        ZERO,
        ONE,
        TWO,
        // New in V2
        THREE,
    }
    union MyUnion {
        int a;
        int b;
        // New in V3
        int c;
    }
    interface IMyCallback {
        MyParcelable repeatParcelable(in MyParcelable input);
        MyEnum repeatEnum(in MyEnum input);
        MyUnion repeatUnion(in MyUnion input);
        MyOtherParcelable repeatOtherParcelable(in MyOtherParcelable input);
    }

    MyParcelable repeatParcelable(in MyParcelable input);
    MyEnum repeatEnum(in MyEnum input);
    MyUnion repeatUnion(in MyUnion input);
    void callMyCallback(in IMyCallback cb);

    // New in V2
    parcelable MyOtherParcelable {
        int a;
        int b;
    }
    MyOtherParcelable repeatOtherParcelable(in MyOtherParcelable input);
}
