// FIXME: license file, or use the -l option to generate the files with the header.
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

package hidl2aidl.test;
@VintfStability
interface IFoo {
  void multipleInputs(in String in1, in String in2);
  void multipleInputsAndOutputs(in String in1, in String in2, out hidl2aidl.test.IFoo.BigStruct out1, out hidl2aidl.test.IFoo.BigStruct out2);
  void multipleOutputs(out hidl2aidl.test.IFoo.BigStruct out1, out hidl2aidl.test.IFoo.BigStruct out2);
  String oneOutput();
  String removedOutput();
  void someBar(in String a, in String b);
  oneway void someFoo(in byte a);
  void useImportedStruct(in hidl2aidl.test.Outer outer);
  hidl2aidl.test.IFoo.BigStruct useStruct();
  void versionTest_(in String a);
  boolean versionTest_two_(in String a);
  @VintfStability
  parcelable BigStruct {
    byte type;
    byte value;
  }
}
