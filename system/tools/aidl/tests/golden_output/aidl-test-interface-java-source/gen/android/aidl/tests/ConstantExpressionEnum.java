/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/ConstantExpressionEnum.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ConstantExpressionEnum.aidl
 */
package android.aidl.tests;
public @interface ConstantExpressionEnum {
  // Should be all true / ones.
  // dec literals are either int or long
  public static final int decInt32_1 = 1;
  public static final int decInt32_2 = 1;
  public static final int decInt64_1 = 1;
  public static final int decInt64_2 = 1;
  public static final int decInt64_3 = 1;
  public static final int decInt64_4 = 1;
  // hex literals could be int or long
  // 0x7fffffff is int, hence can be negated
  public static final int hexInt32_1 = 1;
  // 0x80000000 is int32_t max + 1
  public static final int hexInt32_2 = 1;
  // 0xFFFFFFFF is int32_t, not long; if it were long then ~(long)0xFFFFFFFF != 0
  public static final int hexInt32_3 = 1;
  // 0x7FFFFFFFFFFFFFFF is long, hence can be negated
  public static final int hexInt64_1 = 1;
}
