/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.internal.util;

import static org.junit.Assert.assertThrows;

import android.annotation.AppIdInt;
import android.annotation.ColorInt;
import android.annotation.ColorLong;
import android.annotation.FloatRange;
import android.annotation.IntRange;
import android.annotation.Size;
import android.annotation.UserIdInt;
import android.os.UserHandle;

import org.junit.Test;

public final class AnnotationValidationsUnitTest {

    @Test
    public void testValidateUserIdIntValid() {
        AnnotationValidations.validate(UserIdInt.class, null, UserHandle.USER_SYSTEM);
    }

    @Test
    public void testValidateUserIdIntValidUserNull() {
        AnnotationValidations.validate(UserIdInt.class, null, UserHandle.USER_NULL);
    }


    @Test
    public void testValidateUserIdIntInvalidTooSmall() {
        assertThrows(IllegalStateException.class,
                () ->  AnnotationValidations.validate(UserIdInt.class, null, -10));
    }

    @Test
    public void testValidateUserIdIntInvalidTooLarge() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(UserIdInt.class, null, Integer.MAX_VALUE));
    }

    @Test
    public void testValidateAppIdIntValid() {
        AnnotationValidations.validate(AppIdInt.class, null, 1);
    }

    @Test
    public void testValidateAppIdIntInvalidTooLarge() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(AppIdInt.class, null,
                        UserHandle.PER_USER_RANGE + 1));
    }

    @Test
    public void testValidateAppIdIntInvalidNegative() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(AppIdInt.class, null, -1));
    }

    @Test
    public void testValidateIntRanagValid() {
        AnnotationValidations.validate(IntRange.class, null, 1, "from", 0, "to", 2);
    }

    @Test
    public void testValidateIntRangeInvalidTooSmall() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(IntRange.class, null, -1, "from", 0, "to", 2));
    }

    @Test
    public void testValidateIntRangeInvalidTooLarge() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(IntRange.class, null, 3, "from", 0, "to", 2));
    }

    @Test
    public void testValidateIntRangeLongValid() {
        AnnotationValidations.validate(IntRange.class, null, (long) 1, "from", 0, "to", 2);
    }

    @Test
    public void testValidateIntRangeLongInvalidTooSmall() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(IntRange.class, null, (long) -1, "from", 0,
                        "to", 2));
    }

    @Test
    public void testValidateIntRangeLongInvalidTooLarge() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(IntRange.class, null, (long) 3, "from", 0,
                        "to", 2));
    }

    @Test
    public void testValidateFloatRangeValid() {
        AnnotationValidations.validate(FloatRange.class, null, 0.5f, "from", 0f, "to", 1f);
    }

    @Test
    public void testValidateFloatRangeInvalidTooSmall() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(FloatRange.class, null, -0.5f, "from", 0f,
                        "to", 1f));
    }

    @Test
    public void testValidateFloatRangeInvalidTooLarge() {
        assertThrows(IllegalStateException.class,
                () ->  AnnotationValidations.validate(FloatRange.class, null, 1.5f, "from", 0f,
                        "to", 1f));
    }

    @Test
    public void testValidateSizeValid() {
        AnnotationValidations.validate(Size.class, null, 1, "min", 0, "max", 2);
    }

    @Test
    public void testValidateSizeValidExactValue() {
        AnnotationValidations.validate(Size.class, null, 1, "value", 1, "", 0);
    }

    @Test
    public void testValidateSizeValidMultiple() {
        AnnotationValidations.validate(Size.class, null, 4, "multiple", 2, "", 0);
    }

    @Test
    public void testValidateSizeInvalidTooSmall() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(Size.class, null, -1, "min", 0, "max", 2));
    }

    @Test
    public void testValidateSizeInvalidTooLarge() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(Size.class, null, 3, "min", 0, "max", 2));
    }

    @Test
    public void testValidateSizeInvalidExactValue() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(Size.class, null, 3, "value", 1, "", 0));
    }

    @Test
    public void testValidateSizeInvalidMultiple() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(Size.class, null, 3, "multiple", 2, "", 0));
    }

    @Test
    public void testValidateColorIntValid() {
        AnnotationValidations.validate(ColorInt.class, null, 1);
    }

    @Test
    public void testValidateColorIntInvalid() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(ColorInt.class, null, -1));
    }

    @Test
    public void testValidateLongValid() {
        AnnotationValidations.validate(ColorLong.class, null, 1L);
    }

    @Test
    public void testValidateLongInvalid() {
        assertThrows(IllegalStateException.class,
                () -> AnnotationValidations.validate(ColorLong.class, null, -1L));
    }
}
