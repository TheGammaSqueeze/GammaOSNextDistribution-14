/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-fixedsizearray-java-source/gen/android/aidl/fixedsizearray/FixedSizeArrayExample.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-fixedsizearray-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/fixedsizearray/FixedSizeArrayExample.aidl
 */
package android.aidl.fixedsizearray;
public class FixedSizeArrayExample implements android.os.Parcelable
{
  // to see if NxM array works
  public int[][] int2x3 = {{1, 2, 3}, {4, 5, 6}};
  public boolean[] boolArray;
  public byte[] byteArray;
  public char[] charArray;
  public int[] intArray;
  public long[] longArray;
  public float[] floatArray;
  public double[] doubleArray;
  public java.lang.String[] stringArray = {"hello", "world"};
  public byte[] byteEnumArray;
  public int[] intEnumArray;
  public long[] longEnumArray;
  public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] parcelableArray;
  // Following fields are not test-friendly because they should be initialized as non-null.
  // IBinder[2] binderArray;
  // ParcelFileDescriptor[2] pfdArray;
  // IEmptyInterface[2] interfaceArray;
  public boolean[][] boolMatrix;
  public byte[][] byteMatrix;
  public char[][] charMatrix;
  public int[][] intMatrix;
  public long[][] longMatrix;
  public float[][] floatMatrix;
  public double[][] doubleMatrix;
  public java.lang.String[][] stringMatrix = {{"hello", "world"}, {"Ciao", "mondo"}};
  public byte[][] byteEnumMatrix;
  public int[][] intEnumMatrix;
  public long[][] longEnumMatrix;
  public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] parcelableMatrix;
  // Following fields are not test-friendly because they should be initialized as non-null.
  // ParcelFileDescriptor[2][2] pfdMatrix;
  // IBinder[2][2] binderMatrix;
  // IEmptyInterface[2][2] interfaceMatrix;
  public boolean[] boolNullableArray;
  public byte[] byteNullableArray;
  public char[] charNullableArray;
  public int[] intNullableArray;
  public long[] longNullableArray;
  public float[] floatNullableArray;
  public double[] doubleNullableArray;
  public java.lang.String[] stringNullableArray = {"hello", "world"};
  public byte[] byteEnumNullableArray;
  public int[] intEnumNullableArray;
  public long[] longEnumNullableArray;
  public android.os.IBinder[] binderNullableArray;
  public android.os.ParcelFileDescriptor[] pfdNullableArray;
  public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] parcelableNullableArray;
  public android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface[] interfaceNullableArray;
  public boolean[][] boolNullableMatrix;
  public byte[][] byteNullableMatrix;
  public char[][] charNullableMatrix;
  public int[][] intNullableMatrix;
  public long[][] longNullableMatrix;
  public float[][] floatNullableMatrix;
  public double[][] doubleNullableMatrix;
  public java.lang.String[][] stringNullableMatrix = {{"hello", "world"}, {"Ciao", "mondo"}};
  public byte[][] byteEnumNullableMatrix;
  public int[][] intEnumNullableMatrix;
  public long[][] longEnumNullableMatrix;
  public android.os.IBinder[][] binderNullableMatrix;
  public android.os.ParcelFileDescriptor[][] pfdNullableMatrix;
  public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] parcelableNullableMatrix;
  public android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface[][] interfaceNullableMatrix;
  public static final android.os.Parcelable.Creator<FixedSizeArrayExample> CREATOR = new android.os.Parcelable.Creator<FixedSizeArrayExample>() {
    @Override
    public FixedSizeArrayExample createFromParcel(android.os.Parcel _aidl_source) {
      FixedSizeArrayExample _aidl_out = new FixedSizeArrayExample();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public FixedSizeArrayExample[] newArray(int _aidl_size) {
      return new FixedSizeArrayExample[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeFixedArray(int2x3, _aidl_flag, 2, 3);
    _aidl_parcel.writeFixedArray(boolArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(byteArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(charArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(intArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(longArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(floatArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(doubleArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(stringArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(byteEnumArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(intEnumArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(longEnumArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(parcelableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(boolMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(byteMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(charMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(intMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(longMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(floatMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(doubleMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(stringMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(byteEnumMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(intEnumMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(longEnumMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(parcelableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(boolNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(byteNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(charNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(intNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(longNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(floatNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(doubleNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(stringNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(byteEnumNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(intEnumNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(longEnumNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(binderNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(pfdNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(parcelableNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(interfaceNullableArray, _aidl_flag, 2);
    _aidl_parcel.writeFixedArray(boolNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(byteNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(charNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(intNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(longNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(floatNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(doubleNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(stringNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(byteEnumNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(intEnumNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(longEnumNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(binderNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(pfdNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(parcelableNullableMatrix, _aidl_flag, 2, 2);
    _aidl_parcel.writeFixedArray(interfaceNullableMatrix, _aidl_flag, 2, 2);
    int _aidl_end_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.setDataPosition(_aidl_start_pos);
    _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
    _aidl_parcel.setDataPosition(_aidl_end_pos);
  }
  public final void readFromParcel(android.os.Parcel _aidl_parcel)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    int _aidl_parcelable_size = _aidl_parcel.readInt();
    try {
      if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      int2x3 = _aidl_parcel.createFixedArray(int[][].class, 2, 3);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      boolArray = _aidl_parcel.createFixedArray(boolean[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteArray = _aidl_parcel.createFixedArray(byte[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      charArray = _aidl_parcel.createFixedArray(char[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intArray = _aidl_parcel.createFixedArray(int[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longArray = _aidl_parcel.createFixedArray(long[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      floatArray = _aidl_parcel.createFixedArray(float[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      doubleArray = _aidl_parcel.createFixedArray(double[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      stringArray = _aidl_parcel.createFixedArray(java.lang.String[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteEnumArray = _aidl_parcel.createFixedArray(byte[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intEnumArray = _aidl_parcel.createFixedArray(int[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longEnumArray = _aidl_parcel.createFixedArray(long[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      parcelableArray = _aidl_parcel.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      boolMatrix = _aidl_parcel.createFixedArray(boolean[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteMatrix = _aidl_parcel.createFixedArray(byte[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      charMatrix = _aidl_parcel.createFixedArray(char[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intMatrix = _aidl_parcel.createFixedArray(int[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longMatrix = _aidl_parcel.createFixedArray(long[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      floatMatrix = _aidl_parcel.createFixedArray(float[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      doubleMatrix = _aidl_parcel.createFixedArray(double[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      stringMatrix = _aidl_parcel.createFixedArray(java.lang.String[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteEnumMatrix = _aidl_parcel.createFixedArray(byte[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intEnumMatrix = _aidl_parcel.createFixedArray(int[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longEnumMatrix = _aidl_parcel.createFixedArray(long[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      parcelableMatrix = _aidl_parcel.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      boolNullableArray = _aidl_parcel.createFixedArray(boolean[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteNullableArray = _aidl_parcel.createFixedArray(byte[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      charNullableArray = _aidl_parcel.createFixedArray(char[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intNullableArray = _aidl_parcel.createFixedArray(int[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longNullableArray = _aidl_parcel.createFixedArray(long[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      floatNullableArray = _aidl_parcel.createFixedArray(float[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      doubleNullableArray = _aidl_parcel.createFixedArray(double[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      stringNullableArray = _aidl_parcel.createFixedArray(java.lang.String[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteEnumNullableArray = _aidl_parcel.createFixedArray(byte[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intEnumNullableArray = _aidl_parcel.createFixedArray(int[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longEnumNullableArray = _aidl_parcel.createFixedArray(long[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      binderNullableArray = _aidl_parcel.createFixedArray(android.os.IBinder[].class, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      pfdNullableArray = _aidl_parcel.createFixedArray(android.os.ParcelFileDescriptor[].class, android.os.ParcelFileDescriptor.CREATOR, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      parcelableNullableArray = _aidl_parcel.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      interfaceNullableArray = _aidl_parcel.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface[].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface.Stub::asInterface, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      boolNullableMatrix = _aidl_parcel.createFixedArray(boolean[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteNullableMatrix = _aidl_parcel.createFixedArray(byte[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      charNullableMatrix = _aidl_parcel.createFixedArray(char[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intNullableMatrix = _aidl_parcel.createFixedArray(int[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longNullableMatrix = _aidl_parcel.createFixedArray(long[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      floatNullableMatrix = _aidl_parcel.createFixedArray(float[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      doubleNullableMatrix = _aidl_parcel.createFixedArray(double[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      stringNullableMatrix = _aidl_parcel.createFixedArray(java.lang.String[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteEnumNullableMatrix = _aidl_parcel.createFixedArray(byte[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intEnumNullableMatrix = _aidl_parcel.createFixedArray(int[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longEnumNullableMatrix = _aidl_parcel.createFixedArray(long[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      binderNullableMatrix = _aidl_parcel.createFixedArray(android.os.IBinder[][].class, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      pfdNullableMatrix = _aidl_parcel.createFixedArray(android.os.ParcelFileDescriptor[][].class, android.os.ParcelFileDescriptor.CREATOR, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      parcelableNullableMatrix = _aidl_parcel.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR, 2, 2);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      interfaceNullableMatrix = _aidl_parcel.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface[][].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface.Stub::asInterface, 2, 2);
    } finally {
      if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
        throw new android.os.BadParcelableException("Overflow in the size of parcelable");
      }
      _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    }
  }
  @Override
  public String toString() {
    java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(", ", "{", "}");
    _aidl_sj.add("int2x3: " + (java.util.Arrays.deepToString(int2x3)));
    _aidl_sj.add("boolArray: " + (java.util.Arrays.toString(boolArray)));
    _aidl_sj.add("byteArray: " + (java.util.Arrays.toString(byteArray)));
    _aidl_sj.add("charArray: " + (java.util.Arrays.toString(charArray)));
    _aidl_sj.add("intArray: " + (java.util.Arrays.toString(intArray)));
    _aidl_sj.add("longArray: " + (java.util.Arrays.toString(longArray)));
    _aidl_sj.add("floatArray: " + (java.util.Arrays.toString(floatArray)));
    _aidl_sj.add("doubleArray: " + (java.util.Arrays.toString(doubleArray)));
    _aidl_sj.add("stringArray: " + (java.util.Arrays.toString(stringArray)));
    _aidl_sj.add("byteEnumArray: " + (java.util.Arrays.toString(byteEnumArray)));
    _aidl_sj.add("intEnumArray: " + (java.util.Arrays.toString(intEnumArray)));
    _aidl_sj.add("longEnumArray: " + (java.util.Arrays.toString(longEnumArray)));
    _aidl_sj.add("parcelableArray: " + (java.util.Arrays.toString(parcelableArray)));
    _aidl_sj.add("boolMatrix: " + (java.util.Arrays.deepToString(boolMatrix)));
    _aidl_sj.add("byteMatrix: " + (java.util.Arrays.deepToString(byteMatrix)));
    _aidl_sj.add("charMatrix: " + (java.util.Arrays.deepToString(charMatrix)));
    _aidl_sj.add("intMatrix: " + (java.util.Arrays.deepToString(intMatrix)));
    _aidl_sj.add("longMatrix: " + (java.util.Arrays.deepToString(longMatrix)));
    _aidl_sj.add("floatMatrix: " + (java.util.Arrays.deepToString(floatMatrix)));
    _aidl_sj.add("doubleMatrix: " + (java.util.Arrays.deepToString(doubleMatrix)));
    _aidl_sj.add("stringMatrix: " + (java.util.Arrays.deepToString(stringMatrix)));
    _aidl_sj.add("byteEnumMatrix: " + (java.util.Arrays.deepToString(byteEnumMatrix)));
    _aidl_sj.add("intEnumMatrix: " + (java.util.Arrays.deepToString(intEnumMatrix)));
    _aidl_sj.add("longEnumMatrix: " + (java.util.Arrays.deepToString(longEnumMatrix)));
    _aidl_sj.add("parcelableMatrix: " + (java.util.Arrays.deepToString(parcelableMatrix)));
    _aidl_sj.add("boolNullableArray: " + (java.util.Arrays.toString(boolNullableArray)));
    _aidl_sj.add("byteNullableArray: " + (java.util.Arrays.toString(byteNullableArray)));
    _aidl_sj.add("charNullableArray: " + (java.util.Arrays.toString(charNullableArray)));
    _aidl_sj.add("intNullableArray: " + (java.util.Arrays.toString(intNullableArray)));
    _aidl_sj.add("longNullableArray: " + (java.util.Arrays.toString(longNullableArray)));
    _aidl_sj.add("floatNullableArray: " + (java.util.Arrays.toString(floatNullableArray)));
    _aidl_sj.add("doubleNullableArray: " + (java.util.Arrays.toString(doubleNullableArray)));
    _aidl_sj.add("stringNullableArray: " + (java.util.Arrays.toString(stringNullableArray)));
    _aidl_sj.add("byteEnumNullableArray: " + (java.util.Arrays.toString(byteEnumNullableArray)));
    _aidl_sj.add("intEnumNullableArray: " + (java.util.Arrays.toString(intEnumNullableArray)));
    _aidl_sj.add("longEnumNullableArray: " + (java.util.Arrays.toString(longEnumNullableArray)));
    _aidl_sj.add("binderNullableArray: " + (java.util.Arrays.toString(binderNullableArray)));
    _aidl_sj.add("pfdNullableArray: " + (java.util.Arrays.toString(pfdNullableArray)));
    _aidl_sj.add("parcelableNullableArray: " + (java.util.Arrays.toString(parcelableNullableArray)));
    _aidl_sj.add("interfaceNullableArray: " + (java.util.Arrays.toString(interfaceNullableArray)));
    _aidl_sj.add("boolNullableMatrix: " + (java.util.Arrays.deepToString(boolNullableMatrix)));
    _aidl_sj.add("byteNullableMatrix: " + (java.util.Arrays.deepToString(byteNullableMatrix)));
    _aidl_sj.add("charNullableMatrix: " + (java.util.Arrays.deepToString(charNullableMatrix)));
    _aidl_sj.add("intNullableMatrix: " + (java.util.Arrays.deepToString(intNullableMatrix)));
    _aidl_sj.add("longNullableMatrix: " + (java.util.Arrays.deepToString(longNullableMatrix)));
    _aidl_sj.add("floatNullableMatrix: " + (java.util.Arrays.deepToString(floatNullableMatrix)));
    _aidl_sj.add("doubleNullableMatrix: " + (java.util.Arrays.deepToString(doubleNullableMatrix)));
    _aidl_sj.add("stringNullableMatrix: " + (java.util.Arrays.deepToString(stringNullableMatrix)));
    _aidl_sj.add("byteEnumNullableMatrix: " + (java.util.Arrays.deepToString(byteEnumNullableMatrix)));
    _aidl_sj.add("intEnumNullableMatrix: " + (java.util.Arrays.deepToString(intEnumNullableMatrix)));
    _aidl_sj.add("longEnumNullableMatrix: " + (java.util.Arrays.deepToString(longEnumNullableMatrix)));
    _aidl_sj.add("binderNullableMatrix: " + (java.util.Arrays.deepToString(binderNullableMatrix)));
    _aidl_sj.add("pfdNullableMatrix: " + (java.util.Arrays.deepToString(pfdNullableMatrix)));
    _aidl_sj.add("parcelableNullableMatrix: " + (java.util.Arrays.deepToString(parcelableNullableMatrix)));
    _aidl_sj.add("interfaceNullableMatrix: " + (java.util.Arrays.deepToString(interfaceNullableMatrix)));
    return "FixedSizeArrayExample" + _aidl_sj.toString()  ;
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    _mask |= describeContents(parcelableArray);
    _mask |= describeContents(parcelableMatrix);
    _mask |= describeContents(pfdNullableArray);
    _mask |= describeContents(parcelableNullableArray);
    _mask |= describeContents(pfdNullableMatrix);
    _mask |= describeContents(parcelableNullableMatrix);
    return _mask;
  }
  private int describeContents(Object _v) {
    if (_v == null) return 0;
    if (_v instanceof Object[]) {
      int _mask = 0;
      for (Object o : (Object[]) _v) {
        _mask |= describeContents(o);
      }
      return _mask;
    }
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }
  public interface IRepeatFixedSizeArray extends android.os.IInterface
  {
    /** Default implementation for IRepeatFixedSizeArray. */
    public static class Default implements android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray
    {
      @Override public byte[] RepeatBytes(byte[] input, byte[] repeated) throws android.os.RemoteException
      {
        return null;
      }
      @Override public int[] RepeatInts(int[] input, int[] repeated) throws android.os.RemoteException
      {
        return null;
      }
      @Override public android.os.IBinder[] RepeatBinders(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException
      {
        return null;
      }
      @Override public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] RepeatParcelables(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] input, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] repeated) throws android.os.RemoteException
      {
        return null;
      }
      @Override public byte[][] Repeat2dBytes(byte[][] input, byte[][] repeated) throws android.os.RemoteException
      {
        return null;
      }
      @Override public int[][] Repeat2dInts(int[][] input, int[][] repeated) throws android.os.RemoteException
      {
        return null;
      }
      @Override public android.os.IBinder[][] Repeat2dBinders(android.os.IBinder[][] input, android.os.IBinder[][] repeated) throws android.os.RemoteException
      {
        return null;
      }
      @Override public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] Repeat2dParcelables(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] input, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] repeated) throws android.os.RemoteException
      {
        return null;
      }
      @Override
      public android.os.IBinder asBinder() {
        return null;
      }
    }
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends android.os.Binder implements android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray
    {
      /** Construct the stub at attach it to the interface. */
      @SuppressWarnings("this-escape")
      public Stub()
      {
        this.attachInterface(this, DESCRIPTOR);
      }
      /**
       * Cast an IBinder object into an android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray interface,
       * generating a proxy if needed.
       */
      public static android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray asInterface(android.os.IBinder obj)
      {
        if ((obj==null)) {
          return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin!=null)&&(iin instanceof android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray))) {
          return ((android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray)iin);
        }
        return new android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray.Stub.Proxy(obj);
      }
      @Override public android.os.IBinder asBinder()
      {
        return this;
      }
      @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
      {
        java.lang.String descriptor = DESCRIPTOR;
        if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
          data.enforceInterface(descriptor);
        }
        if (code == INTERFACE_TRANSACTION) {
          reply.writeString(descriptor);
          return true;
        }
        switch (code)
        {
          case TRANSACTION_RepeatBytes:
          {
            byte[] _arg0;
            _arg0 = data.createFixedArray(byte[].class, 3);
            byte[] _arg1;
            _arg1 = new byte[3];
            data.enforceNoDataAvail();
            byte[] _result = this.RepeatBytes(_arg0, _arg1);
            reply.writeNoException();
            reply.writeFixedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 3);
            reply.writeFixedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 3);
            break;
          }
          case TRANSACTION_RepeatInts:
          {
            int[] _arg0;
            _arg0 = data.createFixedArray(int[].class, 3);
            int[] _arg1;
            _arg1 = new int[3];
            data.enforceNoDataAvail();
            int[] _result = this.RepeatInts(_arg0, _arg1);
            reply.writeNoException();
            reply.writeFixedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 3);
            reply.writeFixedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 3);
            break;
          }
          case TRANSACTION_RepeatBinders:
          {
            android.os.IBinder[] _arg0;
            _arg0 = data.createFixedArray(android.os.IBinder[].class, 3);
            android.os.IBinder[] _arg1;
            _arg1 = new android.os.IBinder[3];
            data.enforceNoDataAvail();
            android.os.IBinder[] _result = this.RepeatBinders(_arg0, _arg1);
            reply.writeNoException();
            reply.writeFixedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 3);
            reply.writeFixedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 3);
            break;
          }
          case TRANSACTION_RepeatParcelables:
          {
            android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] _arg0;
            _arg0 = data.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR, 3);
            android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] _arg1;
            _arg1 = new android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[3];
            data.enforceNoDataAvail();
            android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] _result = this.RepeatParcelables(_arg0, _arg1);
            reply.writeNoException();
            reply.writeFixedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 3);
            reply.writeFixedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 3);
            break;
          }
          case TRANSACTION_Repeat2dBytes:
          {
            byte[][] _arg0;
            _arg0 = data.createFixedArray(byte[][].class, 2, 3);
            byte[][] _arg1;
            _arg1 = new byte[2][3];
            data.enforceNoDataAvail();
            byte[][] _result = this.Repeat2dBytes(_arg0, _arg1);
            reply.writeNoException();
            reply.writeFixedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 2, 3);
            reply.writeFixedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 2, 3);
            break;
          }
          case TRANSACTION_Repeat2dInts:
          {
            int[][] _arg0;
            _arg0 = data.createFixedArray(int[][].class, 2, 3);
            int[][] _arg1;
            _arg1 = new int[2][3];
            data.enforceNoDataAvail();
            int[][] _result = this.Repeat2dInts(_arg0, _arg1);
            reply.writeNoException();
            reply.writeFixedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 2, 3);
            reply.writeFixedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 2, 3);
            break;
          }
          case TRANSACTION_Repeat2dBinders:
          {
            android.os.IBinder[][] _arg0;
            _arg0 = data.createFixedArray(android.os.IBinder[][].class, 2, 3);
            android.os.IBinder[][] _arg1;
            _arg1 = new android.os.IBinder[2][3];
            data.enforceNoDataAvail();
            android.os.IBinder[][] _result = this.Repeat2dBinders(_arg0, _arg1);
            reply.writeNoException();
            reply.writeFixedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 2, 3);
            reply.writeFixedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 2, 3);
            break;
          }
          case TRANSACTION_Repeat2dParcelables:
          {
            android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] _arg0;
            _arg0 = data.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR, 2, 3);
            android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] _arg1;
            _arg1 = new android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[2][3];
            data.enforceNoDataAvail();
            android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] _result = this.Repeat2dParcelables(_arg0, _arg1);
            reply.writeNoException();
            reply.writeFixedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 2, 3);
            reply.writeFixedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE, 2, 3);
            break;
          }
          default:
          {
            return super.onTransact(code, data, reply, flags);
          }
        }
        return true;
      }
      private static class Proxy implements android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray
      {
        private android.os.IBinder mRemote;
        Proxy(android.os.IBinder remote)
        {
          mRemote = remote;
        }
        @Override public android.os.IBinder asBinder()
        {
          return mRemote;
        }
        public java.lang.String getInterfaceDescriptor()
        {
          return DESCRIPTOR;
        }
        @Override public byte[] RepeatBytes(byte[] input, byte[] repeated) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          byte[] _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeFixedArray(input, 0, 3);
            boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatBytes, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createFixedArray(byte[].class, 3);
            _reply.readFixedArray(repeated);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public int[] RepeatInts(int[] input, int[] repeated) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          int[] _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeFixedArray(input, 0, 3);
            boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatInts, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createFixedArray(int[].class, 3);
            _reply.readFixedArray(repeated);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public android.os.IBinder[] RepeatBinders(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          android.os.IBinder[] _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeFixedArray(input, 0, 3);
            boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatBinders, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createFixedArray(android.os.IBinder[].class, 3);
            _reply.readFixedArray(repeated);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] RepeatParcelables(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] input, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] repeated) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeFixedArray(input, 0, 3);
            boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatParcelables, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR, 3);
            _reply.readFixedArray(repeated, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public byte[][] Repeat2dBytes(byte[][] input, byte[][] repeated) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          byte[][] _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeFixedArray(input, 0, 2, 3);
            boolean _status = mRemote.transact(Stub.TRANSACTION_Repeat2dBytes, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createFixedArray(byte[][].class, 2, 3);
            _reply.readFixedArray(repeated);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public int[][] Repeat2dInts(int[][] input, int[][] repeated) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          int[][] _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeFixedArray(input, 0, 2, 3);
            boolean _status = mRemote.transact(Stub.TRANSACTION_Repeat2dInts, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createFixedArray(int[][].class, 2, 3);
            _reply.readFixedArray(repeated);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public android.os.IBinder[][] Repeat2dBinders(android.os.IBinder[][] input, android.os.IBinder[][] repeated) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          android.os.IBinder[][] _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeFixedArray(input, 0, 2, 3);
            boolean _status = mRemote.transact(Stub.TRANSACTION_Repeat2dBinders, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createFixedArray(android.os.IBinder[][].class, 2, 3);
            _reply.readFixedArray(repeated);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] Repeat2dParcelables(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] input, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] repeated) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeFixedArray(input, 0, 2, 3);
            boolean _status = mRemote.transact(Stub.TRANSACTION_Repeat2dParcelables, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createFixedArray(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][].class, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR, 2, 3);
            _reply.readFixedArray(repeated, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable.CREATOR);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
      }
      static final int TRANSACTION_RepeatBytes = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
      static final int TRANSACTION_RepeatInts = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
      static final int TRANSACTION_RepeatBinders = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
      static final int TRANSACTION_RepeatParcelables = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
      static final int TRANSACTION_Repeat2dBytes = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
      static final int TRANSACTION_Repeat2dInts = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
      static final int TRANSACTION_Repeat2dBinders = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
      static final int TRANSACTION_Repeat2dParcelables = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    }
    /** @hide */
    public static final java.lang.String DESCRIPTOR = "android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray";
    public byte[] RepeatBytes(byte[] input, byte[] repeated) throws android.os.RemoteException;
    public int[] RepeatInts(int[] input, int[] repeated) throws android.os.RemoteException;
    public android.os.IBinder[] RepeatBinders(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException;
    public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] RepeatParcelables(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] input, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[] repeated) throws android.os.RemoteException;
    public byte[][] Repeat2dBytes(byte[][] input, byte[][] repeated) throws android.os.RemoteException;
    public int[][] Repeat2dInts(int[][] input, int[][] repeated) throws android.os.RemoteException;
    public android.os.IBinder[][] Repeat2dBinders(android.os.IBinder[][] input, android.os.IBinder[][] repeated) throws android.os.RemoteException;
    public android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] Repeat2dParcelables(android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] input, android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable[][] repeated) throws android.os.RemoteException;
  }
  public static @interface ByteEnum {
    public static final byte A = 0;
  }
  public static @interface IntEnum {
    public static final int A = 0;
  }
  public static @interface LongEnum {
    public static final long A = 0L;
  }
  public static class IntParcelable implements android.os.Parcelable
  {
    public int value = 0;
    public static final android.os.Parcelable.Creator<IntParcelable> CREATOR = new android.os.Parcelable.Creator<IntParcelable>() {
      @Override
      public IntParcelable createFromParcel(android.os.Parcel _aidl_source) {
        IntParcelable _aidl_out = new IntParcelable();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public IntParcelable[] newArray(int _aidl_size) {
        return new IntParcelable[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      _aidl_parcel.writeInt(value);
      int _aidl_end_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.setDataPosition(_aidl_start_pos);
      _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
      _aidl_parcel.setDataPosition(_aidl_end_pos);
    }
    public final void readFromParcel(android.os.Parcel _aidl_parcel)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      int _aidl_parcelable_size = _aidl_parcel.readInt();
      try {
        if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        value = _aidl_parcel.readInt();
      } finally {
        if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
          throw new android.os.BadParcelableException("Overflow in the size of parcelable");
        }
        _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
      }
    }
    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null) return false;
      if (!(other instanceof IntParcelable)) return false;
      IntParcelable that = (IntParcelable)other;
      if (!java.util.Objects.deepEquals(value, that.value)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return java.util.Arrays.deepHashCode(java.util.Arrays.asList(value).toArray());
    }
    @Override
    public int describeContents() {
      int _mask = 0;
      return _mask;
    }
  }
  public interface IEmptyInterface extends android.os.IInterface
  {
    /** Default implementation for IEmptyInterface. */
    public static class Default implements android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface
    {
      @Override
      public android.os.IBinder asBinder() {
        return null;
      }
    }
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends android.os.Binder implements android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface
    {
      /** Construct the stub at attach it to the interface. */
      @SuppressWarnings("this-escape")
      public Stub()
      {
        this.attachInterface(this, DESCRIPTOR);
      }
      /**
       * Cast an IBinder object into an android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface interface,
       * generating a proxy if needed.
       */
      public static android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface asInterface(android.os.IBinder obj)
      {
        if ((obj==null)) {
          return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin!=null)&&(iin instanceof android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface))) {
          return ((android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface)iin);
        }
        return new android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface.Stub.Proxy(obj);
      }
      @Override public android.os.IBinder asBinder()
      {
        return this;
      }
      @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
      {
        java.lang.String descriptor = DESCRIPTOR;
        if (code == INTERFACE_TRANSACTION) {
          reply.writeString(descriptor);
          return true;
        }
        switch (code)
        {
          default:
          {
            return super.onTransact(code, data, reply, flags);
          }
        }
      }
      private static class Proxy implements android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface
      {
        private android.os.IBinder mRemote;
        Proxy(android.os.IBinder remote)
        {
          mRemote = remote;
        }
        @Override public android.os.IBinder asBinder()
        {
          return mRemote;
        }
        public java.lang.String getInterfaceDescriptor()
        {
          return DESCRIPTOR;
        }
      }
    }
    /** @hide */
    public static final java.lang.String DESCRIPTOR = "android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface";
  }
}
