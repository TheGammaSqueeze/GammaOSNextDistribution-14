/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_SPIN;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.VibrationParameter.targetAmplitude;
import static android.os.VibrationEffect.VibrationParameter.targetFrequency;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.os.VibrationEffect;
import android.os.vibrator.Flags;
import android.os.vibrator.persistence.ParsedVibration;
import android.os.vibrator.persistence.VibrationXmlParser;
import android.os.vibrator.persistence.VibrationXmlSerializer;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

/**
 * Tests for XML serialization of {@link VibrationEffect} via {@link VibrationXmlSerializer}
 * and {@link VibrationXmlParser}.
 */
@RunWith(JUnitParamsRunner.class)
@ApiTest(apis = {
        "android.os.vibrator.persistence.VibrationXmlParser#parse",
        "android.os.vibrator.persistence.VibrationXmlSerializer#serialize"
})
public class VibrationEffectXmlSerializationTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @Parameters(method = "getEffectsAndVibrationSelectXmls")
    public void testValidParseDocument_vibrationSelectRootTag(
            VibrationEffect[] expectedEffects, String xml) throws Exception {
        assertWithMessage("Found wrong parse result for " + xml)
                .that(parseDocument(xml).getVibrationEffects())
                .containsExactly(expectedEffects).inOrder();
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationSelectXmls")
    public void testValidVibrationSelectXmlsFailWithParseVibrationEffect(
            VibrationEffect[] unused, String xml) throws Exception {
        assertWithMessage("Expected vibration-effect parsing to fail for vibration-select " + xml)
                .that(parseVibrationEffect(xml))
                .isNull();
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationEffectXmls")
    public void testValidParseDocument_vibrationEffectRootTag(
            VibrationEffect expectedEffect, String xml) throws Exception {
        assertWithMessage("Found wrong parse result for " + xml)
                .that(parseDocument(xml).getVibrationEffects())
                .containsExactly(expectedEffect).inOrder();
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationEffectXmls")
    public void testParseValidVibrationEffect(VibrationEffect expectedEffect, String xml)
            throws Exception {
        assertWithMessage("Found wrong parse result for " + xml)
                .that(parseVibrationEffect(xml))
                .isEqualTo(expectedEffect);
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationEffectXmls")
    public void testSerializeValidVibrationEffect(VibrationEffect effect, String expectedXml)
            throws Exception {
        StringWriter writer = new StringWriter();
        VibrationXmlSerializer.serialize(effect, writer);
        assertSameXml(expectedXml, writer.toString());
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationEffectXmls")
    @SuppressWarnings("unused") // Unused serialization argument to reuse parameters for round trip
    public void testParseSerializeRoundTrip(VibrationEffect effect, String unusedXml)
            throws Exception {
        StringWriter writer = new StringWriter();
        // Serialize effect
        VibrationXmlSerializer.serialize(effect, writer);
        // Parse serialized effect
        StringReader reader = new StringReader(writer.toString());
        VibrationEffect parsedEffect = VibrationXmlParser.parseVibrationEffect(reader);
        assertThat(parsedEffect).isEqualTo(effect);
    }

    @Test
    public void testParseValidVibrationEffectWithCommentsAndSpaces() throws Exception {
        assertThat(parseVibrationEffect(
                """

                <!-- comment before root tag -->

                <vibration-effect>
                    <!--
                            multi-lined
                            comment
                    -->
                    <predefined-effect name="click"/>
                    <!-- comment before closing root tag -->
                </vibration-effect>

                <!-- comment after root tag -->
                """))
                .isEqualTo(VibrationEffect.createPredefined(EFFECT_CLICK));
    }

    @Test
    public void testParseValidDocumentWithCommentsAndSpaces() throws Exception {
        assertThat(parseDocument(
                """

                <!-- comment before root tag -->

                <vibration-effect>
                    <!--
                            multi-lined
                            comment
                    -->
                    <predefined-effect name="click"/>
                    <!-- comment before closing root tag -->
                </vibration-effect>

                <!-- comment after root tag -->
                """).getVibrationEffects())
                .containsExactly(VibrationEffect.createPredefined(EFFECT_CLICK)).inOrder();

        assertThat(parseDocument(
                """

                <!-- comment before root tag -->
                <vibration-select>
                    <!-- comment before vibration tag -->
                    <vibration-effect>
                        <!--
                                multi-lined
                                comment
                        -->
                        <predefined-effect name="click"/>
                        <!-- comment before closing vibration tag -->
                    </vibration-effect>
                    <!-- comment after vibration tag -->
                    <vibration-effect>
                        <!-- single-lined comment-->
                        <predefined-effect name="tick"/>
                    </vibration-effect>
                    <!-- comment before closing root tag -->
                </vibration-select>
                <!-- comment after root tag -->
                """).getVibrationEffects())
                .containsExactly(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK),
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                .inOrder();
    }

    @Test
    public void testParseInvalidXmlIsNull() throws Exception {
        assertFailedParse("");
        assertFailedParse("<!-- pure comment -->");
        assertFailedParse("invalid");
        // Malformed vibration tag
        assertFailedParse("<vibration");
        // Open vibration tag is never closed
        assertFailedParse("<vibration-effect>");
        // Open predefined-effect tag is never closed before vibration is closed
        assertFailedParse("<vibration-effect><predefined-effect name=\"click\"></vibration-effect>");
        // Root tags mismatch
        assertFailedParse("<vibration-select></vibration-effect>");
        assertFailedParse("<vibration-effect></vibration-select>");
    }

    @Test
    public void testParseInvalidElementsOnStartIsNull() throws Exception {
        assertFailedParse(
                """
                # some invalid initial text
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <invalid-first-tag/>
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <invalid-root-tag>
                    <predefined-effect name="click"/>
                </invalid-root-tag>
                """);
        assertFailedParse(
                """
                <supposed-to-be-vibration-select>
                    <vibration-effect><predefined-effect name="click"/></vibration-effect>
                </supposed-to-be-vibration-select>
                """);
        assertFailedParse(
                """
                <rand-tag-name>
                    <vibration-select>
                        <vibration-effect><predefined-effect name="click"/></vibration-effect>
                    </vibration-select>
                </rand-tag-name>
                """);
    }

    @Test
    public void testParseInvalidElementsOnEndIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                # some invalid text
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                <invalid-trailing-tag/>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                    <invalid-trailing-end-tag/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                    <invalid-trailing-end-tag/>
                </vibration-select>
                """);
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                </vibration-select>
                <invalid-trailing-end-tag/>
                """);
    }

    @Test
    public void testParseEmptyVibrationTagIsNull() throws Exception {
        assertFailedParse("<vibration/>");
    }

    @Test
    public void testParseEmptyVibrationSelectTagIsEmpty() throws Exception {
        assertThat(parseDocument("<vibration-select/>").getVibrationEffects()).isEmpty();
    }

    @Test
    public void testParseMultipleVibrationTagsIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseMultipleVibrationSelectTagsIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                </vibration-select>
                <vibration-select>
                    <vibration-effect>
                        <predefined-effect name="tick"/>
                    </vibration-effect>
                </vibration-select>
                """);
    }

    @Test
    public void testParseEffectTagWrongAttributesIsNull() throws Exception {
        // Missing name attribute
        assertFailedParse("<vibration-effect><predefined-effect/></vibration-effect>");

        // Wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect id="0"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click" extra="0"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testBadVibrationXmlWithinVibrationSelectTagIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-select>
                    <predefined-effect name="click"/></vibration-effect>
                </vibration-select>
                """);
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect><predefined-effect name="bad_click"/></vibration-effect>
                </vibration-select>
                """);
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect><predefined-effect name="click" rand_attr="100"/></vibration-effect>
                </vibration-select>
                """);
    }

    @Test
    public void testParseHiddenPredefinedEffectIsNull() throws Exception {
        // Hidden effect id
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="texture_tick"/>
                </vibration-effect>
                """);

        // Non-default fallback flag
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="tick" fallback="false"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParsePrimitiveTagWrongAttributesIsNull() throws Exception {
        // Missing name attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect scale="1" delayMs="10"/>
                </vibration-effect>
                """);

        // Wrong attribute "delay" instead of "delayMs"
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" delay="10"/>
                </vibration-effect>
                """);

        // Wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" extra="0"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseWaveformEffectAndRepeatingTagsAnyAttributeIsNull() throws Exception {
        // Waveform with wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect extra="0">
                        <waveform-entry durationMs="10" amplitude="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Repeating with wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <repeating extra="0">
                            <waveform-entry durationMs="10" amplitude="10"/>
                        </repeating>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseWaveformEntryTagWrongAttributesIsNull() throws Exception {
        // Missing amplitude attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Missing durationMs attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="100"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Wrong attribute "duration" instead of "durationMs"
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="100" duration="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="100" durationMs="10" extra="0"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidPredefinedEffectNameIsNull() throws Exception {
        // Invalid effect name
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="lick"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParsePredefinedFollowedAnyEffectIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                    <predefined-effect name="tick"/>
                </vibration-effect>
                """);

        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                    <primitive-effect name="click"/>
                </vibration-effect>
                """);

        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseRepeatingPredefinedEffectsIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-effect>
                    <repeating>
                        <predefined-effect name="click"/>
                    </repeating>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseAnyTagInsidePredefinedEffectIsNull() throws Exception {
        // Predefined inside predefined effect
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click">
                        <predefined-effect name="click"/>
                    </predefined-effect>
                </vibration-effect>
                """);

        // Primitive inside predefined effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click">
                        <primitive-effect name="click"/>
                    </predefined-effect>
                </vibration-effect>
                """);

        // Waveform inside predefined effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click">
                        <waveform-effect>
                            <waveform-entry amplitude="default" durationMs="10"/>
                        </waveform-effect>"
                    </predefined-effect>"
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidPrimitiveNameAndAttributesIsNull() throws Exception {
        // Invalid primitive name.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="lick"/>
                </vibration-effect>
                """);

        // Invalid primitive scale.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" scale="-1"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" scale="2"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" scale="NaN"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" scale="Infinity"/>
                </vibration-effect>
                """);

        // Invalid primitive delay.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" delayMs="-1"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParsePrimitiveFollowedByOtherEffectsIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click"/>
                    <predefined-effect name="click"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click"/>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>"
                </vibration-effect>
                """);
    }

    @Test
    public void testParseAnyTagInsidePrimitiveIsNull() throws Exception {
        // Predefined inside primitive effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click">
                        <predefined-effect name="click"/>
                    </primitive-effect>
                </vibration-effect>
                """);

        // Primitive inside primitive effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click">
                        <primitive-effect name="click"/>
                    </primitive-effect>
                </vibration-effect>
                """);

        // Waveform inside primitive effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click">
                        <waveform-effect>
                            <waveform-entry amplitude="default" durationMs="10"/>
                        </waveform-effect>
                    </primitive-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseRepeatingPrimitivesIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click"/>
                    <repeating>
                        <primitive-effect name="tick"/>
                    </repeating>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidWaveformEntryAttributesIsNull() throws Exception {
        // Invalid amplitude.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="-1"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Invalid duration.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="-1" amplitude="default"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidTagInsideWaveformEffectIsNull() throws Exception {
        // Primitive inside waveform or repeating.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <primitive-effect name="click"/>
                    </waveform-effect>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating><primitive-effect name="click"/></repeating>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Predefined inside waveform or repeating.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <predefined-effect name="click"/>
                    </waveform-effect>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating>
                            <predefined-effect name="click"/>
                        </repeating>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Waveform effect inside waveform or repeating.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <waveform-effect>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </waveform-effect>
                    </waveform-effect>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating>
                            <waveform-effect>
                                <waveform-entry durationMs="10" amplitude="default"/>
                            </waveform-effect>
                        </repeating>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidVibrationWaveformIsNull() throws Exception {
        // Empty waveform.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect></waveform-effect>
                </vibration-effect>
                """);

        // Empty repeating block.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="0" amplitude="10"/>
                        <repeating/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Waveform with multiple repeating blocks.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <repeating>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </repeating>
                        <repeating>
                            <waveform-entry durationMs="100" amplitude="default"/>
                        </repeating>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Waveform with entries after repeating block.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <repeating>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </repeating>
                        <waveform-entry durationMs="100" amplitude="default"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Waveform with total duration zero.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="0" amplitude="10"/>
                        <waveform-entry durationMs="0" amplitude="20"/>
                        <waveform-entry durationMs="0" amplitude="30"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseWaveformFollowedAnyEffectIsNull() throws Exception {
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <predefined-effect name="tick"/>
                </vibration-effect>
                """);

        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <primitive-effect name="click"/>
                </vibration-effect>
                """);

        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testSerializeVibrationEffectFromNonPublicApiIsFalse() {
        StringWriter writer = new StringWriter();

        // Predefined effect with non-default fallback flag.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.get(VibrationEffect.EFFECT_TICK, /* fallback= */ false),
                        writer));
        assertThat(writer.toString()).isEmpty();

        // Predefined effect with hidden effect id.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.get(VibrationEffect.EFFECT_TEXTURE_TICK), writer));
        assertThat(writer.toString()).isEmpty();

        // Step with non-default frequency.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform(targetFrequency(150f))
                                .addSustain(Duration.ofMillis(100))
                                .build(), writer));
        assertThat(writer.toString()).isEmpty();

        // Step with non-integer amplitude.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform(targetAmplitude(0.00123f))
                                .addSustain(Duration.ofMillis(100))
                                .build(), writer));
        assertThat(writer.toString()).isEmpty();

        // Waveform with ramp segments
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform()
                                .addSustain(Duration.ofMillis(100))
                                .addTransition(Duration.ofMillis(50), targetAmplitude(1))
                                .build(), writer));
        assertThat(writer.toString()).isEmpty();

        // Composition with non-primitive segments
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startComposition()
                                .addPrimitive(PRIMITIVE_CLICK)
                                .addEffect(VibrationEffect.createPredefined(EFFECT_CLICK))
                                .compose(), writer));
        assertThat(writer.toString()).isEmpty();

        // Composition with repeating primitive segments
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startComposition()
                                .repeatEffectIndefinitely(
                                        VibrationEffect.startComposition()
                                                .addPrimitive(PRIMITIVE_CLICK)
                                                .addPrimitive(PRIMITIVE_TICK, 1f, /* delay= */ 100)
                                                .compose())
                                .compose(), writer));
        assertThat(writer.toString()).isEmpty();
    }

    private static VibrationEffect parseVibrationEffect(String xml) throws IOException {
        return VibrationXmlParser.parseVibrationEffect(new StringReader(xml));
    }

    private static ParsedVibration parseDocument(String xml) throws IOException {
        return VibrationXmlParser.parseDocument(new StringReader(xml));
    }

    private static void assertFailedParse(String xml) throws IOException {
        assertWithMessage("Expected vibration-effect parsing to fail for " + xml)
                .that(VibrationXmlParser.parseVibrationEffect(new StringReader(xml)))
                .isNull();
        assertWithMessage("Expected document parsing to fail for " + xml)
                .that(VibrationXmlParser.parseDocument(new StringReader(xml)))
                .isNull();
    }

    @SuppressWarnings("unused") // Used in tests with @Parameters
    private Object[] getEffectsAndVibrationSelectXmls() {
        return new Object[] {
                new Object[] {
                        new VibrationEffect[] {
                                VibrationEffect.createWaveform(new long[]{10, 20},
                                        /* repeat= */ -1)
                        },
                        """
                        <vibration-select>
                            <vibration-effect>
                                <waveform-effect>
                                    <waveform-entry durationMs="10" amplitude="0"/>
                                    <waveform-entry durationMs="20" amplitude="default"/>
                                </waveform-effect>
                            </vibration-effect>
                        </vibration-select>
                        """,
                },
                new Object[] {
                        new VibrationEffect[] {
                                VibrationEffect.createWaveform(new long[]{1, 2, 3, 4},
                                        /* repeat= */ -1),
                                VibrationEffect.startComposition()
                                        .addPrimitive(PRIMITIVE_TICK)
                                        .addPrimitive(PRIMITIVE_CLICK, 0.123f)
                                        .addPrimitive(PRIMITIVE_LOW_TICK, 1f, 900)
                                        .addPrimitive(PRIMITIVE_SPIN, 0.404f, 9)
                                        .compose(),
                                VibrationEffect.createPredefined(EFFECT_CLICK),
                                VibrationEffect.createWaveform(new long[]{1, 9, 7, 3},
                                        /* repeat= */ 1)
                        },
                        """
                        <vibration-select>
                            <vibration-effect>
                                <waveform-effect>
                                    <waveform-entry durationMs="1" amplitude="0"/>
                                    <waveform-entry durationMs="2" amplitude="default"/>
                                    <waveform-entry durationMs="3" amplitude="0"/>
                                    <waveform-entry durationMs="4" amplitude="default"/>
                                </waveform-effect>
                            </vibration-effect>
                            <vibration-effect>
                                <primitive-effect name="tick"/>
                                <primitive-effect name="click" scale="0.123"/>
                                <primitive-effect name="low_tick" delayMs="900"/>
                                <primitive-effect name="spin" scale="0.404" delayMs="9"/>
                            </vibration-effect>
                            <vibration-effect><predefined-effect name="click"/></vibration-effect>
                            <vibration-effect>
                                <waveform-effect>
                                    <waveform-entry durationMs="1" amplitude="0"/>
                                    <repeating>
                                        <waveform-entry durationMs="9" amplitude="default"/>
                                        <waveform-entry durationMs="7" amplitude="0"/>
                                        <waveform-entry durationMs="3" amplitude="default"/>
                                    </repeating>
                                </waveform-effect>
                            </vibration-effect>
                        </vibration-select>
                        """
                }
        };
    }

    @SuppressWarnings("unused") // Used in tests with @Parameters
    private Object[] getEffectsAndVibrationEffectXmls() {
        return new Object[]{
                new Object[]{
                        // On-off pattern
                        VibrationEffect.createWaveform(new long[]{10, 20, 30, 40},
                                /* repeat= */ -1),
                        """
                        <vibration-effect>
                            <waveform-effect>
                                <waveform-entry durationMs="10" amplitude="0"/>
                                <waveform-entry durationMs="20" amplitude="default"/>
                                <waveform-entry durationMs="30" amplitude="0"/>
                                <waveform-entry durationMs="40" amplitude="default"/>
                            </waveform-effect>
                        </vibration-effect>
                        """,
                },
                new Object[]{
                        // Repeating on-off pattern
                        VibrationEffect.createWaveform(new long[]{100, 200, 300, 400},
                                /* repeat= */ 2),
                        """
                        <vibration-effect>
                            <waveform-effect>
                                <waveform-entry durationMs="100" amplitude="0"/>
                                <waveform-entry durationMs="200" amplitude="default"/>
                                <repeating>
                                    <waveform-entry durationMs="300" amplitude="0"/>
                                    <waveform-entry durationMs="400" amplitude="default"/>
                                </repeating>
                            </waveform-effect>
                        </vibration-effect>
                        """,
                },
                new Object[]{
                        // Amplitude waveform
                        VibrationEffect.createWaveform(new long[]{100, 200, 300},
                                new int[]{1, VibrationEffect.DEFAULT_AMPLITUDE, 250},
                                /* repeat= */ -1),
                        """
                        <vibration-effect>
                            <waveform-effect>
                                <waveform-entry amplitude="1" durationMs="100"/>
                                <waveform-entry amplitude="default" durationMs="200"/>
                                <waveform-entry durationMs="300" amplitude="250"/>
                            </waveform-effect>
                        </vibration-effect>
                        """,
                },
                new Object[]{
                        // Repeating amplitude waveform
                        VibrationEffect.createWaveform(new long[]{123, 456, 789, 0},
                                new int[]{254, 1, 255, 0}, /* repeat= */ 0),
                        """
                        <vibration-effect>
                            <waveform-effect>
                                <repeating>
                                    <waveform-entry durationMs="123" amplitude="254"/>
                                    <waveform-entry durationMs="456" amplitude="1"/>
                                    <waveform-entry durationMs="789" amplitude="255"/>
                                    <waveform-entry durationMs="0" amplitude="0"/>
                                </repeating>
                            </waveform-effect>
                        </vibration-effect>
                        """,
                },
                new Object[]{
                        // Predefined effect
                        VibrationEffect.createPredefined(EFFECT_CLICK),
                        """
                        <vibration-effect><predefined-effect name="click"/></vibration-effect>
                        """,
                },
                new Object[]{
                        // Primitive composition
                        VibrationEffect.startComposition()
                                .addPrimitive(PRIMITIVE_CLICK)
                                .addPrimitive(PRIMITIVE_TICK, 0.2497f)
                                .addPrimitive(PRIMITIVE_LOW_TICK, 1f, 356)
                                .addPrimitive(PRIMITIVE_SPIN, 0.6364f, 7)
                                .compose(),
                        """
                        <vibration-effect>
                            <primitive-effect name="click"/>
                            <primitive-effect name="tick" scale="0.2497"/>
                            <primitive-effect name="low_tick" delayMs="356"/>
                            <primitive-effect name="spin" scale="0.6364" delayMs="7"/>
                        </vibration-effect>
                        """,
                },
        };
    }

    static void assertSameXml(String expectedXml, String actualXml)
            throws ParserConfigurationException {
        // DocumentBuilderFactory does not support setValidating(true) in Android, so the method
        // setIgnoringElementContentWhitespace does not work. Remove whitespace manually.
        expectedXml = removeWhitespaceBetweenXmlTags(expectedXml);
        actualXml = removeWhitespaceBetweenXmlTags(actualXml);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document expectedDoc, actualDoc;

        try {
            expectedDoc = db.parse(new ByteArrayInputStream(expectedXml.getBytes()));
            expectedDoc.normalizeDocument();
        } catch (IOException | SAXException e) {
            throw new RuntimeException("Failed to parse XML for comparison:\n" + expectedXml, e);
        }

        try {
            actualDoc = db.parse(new ByteArrayInputStream(actualXml.getBytes()));
            actualDoc.normalizeDocument();
        } catch (IOException | SAXException e) {
            throw new RuntimeException("Failed to parse XML for comparison:\n" + actualXml, e);
        }

        assertWithMessage("Expected XML:\n%s\n\nActual XML:\n%s", expectedXml, actualXml)
                .that(expectedDoc.isEqualNode(actualDoc)).isTrue();
    }

    private static String removeWhitespaceBetweenXmlTags(String xml) {
        return xml.replaceAll(">\\s+<", "><");
    }
}
