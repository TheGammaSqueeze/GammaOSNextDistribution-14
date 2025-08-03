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
package com.android.server.uwb.secure.iso7816;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Test for {@link CommandApdu}. */
public class CommandApduTest {
    private static BaseEncoding sHex = BaseEncoding.base16().lowerCase();

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCommandApdu() {
        StatusWord[] exp = {StatusWord.SW_NO_ERROR};
        byte[] cdata = new byte[255];
        generateData(cdata);
        CommandApdu cmd = new CommandApdu(0, 1, 2, 3, cdata, 255, false, exp);
        assertThat(cmd.getCla()).isEqualTo(0);
        assertThat(cmd.getIns()).isEqualTo(1);
        assertThat(cmd.getP1()).isEqualTo(2);
        assertThat(cmd.getP2()).isEqualTo(3);
        assertThat(cmd.getCommandData()).isEqualTo(cdata);
        assertThat(cmd.getLe()).isEqualTo(255);
    }

    @Test
    public void testCommandApdu_InvalidLc() {
        thrown.expect(IllegalArgumentException.class);
        new CommandApdu(0, 1, 2, 3, new byte[65566], 255, false, StatusWord.SW_NO_ERROR);
    }

    @Test
    public void testCommandApdu_InvalidLe() {
        thrown.expect(IllegalArgumentException.class);
        new CommandApdu(0, 1, 2, 3, null, 65566, false, StatusWord.SW_NO_ERROR);
    }

    @Test
    public void testGetEncoded_standard() {
        StatusWord[] exp = {StatusWord.SW_NO_ERROR};
        byte[] cdata = new byte[255];
        generateData(cdata);
        CommandApdu cmd = new CommandApdu(0, 1, 2, 3, cdata, -1, false, exp);

        assertThat(cmd.getEncoded()).isEqualTo(Bytes.concat(sHex.decode("00010203ff"), cdata));
    }

    @Test
    public void testGetEncoded_extended() {
        StatusWord[] exp = {StatusWord.SW_NO_ERROR};
        byte[] cdata = new byte[512];
        generateData(cdata);
        CommandApdu cmd = new CommandApdu(0, 1, 2, 3, cdata, -1, false, exp);

        assertThat(cmd.getEncoded()).isEqualTo(Bytes.concat(sHex.decode("00010203000200"), cdata));
    }

    @Test
    public void testExpected() {
        StatusWord[] errorNoError =
                new StatusWord[] {StatusWord.SW_NO_ERROR, StatusWord.SW_DATA_NOT_FOUND};
        CommandApdu.Builder builder = CommandApdu.builder(0x80, 0xe2, 0x00, 0x00);

        Set<StatusWord> noErrorSet = new HashSet<>();
        noErrorSet.add(StatusWord.SW_NO_ERROR);
        assertThat(noErrorSet).isEqualTo(builder.build().getExpected());

        Set<StatusWord> dataNotFoundSet = new HashSet<>();
        dataNotFoundSet.add(StatusWord.SW_DATA_NOT_FOUND);
        Set<StatusWord> errorNoErrorSet = new HashSet<>(Arrays.asList(errorNoError));
        CommandApdu[] cmds =
                new CommandApdu[] {
                        builder.setExpected(noErrorSet).build(),
                        builder.setExpected(dataNotFoundSet).build(),
                        builder.setExpected(errorNoErrorSet).build(),
                        builder.setExpected(
                                new StatusWord[] {StatusWord.SW_NO_ERROR}).build(),
                        builder.setExpected(
                                new StatusWord[] {StatusWord.SW_DATA_NOT_FOUND}).build(),
                        builder.setExpected(errorNoError).build(),
                };

        StatusWord[][] expected =
                new StatusWord[][] {
                        {StatusWord.SW_NO_ERROR},
                        {StatusWord.SW_DATA_NOT_FOUND},
                        errorNoError,
                        {StatusWord.SW_NO_ERROR},
                        {StatusWord.SW_DATA_NOT_FOUND},
                        errorNoError,
                };

        int i = 0;
        for (CommandApdu cmd : cmds) {
            // make sure that each command's expected set has exactly what we put in the builder.
            Set<StatusWord> expectedSet = new HashSet<>(Arrays.asList(expected[i++]));
            assertThat(cmd.getExpected()).isEqualTo(expectedSet);
        }
    }

    @Test
    public void testDoNotSetExpected() {
        CommandApdu cmd = CommandApdu.builder(0, 0, 0, 0).build();
        assertThat(cmd.getExpected()).hasSize(1);
        assertThat(cmd.getExpected()).contains(StatusWord.SW_NO_ERROR);
    }

    @Test
    public void testSetEmptyExpected() {
        thrown.expect(IllegalArgumentException.class);
        @SuppressWarnings("unused")
        CommandApdu cmd =
                CommandApdu.builder(0, 0, 0, 0)
                        .setExpected(Collections.emptySet()).build();
    }

    @Test
    public void testExtendedLe() {
        CommandApdu apdu = CommandApdu.builder(0, 1, 2, 3)
                .setExtendedLength().setLe(0).build();
        assertThat(apdu.getEncoded()).isEqualTo(sHex.decode("00010203000000"));

        apdu = CommandApdu.builder(0, 1, 2, 3)
                .setExtendedLength().setLe(0x1234).build();
        assertThat(apdu.getEncoded()).isEqualTo(sHex.decode("00010203001234"));
    }

    @Test
    public void testUnknownExpected() {
        thrown.expect(IllegalArgumentException.class);
        @SuppressWarnings("unused")
        CommandApdu cmd =
                CommandApdu.builder(0x80, 0xe2, 0x00, 0x00)
                        .setExpected(StatusWord.fromInt(0x1111)).build();
    }

    private void generateData(byte[] cdata) {
        for (int i = 0; i < cdata.length; ++i) {
            cdata[i] = (byte) (i % 255);
        }
    }
}
