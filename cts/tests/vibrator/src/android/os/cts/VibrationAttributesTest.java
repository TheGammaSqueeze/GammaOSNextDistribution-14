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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.media.AudioAttributes;
import android.os.VibrationAttributes;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VibrationAttributesTest {
    private static final Map<Integer, Integer> AUDIO_TO_VIBRATION_USAGE_MAP;
    static {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(AudioAttributes.USAGE_ALARM, VibrationAttributes.USAGE_ALARM);
        map.put(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
                VibrationAttributes.USAGE_ACCESSIBILITY);
        map.put(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, VibrationAttributes.USAGE_TOUCH);
        map.put(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                VibrationAttributes.USAGE_COMMUNICATION_REQUEST);
        map.put(AudioAttributes.USAGE_ASSISTANT, VibrationAttributes.USAGE_COMMUNICATION_REQUEST);
        map.put(AudioAttributes.USAGE_GAME, VibrationAttributes.USAGE_MEDIA);
        map.put(AudioAttributes.USAGE_MEDIA, VibrationAttributes.USAGE_MEDIA);
        map.put(AudioAttributes.USAGE_NOTIFICATION, VibrationAttributes.USAGE_NOTIFICATION);
        map.put(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
                VibrationAttributes.USAGE_NOTIFICATION);
        map.put(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
                VibrationAttributes.USAGE_NOTIFICATION);
        map.put(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT,
                VibrationAttributes.USAGE_NOTIFICATION);
        map.put(AudioAttributes.USAGE_NOTIFICATION_EVENT, VibrationAttributes.USAGE_NOTIFICATION);
        map.put(AudioAttributes.USAGE_NOTIFICATION_RINGTONE, VibrationAttributes.USAGE_RINGTONE);
        map.put(AudioAttributes.USAGE_UNKNOWN, VibrationAttributes.USAGE_UNKNOWN);
        map.put(AudioAttributes.USAGE_VOICE_COMMUNICATION,
                VibrationAttributes.USAGE_COMMUNICATION_REQUEST);
        map.put(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
                VibrationAttributes.USAGE_COMMUNICATION_REQUEST);
        AUDIO_TO_VIBRATION_USAGE_MAP = map;
    }

    private static final Map<Integer, Integer> VIBRATION_TO_AUDIO_USAGE_MAP;
    static {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(VibrationAttributes.USAGE_ALARM, AudioAttributes.USAGE_ALARM);
        map.put(VibrationAttributes.USAGE_ACCESSIBILITY,
                AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY);
        map.put(VibrationAttributes.USAGE_COMMUNICATION_REQUEST,
                AudioAttributes.USAGE_VOICE_COMMUNICATION);
        map.put(VibrationAttributes.USAGE_HARDWARE_FEEDBACK, AudioAttributes.USAGE_UNKNOWN);
        map.put(VibrationAttributes.USAGE_MEDIA, AudioAttributes.USAGE_MEDIA);
        map.put(VibrationAttributes.USAGE_NOTIFICATION, AudioAttributes.USAGE_NOTIFICATION);
        map.put(VibrationAttributes.USAGE_PHYSICAL_EMULATION, AudioAttributes.USAGE_UNKNOWN);
        map.put(VibrationAttributes.USAGE_RINGTONE, AudioAttributes.USAGE_NOTIFICATION_RINGTONE);
        map.put(VibrationAttributes.USAGE_TOUCH, AudioAttributes.USAGE_ASSISTANCE_SONIFICATION);
        VIBRATION_TO_AUDIO_USAGE_MAP = map;
    }

    @Test
    public void testCreateFromVibrationAttributes() {
        VibrationAttributes tmp = VibrationAttributes.createForUsage(
                VibrationAttributes.USAGE_TOUCH);
        VibrationAttributes attr = new VibrationAttributes.Builder(tmp).build();
        assertEquals(attr.getUsage(), VibrationAttributes.USAGE_TOUCH);
        assertEquals(attr.getUsageClass(), VibrationAttributes.USAGE_CLASS_FEEDBACK);
        assertEquals(attr.getFlags(), 0);
        assertEquals(attr.getAudioUsage(), AudioAttributes.USAGE_ASSISTANCE_SONIFICATION);
    }

    @Test
    public void testCreateFromAudioAttributes() {
        AudioAttributes audioAttributes = createAudioAttributes(AudioAttributes.USAGE_ALARM);
        VibrationAttributes attr = new VibrationAttributes.Builder(audioAttributes).build();
        assertEquals(attr.getUsage(), VibrationAttributes.USAGE_ALARM);
        assertEquals(attr.getUsageClass(), VibrationAttributes.USAGE_CLASS_ALARM);
        assertEquals(attr.getFlags(), 0);
        assertEquals(attr.getAudioUsage(), AudioAttributes.USAGE_ALARM);
    }

    @Test
    public void testCreateForUsage() {
        VibrationAttributes attr = VibrationAttributes.createForUsage(
                VibrationAttributes.USAGE_RINGTONE);
        assertEquals(attr.getUsage(), VibrationAttributes.USAGE_RINGTONE);
        assertEquals(attr.getUsageClass(), VibrationAttributes.USAGE_CLASS_ALARM);
        assertEquals(attr.getFlags(), 0);
        assertEquals(attr.getAudioUsage(), AudioAttributes.USAGE_NOTIFICATION_RINGTONE);
    }

    @Test
    public void testGetAudioUsageReturnOriginalUsage() {
        VibrationAttributes attr = createForAudioUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION);
        assertEquals(attr.getUsage(), VibrationAttributes.USAGE_COMMUNICATION_REQUEST);
        assertEquals(attr.getAudioUsage(), AudioAttributes.USAGE_VOICE_COMMUNICATION);
    }

    @Test
    public void testGetAudioUsageUnknownReturnsBasedOnVibrationUsage() {
        VibrationAttributes attr = VibrationAttributes.createForUsage(
                VibrationAttributes.USAGE_NOTIFICATION);
        assertEquals(attr.getUsage(), VibrationAttributes.USAGE_NOTIFICATION);
        assertEquals(attr.getAudioUsage(), AudioAttributes.USAGE_NOTIFICATION);
    }

    @Test
    public void testAudioToVibrationUsageMapping() {
        for (Map.Entry<Integer, Integer> entry : AUDIO_TO_VIBRATION_USAGE_MAP.entrySet()) {
            assertEquals(entry.getValue().intValue(), new VibrationAttributes.Builder(
                    createForAudioUsage(entry.getKey())).build().getUsage());
        }
    }

    @Test
    public void testVibrationToAudioUsageMapping() {
        for (Map.Entry<Integer, Integer> entry : VIBRATION_TO_AUDIO_USAGE_MAP.entrySet()) {
            assertEquals(entry.getValue().intValue(),
                    new VibrationAttributes.Builder()
                            .setUsage(entry.getKey())
                            .build()
                            .getAudioUsage());
        }
    }

    @Test
    public void testEquals() {
        AudioAttributes audioAttributes = createAudioAttributes(
                AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY);
        VibrationAttributes attr = new VibrationAttributes.Builder(audioAttributes).build();
        VibrationAttributes attr2 = new VibrationAttributes.Builder(audioAttributes).build();
        assertEquals(attr, attr2);
    }

    @Test
    public void testNotEqualsDifferentAudioUsage() {
        VibrationAttributes attr = createForAudioUsage(AudioAttributes.USAGE_NOTIFICATION);
        VibrationAttributes attr2 = createForAudioUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT);
        assertEquals(attr.getUsage(), attr2.getUsage());
        assertNotEquals(attr, attr2);
    }

    @Test
    public void testNotEqualsDifferentVibrationUsage() {
        VibrationAttributes attr = VibrationAttributes.createForUsage(
                VibrationAttributes.USAGE_TOUCH);
        VibrationAttributes attr2 = VibrationAttributes.createForUsage(
                VibrationAttributes.USAGE_NOTIFICATION);
        assertNotEquals(attr, attr2);
    }

    @Test
    public void testNotEqualsDifferentFlags() {
        AudioAttributes audioAttributes = createAudioAttributes(
                AudioAttributes.USAGE_ASSISTANCE_SONIFICATION);
        VibrationAttributes attr = new VibrationAttributes.Builder(audioAttributes).build();
        VibrationAttributes attr2 = new VibrationAttributes.Builder(audioAttributes).setFlags(1, 1)
                .build();
        assertNotEquals(attr, attr2);
    }

    private static AudioAttributes createAudioAttributes(int audioUsage) {
        return new AudioAttributes.Builder().setUsage(audioUsage).build();
    }

    private static VibrationAttributes createForAudioUsage(int audioUsage) {
        return new VibrationAttributes.Builder(createAudioAttributes(audioUsage)).build();
    }
}

