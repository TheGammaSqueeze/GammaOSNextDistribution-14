/*
 * Copyright (C) 2017 The Android Open Source Project
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

  /// CHECK-START: java.lang.Integer Main.foo(int) disassembly (after)
  /// CHECK: <<Integer:l\d+>>     InvokeStaticOrDirect method_name:java.lang.Integer.valueOf intrinsic:IntegerValueOf
  /// CHECK:                      pAllocObjectInitialized
  /// CHECK:                      Return [<<Integer>>]
  public static Integer foo(int a) {
    return Integer.valueOf(a);
  }

  /// CHECK-START: java.lang.Integer Main.foo2() disassembly (after)
  /// CHECK: <<Integer:l\d+>>     InvokeStaticOrDirect method_name:java.lang.Integer.valueOf intrinsic:IntegerValueOf
  /// CHECK-NOT:                  pAllocObjectInitialized
  /// CHECK:                      Return [<<Integer>>]
  public static Integer foo2() {
    return Integer.valueOf(-42);
  }

  /// CHECK-START: java.lang.Integer Main.foo3() disassembly (after)
  /// CHECK: <<Integer:l\d+>>     InvokeStaticOrDirect method_name:java.lang.Integer.valueOf intrinsic:IntegerValueOf
  /// CHECK-NOT:                  pAllocObjectInitialized
  /// CHECK:                      Return [<<Integer>>]
  public static Integer foo3() {
    return Integer.valueOf(42);
  }

  /// CHECK-START: java.lang.Integer Main.foo4() disassembly (after)
  /// CHECK: <<Integer:l\d+>>     InvokeStaticOrDirect method_name:java.lang.Integer.valueOf intrinsic:IntegerValueOf
  /// CHECK:                      pAllocObjectInitialized
  /// CHECK:                      Return [<<Integer>>]
  public static Integer foo4() {
    return Integer.valueOf(55555);
  }

  /// CHECK-START: byte Main.$noinline$boxUnboxByte(byte) builder (after)
  /// CHECK: <<Input:b\d+>>       ParameterValue
  /// CHECK: <<Boxed:l\d+>>       InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK-NOT:                  NullCheck [<<Boxed>>]
  /// CHECK: <<Unboxed:b\d+>>     InvokeVirtual [<<Boxed>>] method_name:java.lang.Byte.byteValue
  /// CHECK:                      Return [<<Unboxed>>]

  /// CHECK-START: byte Main.$noinline$boxUnboxByte(byte) inliner (after)
  /// CHECK: <<Input:b\d+>>       ParameterValue
  /// CHECK: <<Boxed:l\d+>>       InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK: <<Unboxed:b\d+>>     InstanceFieldGet [<<Boxed>>] field_name:java.lang.Byte.value
  /// CHECK:                      Return [<<Unboxed>>]

  /// CHECK-START: byte Main.$noinline$boxUnboxByte(byte) instruction_simplifier$after_inlining (after)
  /// CHECK: <<Input:b\d+>>       ParameterValue
  /// CHECK:                      Return [<<Input>>]

  /// CHECK-START: byte Main.$noinline$boxUnboxByte(byte) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:                  InvokeStaticOrDirect
  /// CHECK-NOT:                  InstanceFieldGet

  public static byte $noinline$boxUnboxByte(byte value) {
    return Byte.valueOf(value).byteValue();
  }

  /// CHECK-START: short Main.$noinline$boxUnboxShort(short) builder (after)
  /// CHECK: <<Input:s\d+>>       ParameterValue
  /// CHECK: <<Boxed:l\d+>>       InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Short.valueOf intrinsic:ShortValueOf
  /// CHECK-NOT:                  NullCheck [<<Boxed>>]
  /// CHECK: <<Unboxed:s\d+>>     InvokeVirtual [<<Boxed>>] method_name:java.lang.Short.shortValue
  /// CHECK:                      Return [<<Unboxed>>]

  /// CHECK-START: short Main.$noinline$boxUnboxShort(short) inliner (after)
  /// CHECK: <<Input:s\d+>>       ParameterValue
  /// CHECK: <<Boxed:l\d+>>       InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Short.valueOf intrinsic:ShortValueOf
  /// CHECK: <<Unboxed:s\d+>>     InstanceFieldGet [<<Boxed>>] field_name:java.lang.Short.value
  /// CHECK:                      Return [<<Unboxed>>]

  /// CHECK-START: short Main.$noinline$boxUnboxShort(short) instruction_simplifier$after_inlining (after)
  /// CHECK: <<Input:s\d+>>       ParameterValue
  /// CHECK:                      Return [<<Input>>]

  /// CHECK-START: short Main.$noinline$boxUnboxShort(short) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:                  InvokeStaticOrDirect
  /// CHECK-NOT:                  InstanceFieldGet

  public static short $noinline$boxUnboxShort(short value) {
    return Short.valueOf(value).shortValue();
  }

  /// CHECK-START: char Main.$noinline$boxUnboxCharacter(char) builder (after)
  /// CHECK: <<Input:c\d+>>       ParameterValue
  /// CHECK: <<Boxed:l\d+>>       InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Character.valueOf intrinsic:CharacterValueOf
  /// CHECK-NOT:                  NullCheck [<<Boxed>>]
  /// CHECK: <<Unboxed:c\d+>>     InvokeVirtual [<<Boxed>>] method_name:java.lang.Character.charValue
  /// CHECK:                      Return [<<Unboxed>>]

  /// CHECK-START: char Main.$noinline$boxUnboxCharacter(char) inliner (after)
  /// CHECK: <<Input:c\d+>>       ParameterValue
  /// CHECK: <<Boxed:l\d+>>       InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Character.valueOf intrinsic:CharacterValueOf
  /// CHECK: <<Unboxed:c\d+>>     InstanceFieldGet [<<Boxed>>] field_name:java.lang.Character.value
  /// CHECK:                      Return [<<Unboxed>>]

  /// CHECK-START: char Main.$noinline$boxUnboxCharacter(char) instruction_simplifier$after_inlining (after)
  /// CHECK: <<Input:c\d+>>       ParameterValue
  /// CHECK:                      Return [<<Input>>]

  /// CHECK-START: char Main.$noinline$boxUnboxCharacter(char) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:                  InvokeStaticOrDirect
  /// CHECK-NOT:                  InstanceFieldGet

  public static char $noinline$boxUnboxCharacter(char value) {
    return Character.valueOf(value).charValue();
  }

  /// CHECK-START: int Main.$noinline$boxUnboxInteger(int) builder (after)
  /// CHECK: <<Input:i\d+>>       ParameterValue
  /// CHECK: <<Boxed:l\d+>>       InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Integer.valueOf intrinsic:IntegerValueOf
  /// CHECK-NOT:                  NullCheck [<<Boxed>>]
  /// CHECK: <<Unboxed:i\d+>>     InvokeVirtual [<<Boxed>>] method_name:java.lang.Integer.intValue
  /// CHECK:                      Return [<<Unboxed>>]

  /// CHECK-START: int Main.$noinline$boxUnboxInteger(int) inliner (after)
  /// CHECK: <<Input:i\d+>>       ParameterValue
  /// CHECK: <<Boxed:l\d+>>       InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Integer.valueOf intrinsic:IntegerValueOf
  /// CHECK: <<Unboxed:i\d+>>     InstanceFieldGet [<<Boxed>>] field_name:java.lang.Integer.value
  /// CHECK:                      Return [<<Unboxed>>]

  /// CHECK-START: int Main.$noinline$boxUnboxInteger(int) instruction_simplifier$after_inlining (after)
  /// CHECK: <<Input:i\d+>>       ParameterValue
  /// CHECK:                      Return [<<Input>>]

  /// CHECK-START: int Main.$noinline$boxUnboxInteger(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:                  InvokeStaticOrDirect
  /// CHECK-NOT:                  InstanceFieldGet

  public static int $noinline$boxUnboxInteger(int value) {
    return Integer.valueOf(value).intValue();
  }

  /// CHECK-START: int Main.$noinline$boxUnboxByteAsUint8(byte) builder (after)
  /// CHECK-DAG: <<Input:b\d+>>   ParameterValue
  /// CHECK-DAG: <<Boxed:l\d+>>   InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK-DAG: <<Phi:l\d+>>     Phi
  /// CHECK-DAG: <<NC:l\d+>>      NullCheck [<<Phi>>]
  /// CHECK-DAG: <<Unboxed:b\d+>> InvokeVirtual [<<NC>>] method_name:java.lang.Byte.byteValue
  /// CHECK-DAG: <<And:i\d+>>     And
  /// CHECK-DAG:                  Return [<<And>>]

  /// CHECK-START: int Main.$noinline$boxUnboxByteAsUint8(byte) instruction_simplifier (after)
  /// CHECK-DAG: <<Input:b\d+>>   ParameterValue
  /// CHECK-DAG: <<Boxed:l\d+>>   InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK-DAG: <<Phi:l\d+>>     Phi
  /// CHECK-DAG: <<NC:l\d+>>      NullCheck [<<Phi>>]
  /// CHECK-DAG: <<Unboxed:b\d+>> InvokeVirtual [<<NC>>] method_name:java.lang.Byte.byteValue
  /// CHECK-DAG: <<Conv:a\d+>>    TypeConversion [<<Unboxed>>]
  /// CHECK-DAG:                  Return [<<Conv>>]

  /// CHECK-START: int Main.$noinline$boxUnboxByteAsUint8(byte) inliner (after)
  /// CHECK-DAG: <<Input:b\d+>>   ParameterValue
  /// CHECK-DAG: <<Boxed:l\d+>>   InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK-DAG: <<Phi:l\d+>>     Phi
  /// CHECK-DAG: <<NC:l\d+>>      NullCheck [<<Phi>>]
  /// CHECK-DAG: <<Unboxed:b\d+>> InstanceFieldGet [<<NC>>] field_name:java.lang.Byte.value
  /// CHECK-DAG: <<Conv:a\d+>>    TypeConversion [<<Unboxed>>]
  /// CHECK-DAG:                  Return [<<Conv>>]

  /// CHECK-START: int Main.$noinline$boxUnboxByteAsUint8(byte) instruction_simplifier$after_inlining (after)
  /// CHECK-DAG: <<Input:b\d+>>   ParameterValue
  /// CHECK-DAG: <<Boxed:l\d+>>   InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK-DAG: <<Phi:l\d+>>     Phi
  /// CHECK-DAG: <<NC:l\d+>>      NullCheck [<<Phi>>]
  /// CHECK-DAG: <<Conv:a\d+>>    InstanceFieldGet [<<NC>>] field_name:java.lang.Byte.value
  /// CHECK-DAG:                  Return [<<Conv>>]

  /// CHECK-START: int Main.$noinline$boxUnboxByteAsUint8(byte) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG: <<Input:b\d+>>   ParameterValue
  /// CHECK-DAG: <<Boxed:l\d+>>   InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK-DAG: <<NC:l\d+>>      NullCheck [<<Boxed>>]
  /// CHECK-DAG: <<Conv:a\d+>>    InstanceFieldGet [<<NC>>] field_name:java.lang.Byte.value
  /// CHECK-DAG:                  Return [<<Conv>>]

  /// CHECK-START: int Main.$noinline$boxUnboxByteAsUint8(byte) instruction_simplifier$after_gvn (after)
  /// CHECK-DAG: <<Input:b\d+>>   ParameterValue
  /// CHECK-DAG: <<Boxed:l\d+>>   InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK-DAG: <<Conv:a\d+>>    InstanceFieldGet [<<Boxed>>] field_name:java.lang.Byte.value
  /// CHECK-DAG:                  Return [<<Conv>>]

  /// CHECK-START: int Main.$noinline$boxUnboxByteAsUint8(byte) disassembly (after)
  /// CHECK-DAG: <<Input:b\d+>>   ParameterValue
  /// CHECK-DAG: <<Boxed:l\d+>>   InvokeStaticOrDirect [<<Input>>{{(,[ij]\d+)?}}] method_name:java.lang.Byte.valueOf intrinsic:ByteValueOf
  /// CHECK-DAG: <<Conv:a\d+>>    InstanceFieldGet [<<Boxed>>] field_name:java.lang.Byte.value
  /// CHECK-DAG:                  Return [<<Conv>>]

  public static int $noinline$boxUnboxByteAsUint8(byte value) {
    Byte boxed = Byte.valueOf(value);

    // Hide the unboxing from the initial instruction simplifier pass or the one after inlining,
    // so that after inlining we can merge the `TypeConversion` into `InstanceFieldGet` with
    // a modified type. The Phi shall be eliminated by `dead_code_elimination$after_inlining`.
    // The null check that shall be eliminated by `instruction_simplifier$after_gvn (after)`
    Byte merged = $inline$returnTrue() ? boxed : null;

    // Due to the type mismatch, we shall not simplify the unboxing. The boxing and unboxing
    // shall be kept during subsequent simplifier passes all the way to the disassembly.
    return merged.byteValue() & 0xff;
  }

  public static boolean $inline$returnTrue() {
    return true;
  }

  public static void main(String[] args) {
    assertEqual("42", foo(intField));
    assertEqual(foo(intField), foo(intField2));
    assertEqual("-42", foo2());
    assertEqual("42", foo3());
    assertEqual("55555", foo4());
    assertEqual("55555", foo(intField3));
    assertEqual("-129", foo(intFieldMinus129));
    assertEqual("-128", foo(intFieldMinus128));
    assertEqual(foo(intFieldMinus128), foo(intFieldMinus128));
    assertEqual("-127", foo(intFieldMinus127));
    assertEqual(foo(intFieldMinus127), foo(intFieldMinus127));
    assertEqual("126", foo(intField126));
    assertEqual(foo(intField126), foo(intField126));
    assertEqual("127", foo(intField127));
    assertEqual(foo(intField127), foo(intField127));
    assertEqual("128", foo(intField128));

    assertEqual(42, (int) $noinline$boxUnboxByte((byte) 42));
    assertEqual(-42, (int) $noinline$boxUnboxByte((byte) -42));
    assertEqual(42, (int) $noinline$boxUnboxShort((short) 42));
    assertEqual(-42, (int) $noinline$boxUnboxShort((short) -42));
    assertEqual((int) (char) 42, (int) $noinline$boxUnboxCharacter((char) 42));
    assertEqual((int) (char) -42, (int) $noinline$boxUnboxCharacter((char) -42));
    assertEqual(42, $noinline$boxUnboxInteger(42));
    assertEqual(-42, $noinline$boxUnboxInteger(-42));

    assertEqual(42, $noinline$boxUnboxByteAsUint8((byte) 42));
    assertEqual(-42 & 0xff, $noinline$boxUnboxByteAsUint8((byte) -42));
  }

  static void assertEqual(String a, Integer b) {
    if (!a.equals(b.toString())) {
      throw new Error("Expected " + a + ", got " + b);
    }
  }

  static void assertEqual(Integer a, Integer b) {
    if (a != b) {
      throw new Error("Expected " + a + ", got " + b);
    }
  }

  static void assertEqual(int a, int b) {
    if (a != b) {
      throw new Error("Expected " + a + ", got " + b);
    }
  }

  static int intField = 42;
  static int intField2 = 42;
  static int intField3 = 55555;

  // Edge cases.
  static int intFieldMinus129 = -129;
  static int intFieldMinus128 = -128;
  static int intFieldMinus127 = -127;
  static int intField126 = 126;
  static int intField127 = 127;
  static int intField128 = 128;
}
