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

package libcore.java.security;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.DrbgParameters;
import java.security.DrbgParameters.Capability;
import java.security.DrbgParameters.Instantiation;

@RunWith(JUnit4.class)
public class DrbgParametersTest {

    @Test
    public void testInstantiation() {
        int strength = 1;
        Capability capability = Capability.PR_AND_RESEED;
        byte[] personalizationString = new byte[] {'b', 'y', 'e'};
        Instantiation instantiation = DrbgParameters.instantiation(strength, capability,
                personalizationString);
        assertArrayEquals(personalizationString, instantiation.getPersonalizationString());
        assertEquals(capability, instantiation.getCapability());
        assertEquals(strength, instantiation.getStrength());
    }

    @Test
    public void testNextBytes_withValidValues() {
        byte[] additionalInput = new byte[] { 0x00, 0x01, 0x02 };
        DrbgParameters.NextBytes nextBytes =
                DrbgParameters.nextBytes(1234567890, true, additionalInput);
        assertEquals(1234567890, nextBytes.getStrength());
        assertTrue(nextBytes.getPredictionResistance());
        assertArrayEquals(additionalInput, nextBytes.getAdditionalInput());
    }

    @Test
    public void testNextBytes_withNegativeStrength() {
        assertThrows(IllegalArgumentException.class, () ->
                DrbgParameters.nextBytes(-2, true, null));
    }

    @Test
    public void testNextBytes_withNullAdditionalInput() {
        DrbgParameters.NextBytes nextBytes =
                DrbgParameters.nextBytes(1234567890, true, null);
        assertEquals(null, nextBytes.getAdditionalInput());
    }

    @Test
    public void testReseed_withValidValues() {
        byte[] additionalInput = new byte[] { 0x00, 0x01, 0x02 };
        DrbgParameters.Reseed reseed =
                DrbgParameters.reseed(true, additionalInput);
        assertTrue(reseed.getPredictionResistance());
        assertArrayEquals(additionalInput, reseed.getAdditionalInput());
    }

    @Test
    public void testReseed_withNullAdditionalInput() {
        DrbgParameters.Reseed reseed = DrbgParameters.reseed(true, null);
        assertEquals(null, reseed.getAdditionalInput());
    }

    @Test
    public void testCapability() {
        Capability capability = Capability.PR_AND_RESEED;
        assertTrue(capability.supportsReseeding());
        assertTrue(capability.supportsPredictionResistance());

        capability = Capability.RESEED_ONLY;
        assertTrue(capability.supportsReseeding());
        assertFalse(capability.supportsPredictionResistance());

    }

}
