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

import static art.Redefinition.doCommonClassRedefinition;
import java.util.Base64;
import java.util.Random;
import java.util.function.*;
import java.util.stream.*;

public class Main {

  // The bytes below define the following java program.
  // To generate the bytes from the following Long.java file:
  // 1. Commit your Long.java change: git commit -- libcore/ojluni/src/main/java/java/lang/Long.java
  // 2. Copy the following java program into libcore/ojluni/src/main/java/java/lang/Long.java
  // 3. Run this build command: m core-all && d8 --classpath out/soong/.intermediates/libcore/core-all/android_common/javac/classes/  out/soong/.intermediates/libcore/core-all/android_common/javac/classes/java/lang/Long.class && base64 out/soong/.intermediates/libcore/core-all/android_common/javac/classes/java/lang/Long.class > class_Long.txt && base64 classes.dex > dex_Long.txt
  // 4. Copy class_Long.txt into CLASS_BYTES String and dex_Long.txt into DEX_BYTES
  // 5. Checkout the original Long.java: croot libcore && git checkout ojluni/src/main/java/java/lang/Long.java
  // package java.lang;
  // import java.lang.constant.Constable;
  // import java.lang.constant.ConstantDesc;
  // import java.lang.invoke.MethodHandles;
  // import java.math.*;
  //
  // import java.util.Optional;
  // public final class Long extends Number implements Comparable<Long>, Constable, ConstantDesc {
  //     public static final long MIN_VALUE = 0;
  //     public static final long MAX_VALUE = 0;
  //     public static final Class<Long> TYPE = null;
  //     static { }
  //     // Used for Stream.count for some reason.
  //     public static long sum(long a, long b) {
  //       return a + b;
  //     }
  //     // Used in stream/lambda functions.
  //     public Long(long value) {
  //       this.value = value;
  //     }
  //     // Used in stream/lambda functions.
  //     public static Long valueOf(long l) {
  //       return new Long(l);
  //     }
  //     // Intrinsic! Do something cool. Return i + 1
  //     public static long highestOneBit(long i) {
  //       return i + 1;
  //     }
  //     // Intrinsic! Do something cool. Return i - 1
  //     public static long lowestOneBit(long i) {
  //       return i - 1;
  //     }
  //     // Intrinsic! Do something cool. Return i + i
  //     public static int numberOfLeadingZeros(long i) {
  //       return (int)(i + i);
  //     }
  //     // Intrinsic! Do something cool. Return i & (i >>> 1);
  //     public static int numberOfTrailingZeros(long i) {
  //       return (int)(i & (i >>> 1));
  //     }
  //     // Intrinsic! Do something cool. Return 5
  //      public static int bitCount(long i) {
  //        return 5;
  //      }
  //     // Intrinsic! Do something cool. Return i
  //     public static long rotateLeft(long i, int distance) {
  //       return i;
  //     }
  //     // Intrinsic! Do something cool. Return 10 * i
  //     public static long rotateRight(long i, int distance) {
  //       return 10 * i;
  //     }
  //     // Intrinsic! Do something cool. Return -i
  //     public static long reverse(long i) {
  //       return -i;
  //     }
  //     // Intrinsic! Do something cool. Return 0
  //     public static int signum(long i) {
  //       return 0;
  //     }
  //     // Intrinsic! Do something cool. Return 0
  //     public static long reverseBytes(long i) {
  //       return 0;
  //     }
  //     public String toString() {
  //       return "Redefined Long! value (as double)=" + ((double)value);
  //     }
  //     public static String toString(long i, int radix) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static String toUnsignedString(long i, int radix) {
  //       throw new Error("Method redefined away!");
  //     }
  //     private static BigInteger toUnsignedBigInteger(long i) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static String toHexString(long i) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static String toOctalString(long i) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static String toBinaryString(long i) {
  //       throw new Error("Method redefined away!");
  //     }
  //     static String toUnsignedString0(long val, int shift) {
  //       throw new Error("Method redefined away!");
  //     }
  //     static void formatUnsignedLong0(long val, int shift, byte[] buf, int offset, int len) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static String toString(long i) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static String toUnsignedString(long i) {
  //       throw new Error("Method redefined away!");
  //     }
  //     static int getChars(long i, int index, byte[] buf) {
  //       throw new Error("Method redefined away!");
  //     }
  //     static int getChars(long i, int index, char[] buf) {
  //       throw new Error("Method redefined away!");
  //     }
  //     static int stringSize(long x) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long parseLong(String s, int radix) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long parseLong(String s) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long parseLong(CharSequence s, int beginIndex, int endIndex, int radix) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long parseUnsignedLong(String s, int radix) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long parseUnsignedLong(String s) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long parseUnsignedLong(CharSequence s, int beginIndex, int endIndex, int radix) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static Long valueOf(String s, int radix) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static Long valueOf(String s) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static Long decode(String nm) throws NumberFormatException {
  //       throw new Error("Method redefined away!");
  //     }
  //     private final long value;
  //     public Long(String s) throws NumberFormatException {
  //       this(0);
  //       throw new Error("Method redefined away!");
  //     }
  //     public byte byteValue() {
  //       throw new Error("Method redefined away!");
  //     }
  //     public short shortValue() {
  //       throw new Error("Method redefined away!");
  //     }
  //     public int intValue() {
  //       throw new Error("Method redefined away!");
  //     }
  //     public long longValue() {
  //       return value;
  //     }
  //     public float floatValue() {
  //       throw new Error("Method redefined away!");
  //     }
  //     public double doubleValue() {
  //       throw new Error("Method redefined away!");
  //     }
  //     public int hashCode() {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static int hashCode(long value) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public boolean equals(Object obj) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static Long getLong(String nm) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static Long getLong(String nm, long val) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static Long getLong(String nm, Long val) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public int compareTo(Long anotherLong) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static int compare(long x, long y) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static int compareUnsigned(long x, long y) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long divideUnsigned(long dividend, long divisor) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long remainderUnsigned(long dividend, long divisor) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static final int SIZE = 64;
  //     public static final int BYTES = SIZE / Byte.SIZE;
  //     public static long max(long a, long b) {
  //       throw new Error("Method redefined away!");
  //     }
  //     public static long min(long a, long b) {
  //       throw new Error("Method redefined away!");
  //     }
  //     private static final long serialVersionUID = 0;
  //
  //     /** @hide */
  //     @Override
  //     public Optional<? extends ConstantDesc> describeConstable() {
  //       throw new Error("Method redefined away!");
  //     }
  //
  //     /** @hide */
  //     @Override
  //     public Object resolveConstantDesc(MethodHandles.Lookup lookup) {
  //       throw new Error("Method redefined away!");
  //     }
  // }
  private static final byte[] CLASS_BYTES = Base64.getDecoder().decode(
    "yv66vgAAAD0AwQcAAgEADmphdmEvbGFuZy9Mb25nBwAEAQAOamF2YS9sYW5nL0J5dGUKAAYABwcA" +
    "CAwACQAKAQAQamF2YS9sYW5nL051bWJlcgEABjxpbml0PgEAAygpVgkAAQAMDAANAA4BAAV2YWx1" +
    "ZQEAAUoKAAEAEAwACQARAQAEKEopVgUAAAAAAAAACgcAFQEAF2phdmEvbGFuZy9TdHJpbmdCdWls" +
    "ZGVyCgAUAAcIABgBACJSZWRlZmluZWQgTG9uZyEgdmFsdWUgKGFzIGRvdWJsZSk9CgAUABoMABsA" +
    "HAEABmFwcGVuZAEALShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9TdHJpbmdCdWlsZGVy" +
    "OwoAFAAeDAAbAB8BABwoRClMamF2YS9sYW5nL1N0cmluZ0J1aWxkZXI7CgAUACEMACIAIwEACHRv" +
    "U3RyaW5nAQAUKClMamF2YS9sYW5nL1N0cmluZzsHACUBAA9qYXZhL2xhbmcvRXJyb3IIACcBABZN" +
    "ZXRob2QgcmVkZWZpbmVkIGF3YXkhCgAkACkMAAkAKgEAFShMamF2YS9sYW5nL1N0cmluZzspVgoA" +
    "AQAsDAAtAC4BAAljb21wYXJlVG8BABMoTGphdmEvbGFuZy9Mb25nOylJCgABADAMADEAMgEAE3Jl" +
    "c29sdmVDb25zdGFudERlc2MBADkoTGphdmEvbGFuZy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29r" +
    "dXA7KUxqYXZhL2xhbmcvTG9uZzsJAAEANAwANQA2AQAEVFlQRQEAEUxqYXZhL2xhbmcvQ2xhc3M7" +
    "BwA4AQAUamF2YS9sYW5nL0NvbXBhcmFibGUHADoBABxqYXZhL2xhbmcvY29uc3RhbnQvQ29uc3Rh" +
    "YmxlBwA8AQAfamF2YS9sYW5nL2NvbnN0YW50L0NvbnN0YW50RGVzYwEACU1JTl9WQUxVRQEADUNv" +
    "bnN0YW50VmFsdWUFAAAAAAAAAAABAAlNQVhfVkFMVUUBAAlTaWduYXR1cmUBACNMamF2YS9sYW5n" +
    "L0NsYXNzPExqYXZhL2xhbmcvTG9uZzs+OwEABFNJWkUBAAFJAwAAAEABAAVCWVRFUwMAAAAIAQAQ" +
    "c2VyaWFsVmVyc2lvblVJRAEAA3N1bQEABShKSilKAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEA" +
    "EkxvY2FsVmFyaWFibGVUYWJsZQEAAWEBAAFiAQAEdGhpcwEAEExqYXZhL2xhbmcvTG9uZzsBAAd2" +
    "YWx1ZU9mAQATKEopTGphdmEvbGFuZy9Mb25nOwEAAWwBAA1oaWdoZXN0T25lQml0AQAEKEopSgEA" +
    "AWkBAAxsb3dlc3RPbmVCaXQBABRudW1iZXJPZkxlYWRpbmdaZXJvcwEABChKKUkBABVudW1iZXJP" +
    "ZlRyYWlsaW5nWmVyb3MBAAhiaXRDb3VudAEACnJvdGF0ZUxlZnQBAAUoSkkpSgEACGRpc3RhbmNl" +
    "AQALcm90YXRlUmlnaHQBAAdyZXZlcnNlAQAGc2lnbnVtAQAMcmV2ZXJzZUJ5dGVzAQAWKEpJKUxq" +
    "YXZhL2xhbmcvU3RyaW5nOwEABXJhZGl4AQAQdG9VbnNpZ25lZFN0cmluZwEAFHRvVW5zaWduZWRC" +
    "aWdJbnRlZ2VyAQAZKEopTGphdmEvbWF0aC9CaWdJbnRlZ2VyOwEAC3RvSGV4U3RyaW5nAQAVKEop" +
    "TGphdmEvbGFuZy9TdHJpbmc7AQANdG9PY3RhbFN0cmluZwEADnRvQmluYXJ5U3RyaW5nAQARdG9V" +
    "bnNpZ25lZFN0cmluZzABAAN2YWwBAAVzaGlmdAEAE2Zvcm1hdFVuc2lnbmVkTG9uZzABAAkoSklb" +
    "QklJKVYBAANidWYBAAJbQgEABm9mZnNldAEAA2xlbgEACGdldENoYXJzAQAHKEpJW0IpSQEABWlu" +
    "ZGV4AQAHKEpJW0MpSQEAAltDAQAKc3RyaW5nU2l6ZQEAAXgBAAlwYXJzZUxvbmcBABYoTGphdmEv" +
    "bGFuZy9TdHJpbmc7SSlKAQABcwEAEkxqYXZhL2xhbmcvU3RyaW5nOwEACkV4Y2VwdGlvbnMHAIQB" +
    "AB9qYXZhL2xhbmcvTnVtYmVyRm9ybWF0RXhjZXB0aW9uAQAVKExqYXZhL2xhbmcvU3RyaW5nOylK" +
    "AQAeKExqYXZhL2xhbmcvQ2hhclNlcXVlbmNlO0lJSSlKAQAYTGphdmEvbGFuZy9DaGFyU2VxdWVu" +
    "Y2U7AQAKYmVnaW5JbmRleAEACGVuZEluZGV4AQARcGFyc2VVbnNpZ25lZExvbmcBACUoTGphdmEv" +
    "bGFuZy9TdHJpbmc7SSlMamF2YS9sYW5nL0xvbmc7AQAkKExqYXZhL2xhbmcvU3RyaW5nOylMamF2" +
    "YS9sYW5nL0xvbmc7AQAGZGVjb2RlAQACbm0BAAlieXRlVmFsdWUBAAMoKUIBAApzaG9ydFZhbHVl" +
    "AQADKClTAQAIaW50VmFsdWUBAAMoKUkBAAlsb25nVmFsdWUBAAMoKUoBAApmbG9hdFZhbHVlAQAD" +
    "KClGAQALZG91YmxlVmFsdWUBAAMoKUQBAAhoYXNoQ29kZQEABmVxdWFscwEAFShMamF2YS9sYW5n" +
    "L09iamVjdDspWgEAA29iagEAEkxqYXZhL2xhbmcvT2JqZWN0OwEAB2dldExvbmcBACUoTGphdmEv" +
    "bGFuZy9TdHJpbmc7SilMamF2YS9sYW5nL0xvbmc7AQA0KExqYXZhL2xhbmcvU3RyaW5nO0xqYXZh" +
    "L2xhbmcvTG9uZzspTGphdmEvbGFuZy9Mb25nOwEAC2Fub3RoZXJMb25nAQAHY29tcGFyZQEABShK" +
    "SilJAQABeQEAD2NvbXBhcmVVbnNpZ25lZAEADmRpdmlkZVVuc2lnbmVkAQAIZGl2aWRlbmQBAAdk" +
    "aXZpc29yAQARcmVtYWluZGVyVW5zaWduZWQBAANtYXgBAANtaW4BABFkZXNjcmliZUNvbnN0YWJs" +
    "ZQEAFigpTGphdmEvdXRpbC9PcHRpb25hbDsBAEAoKUxqYXZhL3V0aWwvT3B0aW9uYWw8TGphdmEv" +
    "bGFuZy9jb25zdGFudC9EeW5hbWljQ29uc3RhbnREZXNjOz47AQAGbG9va3VwAQAnTGphdmEvbGFu" +
    "Zy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29rdXA7AQAVKExqYXZhL2xhbmcvT2JqZWN0OylJAQA7" +
    "KExqYXZhL2xhbmcvaW52b2tlL01ldGhvZEhhbmRsZXMkTG9va3VwOylMamF2YS9sYW5nL09iamVj" +
    "dDsHALYBACZqYXZhL2xhbmcvUmVmbGVjdGl2ZU9wZXJhdGlvbkV4Y2VwdGlvbgEACDxjbGluaXQ+" +
    "AQB5TGphdmEvbGFuZy9OdW1iZXI7TGphdmEvbGFuZy9Db21wYXJhYmxlPExqYXZhL2xhbmcvTG9u" +
    "Zzs+O0xqYXZhL2xhbmcvY29uc3RhbnQvQ29uc3RhYmxlO0xqYXZhL2xhbmcvY29uc3RhbnQvQ29u" +
    "c3RhbnREZXNjOwEAClNvdXJjZUZpbGUBAAlMb25nLmphdmEBAAxJbm5lckNsYXNzZXMHAL0BACVq" +
    "YXZhL2xhbmcvaW52b2tlL01ldGhvZEhhbmRsZXMkTG9va3VwBwC/AQAeamF2YS9sYW5nL2ludm9r" +
    "ZS9NZXRob2RIYW5kbGVzAQAGTG9va3VwADEAAQAGAAMANwA5ADsABwAZAD0ADgABAD4AAAACAD8A" +
    "GQBBAA4AAQA+AAAAAgA/ABkANQA2AAEAQgAAAAIAQwASAA0ADgAAABkARABFAAEAPgAAAAIARgAZ" +
    "AEcARQABAD4AAAACAEgAGgBJAA4AAQA+AAAAAgA/AD0ACQBKAEsAAQBMAAAAOAAEAAQAAAAEHiBh" +
    "rQAAAAIATQAAAAYAAQAAABAATgAAABYAAgAAAAQATwAOAAAAAAAEAFAADgACAAEACQARAAEATAAA" +
    "AEYAAwADAAAACiq3AAUqH7UAC7EAAAACAE0AAAAOAAMAAAATAAQAFAAJABUATgAAABYAAgAAAAoA" +
    "UQBSAAAAAAAKAA0ADgABAAkAUwBUAAEATAAAADMABAACAAAACbsAAVketwAPsAAAAAIATQAAAAYA" +
    "AQAAABgATgAAAAwAAQAAAAkAVQAOAAAACQBWAFcAAQBMAAAALgAEAAIAAAAEHgphrQAAAAIATQAA" +
    "AAYAAQAAABwATgAAAAwAAQAAAAQAWAAOAAAACQBZAFcAAQBMAAAALgAEAAIAAAAEHgplrQAAAAIA" +
    "TQAAAAYAAQAAACAATgAAAAwAAQAAAAQAWAAOAAAACQBaAFsAAQBMAAAALwAEAAIAAAAFHh5hiKwA" +
    "AAACAE0AAAAGAAEAAAAkAE4AAAAMAAEAAAAFAFgADgAAAAkAXABbAAEATAAAADEABQACAAAABx4e" +
    "BH1/iKwAAAACAE0AAAAGAAEAAAAoAE4AAAAMAAEAAAAHAFgADgAAAAkAXQBbAAEATAAAACwAAQAC" +
    "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" +
    "JrcAKL8AAAACAE0AAAAGAAEAAAC9AE4AAAAWAAIAAAAKAKkADgAAAAAACgCqAA4AAgAJAKwASwAB" +
    "AEwAAAA+AAMABAAAAAq7ACRZEia3ACi/AAAAAgBNAAAABgABAAAAwgBOAAAAFgACAAAACgBPAA4A" +
    "AAAAAAoAUAAOAAIACQCtAEsAAQBMAAAAPgADAAQAAAAKuwAkWRImtwAovwAAAAIATQAAAAYAAQAA" +
    "AMUATgAAABYAAgAAAAoATwAOAAAAAAAKAFAADgACAAEArgCvAAIATAAAADQAAwABAAAACrsAJFkS" +
    "JrcAKL8AAAACAE0AAAAGAAEAAADMAE4AAAAMAAEAAAAKAFEAUgAAAEIAAAACALAAAQAxADIAAQBM" +
    "AAAAPgADAAIAAAAKuwAkWRImtwAovwAAAAIATQAAAAYAAQAAANIATgAAABYAAgAAAAoAUQBSAAAA" +
    "AAAKALEAsgABEEEALQCzAAEATAAAADMAAgACAAAACSorwAABtgArrAAAAAIATQAAAAYAAQAAAAkA" +
    "TgAAAAwAAQAAAAkAUQBSAAAQQQAxALQAAgBMAAAAMAACAAIAAAAGKiu2AC+wAAAAAgBNAAAABgAB" +
    "AAAACQBOAAAADAABAAAABgBRAFIAAACCAAAABAABALUACAC3AAoAAQBMAAAAIQABAAAAAAAFAbMA" +
    "M7EAAAABAE0AAAAKAAIAAAAMAAQADQADAEIAAAACALgAuQAAAAIAugC7AAAACgABALwAvgDAABk="
    );

  private static final byte[] DEX_BYTES = Base64.getDecoder().decode(
    "ZGV4CjAzNQCC6NqF6YcFy4KrMQ61yOakOSk9scYPTAY0GgAAcAAAAHhWNBIAAAAAAAAAAGQZAACI" +
    "AAAAcAAAABwAAACQAgAAJQAAAAADAAAHAAAAvAQAAEMAAAD0BAAAAQAAAAwHAAAIEwAALAcAAH4Q" +
    "AACCEAAAjBAAAJQQAACYEAAAmxAAAKIQAAClEAAAqBAAAKsQAACvEAAAtRAAALoQAAC+EAAAwRAA" +
    "AMUQAADKEAAAzxAAANMQAADYEAAA3xAAAOIQAADmEAAA6hAAAO8QAADzEAAA+BAAAP0QAAACEQAA" +
    "IREAAD0RAABXEQAAahEAAH0RAACVEQAArREAAMARAADSEQAA5hEAAAkSAAAdEgAARxIAAFsSAAB2" +
    "EgAAlhIAALkSAADjEgAADBMAACQTAAA6EwAAUBMAAFsTAABmEwAAcRMAAIkTAACtEwAAsBMAALYT" +
    "AAC8EwAAvxMAAMMTAADLEwAAzxMAANITAADWEwAA2hMAAN4TAADhEwAA7hMAAPYTAAD5EwAABRQA" +
    "AA8UAAAUFAAAHxQAACgUAAAzFAAARBQAAEwUAABfFAAAaRQAAHkUAACDFAAAjBQAAJkUAACjFAAA" +
    "qxQAALcUAADMFAAA1hQAAN8UAADpFAAA+BQAAPsUAAACFQAADBUAAA8VAAAUFQAAHxUAACcVAAA1" +
    "FQAAOhUAAD8VAABDFQAAWRUAAHAVAAB1FQAAfRUAAIgVAACbFQAAohUAALUVAADKFQAA0xUAAOEV" +
    "AADtFQAA+hUAAP0VAAAPFgAAFhYAACIWAAAqFgAANhYAADsWAABLFgAAWBYAAGcWAABxFgAAhxYA" +
    "AJkWAACsFgAAsRYAALgWAADBFgAAxBYAAMcWAAAEAAAABgAAAAcAAAAIAAAADQAAABwAAAAdAAAA" +
    "HgAAAB8AAAAhAAAAIwAAACQAAAAlAAAAJgAAACcAAAAoAAAAKQAAACoAAAArAAAALAAAAC4AAAAv" +
    "AAAAMAAAADcAAAA6AAAAPgAAAEAAAABBAAAABAAAAAAAAAAAAAAABgAAAAEAAAAAAAAABwAAAAIA" +
    "AAAAAAAACAAAAAMAAAAAAAAACQAAAAMAAAD0DwAACgAAAAMAAAD8DwAACgAAAAMAAAAIEAAACwAA" +
    "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" +
    "TAADSUpKAAJJTAABSgACSkoAA0pKSQADSkpKAAJKTAADSkxJAAVKTElJSQABTAACTEQAAkxKAANM" +
    "SkkAAkxMAANMTEkAA0xMSgADTExMAB1MZGFsdmlrL2Fubm90YXRpb24vU2lnbmF0dXJlOwAaTGRh" +
    "bHZpay9hbm5vdGF0aW9uL1Rocm93czsAGExqYXZhL2xhbmcvQ2hhclNlcXVlbmNlOwARTGphdmEv" +
    "bGFuZy9DbGFzczsAEUxqYXZhL2xhbmcvQ2xhc3M8ABZMamF2YS9sYW5nL0NvbXBhcmFibGU7ABZM" +
    "amF2YS9sYW5nL0NvbXBhcmFibGU8ABFMamF2YS9sYW5nL0Vycm9yOwAQTGphdmEvbGFuZy9Mb25n" +
    "OwASTGphdmEvbGFuZy9OdW1iZXI7ACFMamF2YS9sYW5nL051bWJlckZvcm1hdEV4Y2VwdGlvbjsA" +
    "EkxqYXZhL2xhbmcvT2JqZWN0OwAoTGphdmEvbGFuZy9SZWZsZWN0aXZlT3BlcmF0aW9uRXhjZXB0" +
    "aW9uOwASTGphdmEvbGFuZy9TdHJpbmc7ABlMamF2YS9sYW5nL1N0cmluZ0J1aWxkZXI7AB5MamF2" +
    "YS9sYW5nL2NvbnN0YW50L0NvbnN0YWJsZTsAIUxqYXZhL2xhbmcvY29uc3RhbnQvQ29uc3RhbnRE" +
    "ZXNjOwAoTGphdmEvbGFuZy9jb25zdGFudC9EeW5hbWljQ29uc3RhbnREZXNjOwAnTGphdmEvbGFu" +
    "Zy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29rdXA7ABZMamF2YS9tYXRoL0JpZ0ludGVnZXI7ABRM" +
    "amF2YS91dGlsL09wdGlvbmFsOwAUTGphdmEvdXRpbC9PcHRpb25hbDwACUxvbmcuamF2YQAJTUFY" +
    "X1ZBTFVFAAlNSU5fVkFMVUUAFk1ldGhvZCByZWRlZmluZWQgYXdheSEAIlJlZGVmaW5lZCBMb25n" +
    "ISB2YWx1ZSAoYXMgZG91YmxlKT0AAVMABFNJWkUABFRZUEUAAVYAAlZKAAZWSklMSUkAAlZMAAFa" +
    "AAJaTAACW0IAAltDAAFhAAthbm90aGVyTG9uZwAGYXBwZW5kAAFiAApiZWdpbkluZGV4AAhiaXRD" +
    "b3VudAADYnVmAAlieXRlVmFsdWUAB2NvbXBhcmUACWNvbXBhcmVUbwAPY29tcGFyZVVuc2lnbmVk" +
    "AAZkZWNvZGUAEWRlc2NyaWJlQ29uc3RhYmxlAAhkaXN0YW5jZQAOZGl2aWRlVW5zaWduZWQACGRp" +
    "dmlkZW5kAAdkaXZpc29yAAtkb3VibGVWYWx1ZQAIZW5kSW5kZXgABmVxdWFscwAKZmxvYXRWYWx1" +
    "ZQATZm9ybWF0VW5zaWduZWRMb25nMAAIZ2V0Q2hhcnMAB2dldExvbmcACGhhc2hDb2RlAA1oaWdo" +
    "ZXN0T25lQml0AAFpAAVpbmRleAAIaW50VmFsdWUAAWwAA2xlbgAJbG9uZ1ZhbHVlAAZsb29rdXAA" +
    "DGxvd2VzdE9uZUJpdAADbWF4AANtaW4AAm5tABRudW1iZXJPZkxlYWRpbmdaZXJvcwAVbnVtYmVy" +
    "T2ZUcmFpbGluZ1plcm9zAANvYmoABm9mZnNldAAJcGFyc2VMb25nABFwYXJzZVVuc2lnbmVkTG9u" +
    "ZwAFcmFkaXgAEXJlbWFpbmRlclVuc2lnbmVkABNyZXNvbHZlQ29uc3RhbnREZXNjAAdyZXZlcnNl" +
    "AAxyZXZlcnNlQnl0ZXMACnJvdGF0ZUxlZnQAC3JvdGF0ZVJpZ2h0AAFzABBzZXJpYWxWZXJzaW9u" +
    "VUlEAAVzaGlmdAAKc2hvcnRWYWx1ZQAGc2lnbnVtAApzdHJpbmdTaXplAANzdW0ADnRvQmluYXJ5" +
    "U3RyaW5nAAt0b0hleFN0cmluZwANdG9PY3RhbFN0cmluZwAIdG9TdHJpbmcAFHRvVW5zaWduZWRC" +
    "aWdJbnRlZ2VyABB0b1Vuc2lnbmVkU3RyaW5nABF0b1Vuc2lnbmVkU3RyaW5nMAADdmFsAAV2YWx1" +
    "ZQAHdmFsdWVPZgABeAABeQCbAX5+RDh7ImJhY2tlbmQiOiJkZXgiLCJjb21waWxhdGlvbi1tb2Rl" +
    "IjoiZGVidWciLCJoYXMtY2hlY2tzdW1zIjpmYWxzZSwibWluLWFwaSI6MSwic2hhLTEiOiJmZWE0" +
    "ZmY0ZDg1MWIxMThhZWFjZjMxZDRkODJmZmJjYmNiZWE0ZGVmIiwidmVyc2lvbiI6IjguMi44LWRl" +
    "diJ9AAIGAYMBHAEYDQIFAYMBHAQXABcxFy0XAwIGAYMBHAEYDwIFAYMBHAMXIBckFwMCBQGDARwG" +
    "FyUXIhckFwMXKxcsBgEvDgAZARkBGQEZARkBGgYSAYiABIAcAYGABMQcAYGABJgcAQmsDwIJwA8D" +
    "CaAQAQnIEgIJ4BcECOAcAQjAEAEI4BABCegSAQmoEwEJiBMCCaARAQmAGAMJsBgBCcwYAQnsGAEJ" +
    "4BEBCfgRAQmMGQEJrBkBCcwZAQnsGQEJjBoBCawaAQnMGgMJ7BoBCYAbAQmYGwEJrBsCCZQSAQio" +
    "EgEJyBsBCeAUAQmAFQEJoBUCCYAWAQmgFgEKoBcBCcAWAQngFgEIgBcBCagUAQnoEwEJiBQFAcwO" +
    "AgHgDwHBIIAQAwHAFwIB7A4BAawOAQGMDwcBgBEDAcARAQGYGA0ByBMBwSDEFAUB4BsHAcAVBAQI" +
    "BgAGAARAAAAAAAAAAAEAAABlFwAAAQAAAG4XAAABAAAAfRcAAAEAAACGFwAAAQAAAJMXAADkGAAA" +
    "AQAAAAwAAAAAAAAABAAAANwYAAADAAAAxBgAAAoAAADEGAAACwAAAMwYAAAgAAAAxBgAACEAAADE" +
    "GAAAIgAAAMQYAAAjAAAAxBgAACQAAADEGAAAJQAAAMQYAAAoAAAA1BgAADwAAADEGAAAPQAAAMQY" +
    "AAARAAAAAAAAAAEAAAAAAAAAAQAAAIgAAABwAAAAAgAAABwAAACQAgAAAwAAACUAAAAAAwAABAAA" +
    "AAcAAAC8BAAABQAAAEMAAAD0BAAABgAAAAEAAAAMBwAAASAAAD0AAAAsBwAAAyAAADwAAACADgAA" +
    "ARAAABAAAADoDwAAAiAAAIgAAAB+EAAABCAAAAUAAABlFwAAACAAAAEAAACmFwAABSAAAAEAAAC0" +
    "GAAAAxAAAAYAAADAGAAABiAAAAEAAADsGAAAABAAAAEAAABkGQAA"
    );

  static class FuncCmp implements LongPredicate {
    final String name;
    final LongPredicate p;
    public FuncCmp(String name, LongPredicate p) {
      this.name = name;
      this.p = p;
    }

    public boolean test(long l) {
      return p.test(l);
    }
  }
  static FuncCmp l2l(String name, final LongUnaryOperator a, final LongUnaryOperator b) {
    return new FuncCmp(name, (v) -> a.applyAsLong(v) == b.applyAsLong(v));
  }
  static FuncCmp l2i(String name, final LongToIntFunction a, final LongToIntFunction b) {
    return new FuncCmp(name, (v) -> a.applyAsInt(v) == b.applyAsInt(v));
  }

  /** Interface for a long, int -> long function. */
  static interface LI2IFunction { public long applyToLongInt(long a, int b); }

  static FuncCmp li2l(String name, final Random r, final LI2IFunction a, final LI2IFunction b) {
    return new FuncCmp(name, new LongPredicate() {
      public boolean test(long v) {
        int i = r.nextInt();
        return a.applyToLongInt(v, i) == b.applyToLongInt(v, i);
      }
    });
  }

  public static void main(String[] args) {
    doTest(10000);
  }

  public static void doTest(int iters) {
    // Just transform immediately.
    doCommonClassRedefinition(Long.class, CLASS_BYTES, DEX_BYTES);
    final Random r = new Random();
    FuncCmp[] comps = new FuncCmp[] {
      l2l("highestOneBit", Long::highestOneBit, RedefinedLongIntrinsics::highestOneBit),
      l2l("lowestOneBit", Long::lowestOneBit, RedefinedLongIntrinsics::lowestOneBit),
      l2i("numberOfLeadingZeros",
          Long::numberOfLeadingZeros,
          RedefinedLongIntrinsics::numberOfLeadingZeros),
      l2i("numberOfTrailingZeros",
          Long::numberOfTrailingZeros,
          RedefinedLongIntrinsics::numberOfTrailingZeros),
      l2i("bitCount", Long::bitCount, RedefinedLongIntrinsics::bitCount),
      li2l("rotateLeft", r, Long::rotateLeft, RedefinedLongIntrinsics::rotateLeft),
      li2l("rotateRight", r, Long::rotateRight, RedefinedLongIntrinsics::rotateRight),
      l2l("reverse", Long::reverse, RedefinedLongIntrinsics::reverse),
      l2i("signum", Long::signum, RedefinedLongIntrinsics::signum),
      l2l("reverseBytes", Long::reverseBytes, RedefinedLongIntrinsics::reverseBytes),
    };
    for (FuncCmp f : comps) {
      // Just actually use ints so we can cast them back int the tests to print them (since we
      // deleted a bunch of the Long methods needed for printing longs)!
      int failures = (int)r.ints(iters)
                           .mapToLong((v) -> (long)v)
                           .filter(f.negate()) // Get all the test cases that failed.
                           .count();
      if (failures != 0) {
        double percent = 100.0d*((double)failures/(double)iters);
        System.out.println("for intrinsic " + f.name + " " + failures + "/" + iters
            + " (" + Double.toString(percent) + "%) tests failed!");
      }
    }
    System.out.println("Finished!");
  }
}
