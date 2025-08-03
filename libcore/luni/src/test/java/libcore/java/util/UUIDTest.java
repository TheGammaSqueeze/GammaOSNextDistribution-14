/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package libcore.java.util;

import libcore.test.annotation.NonCts;
import libcore.test.reasons.NonCtsReasons;

import junit.framework.TestCase;

import java.util.UUID;

// There are more tests in the harmony suite:
// harmony-tests/src/test/java/org/apache/harmony/tests/java/util/UUIDTest.java
public class UUIDTest extends TestCase {

  @SuppressWarnings("AlwaysThrows")
  public void testFromStringInvalidValues() {
    try {
      UUID.fromString("+f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
      fail();
    } catch (IllegalArgumentException expected) { }

    try {
      UUID.fromString("f81d4fae-+7dec-11d0-a765-00a0c91e6bf6");
      fail();
    } catch (IllegalArgumentException expected) { }

    try {
      UUID.fromString("f81d4fae-7dec-+11d0-a765-00a0c91e6bf6");
      fail();
    } catch (IllegalArgumentException expected) { }

    try {
      UUID.fromString("f81d4fae-7dec-11d0-+a765-00a0c91e6bf6");
      fail();
    } catch (IllegalArgumentException expected) { }

    try {
      UUID.fromString("f81d4fae-7dec-11d0-a765-+00a0c91e6bf6");
      fail();
    } catch (IllegalArgumentException expected) { }
  }

  @NonCts(bug = 310050493, reason = NonCtsReasons.INTERNAL_APIS)
  public void testJava8Implementation_allowsLongInputs() {
    var uuid = UUID.randomUUID();
    var parsedBackWithLeadingZero = UUID.fromStringJava8("0" + uuid);

    assertEquals(uuid, parsedBackWithLeadingZero);

    uuid = UUID
            .fromStringJava8("7fffffffffffffff-7fffffffffffffff-7fffffffffffffff-0-0");
    assertEquals(0xffffffffffffffffL, uuid.getMostSignificantBits());
    assertEquals(0x0L, uuid.getLeastSignificantBits());

    uuid = UUID.fromStringJava8("0-0-0-7fffffffffffffff-7fffffffffffffff");
    assertEquals(0x0L, uuid.getMostSignificantBits());
    assertEquals(0xffffffffffffffffL, uuid.getLeastSignificantBits());
  }

  @NonCts(bug = 310050493, reason = NonCtsReasons.INTERNAL_APIS)
  public void testCurrentImplementation_invalidInputs() {
    var uuid = UUID.randomUUID();
    try {
      UUID.fromStringCurrentJava("0" + uuid);
      fail("0" + uuid + " is invalid UUID, IAE should be thrown");
    } catch (IllegalArgumentException expected) { }

    try {
      UUID.fromStringCurrentJava("0-0-0-0-0-");
      fail("0-0-0-0-0- is invalid UUID, IAE should be thrown");
    } catch (IllegalArgumentException expected) { }
  }

  @NonCts(bug = 310050493, reason = NonCtsReasons.INTERNAL_APIS)
  public void testJava11Implementation_invalidInputs() {
      // The test moved to testCurrentImplementation_invalidInputs()
  }
}
