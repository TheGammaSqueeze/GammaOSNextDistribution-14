/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.media.audio.cts;

import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.audio.policy.configuration.V7_0.AudioUsage;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.NonMainlineTest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class AudioAttributesTest extends CtsAndroidTestCase {

    // -----------------------------------------------------------------
    // AUDIOATTRIBUTES TESTS:
    // ----------------------------------

    // -----------------------------------------------------------------
    // Parcelable tests
    // ----------------------------------

    // Test case 1: call describeContents(), not used yet, but needs to be exercised
    public void testParcelableDescribeContents() throws Exception {
        final AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).build();
        assertNotNull("Failure to create the AudioAttributes", aa);
        assertEquals(0, aa.describeContents());
    }

    // Test case 2: create an instance, marshall it and create a new instance,
    //      check for equality, both by comparing fields, and with the equals(Object) method
    public void testParcelableWriteToParcelCreate() throws Exception {
        final AudioAttributes srcAttr = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED).build();
        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        srcAttr.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioAttributes targetAttr = AudioAttributes.CREATOR.createFromParcel(dstParcel);

        assertEquals("Marshalled/restored usage doesn't match",
                srcAttr.getUsage(), targetAttr.getUsage());
        assertEquals("Marshalled/restored content type doesn't match",
                srcAttr.getContentType(), targetAttr.getContentType());
        assertEquals("Marshalled/restored flags don't match",
                srcAttr.getFlags(), targetAttr.getFlags());
        assertTrue("Source and target attributes are not considered equal",
                srcAttr.equals(targetAttr));
    }

    // Test case 3: verify going from AudioAttributes to stream type, with attributes built from
    //    stream type.
    public void testGetVolumeControlStreamVsLegacyStream() throws Exception {
        for (int testType : new int[] { AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_RING,
                AudioManager.STREAM_SYSTEM, AudioManager.STREAM_VOICE_CALL}) {
            final AudioAttributes aa = new AudioAttributes.Builder().setLegacyStreamType(testType)
                    .build();
            final int stream = aa.getVolumeControlStream();
            assertEquals("Volume control from attributes, stream doesn't match", testType, stream);
        }
    }

    // Test case 4: verify the Ultrasound content APIs for AudioAttributes
    public void testSetUltrasoundContentType() throws Exception {
        final AudioAttributes internalContentApiAttr = new AudioAttributes.Builder()
                .setInternalContentType(AudioAttributes.CONTENT_TYPE_ULTRASOUND)
                .build();

        assertEquals("Ultrasound by setInternalContentType doesn't match",
                internalContentApiAttr.getContentType(), AudioAttributes.CONTENT_TYPE_ULTRASOUND);
    }
    // -----------------------------------------------------------------
    // Builder tests
    // ----------------------------------
    public void testInvalidUsage() {
        assertThrows(IllegalArgumentException.class,
                () -> { new AudioAttributes.Builder()
                        .setUsage(Integer.MIN_VALUE / 2) // some invalid value
                        .build();
                });
    }

    public void testInvalidContentType() {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    new AudioAttributes.Builder()
                            .setContentType(Integer.MIN_VALUE / 2) // some invalid value
                            .build();
                } );
    }

    public void testDefaultUnknown() {
        final AudioAttributes aa = new AudioAttributes.Builder()
                .setFlags(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                .build();
        assertEquals("Unexpected default usage", AudioAttributes.USAGE_UNKNOWN, aa.getUsage());
        assertEquals("Unexpected default content type",
                AudioAttributes.CONTENT_TYPE_UNKNOWN, aa.getContentType());
    }

    // -----------------------------------------------------------------
    // System usage tests
    // ----------------------------------

    public void testSetUsage_throwsWhenPassedSystemUsage()
            throws NoSuchFieldException, IllegalAccessException {
        int emergencySystemUsage = getEmergencySystemUsage();
        AudioAttributes.Builder builder = new AudioAttributes.Builder();

        assertThrows(IllegalArgumentException.class, () -> builder.setUsage(emergencySystemUsage));
    }

    public void testSetSystemUsage_throwsWhenPassedSdkUsage() {
        InvocationTargetException e = expectThrows(InvocationTargetException.class, () -> {
            setSystemUsage(new AudioAttributes.Builder(), AudioAttributes.USAGE_MEDIA);
        });

        assertEquals(IllegalArgumentException.class, e.getTargetException().getClass());
    }

    public void testBuild_throwsWhenSettingBothSystemAndSdkUsages()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        AudioAttributes.Builder builder = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA);
        builder = setEmergencySystemUsage(builder);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    public void testGetUsage_returnsUnknownWhenSystemUsageSet()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        AudioAttributes attributes = getAudioAttributesWithEmergencySystemUsage();

        assertEquals(AudioAttributes.USAGE_UNKNOWN, attributes.getUsage());
    }

    public void testGetSystemUsage_returnsSetUsage()
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();

        assertEquals(AudioAttributes.USAGE_MEDIA, getSystemUsage(attributes));
    }

    public void testSpatializationAttr() {
        for (int virtBehavior : new int[] { AudioAttributes.SPATIALIZATION_BEHAVIOR_AUTO,
                                            AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER}) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setSpatializationBehavior(virtBehavior)
                    .build();
            assertEquals("Spatialization behavior doesn't match", virtBehavior,
                    attributes.getSpatializationBehavior());
        }

        for (boolean isVirtualized : new boolean[] { true, false }) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setIsContentSpatialized(isVirtualized)
                    .build();
            assertEquals("Is content virtualized doesn't match", isVirtualized,
                    attributes.isContentSpatialized());
        }
    }

    // -----------------------------------------------------------------
    // Capture policy tests
    // ----------------------------------
    public void testAllowedCapturePolicy() throws Exception {
        for (int setPolicy : new int[] { AudioAttributes.ALLOW_CAPTURE_BY_ALL,
                                      AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM,
                                      AudioAttributes.ALLOW_CAPTURE_BY_NONE }) {
            final AudioAttributes aa = new AudioAttributes.Builder()
                    .setAllowedCapturePolicy(setPolicy).build();
            final int getPolicy = aa.getAllowedCapturePolicy();
            assertEquals("Allowed capture policy doesn't match", setPolicy, getPolicy);
        }
    }

    public void testDefaultAllowedCapturePolicy() throws Exception {
        final AudioAttributes aa = new AudioAttributes.Builder().build();
        final int policy = aa.getAllowedCapturePolicy();
        assertEquals("Wrong default capture policy", AudioAttributes.ALLOW_CAPTURE_BY_ALL, policy);
    }

    // -----------------------------------------------------------------
    // Deprecation tests
    // ----------------------------------
    private int[] DEPRECATED_USAGES = { AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
            AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
            AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT };

    public void testDeprecationNotificationUsagesBuilder() throws Exception {
        for (int deprecatedNotifUsage : DEPRECATED_USAGES) {
            final AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(deprecatedNotifUsage)
                    .build();
            assertEquals("Deprecated notification usage value not remapped",
                    AudioAttributes.USAGE_NOTIFICATION, aa.getUsage());
        }
    }

    public void testDeprecationNotificationUsagesCopyBuilder() throws Exception {
        for (int deprecatedNotifUsage : DEPRECATED_USAGES) {
            final AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(deprecatedNotifUsage)
                    .build();
            final AudioAttributes copy = new AudioAttributes.Builder(aa).build();
            assertEquals("Deprecated notification usage value not remapped",
                    AudioAttributes.USAGE_NOTIFICATION, copy.getUsage());
        }
    }

    // -----------------------------------------------------------------
    // Regression tests
    // ----------------------------------
    // Test against regression where setLegacyStreamType() was creating a different Builder
    public void testSetLegacyStreamOnBuilder() throws Exception {
        final int stream = AudioManager.STREAM_MUSIC;
        AudioAttributes.Builder builder1 = new AudioAttributes.Builder();
        builder1.setLegacyStreamType(stream);
        AudioAttributes attr1 = builder1.build();

        AudioAttributes.Builder builder2 = new AudioAttributes.Builder().setLegacyStreamType(stream);
        AudioAttributes attr2 = builder2.build();

        assertEquals(attr1, attr2);
    }

    /**
     * Test AudioAttributes Builder error handling.
     *
     * @throws Exception
     */
    public void testAudioAttributesBuilderError() throws Exception {
        final int BIGNUM = Integer.MAX_VALUE;

        assertThrows(IllegalArgumentException.class, () -> {
            new AudioAttributes.Builder()
                    .setContentType(BIGNUM)
                    .build();
        });

        // TODO(b/207021564): This should throw IAE in AudioAttributes.Builder.
        //assertThrows(IllegalArgumentException.class, () -> {
            new AudioAttributes.Builder()
                    .setFlags(BIGNUM)
                    .build();
        //});

        // TODO(b/207016008): This should throw IAE in AudioAttributes.Builder.
        //assertThrows(IllegalArgumentException.class, () -> {
            new AudioAttributes.Builder()
                    .setLegacyStreamType(BIGNUM)
                    .build();
        //});

        assertThrows(IllegalArgumentException.class, () -> {
            new AudioAttributes.Builder()
                    .setSpatializationBehavior(BIGNUM)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new AudioAttributes.Builder()
                    .setUsage(BIGNUM)
                    .build();
        });
    }

    // -----------------------------------------------------------------
    // audio_policy_configuration.xsd converter tests
    // ----------------------------------
    public void testXsdStringToUsage_returnsCorrectUsage() {
        int usage = AudioAttributes.xsdStringToUsage(AudioUsage.AUDIO_USAGE_MEDIA.toString());

        assertEquals(AudioAttributes.USAGE_MEDIA, usage);
    }

    public void testXsdStringToUsage_withUnsupportedString_returnsUnknownUsage() {
        int usage = AudioAttributes.xsdStringToUsage("not a usage");

        assertEquals(AudioAttributes.USAGE_UNKNOWN, usage);
    }

    public void testUsageToXsdString_returnsCorrectString() {
        String xsdUsage = AudioAttributes.usageToXsdString(AudioAttributes.USAGE_MEDIA);

        assertEquals(AudioUsage.AUDIO_USAGE_MEDIA.toString(), xsdUsage);
    }

    // -------------------------------------------------------------------
    // Reflection helpers for accessing system usage methods and fields
    // -------------------------------------------------------------------
    private static AudioAttributes getAudioAttributesWithEmergencySystemUsage()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        builder = setEmergencySystemUsage(builder);
        return builder.build();
    }

    private static AudioAttributes.Builder setEmergencySystemUsage(AudioAttributes.Builder builder)
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        int emergencySystemUsage = getEmergencySystemUsage();
        return setSystemUsage(builder, emergencySystemUsage);
    }

    private static AudioAttributes.Builder setSystemUsage(AudioAttributes.Builder builder,
            int systemUsage)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method setSystemUsageMethod = AudioAttributes.Builder.class
                .getMethod("setSystemUsage", int.class);
        return (AudioAttributes.Builder) setSystemUsageMethod.invoke(builder, systemUsage);
    }

    private static int getEmergencySystemUsage()
            throws IllegalAccessException, NoSuchFieldException {
        Field emergencyField = AudioAttributes.class.getDeclaredField("USAGE_EMERGENCY");
        return emergencyField.getInt(null);
    }

    private static int getSystemUsage(AudioAttributes attributes)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getSystemUsageMethod = AudioAttributes.class.getMethod("getSystemUsage");
        return (int) getSystemUsageMethod.invoke(attributes);
    }
}
