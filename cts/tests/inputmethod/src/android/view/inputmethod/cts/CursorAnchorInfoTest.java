/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static android.view.inputmethod.CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION;
import static android.view.inputmethod.CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION;
import static android.view.inputmethod.CursorAnchorInfo.FLAG_IS_RTL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.text.LineBreakConfig;
import android.os.LocaleList;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.TextUtils;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.CursorAnchorInfo.Builder;
import android.view.inputmethod.EditorBoundsInfo;
import android.view.inputmethod.TextAppearanceInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class CursorAnchorInfoTest {
    private static final float EPSILON = 0.0000001f;

    private static final RectF[] MANY_BOUNDS = new RectF[] {
            new RectF(101.0f, 201.0f, 301.0f, 401.0f),
            new RectF(102.0f, 202.0f, 302.0f, 402.0f),
            new RectF(103.0f, 203.0f, 303.0f, 403.0f),
            new RectF(104.0f, 204.0f, 304.0f, 404.0f),
            new RectF(105.0f, 205.0f, 305.0f, 405.0f),
            new RectF(106.0f, 206.0f, 306.0f, 406.0f),
            new RectF(107.0f, 207.0f, 307.0f, 407.0f),
            new RectF(108.0f, 208.0f, 308.0f, 408.0f),
            new RectF(109.0f, 209.0f, 309.0f, 409.0f),
            new RectF(110.0f, 210.0f, 310.0f, 410.0f),
            new RectF(111.0f, 211.0f, 311.0f, 411.0f),
            new RectF(112.0f, 212.0f, 312.0f, 412.0f),
            new RectF(113.0f, 213.0f, 313.0f, 413.0f),
            new RectF(114.0f, 214.0f, 314.0f, 414.0f),
            new RectF(115.0f, 215.0f, 315.0f, 415.0f),
            new RectF(116.0f, 216.0f, 316.0f, 416.0f),
            new RectF(117.0f, 217.0f, 317.0f, 417.0f),
            new RectF(118.0f, 218.0f, 318.0f, 418.0f),
            new RectF(119.0f, 219.0f, 319.0f, 419.0f),
    };
    private static final int[] MANY_FLAGS_ARRAY = new int[] {
        FLAG_HAS_INVISIBLE_REGION,
        FLAG_HAS_INVISIBLE_REGION | FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_VISIBLE_REGION | FLAG_IS_RTL,
        FLAG_HAS_INVISIBLE_REGION | FLAG_HAS_VISIBLE_REGION | FLAG_IS_RTL,
        FLAG_HAS_INVISIBLE_REGION | FLAG_IS_RTL,
        FLAG_HAS_VISIBLE_REGION | FLAG_IS_RTL,
        FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_VISIBLE_REGION | FLAG_IS_RTL,
        FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_VISIBLE_REGION | FLAG_IS_RTL,
        FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_VISIBLE_REGION | FLAG_IS_RTL,
        FLAG_HAS_VISIBLE_REGION,
        FLAG_HAS_INVISIBLE_REGION,
        FLAG_HAS_INVISIBLE_REGION | FLAG_IS_RTL,
    };

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Test
    @ApiTest(
            apis = {
                    "android.view.inputmethod.CursorAnchorInfo#getComposingText",
                    "android.view.inputmethod.CursorAnchorInfo#getEditorBoundsInfo",
                    "android.view.inputmethod.CursorAnchorInfo#getInsertionMarkerFlags",
                    "android.view.inputmethod.CursorAnchorInfo#getInsertionMarkerHorizontal",
                    "android.view.inputmethod.CursorAnchorInfo#getInsertionMarkerTop",
                    "android.view.inputmethod.CursorAnchorInfo#getInsertionMarkerBaseline",
                    "android.view.inputmethod.CursorAnchorInfo#getInsertionMarkerBottom",
                    "android.view.inputmethod.CursorAnchorInfo#getSelectionStart",
                    "android.view.inputmethod.CursorAnchorInfo#getSelectionEnd",
                    "android.view.inputmethod.CursorAnchorInfo#getMatrix",
                    "android.view.inputmethod.CursorAnchorInfo#getVisibleLineBounds",
                    "android.view.inputmethod.CursorAnchorInfo#getTextAppearanceInfo",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#build",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setComposingText",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setEditorBoundsInfo",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setMatrix",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setInsertionMarkerLocation",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setSelectinRange",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setVisibleLineBounds",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setTextAppearanceInfo",
            }
    )
    public void testBuilder() {
        final int selectionStart = 30;
        final int selectionEnd = 40;
        final int composingTextStart = 32;
        final String composingText = "test";
        final int insertionMarkerFlags =
                FLAG_HAS_VISIBLE_REGION | FLAG_HAS_INVISIBLE_REGION | FLAG_IS_RTL;
        final float insertionMarkerHorizontal = 10.5f;
        final float insertionMarkerTop = 100.1f;
        final float insertionMarkerBaseline = 110.4f;
        final float insertionMarkerBottom = 111.0f;

        Matrix transformMatrix = new Matrix();
        transformMatrix.setScale(10.0f, 20.0f);

        final EditorBoundsInfo boundsInfo =
                new EditorBoundsInfo.Builder().setEditorBounds(MANY_BOUNDS[0])
                        .setHandwritingBounds(MANY_BOUNDS[1]).build();

        final TextAppearanceInfo textAppearanceInfo = createTextAppearanceInfoBuilder().build();

        final Builder builder = new Builder();
        builder.setSelectionRange(selectionStart, selectionEnd)
                .setComposingText(composingTextStart, composingText)
                .setInsertionMarkerLocation(insertionMarkerHorizontal, insertionMarkerTop,
                        insertionMarkerBaseline, insertionMarkerBottom, insertionMarkerFlags)
                .setMatrix(transformMatrix)
                .setEditorBoundsInfo(boundsInfo)
                .setTextAppearanceInfo(textAppearanceInfo);

        for (int i = 0; i < MANY_BOUNDS.length; i++) {
            final RectF bounds = MANY_BOUNDS[i];
            final int flags = MANY_FLAGS_ARRAY[i];
            builder.addCharacterBounds(i, bounds.left, bounds.top, bounds.right, bounds.bottom,
                    flags);
            builder.addVisibleLineBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
        }

        final CursorAnchorInfo info = builder.build();
        assertEquals(selectionStart, info.getSelectionStart());
        assertEquals(selectionEnd, info.getSelectionEnd());
        assertEquals(composingTextStart, info.getComposingTextStart());
        assertTrue(TextUtils.equals(composingText, info.getComposingText()));
        assertEquals(insertionMarkerFlags, info.getInsertionMarkerFlags());
        assertEquals(insertionMarkerHorizontal, info.getInsertionMarkerHorizontal(), EPSILON);
        assertEquals(insertionMarkerTop, info.getInsertionMarkerTop(), EPSILON);
        assertEquals(insertionMarkerBaseline, info.getInsertionMarkerBaseline(), EPSILON);
        assertEquals(insertionMarkerBottom, info.getInsertionMarkerBottom(), EPSILON);
        assertEquals(transformMatrix, info.getMatrix());
        assertEquals(boundsInfo, info.getEditorBoundsInfo());
        assertEquals(Arrays.asList(MANY_BOUNDS), info.getVisibleLineBounds());
        assertEquals(MANY_BOUNDS[0],
                info.getEditorBoundsInfo().getEditorBounds());
        assertEquals(MANY_BOUNDS[1],
                info.getEditorBoundsInfo().getHandwritingBounds());
        for (int i = 0; i < MANY_BOUNDS.length; i++) {
            final RectF expectedBounds = MANY_BOUNDS[i];
            assertEquals(expectedBounds, info.getCharacterBounds(i));
        }
        assertNull(info.getCharacterBounds(-1));
        assertNull(info.getCharacterBounds(MANY_BOUNDS.length + 1));
        for (int i = 0; i < MANY_FLAGS_ARRAY.length; i++) {
            final int expectedFlags = MANY_FLAGS_ARRAY[i];
            assertEquals(expectedFlags, info.getCharacterBoundsFlags(i));
        }
        assertEquals(0, info.getCharacterBoundsFlags(-1));
        assertEquals(0, info.getCharacterBoundsFlags(MANY_BOUNDS.length + 1));
        assertEquals(textAppearanceInfo, info.getTextAppearanceInfo());
        assertTextAppearanceInfoContentsEqual(info.getTextAppearanceInfo());

        // Make sure that the builder can reproduce the same object.
        final CursorAnchorInfo info2 = builder.build();
        assertEquals(selectionStart, info2.getSelectionStart());
        assertEquals(selectionEnd, info2.getSelectionEnd());
        assertEquals(composingTextStart, info2.getComposingTextStart());
        assertTrue(TextUtils.equals(composingText, info2.getComposingText()));
        assertEquals(insertionMarkerFlags, info2.getInsertionMarkerFlags());
        assertEquals(insertionMarkerHorizontal, info2.getInsertionMarkerHorizontal(), EPSILON);
        assertEquals(insertionMarkerTop, info2.getInsertionMarkerTop(), EPSILON);
        assertEquals(insertionMarkerBaseline, info2.getInsertionMarkerBaseline(), EPSILON);
        assertEquals(insertionMarkerBottom, info2.getInsertionMarkerBottom(), EPSILON);
        assertEquals(boundsInfo, info2.getEditorBoundsInfo());
        assertEquals(Arrays.asList(MANY_BOUNDS), info2.getVisibleLineBounds());
        assertEquals(transformMatrix, info2.getMatrix());
        for (int i = 0; i < MANY_BOUNDS.length; i++) {
            final RectF expectedBounds = MANY_BOUNDS[i];
            assertEquals(expectedBounds, info2.getCharacterBounds(i));
        }
        assertNull(info2.getCharacterBounds(-1));
        assertNull(info2.getCharacterBounds(MANY_BOUNDS.length + 1));
        for (int i = 0; i < MANY_FLAGS_ARRAY.length; i++) {
            final int expectedFlags = MANY_FLAGS_ARRAY[i];
            assertEquals(expectedFlags, info2.getCharacterBoundsFlags(i));
        }
        assertEquals(0, info2.getCharacterBoundsFlags(-1));
        assertEquals(0, info2.getCharacterBoundsFlags(MANY_BOUNDS.length + 1));
        assertEquals(textAppearanceInfo, info2.getTextAppearanceInfo());
        assertTextAppearanceInfoContentsEqual(info2.getTextAppearanceInfo());
        assertEquals(info, info2);
        assertEquals(info.hashCode(), info2.hashCode());

        // Make sure that object can be marshaled via Parcel.
        final CursorAnchorInfo info3 = cloneViaParcel(info2);
        assertEquals(selectionStart, info3.getSelectionStart());
        assertEquals(selectionEnd, info3.getSelectionEnd());
        assertEquals(composingTextStart, info3.getComposingTextStart());
        assertTrue(TextUtils.equals(composingText, info3.getComposingText()));
        assertEquals(insertionMarkerFlags, info3.getInsertionMarkerFlags());
        assertEquals(insertionMarkerHorizontal, info3.getInsertionMarkerHorizontal(), EPSILON);
        assertEquals(insertionMarkerTop, info3.getInsertionMarkerTop(), EPSILON);
        assertEquals(insertionMarkerBaseline, info3.getInsertionMarkerBaseline(), EPSILON);
        assertEquals(insertionMarkerBottom, info3.getInsertionMarkerBottom(), EPSILON);
        assertEquals(boundsInfo, info3.getEditorBoundsInfo());
        assertEquals(Arrays.asList(MANY_BOUNDS), info3.getVisibleLineBounds());
        assertEquals(transformMatrix, info3.getMatrix());
        for (int i = 0; i < MANY_BOUNDS.length; i++) {
            final RectF expectedBounds = MANY_BOUNDS[i];
            assertEquals(expectedBounds, info3.getCharacterBounds(i));
        }
        assertNull(info3.getCharacterBounds(-1));
        assertNull(info3.getCharacterBounds(MANY_BOUNDS.length + 1));
        for (int i = 0; i < MANY_FLAGS_ARRAY.length; i++) {
            final int expectedFlags = MANY_FLAGS_ARRAY[i];
            assertEquals(expectedFlags, info3.getCharacterBoundsFlags(i));
        }
        assertEquals(0, info3.getCharacterBoundsFlags(-1));
        assertEquals(0, info3.getCharacterBoundsFlags(MANY_BOUNDS.length + 1));
        assertEquals(textAppearanceInfo, info3.getTextAppearanceInfo());
        assertTextAppearanceInfoContentsEqual(info3.getTextAppearanceInfo());
        assertEquals(info.hashCode(), info3.hashCode());

        builder.reset();
        final CursorAnchorInfo uninitializedInfo = builder.build();
        assertEquals(-1, uninitializedInfo.getSelectionStart());
        assertEquals(-1, uninitializedInfo.getSelectionEnd());
        assertEquals(-1, uninitializedInfo.getComposingTextStart());
        assertNull(uninitializedInfo.getComposingText());
        assertEquals(0, uninitializedInfo.getInsertionMarkerFlags());
        assertEquals(Float.NaN, uninitializedInfo.getInsertionMarkerHorizontal(), EPSILON);
        assertEquals(Float.NaN, uninitializedInfo.getInsertionMarkerTop(), EPSILON);
        assertEquals(Float.NaN, uninitializedInfo.getInsertionMarkerBaseline(), EPSILON);
        assertEquals(Float.NaN, uninitializedInfo.getInsertionMarkerBottom(), EPSILON);
        assertEquals(null, uninitializedInfo.getEditorBoundsInfo());
        assertEquals(new ArrayList<>(), uninitializedInfo.getVisibleLineBounds());
        assertEquals(new Matrix(), uninitializedInfo.getMatrix());
        assertEquals(null, uninitializedInfo.getTextAppearanceInfo());
    }

    @Test
    @ApiTest(
            apis = {
                    "android.view.inputmethod.CursorAnchorInfo.Builder#build",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setComposingText",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setMatrix",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setInsertionMarkerLocation",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setSelectinRange",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setVisibleLineBounds",
                    "android.view.inputmethod.CursorAnchorInfo.Builder#setTextAppearanceInfo",
            }
    )
    public void testEquality() {
        final Matrix matrix1 = new Matrix();
        matrix1.setTranslate(10.0f, 20.0f);
        final Matrix matrix2 = new Matrix();
        matrix2.setTranslate(110.0f, 120.0f);
        final Matrix nanMatrix = new Matrix();
        nanMatrix.setValues(new float[]{
                Float.NaN, Float.NaN, Float.NaN,
                Float.NaN, Float.NaN, Float.NaN,
                Float.NaN, Float.NaN, Float.NaN});
        final int selectionStart1 = 2;
        final int selectionEnd1 = 7;
        final String composingText1 = "0123456789";
        final int composingTextStart1 = 0;
        final int insertionMarkerFlags1 = FLAG_HAS_VISIBLE_REGION;
        final float insertionMarkerHorizontal1 = 10.5f;
        final float insertionMarkerTop1 = 100.1f;
        final float insertionMarkerBaseline1 = 110.4f;
        final float insertionMarkerBottom1 = 111.0f;
        final int selectionStart2 = 4;
        final int selectionEnd2 = 8;
        final String composingText2 = "9876543210";
        final int composingTextStart2 = 3;
        final int insertionMarkerFlags2 =
                FLAG_HAS_VISIBLE_REGION | FLAG_HAS_INVISIBLE_REGION | FLAG_IS_RTL;
        final float insertionMarkerHorizontal2 = 14.5f;
        final float insertionMarkerTop2 = 200.1f;
        final float insertionMarkerBaseline2 = 210.4f;
        final float insertionMarkerBottom2 = 211.0f;
        final List<RectF> visibleLineBounds1 = Arrays.asList(MANY_BOUNDS[0], MANY_BOUNDS[1]);
        final List<RectF> visibleLineBounds2 = Arrays.asList(MANY_BOUNDS[2], MANY_BOUNDS[3]);

        // Default instance should be equal.
        assertEquals(new Builder().build(), new Builder().build());

        assertEquals(
                new Builder().setSelectionRange(selectionStart1, selectionEnd1).build(),
                new Builder().setSelectionRange(selectionStart1, selectionEnd1).build());
        assertNotEquals(
                new Builder().setSelectionRange(selectionStart1, selectionEnd1).build(),
                new Builder().setSelectionRange(selectionStart1, selectionEnd2).build());
        assertNotEquals(
                new Builder().setSelectionRange(selectionStart1, selectionEnd1).build(),
                new Builder().setSelectionRange(selectionStart2, selectionEnd1).build());
        assertNotEquals(
                new Builder().setSelectionRange(selectionStart1, selectionEnd1).build(),
                new Builder().setSelectionRange(selectionStart2, selectionEnd2).build());
        assertEquals(
                new Builder().setComposingText(composingTextStart1, composingText1).build(),
                new Builder().setComposingText(composingTextStart1, composingText1).build());
        assertNotEquals(
                new Builder().setComposingText(composingTextStart1, composingText1).build(),
                new Builder().setComposingText(composingTextStart2, composingText1).build());
        assertNotEquals(
                new Builder().setComposingText(composingTextStart1, composingText1).build(),
                new Builder().setComposingText(composingTextStart1, composingText2).build());
        assertNotEquals(
                new Builder().setComposingText(composingTextStart1, composingText1).build(),
                new Builder().setComposingText(composingTextStart2, composingText2).build());

        // For insertion marker locations, Float#NaN is treated as if it was a number.
        assertEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                        insertionMarkerFlags1).build());

        // Check Matrix.
        assertEquals(
                new Builder().setMatrix(matrix1).build(),
                new Builder().setMatrix(matrix1).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).build(),
                new Builder().setMatrix(matrix2).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).build(),
                new Builder().setMatrix(nanMatrix).build());
        // Unlike insertion marker locations, Float#NaN in the matrix is treated as just a NaN as
        // usual (NaN == NaN -> false).
        assertNotEquals(
                new Builder().setMatrix(nanMatrix).build(),
                new Builder().setMatrix(nanMatrix).build());

        assertEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        Float.NaN, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal2, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop2,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline2, insertionMarkerBottom1,
                        insertionMarkerFlags1).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal2, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom2,
                        insertionMarkerFlags1).build());
        assertNotEquals(
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags1).build(),
                new Builder().setMatrix(matrix1).setInsertionMarkerLocation(
                        insertionMarkerHorizontal1, insertionMarkerTop1,
                        insertionMarkerBaseline1, insertionMarkerBottom1,
                        insertionMarkerFlags2).build());
        {
            CursorAnchorInfo.Builder builder1 = new Builder().setMatrix(matrix1);
            for (RectF rectF : visibleLineBounds1) {
                builder1.addVisibleLineBounds(rectF.left, rectF.top, rectF.right, rectF.bottom);
            }

            CursorAnchorInfo.Builder builder2 = new Builder().setMatrix(matrix1);
            for (RectF rectF : visibleLineBounds1) {
                builder2.addVisibleLineBounds(rectF.left, rectF.top, rectF.right, rectF.bottom);
            }

            assertEquals(builder1.build(), builder2.build());
        }
        {
            CursorAnchorInfo.Builder builder1 = new Builder().setMatrix(matrix1);
            for (RectF rectF : visibleLineBounds1) {
                builder1.addVisibleLineBounds(rectF.left, rectF.top, rectF.right, rectF.bottom);
            }

            CursorAnchorInfo.Builder builder2 = new Builder().setMatrix(matrix1);
            for (RectF rectF : visibleLineBounds2) {
                builder2.addVisibleLineBounds(rectF.left, rectF.top, rectF.right, rectF.bottom);
            }

            assertNotEquals(builder1.build(), builder2.build());
        }
        {
            TextAppearanceInfo.Builder appearanceBuilder1 = createTextAppearanceInfoBuilder();
            TextAppearanceInfo.Builder appearanceBuilder2 = createTextAppearanceInfoBuilder();
            TextAppearanceInfo.Builder appearanceBuilder3 = createTextAppearanceInfoBuilder();
            appearanceBuilder3.setTextSize(30f);

            TextAppearanceInfo textAppearanceInfo1 = appearanceBuilder1.build();
            TextAppearanceInfo textAppearanceInfo2 = appearanceBuilder2.build();
            TextAppearanceInfo textAppearanceInfo3 = appearanceBuilder3.build();

            assertEquals(new Builder().setTextAppearanceInfo(textAppearanceInfo1).build(),
                    new Builder().setTextAppearanceInfo(textAppearanceInfo1).build());
            assertEquals(new Builder().setTextAppearanceInfo(textAppearanceInfo1).build(),
                    new Builder().setTextAppearanceInfo(textAppearanceInfo2).build());
            assertNotEquals(new Builder().setTextAppearanceInfo(textAppearanceInfo1).build(),
                    new Builder().setTextAppearanceInfo(textAppearanceInfo3).build());
        }
    }

    @Test
    public void testMatrixIsCopied() {
        final Matrix matrix1 = new Matrix();
        matrix1.setTranslate(10.0f, 20.0f);
        final Matrix matrix2 = new Matrix();
        matrix2.setTranslate(110.0f, 120.0f);
        final Matrix matrix3 = new Matrix();
        matrix3.setTranslate(210.0f, 220.0f);
        final Matrix matrix = new Matrix();
        final Builder builder = new Builder();

        matrix.set(matrix1);
        builder.setMatrix(matrix);
        matrix.postRotate(90.0f);

        final CursorAnchorInfo firstInstance = builder.build();
        assertEquals(matrix1, firstInstance.getMatrix());
        matrix.set(matrix2);
        builder.setMatrix(matrix);
        final CursorAnchorInfo secondInstance = builder.build();
        assertEquals(matrix1, firstInstance.getMatrix());
        assertEquals(matrix2, secondInstance.getMatrix());

        matrix.set(matrix3);
        assertEquals(matrix1, firstInstance.getMatrix());
        assertEquals(matrix2, secondInstance.getMatrix());
    }

    @Test
    public void testMatrixIsRequired() {
        final int selectionStart = 30;
        final int selectionEnd = 40;
        final int composingTextStart = 32;
        final String composingText = "test";
        final int insertionMarkerFlags = FLAG_HAS_VISIBLE_REGION;
        final float insertionMarkerHorizontal = 10.5f;
        final float insertionMarkerTop = 100.1f;
        final float insertionMarkerBaseline = 110.4f;
        final float insertionMarkerBottom = 111.0f;
        Matrix transformMatrix = new Matrix();
        transformMatrix.setScale(10.0f, 20.0f);

        final Builder builder = new Builder();
        // Check twice to make sure if Builder#reset() works as expected.
        for (int repeatCount = 0; repeatCount < 2; ++repeatCount) {
            builder.setSelectionRange(selectionStart, selectionEnd)
                    .setComposingText(composingTextStart, composingText);
            try {
                // Should succeed as coordinate transformation matrix is not required if no
                // positional information is specified.
                builder.build();
            } catch (IllegalArgumentException ex) {
                fail();
            }

            builder.setInsertionMarkerLocation(insertionMarkerHorizontal, insertionMarkerTop,
                    insertionMarkerBaseline, insertionMarkerBottom, insertionMarkerFlags);
            try {
                // Coordinate transformation matrix is required if no positional information is
                // specified.
                builder.build();
                fail();
            } catch (IllegalArgumentException ex) {
            }

            builder.setMatrix(transformMatrix);
            try {
                // Should succeed as coordinate transformation matrix is required.
                builder.build();
            } catch (IllegalArgumentException ex) {
                fail();
            }

            builder.reset();
        }
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.CursorAnchorInfo.Builder#setVisibleLineBounds")
    public void testMatrixIsRequiredForVisibleLineBounds() {
        final List<RectF> visibleLineBounds = Arrays.asList(MANY_BOUNDS);
        final Matrix transformMatrix = new Matrix();

        final Builder builder = new Builder();
        try {
            // Should succeed as coordinate transformation matrix is not required if no
            // positional information is specified.
            builder.build();
        } catch (IllegalArgumentException ex) {
            fail();
        }

        for (RectF rectF: visibleLineBounds) {
            builder.addVisibleLineBounds(rectF.left, rectF.top, rectF.right, rectF.bottom);
        }
        try {
            // Should fail since visible line bounds requires coordinates transformation matrix.
            builder.build();
            fail();
        } catch (IllegalArgumentException ex) {
        }

        builder.setMatrix(transformMatrix);
        try {
            // Should succeed as coordinate transformation matrix is provided.
            builder.build();
        } catch (IllegalArgumentException ex) {
            fail();
        }
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.CursorAnchorInfo.Builder#addCharacterBounds")
    public void testBuilderAddCharacterBounds() {
        // A negative index should be rejected.
        try {
            new Builder().addCharacterBounds(-1, 0.0f, 0.0f, 0.0f, 0.0f, FLAG_HAS_VISIBLE_REGION);
            fail();
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.CursorAnchorInfo.Builder#clearVisibleLineBounds")
    public void testBuilderClearVisibleLineBounds() {
        CursorAnchorInfo.Builder builder = new Builder().setMatrix(Matrix.IDENTITY_MATRIX);
        for (RectF rectF: MANY_BOUNDS) {
            builder.addVisibleLineBounds(rectF.left, rectF.top, rectF.right, rectF.bottom);
        }

        // Making sure visible line bounds are added correctly.
        assertEquals(Arrays.asList(MANY_BOUNDS), builder.build().getVisibleLineBounds());

        builder.clearVisibleLineBounds();
        // No visible line bounds is returned after clearVisibleLineBounds is called.
        assertEquals(Collections.emptyList(), builder.build().getVisibleLineBounds());

        // Add more line bounds and verify again.
        List<RectF> rectFs = Arrays.asList(MANY_BOUNDS[1], MANY_BOUNDS[0]);

        for (RectF rectF: rectFs) {
            builder.addVisibleLineBounds(rectF.left, rectF.top, rectF.right, rectF.bottom);
        }
        assertEquals(rectFs, builder.build().getVisibleLineBounds());
    }

    @Test
    @ApiTest(apis = {"android.view.inputmethod.CursorAnchorInfo#getTextAppearanceInfo",
            "android.view.inputmethod.CursorAnchorInfo.Builder#setTextAppearanceInfo"})
    public void testTextAppearanceInfoWithEmptyEditText() {
        TextAppearanceInfo textAppearanceInfo = new TextAppearanceInfo.Builder().build();
        CursorAnchorInfo.Builder builder = new Builder().setTextAppearanceInfo(textAppearanceInfo);
        CursorAnchorInfo info1 = builder.build();
        CursorAnchorInfo info2 = builder.build();
        CursorAnchorInfo info3 = cloneViaParcel(info2);
        assertEquals(textAppearanceInfo, info1.getTextAppearanceInfo());
        assertEquals(textAppearanceInfo, info2.getTextAppearanceInfo());
        assertEquals(textAppearanceInfo, info3.getTextAppearanceInfo());
    }

    private static CursorAnchorInfo cloneViaParcel(CursorAnchorInfo src) {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            src.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return new CursorAnchorInfo(parcel);
        } finally {
            if (parcel != null) {
                parcel.recycle();
            }
        }
    }

    private TextAppearanceInfo.Builder createTextAppearanceInfoBuilder() {
        TextAppearanceInfo.Builder builder = new TextAppearanceInfo.Builder()
                .setTextSize(16.5f)
                .setTextLocales(LocaleList.forLanguageTags("en,ja"))
                .setSystemFontFamilyName("sans-serif")
                .setTextFontWeight(10)
                .setTextStyle(Typeface.ITALIC)
                .setAllCaps(true)
                .setShadowDx(2.0f)
                .setShadowDy(2.0f)
                .setShadowRadius(2.0f)
                .setShadowColor(Color.GRAY)
                .setElegantTextHeight(true)
                .setFallbackLineSpacing(true)
                .setLetterSpacing(5.0f)
                .setFontFeatureSettings("smcp")
                .setFontVariationSettings("'wdth' 1.0")
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .setTextScaleX(1.5f)
                .setHighlightTextColor(Color.YELLOW)
                .setTextColor(Color.RED)
                .setHintTextColor(Color.GREEN)
                .setLinkTextColor(Color.BLUE);
        return builder;
    }

    private void assertTextAppearanceInfoContentsEqual(TextAppearanceInfo textAppearanceInfo) {
        assertEquals(textAppearanceInfo.getTextSize(), 16.5f, EPSILON);
        assertEquals(textAppearanceInfo.getTextLocales(), LocaleList.forLanguageTags("en,ja"));
        assertEquals(textAppearanceInfo.getSystemFontFamilyName(), "sans-serif");
        assertEquals(textAppearanceInfo.getTextFontWeight(), 10);
        assertEquals(textAppearanceInfo.getTextStyle(), Typeface.ITALIC);
        assertTrue(textAppearanceInfo.isAllCaps());
        assertEquals(textAppearanceInfo.getShadowRadius(), 2.0f, EPSILON);
        assertEquals(textAppearanceInfo.getShadowDx(), 2.0f, EPSILON);
        assertEquals(textAppearanceInfo.getShadowDy(), 2.0f, EPSILON);
        assertEquals(textAppearanceInfo.getShadowColor(), Color.GRAY);
        assertTrue(textAppearanceInfo.isElegantTextHeight());
        assertTrue(textAppearanceInfo.isFallbackLineSpacing());
        assertEquals(textAppearanceInfo.getLetterSpacing(), 5.0f, EPSILON);
        assertEquals(textAppearanceInfo.getFontFeatureSettings(), "smcp");
        assertEquals(textAppearanceInfo.getFontVariationSettings(), "'wdth' 1.0");
        assertEquals(textAppearanceInfo.getLineBreakStyle(),
                LineBreakConfig.LINE_BREAK_STYLE_LOOSE);
        assertEquals(textAppearanceInfo.getLineBreakWordStyle(),
                LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
        assertEquals(textAppearanceInfo.getTextScaleX(), 1.5f, EPSILON);
        assertEquals(textAppearanceInfo.getHighlightTextColor(), Color.YELLOW);
        assertEquals(textAppearanceInfo.getTextColor(), Color.RED);
        assertEquals(textAppearanceInfo.getHintTextColor(), Color.GREEN);
        assertEquals(textAppearanceInfo.getLinkTextColor(), Color.BLUE);
    }
}
