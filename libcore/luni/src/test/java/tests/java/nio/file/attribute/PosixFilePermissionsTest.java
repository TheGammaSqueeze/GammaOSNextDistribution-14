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
package tests.java.nio.file.attribute;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class PosixFilePermissionsTest {

    @Parameters(name = "perm:{0} str:{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                // edge cases
                {"0000", "---------", Set.<PosixFilePermission>of()},
                {"0777", "rwxrwxrwx", Set.of(PosixFilePermission.values())},
                // individual bits only
                {"0400", "r--------", Set.of(OWNER_READ)},
                {"0200", "-w-------", Set.of(OWNER_WRITE)},
                {"0100", "--x------", Set.of(OWNER_EXECUTE)},
                {"0040", "---r-----", Set.of(GROUP_READ)},
                {"0020", "----w----", Set.of(GROUP_WRITE)},
                {"0010", "-----x---", Set.of(GROUP_EXECUTE)},
                {"0004", "------r--", Set.of(OTHERS_READ)},
                {"0002", "-------w-", Set.of(OTHERS_WRITE)},
                {"0001", "--------x", Set.of(OTHERS_EXECUTE)},
                // only owner/group/others
                {"0700", "rwx------", Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)},
                {"0070", "---rwx---", Set.of(GROUP_READ, GROUP_WRITE, GROUP_EXECUTE)},
                {"0007", "------rwx", Set.of(OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE)},
                // all only r/w/x
                {"0444", "r--r--r--", Set.of(OWNER_READ, GROUP_READ, OTHERS_READ)},
                {"0222", "-w--w--w-", Set.of(OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE)},
                {"0111", "--x--x--x", Set.of(OWNER_EXECUTE, GROUP_EXECUTE, OTHERS_EXECUTE)},
                // all only rw/rx/wx
                {"0666", "rw-rw-rw-", Set.of(OWNER_READ, OWNER_WRITE, GROUP_READ, GROUP_WRITE,
                                             OTHERS_READ, OTHERS_WRITE)},
                {"0555", "r-xr-xr-x", Set.of(OWNER_READ, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE,
                                             OTHERS_READ, OTHERS_EXECUTE)},
                {"0333", "-wx-wx-wx", Set.of(OWNER_WRITE, OWNER_EXECUTE, GROUP_WRITE, GROUP_EXECUTE,
                                             OTHERS_WRITE, OTHERS_EXECUTE)},
                // misc
                {"0755", "rwxr-xr-x", Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
                                             GROUP_READ, GROUP_EXECUTE,
                                             OTHERS_READ, OTHERS_EXECUTE)},
                {"0750", "rwxr-x---", Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
                                             GROUP_READ, GROUP_EXECUTE)},
                {"0644", "rw-r--r--", Set.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ)},
        });
    }

    /**
     * Permission value being tested in *nix format, e.g. 0755 or 0666.
     */
    @Parameter
    public String perm;

    @Parameter(1)
    public String asString;

    @Parameter(2)
    public Set<PosixFilePermission> asSet;

    @Test
    public void test_asFileAttribute() {
        Set<PosixFilePermission> input = asSet;
        var asFileAttribute = PosixFilePermissions.asFileAttribute(input);
        assertEquals("posix:permissions", asFileAttribute.name());
        assertEquals(input, asFileAttribute.value());
    }

    @Test
    public void test_fromString() {
        String input = asString;
        Set<PosixFilePermission> actual = PosixFilePermissions.fromString(input);
        Set<PosixFilePermission> expected = asSet;
        assertEquals(expected, actual);
    }

    @Test
    public void test_toString() {
        Set<PosixFilePermission> input = asSet;
        String actual = PosixFilePermissions.toString(input);
        String expected = asString;
        assertEquals(expected, actual);
    }

    @Test
    public void roundtrip() {
        { // toString ∘ fromString ＝ id
            String input = asString;

            var convertForward = PosixFilePermissions.fromString(input);
            var actual = PosixFilePermissions.toString(convertForward);
            assertEquals(asString, actual);
        }

        { // fromString ∘ toString ＝ id
            Set<PosixFilePermission> input = asSet;

            var convertForward = PosixFilePermissions.toString(input);
            var actual = PosixFilePermissions.fromString(convertForward);
            assertEquals(asSet, actual);
        }
    }
}
