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
package com.google.android.enterprise.connectedapps.robotests;

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestCustomWrapperCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestParcelableCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;
import com.google.android.enterprise.connectedapps.testapp.CustomWrapper2;
import com.google.android.enterprise.connectedapps.testapp.ParcelableContainingBinder;
import com.google.android.enterprise.connectedapps.testapp.ParcelableObject;
import com.google.android.enterprise.connectedapps.testapp.SerializableObject;
import com.google.android.enterprise.connectedapps.testapp.SimpleFuture;
import com.google.android.enterprise.connectedapps.testapp.StringWrapper;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType_SingleSenderCanThrow;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class TypesTest {

  private static final String STRING = "string";
  private static final byte BYTE = 1;
  private static final byte[] BYTE_ARRAY = new byte[]{BYTE};
  private static final byte[][][] MULTIDIMENSIONAL_BYTE_ARRAY = new byte[][][] {{BYTE_ARRAY}};
  private static final Byte BYTE_BOXED = 1;
  private static final short SHORT = 1;
  private static final short[] SHORT_ARRAY = new short[]{SHORT};
  private static final short[][][] MULTIDIMENSIONAL_SHORT_ARRAY = new short[][][] {{SHORT_ARRAY}};
  private static final Short SHORT_BOXED = 1;
  private static final int INT = 1;
  private static final int[] INT_ARRAY = new int[]{INT};
  private static final int[][][] MULTIDIMENSIONAL_INT_ARRAY = new int[][][] {{INT_ARRAY}};
  private static final Integer INTEGER = 1;
  private static final long LONG = 1;
  private static final long[] LONG_ARRAY = new long[]{LONG};
  private static final long[][][] MULTIDIMENSIONAL_LONG_ARRAY = new long[][][] {{LONG_ARRAY}};
  private static final Long LONG_BOXED = 1L;
  private static final float FLOAT = 1;
  private static final float[] FLOAT_ARRAY = new float[]{FLOAT};
  private static final float[][][] MULTIDIMENSIONAL_FLOAT_ARRAY = new float[][][] {{FLOAT_ARRAY}};
  private static final Float FLOAT_BOXED = 1f;
  private static final double DOUBLE = 1;
  private static final double[] DOUBLE_ARRAY = new double[]{DOUBLE};
  private static final double[][][] MULTIDIMENSIONAL_DOUBLE_ARRAY =
      new double[][][] {{DOUBLE_ARRAY}};
  private static final Double DOUBLE_BOXED = 1d;
  private static final char CHAR = 1;
  private static final char[] CHAR_ARRAY = new char[]{CHAR};
  private static final char[][][] MULTIDIMENSIONAL_CHAR_ARRAY = new char[][][] {{CHAR_ARRAY}};
  private static final Character CHARACTER = 1;
  private static final boolean BOOLEAN = true;
  private static final boolean[] BOOLEAN_ARRAY = new boolean[]{BOOLEAN};
  private static final boolean[][][] MULTIDIMENSIONAL_BOOLEAN_ARRAY =
      new boolean[][][] {{BOOLEAN_ARRAY}};
  private static final Boolean BOOLEAN_BOXED = true;
  private static final ParcelableObject PARCELABLE = new ParcelableObject("test");
  private static final SerializableObject SERIALIZABLE = new SerializableObject("test");
  private static final List<String> listOfString = Collections.singletonList(STRING);
  private static final List<List<String>> listOfListOfString = ImmutableList.of(listOfString);
  private static final List<ParcelableObject> listOfParcelable = ImmutableList.of(PARCELABLE);
  private static final List<SerializableObject> listOfSerializable = ImmutableList.of(SERIALIZABLE);
  private static final ImmutableMap<String, String> IMMUTABLE_MAP_STRING_TO_STRING =
      ImmutableMap.of(STRING, STRING);
  private static final Set<String> setOfString = ImmutableSet.of(STRING);
  private static final Collection<String> collectionOfString = ImmutableList.of(STRING);
  // private static final TestProto PROTO = TestProto.newBuilder().setText(STRING).build();
  // private static final List<TestProto> listOfProto = ImmutableList.of(PROTO);
  private static final String[] arrayOfString = new String[] {STRING};
  private static final Collection<String[]> collectionOfStringArray =
      ImmutableList.of(arrayOfString);
  private static final ParcelableObject[] arrayOfParcelable = new ParcelableObject[] {PARCELABLE};
  private static final SerializableObject[] arrayOfSerializable =
      new SerializableObject[] {SERIALIZABLE};
  private static final Collection<ParcelableObject[]> collectionOfParcelableArray =
      ImmutableList.of(arrayOfParcelable);
  private static final Collection<SerializableObject[]> collectionOfSerializableArray =
      ImmutableList.of(arrayOfSerializable);
  // private static final TestProto[] arrayOfProto = new TestProto[] {PROTO};
  private static final String[] emptyStringArray = new String[] {};
  private static final CustomWrapper<String> CUSTOM_WRAPPER = new CustomWrapper<>(STRING);
  private static final CustomWrapper2<String> CUSTOM_WRAPPER2 = new CustomWrapper2<>(STRING);
  private static final StringWrapper STRING_WRAPPER = new StringWrapper(STRING);
  private static final Optional<ParcelableObject> GUAVA_OPTIONAL = Optional.of(PARCELABLE);
  private static final int[] BITMAP_PIXELS = {1, 2, 3, 4, 5, 6, 7, 8, 9};
  private static final CharSequence CHAR_SEQUENCE = STRING;

  private final Application context = ApplicationProvider.getApplicationContext();
  // Android type can't be static due to Robolectric
  private final Pair<String, Integer> pair = new Pair<>(STRING, INTEGER);
  private final Bitmap bitmap = Bitmap.createBitmap(BITMAP_PIXELS, 3, 3, Bitmap.Config.ARGB_8888);
  private final ParcelableContainingBinder parcelableContainingBinder =
      new ParcelableContainingBinder();
  private final Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);


  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);

  private interface SenderProvider {
    TestCrossProfileType_SingleSenderCanThrow provide(
        Context context, TestProfileConnector testProfileConnector);
  }

  @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
  public static Collection<Object[]> data() {

    SenderProvider currentProfileSenderProvider =
        (Context context, TestProfileConnector testProfileConnector) ->
            (TestCrossProfileType_SingleSenderCanThrow)
                ProfileTestCrossProfileType.create(testProfileConnector).current();
    SenderProvider otherProfileSenderProvider =
        (Context context, TestProfileConnector testProfileConnector) ->
            ProfileTestCrossProfileType.create(testProfileConnector).other();

    return Arrays.asList(
        new Object[][] {
          {"CurrentProfile", currentProfileSenderProvider},
          {"OtherProfile", otherProfileSenderProvider},
        });
  }

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(TestApplication.getService());
    testUtilities.initTests();
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    testUtilities.setBinding(binder, TestProfileConnector.class.getName());
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);
    testUtilities.addDefaultConnectionHolderAndWait();
  }

  private SenderProvider senderProvider;

  public TypesTest(String profile, SenderProvider senderProvider) {
    this.senderProvider = senderProvider;
  }

  @Test
  public void voidMethodWithNoArguments_callsMethod() throws UnavailableProfileException {
    TestCrossProfileType.voidMethodCalls = 0;

    senderProvider.provide(context, testProfileConnector).voidMethod();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void voidMethodWithArguments_callsMethod() throws UnavailableProfileException {
    TestCrossProfileType.voidMethodCalls = 0;

    senderProvider.provide(context, testProfileConnector).voidMethod("argument");

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void stringReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityStringMethod(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void byteReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityByteMethod(BYTE))
        .isEqualTo(BYTE);
  }

  @Test
  public void boxedByteReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityByteMethod(BYTE_BOXED))
        .isEqualTo(BYTE_BOXED);
  }

  @Test
  public void shortReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityShortMethod(SHORT))
        .isEqualTo(SHORT);
  }

  @Test
  public void boxedShortReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider.provide(context, testProfileConnector).identityShortMethod(SHORT_BOXED))
        .isEqualTo(SHORT_BOXED);
  }

  @Test
  public void intReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityIntMethod(INT))
        .isEqualTo(INT);
  }

  @Test
  public void integerReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityIntegerMethod(INTEGER))
        .isEqualTo(INTEGER);
  }

  @Test
  public void longReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityLongMethod(LONG))
        .isEqualTo(LONG);
  }

  @Test
  public void boxedLongReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityLongMethod(LONG_BOXED))
        .isEqualTo(LONG_BOXED);
  }

  @Test
  public void floatReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityFloatMethod(FLOAT))
        .isEqualTo(FLOAT);
  }

  @Test
  public void boxedFloatReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider.provide(context, testProfileConnector).identityFloatMethod(FLOAT_BOXED))
        .isEqualTo(FLOAT_BOXED);
  }

  @Test
  public void doubleReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityDoubleMethod(DOUBLE))
        .isEqualTo(DOUBLE);
  }

  @Test
  public void boxedDoubleReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityDoubleMethod(DOUBLE_BOXED))
        .isEqualTo(DOUBLE_BOXED);
  }

  @Test
  public void charReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityCharMethod(CHAR))
        .isEqualTo(CHAR);
  }

  @Test
  public void characterReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityCharacterMethod(CHARACTER))
        .isEqualTo(CHARACTER);
  }

  @Test
  public void booleanReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityBooleanMethod(BOOLEAN))
        .isEqualTo(BOOLEAN);
  }

  @Test
  public void boxedBooleanReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityBooleanMethod(BOOLEAN_BOXED))
        .isEqualTo(BOOLEAN_BOXED);
  }

  @Test
  public void parcelableReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityParcelableMethod(PARCELABLE))
        .isEqualTo(PARCELABLE);
  }

  @Test
  public void serializableReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identitySerializableObjectMethod(SERIALIZABLE))
        .isEqualTo(SERIALIZABLE);
  }

  @Test
  public void parcelableWrapperOfParcelableTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityParcelableWrapperOfParcelableMethod(listOfParcelable))
        .isEqualTo(listOfParcelable);
  }

  @Test
  public void parcelableWrapperOfSerializableTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityParcelableWrapperOfSerializableMethod(listOfSerializable))
        .isEqualTo(listOfSerializable);
  }

  @Test
  public void parcelableWrapperOfParcelableWrapperTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityParcelableWrapperOfParcelableWrapperMethod(listOfListOfString))
        .isEqualTo(listOfListOfString);
  }

  @Test
  public void listReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider.provide(context, testProfileConnector).identityListMethod(listOfString))
        .isEqualTo(listOfString);
  }

  @Test
  public void mapReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMapMethod(IMMUTABLE_MAP_STRING_TO_STRING))
        .isEqualTo(IMMUTABLE_MAP_STRING_TO_STRING);
  }

  @Test
  public void immutableMapReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityImmutableMapMethod(IMMUTABLE_MAP_STRING_TO_STRING))
        .isEqualTo(IMMUTABLE_MAP_STRING_TO_STRING);
  }

  @Test
  public void setReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identitySetMethod(setOfString))
        .isEqualTo(setOfString);
  }

  @Test
  public void collectionReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityCollectionMethod(collectionOfString))
        .containsExactlyElementsIn(collectionOfString);
  }

  @Test
  public void arrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityStringArrayMethod(arrayOfString))
        .asList()
        .containsExactlyElementsIn(arrayOfString);
  }

  @Test
  public void collectionOfArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    TestCrossProfileType_SingleSenderCanThrow sender =
        senderProvider.provide(context, testProfileConnector);

    List<String[]> originalAsList = new ArrayList<>(collectionOfStringArray);
    List<String[]> resultAsList =
        new ArrayList<>(sender.identityCollectionOfStringArrayMethod(collectionOfStringArray));

    assertThat(sender.identityCollectionOfStringArrayMethod(collectionOfStringArray))
        .hasSize(collectionOfStringArray.size());
    for (int i = 0; i < collectionOfStringArray.size(); i++) {
      assertThat(resultAsList.get(i)).asList().containsExactlyElementsIn(originalAsList.get(i));
    }
  }

  @Test
  public void parcelableArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityParcelableObjectArrayMethod(arrayOfParcelable))
        .asList()
        .containsExactlyElementsIn(arrayOfParcelable);
  }

  @Test
  public void serializableArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identitySerializableObjectArrayMethod(arrayOfSerializable))
        .asList()
        .containsExactlyElementsIn(arrayOfSerializable);
  }

  @Test
  public void collectionOfParcelableArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    TestCrossProfileType_SingleSenderCanThrow sender =
        senderProvider.provide(context, testProfileConnector);

    List<ParcelableObject[]> originalAsList = new ArrayList<>(collectionOfParcelableArray);
    List<ParcelableObject[]> resultAsList =
        new ArrayList<>(
            sender.identityCollectionOfParcelableObjectArrayMethod(collectionOfParcelableArray));

    assertThat(resultAsList).hasSize(collectionOfParcelableArray.size());
    for (int i = 0; i < collectionOfParcelableArray.size(); i++) {
      assertThat(resultAsList.get(i)).asList().containsExactlyElementsIn(originalAsList.get(i));
    }
  }

  @Test
  public void collectionOfSerializableArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    TestCrossProfileType_SingleSenderCanThrow sender =
        senderProvider.provide(context, testProfileConnector);

    List<SerializableObject[]> originalAsList = new ArrayList<>(collectionOfSerializableArray);
    List<SerializableObject[]> resultAsList =
        new ArrayList<>(
            sender.identityCollectionOfSerializableObjectArrayMethod(
                collectionOfSerializableArray));

    assertThat(resultAsList).hasSize(collectionOfSerializableArray.size());
    for (int i = 0; i < collectionOfSerializableArray.size(); i++) {
      assertThat(resultAsList.get(i)).asList().containsExactlyElementsIn(originalAsList.get(i));
    }
  }

  @Test
  public void pairReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityPairMethod(pair))
        .isEqualTo(pair);
  }

  @Test
  public void optionalReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityGuavaOptionalMethod(GUAVA_OPTIONAL))
        .isEqualTo(GUAVA_OPTIONAL);
  }

  // TODO: Disabled because use of Optional fails lint check. Re-enable when this is disabled.
  // @Test
  // public void optionalReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
  //   assertThat(senderProvider.provide(context,
  // testProfileConnector).identityOptionalMethod(OPTIONAL_OF_STRING)).isEqualTo(OPTIONAL_OF_STRING);
  // }

  @Test
  public void voidObjectReturnType_works() throws UnavailableProfileException {
    TestCrossProfileType.voidMethodCalls = 0;

    assertThat(senderProvider.provide(context, testProfileConnector).identityVoidMethod())
        .isEqualTo(null);
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void methodWhichReturnsNull_works() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).getNull()).isNull();
  }

  @Test
  public void methodWhichReturnsNullCollection_works() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).getNullCollection()).isNull();
  }

  @Test
  public void methodWhichReturnsNullList_works() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).getNullList()).isNull();
  }

  @Test
  public void methodWhichReturnsNullMap_works() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).getNullMap()).isNull();
  }

  // @Test
  // public void methodWhichReturnsNullOptional_works() throws UnavailableProfileException {
  //   assertThat(senderProvider.provide(context, testProfileConnector).getNullOptional()).isNull();
  // }

  @Test
  public void methodWhichReturnsNullSet_works() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).getNullSet()).isNull();
  }

  // @Test
  // public void methodWhichReturnsNullProto_works() throws UnavailableProfileException {
  //   assertThat(senderProvider.provide(context, testProfileConnector).getNullProto()).isNull();
  // }

  @Test
  public void emptyArray_works() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityStringArrayMethod(emptyStringArray))
        .asList()
        .containsExactlyElementsIn(emptyStringArray);
  }

  @Test
  public void nullArray_works() throws UnavailableProfileException {
    assertThat(
            senderProvider.provide(context, testProfileConnector).identityStringArrayMethod(null))
        .isNull();
  }

  @Test
  public void customParcelableWrapperDefinedOnTypeReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityCustomWrapper2Method(CUSTOM_WRAPPER2))
        .isEqualTo(CUSTOM_WRAPPER2);
  }

  @Test
  public void customParcelableWrapperReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityCustomWrapperMethod(CUSTOM_WRAPPER))
        .isEqualTo(CUSTOM_WRAPPER);
  }

  @Test
  public void customParcelableWrapperFutureReturnType_works()
      throws ExecutionException, InterruptedException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .listenableFutureIdentityCustomWrapperMethod(CUSTOM_WRAPPER)
                .get())
        .isEqualTo(CUSTOM_WRAPPER);
  }

  @Test
  public void customParcelableWrapperAsyncMethod_works() {
    TestCustomWrapperCallbackListenerImpl callbackListener =
        new TestCustomWrapperCallbackListenerImpl();
    TestExceptionCallbackListener exceptionListener = new TestExceptionCallbackListener();

    senderProvider
        .provide(context, testProfileConnector)
        .asyncIdentityCustomWrapperMethod(CUSTOM_WRAPPER, callbackListener, exceptionListener);

    assertThat(callbackListener.customWrapperCallbackValue).isEqualTo(CUSTOM_WRAPPER);
  }

  @Test
  public void customFutureWrapper_works() {
    SimpleFuture<String> future =
        senderProvider
            .provide(context, testProfileConnector)
            .simpleFutureIdentityStringMethodWithNonBlockingDelay(STRING, /* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(future.get()).isEqualTo(STRING);
  }

  @Test
  public void parcelableWrapperWithoutGenericReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityStringWrapperMethod(STRING_WRAPPER))
        .isEqualTo(STRING_WRAPPER);
  }

  @Test
  public void bitmapReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    Bitmap returnBitmap =
        senderProvider.provide(context, testProfileConnector).identityBitmapMethod(bitmap);

    assertThat(returnBitmap.getConfig()).isEqualTo(bitmap.getConfig());
    assertThat(returnBitmap.getWidth()).isEqualTo(bitmap.getWidth());
    assertThat(returnBitmap.getHeight()).isEqualTo(bitmap.getHeight());
    assertThat(getBitmapPixels(returnBitmap)).isEqualTo(BITMAP_PIXELS);
  }

  private static int[] getBitmapPixels(Bitmap bitmap) {
    int[] pixels = new int[bitmap.getHeight() * bitmap.getWidth()];
    bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    return pixels;
  }

  @Test
  public void nullBitmap_works() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityBitmapMethod(null))
        .isNull();
  }

  @Test
  public void contextArgument_works() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).isContextArgumentPassed())
        .isTrue();
  }

  @Test
  public void parcelableArgumentAndReturnType_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector)
        .identityParcelableMethod((Parcelable) PARCELABLE)).isEqualTo(PARCELABLE);
  }

  @Test
  public void parcelableContainingBinderArgumentAndReturnType_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityParcelableMethod(parcelableContainingBinder))
        .isEqualTo(parcelableContainingBinder);
  }

  @Test
  public void parcelableContainingBinderAsyncMethod_works() {
    TestParcelableCallbackListenerImpl callbackListener = new TestParcelableCallbackListenerImpl();
    TestExceptionCallbackListener exceptionListener = new TestExceptionCallbackListener();

    senderProvider
        .provide(context, testProfileConnector)
        .asyncIdentityParcelableMethod(
            parcelableContainingBinder, callbackListener, exceptionListener);

    assertThat(callbackListener.parcelableCallbackValue).isEqualTo(parcelableContainingBinder);
  }

  @Test
  public void futureParcelableContainingBinder_works() throws Exception {
    ListenableFuture<Parcelable> future =
        senderProvider
            .provide(context, testProfileConnector)
            .futureIdentityParcelableMethod(parcelableContainingBinder);

    assertThat(future.get()).isEqualTo(parcelableContainingBinder);
  }

  @Test
  public void charSequenceReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector)
                   .identityCharSequenceMethod(CHAR_SEQUENCE).toString())
        .isEqualTo(CHAR_SEQUENCE.toString());
  }

  @Test
  public void floatArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityFloatArrayMethod(FLOAT_ARRAY))
        .isEqualTo(FLOAT_ARRAY);
  }

  @Test
  public void multidimensionalFloatArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMultidimensionalFloatArrayMethod(MULTIDIMENSIONAL_FLOAT_ARRAY))
        .isEqualTo(MULTIDIMENSIONAL_FLOAT_ARRAY);
  }

  @Test
  public void byteArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityByteArrayMethod(BYTE_ARRAY))
        .isEqualTo(BYTE_ARRAY);
  }

  @Test
  public void multidimensionalByteArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMultidimensionalByteArrayMethod(MULTIDIMENSIONAL_BYTE_ARRAY))
        .isEqualTo(MULTIDIMENSIONAL_BYTE_ARRAY);
  }

  @Test
  public void shortArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityShortArrayMethod(SHORT_ARRAY))
        .isEqualTo(SHORT_ARRAY);
  }

  @Test
  public void multidimensionalShortArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMultidimensionalShortArrayMethod(MULTIDIMENSIONAL_SHORT_ARRAY))
        .isEqualTo(MULTIDIMENSIONAL_SHORT_ARRAY);
  }

  @Test
  public void intArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityIntArrayMethod(INT_ARRAY))
        .isEqualTo(INT_ARRAY);
  }

  @Test
  public void multidimensionalIntArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMultidimensionalIntArrayMethod(MULTIDIMENSIONAL_INT_ARRAY))
        .isEqualTo(MULTIDIMENSIONAL_INT_ARRAY);
  }

  @Test
  public void longArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityLongArrayMethod(LONG_ARRAY))
        .isEqualTo(LONG_ARRAY);
  }

  @Test
  public void multidimensionalLongArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMultidimensionalLongArrayMethod(MULTIDIMENSIONAL_LONG_ARRAY))
        .isEqualTo(MULTIDIMENSIONAL_LONG_ARRAY);
  }

  @Test
  public void doubleArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityDoubleArrayMethod(DOUBLE_ARRAY))
        .isEqualTo(DOUBLE_ARRAY);
  }

  @Test
  public void multidimensionalDoubleArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMultidimensionalDoubleArrayMethod(MULTIDIMENSIONAL_DOUBLE_ARRAY))
        .isEqualTo(MULTIDIMENSIONAL_DOUBLE_ARRAY);
  }

  @Test
  public void charArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityCharArrayMethod(CHAR_ARRAY))
        .isEqualTo(CHAR_ARRAY);
  }

  @Test
  public void multidimensionalCharArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMultidimensionalCharArrayMethod(MULTIDIMENSIONAL_CHAR_ARRAY))
        .isEqualTo(MULTIDIMENSIONAL_CHAR_ARRAY);
  }

  @Test
  public void booleanArrayReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    assertThat(senderProvider.provide(context, testProfileConnector).identityBooleanArrayMethod(BOOLEAN_ARRAY))
        .isEqualTo(BOOLEAN_ARRAY);
  }

  @Test
  public void multidimensionalBooleanArrayReturnTypeAndArgument_bothWork()
      throws UnavailableProfileException {
    assertThat(
            senderProvider
                .provide(context, testProfileConnector)
                .identityMultidimensionalBooleanArrayMethod(MULTIDIMENSIONAL_BOOLEAN_ARRAY))
        .isEqualTo(MULTIDIMENSIONAL_BOOLEAN_ARRAY);
  }

  @Test
  public void drawableReturnTypeAndArgument_bothWork() throws UnavailableProfileException {
    Drawable returnDrawable =
        senderProvider.provide(context, testProfileConnector).identityDrawableMethod(drawable);

    assertThat(returnDrawable.getIntrinsicHeight()).isEqualTo(drawable.getIntrinsicHeight());
    assertThat(returnDrawable.getIntrinsicWidth()).isEqualTo(drawable.getIntrinsicWidth());
    assertThat(getDrawablePixels(returnDrawable)).isEqualTo(BITMAP_PIXELS);
  }

  private static int[] getDrawablePixels(Drawable drawable) {
    Bitmap bitmap = Bitmap.createBitmap(
        drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
    drawable.draw(new Canvas(bitmap));

    int[] pixels = new int[bitmap.getHeight() * bitmap.getWidth()];
    bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    return pixels;
  }
}

