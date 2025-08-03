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

package com.android.car.audio;

import static com.google.common.truth.Truth.assertWithMessage;

import android.media.AudioAttributes;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public final class CarAudioContextToAudioAttributeValidityTest {

    private static final String TAG =
            CarAudioContextToAudioAttributeValidityTest.class.getSimpleName();

    @Parameterized.Parameters
    public static Collection provideParams() {
        List<Field> usageFields = getAudioAttributeUsageFields();
        ArrayList<Object[]> data = new ArrayList<>(usageFields.size());

        for (Field usageField : usageFields) {
            String name;
            int value;
            try {
                name = usageField.getName();
                value = usageField.getInt(/* object= */ null);
            } catch (IllegalAccessException e) {
                Log.wtf(TAG, "Failed trying to find value for audio attribute usage "
                        + usageField.getName(), e);
                continue;
            }
            data.add(new Object[] {new AudioAttributesUsageField(name, value)});
        }

        return data;
    }

    private final AudioAttributesUsageField mAudioAttributeUsageField;

    public CarAudioContextToAudioAttributeValidityTest(AudioAttributesUsageField
            audioAttributeUsageField) {
        mAudioAttributeUsageField = audioAttributeUsageField;
    }

    @Test
    public void isValidAudioAttributeUsage_withValidAttributeUsage_succeeds() {
        boolean isValidUsage =
                CarAudioContext.isValidAudioAttributeUsage(mAudioAttributeUsageField.mValue);

        assertWithMessage("Valid result for audio attribute usage %s value %s",
                mAudioAttributeUsageField.mName, mAudioAttributeUsageField.mValue)
                .that(isValidUsage).isTrue();
    }

    @Test
    public void checkAudioAttributeUsage_validAttributeUsage_succeeds() {
        CarAudioContext.checkAudioAttributeUsage(mAudioAttributeUsageField.mValue);
    }

    private static List<Field> getAudioAttributeUsageFields() {
        Field[] audioAttributesFields = AudioAttributes.class.getDeclaredFields();
        List<Field> audioAttributesUsageFields = new ArrayList<>();

        for (Field field : audioAttributesFields) {
            if (!isAudioAttributeUsageField(field)) {
                continue;
            }
            audioAttributesUsageFields.add(field);
        }

        return audioAttributesUsageFields;
    }

    private static boolean isAudioAttributeUsageField(Field field) {
        return (field.getType() == int.class)
                && (field.getModifiers() == (Modifier.STATIC | Modifier.FINAL | Modifier.PUBLIC))
                && field.getName().startsWith("USAGE_");
    }

    private static final class AudioAttributesUsageField {

        private final String mName;
        private final int mValue;

        AudioAttributesUsageField(String name, int value) {
            mName = name;
            mValue = value;
        }
    }
}
