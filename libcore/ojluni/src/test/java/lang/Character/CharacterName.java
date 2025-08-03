/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8147531
 * @summary  Check j.l.Character.getName and codePointOf
 */

package test.java.lang.Character;

import android.platform.test.annotations.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Locale;

// Android-changed: Shard the test.
@LargeTest
@RunWith(JUnit4.class)
public class CharacterName {

    // Android-changed: Shard the test.
    // public static void main(String[] args) {
    //    for (int cp = 0; cp < Character.MAX_CODE_POINT; cp++) {
    private static void testCodePointRange(int start) {
        int end = start + 0x8000;
        for (int cp = start; cp < end; cp++) {
            if (!Character.isValidCodePoint(cp)) {
                try {
                    Character.getName(cp);
                } catch (IllegalArgumentException x) {
                    continue;
                }
                throw new RuntimeException("Invalid failed: " + cp);
            } else if (Character.getType(cp) == Character.UNASSIGNED) {
                if (Character.getName(cp) != null)
                    throw new RuntimeException("Unsigned failed: " + cp);
            } else {
                String name = Character.getName(cp);
                if (cp != Character.codePointOf(name) ||
                    cp != Character.codePointOf(name.toLowerCase(Locale.ENGLISH)))
                throw new RuntimeException("Roundtrip failed: " + cp);
            }
        }
    }

    // BEGIN Android-added: Shard the test.
    @Test
    public void testGetName_shard1() {
        testCodePointRange(0);
    }
    @Test
    public void testGetName_shard2() {
        testCodePointRange(0x8000);
    }
    @Test
    public void testGetName_shard3() {
        testCodePointRange(0x10000);
    }
    @Test
    public void testGetName_shard4() {
        testCodePointRange(0x18000);
    }
    @Test
    public void testGetName_shard5() {
        testCodePointRange(0x100000);
    }
    @Test
    public void testGetName_shard6() {
        testCodePointRange(0x108000);
    }
    // END Android-added: Shard the test.
}
