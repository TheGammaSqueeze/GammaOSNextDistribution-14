/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.testapp.types;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Pair;
import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.testapp.CustomError;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;
import com.google.android.enterprise.connectedapps.testapp.CustomWrapper2;
import com.google.android.enterprise.connectedapps.testapp.NonSimpleCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.NotReallySerializableObject;
import com.google.android.enterprise.connectedapps.testapp.ParcelableObject;
import com.google.android.enterprise.connectedapps.testapp.SerializableObject;
import com.google.android.enterprise.connectedapps.testapp.SimpleFuture;
import com.google.android.enterprise.connectedapps.testapp.StringWrapper;
import com.google.android.enterprise.connectedapps.testapp.TestBooleanCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.TestCustomWrapperCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.TestNotReallySerializableObjectCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.TestParcelableCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.TestStringCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.TestVoidCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.wrappers.ParcelableCustomWrapper2;
import com.google.android.enterprise.connectedapps.testapp.wrappers.ParcelableStringWrapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@CrossProfile(
    connector = TestProfileConnector.class,
    parcelableWrappers = {ParcelableCustomWrapper2.class, ParcelableStringWrapper.class})
public class TestCrossProfileType {

  public static int voidMethodCalls = 0;
  public int voidMethodInstanceCalls;

  @CrossProfile
  public void voidMethod() {
    voidMethodCalls += 1;
    voidMethodInstanceCalls += 1;
  }

  @CrossProfile
  public void voidMethod(String s) {
    voidMethod();
  }

  @CrossProfile
  public String methodWhichThrowsError() {
    throw new CustomError();
  }

  @CrossProfile
  public String methodWhichThrowsRuntimeException() {
    throw new CustomRuntimeException("Exception");
  }

  @CrossProfile
  public String methodWhichThrowsRuntimeExceptionAndDeclaresException() throws IOException {
    throw new CustomRuntimeException("Exception");
  }

  @CrossProfile
  public ListenableFuture<Void> listenableFutureVoidMethod() {
    voidMethod();
    return immediateFuture(null);
  }

  @CrossProfile
  public ListenableFuture<Void> listenableFutureMethodWhichNeverSetsTheValue() {
    return SettableFuture.create();
  }

  @CrossProfile
  public ListenableFuture<Void> listenableFutureVoidMethodWhichThrowsError() {
    throw new CustomError();
  }

  @CrossProfile
  public ListenableFuture<Void> listenableFutureVoidMethodWhichThrowsRuntimeException() {
    throw new CustomRuntimeException("Exception");
  }

  @CrossProfile
  public ListenableFuture<Void> listenableFutureVoidMethodWhichSetsIllegalStateException() {
    return Futures.immediateFailedFuture(new IllegalStateException("Illegal State"));
  }

  @CrossProfile
  public ListenableFuture<Void> listenableFutureVoidMethodWithDelay(int secondsDelay) {
    try {
      TimeUnit.SECONDS.sleep(secondsDelay);
    } catch (InterruptedException e) {
      throw new IllegalStateException("Error during delay");
    }
    return listenableFutureVoidMethod();
  }

  @CrossProfile
  public ListenableFuture<Void> listenableFutureVoidMethodWithNonBlockingDelay(int secondsDelay) {
    SettableFuture<Void> v = SettableFuture.create();

    new Handler(Looper.getMainLooper())
        .postDelayed(
            () -> {
              voidMethod();
              v.set(null);
            },
            TimeUnit.SECONDS.toMillis(secondsDelay));
    return v;
  }

  @CrossProfile
  public ListenableFuture<String> listenableFutureIdentityStringMethodWithNonBlockingDelay(
      String s, int secondsDelay) {
    SettableFuture<String> v = SettableFuture.create();

    new Handler(Looper.getMainLooper())
        .postDelayed(() -> v.set(s), TimeUnit.SECONDS.toMillis(secondsDelay));
    return v;
  }

  @CrossProfile
  public void asyncStringMethodWhichThrowsError(TestStringCallbackListener callback) {
    throw new CustomError();
  }

  @CrossProfile
  public void asyncStringMethodWhichThrowsRuntimeException(TestStringCallbackListener callback) {
    throw new CustomRuntimeException("Exception");
  }

  @CrossProfile
  public void asyncIdentityStringMethodWhichCallsBackTwice(
      String s, TestStringCallbackListener callback) {
    voidMethod();
    callback.stringCallback(s);
    callback.stringCallback(s);
  }

  @CrossProfile
  public void asyncIdentityStringMethodWhichCallsBackTwiceWithNonBlockingDelay(
      String s, TestStringCallbackListener callback, long secondsDelay) {
    voidMethod();
    callback.stringCallback(s);

    new Handler(Looper.getMainLooper())
        .postDelayed(
            () -> {
              callback.stringCallback(s);
            },
            TimeUnit.SECONDS.toMillis(secondsDelay));
  }


  @CrossProfile
  public void asyncVoidMethod(TestVoidCallbackListener callback) {
    voidMethod();
    callback.callback();
  }

  @CrossProfile
  public void asyncMethodWhichNeverCallsBack(TestStringCallbackListener callback) {}

  @CrossProfile
  public void asyncVoidMethodWithDelay(TestVoidCallbackListener callback, int secondsDelay) {
    try {
      TimeUnit.SECONDS.sleep(secondsDelay);
    } catch (InterruptedException e) {
      throw new IllegalStateException("Error during delay");
    }
    asyncVoidMethod(callback);
  }

  @CrossProfile
  public void asyncVoidMethodWithNonBlockingDelay(
      TestVoidCallbackListener callback, int secondsDelay) {
    new Handler(Looper.getMainLooper())
        .postDelayed(() -> asyncVoidMethod(callback), TimeUnit.SECONDS.toMillis(secondsDelay));
  }

  @CrossProfile
  public void asyncIdentityStringMethodWithNonBlockingDelay(
      String s, TestStringCallbackListener callback, int secondsDelay) {
    new Handler(Looper.getMainLooper())
        .postDelayed(
            () -> asyncIdentityStringMethod(s, callback), TimeUnit.SECONDS.toMillis(secondsDelay));
  }

  @CrossProfile
  public Void identityVoidMethod() {
    voidMethod();
    return null;
  }

  @CrossProfile
  public String getNull() {
    return null;
  }

  @CrossProfile
  public Collection<String> getNullCollection() {
    return null;
  }

  @CrossProfile
  public List<String> getNullList() {
    return null;
  }

  @CrossProfile
  public Map<String, String> getNullMap() {
    return null;
  }

  // @CrossProfile
  // public Optional<String> getNullOptional() {
  //   return null;
  // }

  @CrossProfile
  public Set<String> getNullSet() {
    return null;
  }

  // @CrossProfile
  // public TestProto getNullProto() {
  //   return null;
  // }

  @CrossProfile
  public String identityStringMethod(String s) {
    return s;
  }

  @CrossProfile
  public void asyncIdentityStringMethod(String s, TestStringCallbackListener callback) {
    callback.stringCallback(s);
  }

  @CrossProfile
  public ListenableFuture<String> listenableFutureIdentityStringMethod(String s) {
    return immediateFuture(s);
  }

  @CrossProfile
  public byte identityByteMethod(byte b) {
    return b;
  }

  @CrossProfile
  public Byte identityByteMethod(Byte b) {
    return b;
  }

  @CrossProfile
  public short identityShortMethod(short s) {
    return s;
  }

  @CrossProfile
  public Short identityShortMethod(Short s) {
    return s;
  }

  @CrossProfile
  public int identityIntMethod(int i) {
    return i;
  }

  @CrossProfile
  public Integer identityIntegerMethod(Integer i) {
    return i;
  }

  @CrossProfile
  public long identityLongMethod(long l) {
    return l;
  }

  @CrossProfile
  public Long identityLongMethod(Long l) {
    return l;
  }

  @CrossProfile
  public float identityFloatMethod(float f) {
    return f;
  }

  @CrossProfile
  public Float identityFloatMethod(Float f) {
    return f;
  }

  @CrossProfile
  public double identityDoubleMethod(double d) {
    return d;
  }

  @CrossProfile
  public Double identityDoubleMethod(Double d) {
    return d;
  }

  @CrossProfile
  public char identityCharMethod(char c) {
    return c;
  }

  @CrossProfile
  public Character identityCharacterMethod(Character c) {
    return c;
  }

  @CrossProfile
  public boolean identityBooleanMethod(boolean b) {
    return b;
  }

  @CrossProfile
  public Boolean identityBooleanMethod(Boolean b) {
    return b;
  }

  @CrossProfile
  public CharSequence identityCharSequenceMethod(CharSequence c) {
    return c;
  }

  @CrossProfile
  public ParcelableObject identityParcelableMethod(ParcelableObject p) {
    return p;
  }

  @CrossProfile
  public Parcelable identityParcelableMethod(Parcelable p) {
    return p;
  }

  @CrossProfile
  public void asyncIdentityParcelableMethod(Parcelable p, TestParcelableCallbackListener callback) {
    callback.parcelableCallback(p);
  }

  @CrossProfile
  public ListenableFuture<Parcelable> futureIdentityParcelableMethod(Parcelable p) {
    return immediateFuture(p);
  }

  @CrossProfile
  public SerializableObject identitySerializableObjectMethod(SerializableObject s) {
    return s;
  }

  @CrossProfile
  public List<String> identityListMethod(List<String> l) {
    return l;
  }

  @CrossProfile
  public Map<String, String> identityMapMethod(Map<String, String> m) {
    return m;
  }

  @CrossProfile
  public Set<String> identitySetMethod(Set<String> s) {
    return s;
  }

  // TODO: Disabled because use of Optional fails lint check. Re-enable when this is disabled.
  // @CrossProfile
  // public Optional<String> identityOptionalMethod(Optional<String> o) {
  //   return o;
  // }

  @CrossProfile
  public ImmutableMap<String, String> identityImmutableMapMethod(ImmutableMap<String, String> m) {
    return m;
  }

  // @CrossProfile
  // public TestProto identityProtoMethod(TestProto p) {
  //   return p;
  // }

  // @CrossProfile
  // public List<TestProto> identityListOfProtoMethod(List<TestProto> l) {
  //   return l;
  // }

  @CrossProfile
  public Collection<String> identityCollectionMethod(Collection<String> c) {
    return c;
  }

  @CrossProfile
  public List<ParcelableObject> identityParcelableWrapperOfParcelableMethod(
      List<ParcelableObject> l) {
    return l;
  }

  @CrossProfile
  public List<SerializableObject> identityParcelableWrapperOfSerializableMethod(
      List<SerializableObject> l) {
    return l;
  }

  @CrossProfile
  public List<List<String>> identityParcelableWrapperOfParcelableWrapperMethod(
      List<List<String>> l) {
    return l;
  }

  @CrossProfile
  public String[] identityStringArrayMethod(String[] s) {
    return s;
  }

  @CrossProfile
  public ListenableFuture<String[]> asyncIdentityStringArrayMethod(String[] s) {
    return immediateFuture(s);
  }

  @CrossProfile
  public Collection<String[]> identityCollectionOfStringArrayMethod(Collection<String[]> c) {
    return c;
  }

  @CrossProfile
  public ParcelableObject[] identityParcelableObjectArrayMethod(ParcelableObject[] p) {
    return p;
  }

  @CrossProfile
  public SerializableObject[] identitySerializableObjectArrayMethod(SerializableObject[] s) {
    return s;
  }

  @CrossProfile
  public Collection<ParcelableObject[]> identityCollectionOfParcelableObjectArrayMethod(
      Collection<ParcelableObject[]> c) {
    return c;
  }

  @CrossProfile
  public Collection<SerializableObject[]> identityCollectionOfSerializableObjectArrayMethod(
      Collection<SerializableObject[]> c) {
    return c;
  }

  // @CrossProfile
  // public TestProto[] identityProtoArrayMethod(TestProto[] p) {
  //   return p;
  // }

  @CrossProfile
  public Pair<String, Integer> identityPairMethod(Pair<String, Integer> p) {
    return p;
  }

  @CrossProfile
  public Optional<ParcelableObject> identityGuavaOptionalMethod(Optional<ParcelableObject> p) {
    return p;
  }

  @CrossProfile
  public Bitmap identityBitmapMethod(Bitmap p) {
    return p;
  }

  @CrossProfile
  public NotReallySerializableObject identityNotReallySerializableObjectMethod(
      NotReallySerializableObject n) {
    return n;
  }

  @CrossProfile
  public NotReallySerializableObject returnNotReallySerializableObjectMethod() {
    return new NotReallySerializableObject(new ParcelableObject(""));
  }

  @CrossProfile
  public void asyncGetNotReallySerializableObjectMethod(
      TestNotReallySerializableObjectCallbackListener callbackListener) {
    callbackListener.callback(new NotReallySerializableObject(new ParcelableObject("TEST")));
  }

  @CrossProfile
  public ListenableFuture<NotReallySerializableObject>
      futureGetNotReallySerializableObjectMethod() {
    return immediateFuture(new NotReallySerializableObject(new ParcelableObject("TEST")));
  }

  @CrossProfile
  public CustomWrapper<String> identityCustomWrapperMethod(CustomWrapper<String> c) {
    return c;
  }

  @CrossProfile
  public ListenableFuture<CustomWrapper<String>> listenableFutureIdentityCustomWrapperMethod(
      CustomWrapper<String> c) {
    return immediateFuture(c);
  }

  @CrossProfile
  public void asyncIdentityCustomWrapperMethod(
      CustomWrapper<String> c, TestCustomWrapperCallbackListener callbackListener) {
    callbackListener.customWrapperCallback(c);
  }

  @CrossProfile
  public CustomWrapper2<String> identityCustomWrapper2Method(CustomWrapper2<String> c) {
    return c;
  }

  @CrossProfile
  public SimpleFuture<String> simpleFutureIdentityStringMethodWithNonBlockingDelay(
      String s, int secondsDelay) {
    SimpleFuture<String> future = new SimpleFuture<>();

    new Handler(Looper.getMainLooper())
        .postDelayed(() -> future.set(s), TimeUnit.SECONDS.toMillis(secondsDelay));

    return future;
  }

  @CrossProfile
  public StringWrapper identityStringWrapperMethod(StringWrapper s) {
    return s;
  }

  @CrossProfile
  public int getUserId() {
    return android.os.Process.myUid() / 100000;
  }

  @CrossProfile
  public ListenableFuture<Void> killApp() {
    android.os.Process.killProcess(android.os.Process.myPid());
    return immediateVoidFuture();
  }

  @CrossProfile
  public boolean isContextArgumentPassed(Context context) {
    return context != null;
  }

  @CrossProfile
  public void asyncIsContextArgumentPassed(
      Context contextArg, TestBooleanCallbackListener callback) {
    callback.booleanCallback(isContextArgumentPassed(contextArg));
  }

  @CrossProfile
  public ListenableFuture<Boolean> futureIsContextArgumentPassed(Context contextArg) {
    return immediateFuture(isContextArgumentPassed(contextArg));
  }

  @CrossProfile
  public String identityStringMethodDeclaresButDoesNotThrowIOException(String s)
      throws IOException {
    return s;
  }

  @CrossProfile
  public String identityStringMethodThrowsIOException(String s)
      throws IOException {
    throw new IOException("Requested to throw");
  }

  @CrossProfile
  public String identityStringMethodDeclaresIOExceptionThrowsSQLException(String s)
      throws IOException, SQLException {
    throw new SQLException("Requested to throw");
  }

  @CrossProfile
  public void asyncMethodWithNonSimpleCallback(
      NonSimpleCallbackListener callback, String s1, String s2) {
    callback.callback(s1, s2);
  }

  @CrossProfile
  public void asyncMethodWithNonSimpleCallbackCallsSecondMethod(
      NonSimpleCallbackListener callback, String s1, String s2) {
    callback.callback2(s1, s2);
  }

  @CrossProfile
  public float[] identityFloatArrayMethod(float[] f) {
    return f;
  }

  @CrossProfile
  public float[][][] identityMultidimensionalFloatArrayMethod(float[][][] f) {
    return f;
  }

  @CrossProfile
  public int[] identityIntArrayMethod(int[] i) {
    return i;
  }

  @CrossProfile
  public int[][][] identityMultidimensionalIntArrayMethod(int[][][] i) {
    return i;
  }

  @CrossProfile
  public byte[] identityByteArrayMethod(byte[] b) {
    return b;
  }

  @CrossProfile
  public byte[][][] identityMultidimensionalByteArrayMethod(byte[][][] b) {
    return b;
  }

  @CrossProfile
  public short[] identityShortArrayMethod(short[] s) {
    return s;
  }

  @CrossProfile
  public short[][][] identityMultidimensionalShortArrayMethod(short[][][] s) {
    return s;
  }

  @CrossProfile
  public long[] identityLongArrayMethod(long[] l) {
    return l;
  }

  @CrossProfile
  public long[][][] identityMultidimensionalLongArrayMethod(long[][][] l) {
    return l;
  }

  @CrossProfile
  public double[] identityDoubleArrayMethod(double[] d) {
    return d;
  }

  @CrossProfile
  public double[][][] identityMultidimensionalDoubleArrayMethod(double[][][] d) {
    return d;
  }

  @CrossProfile
  public boolean[] identityBooleanArrayMethod(boolean[] b) {
    return b;
  }

  @CrossProfile
  public boolean[][][] identityMultidimensionalBooleanArrayMethod(boolean[][][] b) {
    return b;
  }

  @CrossProfile
  public char[] identityCharArrayMethod(char[] c) {
    return c;
  }

  @CrossProfile
  public char[][][] identityMultidimensionalCharArrayMethod(char[][][] c) {
    return c;
  }

  @CrossProfile
  public Drawable identityDrawableMethod(Drawable d) {
    return d;
  }
}
