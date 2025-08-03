///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package android.aidl.test.trunk;
interface ITrunkStableTest {
  android.aidl.test.trunk.ITrunkStableTest.MyParcelable repeatParcelable(in android.aidl.test.trunk.ITrunkStableTest.MyParcelable input);
  android.aidl.test.trunk.ITrunkStableTest.MyEnum repeatEnum(in android.aidl.test.trunk.ITrunkStableTest.MyEnum input);
  android.aidl.test.trunk.ITrunkStableTest.MyUnion repeatUnion(in android.aidl.test.trunk.ITrunkStableTest.MyUnion input);
  void callMyCallback(in android.aidl.test.trunk.ITrunkStableTest.IMyCallback cb);
  android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable repeatOtherParcelable(in android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable input);
  parcelable MyParcelable {
    int a;
    int b;
    int c;
  }
  enum MyEnum {
    ZERO,
    ONE,
    TWO,
    THREE,
  }
  union MyUnion {
    int a;
    int b;
    int c;
  }
  interface IMyCallback {
    android.aidl.test.trunk.ITrunkStableTest.MyParcelable repeatParcelable(in android.aidl.test.trunk.ITrunkStableTest.MyParcelable input);
    android.aidl.test.trunk.ITrunkStableTest.MyEnum repeatEnum(in android.aidl.test.trunk.ITrunkStableTest.MyEnum input);
    android.aidl.test.trunk.ITrunkStableTest.MyUnion repeatUnion(in android.aidl.test.trunk.ITrunkStableTest.MyUnion input);
    android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable repeatOtherParcelable(in android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable input);
  }
  parcelable MyOtherParcelable {
    int a;
    int b;
  }
}
