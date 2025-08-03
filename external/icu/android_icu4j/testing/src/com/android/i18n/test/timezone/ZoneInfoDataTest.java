/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.i18n.test.timezone;

import static org.junit.Assert.assertArrayEquals;

import android.icu.testsharding.MainTestShard;

import com.android.i18n.timezone.WallTime;
import com.android.i18n.timezone.ZoneInfoData;
import com.android.i18n.timezone.ZoneInfoDb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import junit.framework.TestCase;
import com.android.i18n.timezone.internal.BufferIterator;
import libcore.timezone.testing.ZoneInfoTestHelper;

/**
 * Tests for {@link ZoneInfoData}
 */
@MainTestShard
public class ZoneInfoDataTest extends TestCase {

  /**
   * Checks that a {@link ZoneInfoData} cannot be created without any types.
   */
  public void testMakeTimeZone_NoTypes() throws Exception {
    long[][] transitions = {};
    int[][] types = {};
    try {
      createZoneInfoData(transitions, types);
      fail();
    } catch (IOException expected) {
    }
  }

  /**
   * Checks that a {@link ZoneInfoData} can be created with one type and no transitions.
   */
  public void testMakeTimeZone_OneType_NoTransitions() throws Exception {
    long[][] transitions = {};
    int[][] types = {
        { 4800, 0 }
    };
    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);

    // If there are no transitions then the offset should be constant irrespective of the time.
    Instant[] times = {
            Instant.ofEpochMilli(Long.MIN_VALUE),
            Instant.ofEpochMilli(0),
            Instant.ofEpochMilli(Long.MAX_VALUE),
    };
    assertOffsetAt(zoneInfoData, offsetFromSeconds(4800), times);

    // No transitions means no DST.
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(0));

    // The raw offset should be the offset of the first type.
    assertRawOffset(zoneInfoData, offsetFromSeconds(4800));
  }

  /**
   * Checks that a {@link ZoneInfoData} can be created with one non-DST transition.
   */
  public void testReadTimeZone_OneNonDstTransition() throws Exception {
    long[][] transitions = {
        { 0, 0 }
    };
    int[][] types = {
        { 3600, 0 }
    };
    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);

    // Any time before the first transition is assumed to use the first standard transition.
    Instant[] times = { timeFromSeconds(-2), timeFromSeconds(0), timeFromSeconds(2) };
    assertOffsetAt(zoneInfoData, offsetFromSeconds(3600), times);

    // No transitions means no DST.
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(0));

    // The raw offset should be the offset of the first type.
    assertRawOffset(zoneInfoData, offsetFromSeconds(3600));
  }

  /**
   * Checks that a {@link ZoneInfoData} cannot be created with one DST but no non-DSTs transitions.
   */
  public void testReadTimeZone_OneDstTransition() throws Exception {
    long[][] transitions = {
        { 0, 0 }
    };
    int[][] types = {
        { 3600, 1 }
    };
    try {
      createZoneInfoData(transitions, types);
      fail("Did not detect no non-DST transitions");
    } catch (IllegalStateException expected) {
    }
  }

  /**
   * Checks to make sure that rounding the time from milliseconds to seconds does not cause issues
   * around the boundary of negative transitions.
   */
  public void testReadTimeZone_NegativeTransition() throws Exception {
    long[][] transitions = {
        { -2000, 0 },
        { -5, 1 },
        { 0, 2 },
    };
    int[][] types = {
        { 1800, 0 },
        { 3600, 1 },
        { 5400, 0 }
    };
    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);
    assertArrayEquals(new long[] { -2000, -5, 0 }, zoneInfoData.getTransitions());

    Instant transitionTime = timeFromSeconds(-5);

    // Even a millisecond before a transition means that the transition is not active.
    Instant beforeTransitionTime = transitionTime.minusMillis(1);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(1800), beforeTransitionTime);
    assertInDaylightTime(zoneInfoData, beforeTransitionTime, false);

    // A time equal to the transition point activates the transition.
    assertOffsetAt(zoneInfoData, offsetFromSeconds(3600), transitionTime);
    assertInDaylightTime(zoneInfoData, transitionTime, true);

    // A time after the transition point but before the next activates the transition.
    Instant afterTransitionTime = transitionTime.plusMillis(1);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(3600), afterTransitionTime);
    assertInDaylightTime(zoneInfoData, afterTransitionTime, true);

    assertNoDSTSavings(zoneInfoData, timeFromSeconds(5400));
    assertRawOffset(zoneInfoData, offsetFromSeconds(5400));
  }

  /**
   * Checks to make sure that rounding the time from milliseconds to seconds does not cause issues
   * around the boundary of positive transitions.
   */
  public void testReadTimeZone_PositiveTransition() throws Exception {
    long[][] transitions = {
        { 0, 0 },
        { 5, 1 },
        { 2000, 2 },
    };
    int[][] types = {
        { 1800, 0 },
        { 3600, 1 },
        { 5400, 0 }
    };
    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);
    assertArrayEquals(new long[] { 0, 5, 2000 }, zoneInfoData.getTransitions());

    Instant transitionTime = timeFromSeconds(5);

    // Even a millisecond before a transition means that the transition is not active.
    Instant beforeTransitionTime = transitionTime.minusMillis(1);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(1800), beforeTransitionTime);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(1800), offsetFromSeconds(0),
            beforeTransitionTime);
    assertInDaylightTime(zoneInfoData, beforeTransitionTime, false);

    // A time equal to the transition point activates the transition.
    assertOffsetAt(zoneInfoData, offsetFromSeconds(3600), transitionTime);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(1800), offsetFromSeconds(1800),
            transitionTime);
    assertInDaylightTime(zoneInfoData, transitionTime, true);

    // A time after the transition point but before the next activates the transition.
    Instant afterTransitionTime = transitionTime.plusMillis(1);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(3600), afterTransitionTime);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(1800), offsetFromSeconds(1800),
            afterTransitionTime);
    assertInDaylightTime(zoneInfoData, afterTransitionTime, true);

    assertNoDSTSavings(zoneInfoData, timeFromSeconds(5400));
    assertRawOffset(zoneInfoData, offsetFromSeconds(5400));
  }

  /**
   * Tests {@link ZoneInfoData#createInstance}, and checks to make sure that rounding the time from
   * milliseconds to seconds does not cause issues around the boundary of positive transitions.
   */
  public void testCreateInstance() throws Exception {
    long[][] transitions = {
            { 0, 0 },
            { 5, 1 },
            { 2000, 2 },
    };
    int[][] types = {
            { 1800, 0 },
            { 3600, 1 },
            { 5400, 0 }
    };
    ZoneInfoData zoneInfoData = createInstance(transitions, types);
    assertArrayEquals(new long[] { 0, 5, 2000 }, zoneInfoData.getTransitions());

    Instant transitionTime = timeFromSeconds(5);

    // Even a millisecond before a transition means that the transition is not active.
    Instant beforeTransitionTime = transitionTime.minusMillis(1);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(1800), beforeTransitionTime);
    assertInDaylightTime(zoneInfoData, beforeTransitionTime, false);

    // A time equal to the transition point activates the transition.
    assertOffsetAt(zoneInfoData, offsetFromSeconds(3600), transitionTime);
    assertInDaylightTime(zoneInfoData, transitionTime, true);

    // A time after the transition point but before the next activates the transition.
    Instant afterTransitionTime = transitionTime.plusMillis(1);
    assertOffsetAt(zoneInfoData, offsetFromSeconds(3600), afterTransitionTime);
    assertInDaylightTime(zoneInfoData, afterTransitionTime, true);

    assertNoDSTSavings(zoneInfoData, timeFromSeconds(5400));
    assertRawOffset(zoneInfoData, offsetFromSeconds(5400));
  }

  /**
   * Checks that creating a {@link ZoneInfoData} with future DST transitions but no past DST
   * transitions where the transition times are negative is not affected by rounding issues.
   */
  public void testReadTimeZone_HasFutureDST_NoPastDST_NegativeTransitions() throws Exception {
    long[][] transitions = {
        { -2000, 0 },
        { -500, 1 },
        { -100, 2 },
    };
    int[][] types = {
        { 1800, 0 },
        { 3600, 0 },
        { 5400, 1 }
    };
    // The expected DST savings is the difference between the DST offset (which includes the
    // raw offset) and the preceding non-DST offset (which should just be the raw offset).
    // Or in other words (5400 - 3600) * 1000
    Duration expectedDSTSavings = offsetFromSeconds(5400 - 3600);

    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);
    assertDSTSavings(zoneInfoData, expectedDSTSavings, timeFromSeconds(-700));

    // Now check the DST savings a few milliseconds before the DST transition to make sure that
    // rounding errors don't cause a problem.
    assertDSTSavings(zoneInfoData, expectedDSTSavings, timeFromSeconds(-100).minusMillis(5));
  }

  /**
   * Checks that creating a {@link ZoneInfoData} with future DST transitions but no past DST
   * transitions where the transition times are positive is not affected by rounding issues.
   */
  public void testReadTimeZone_HasFutureDST_NoPastDST_PositiveTransitions() throws Exception {
    long[][] transitions = {
        { 4000, 0 },
        { 5500, 1 },
        { 6000, 2 },
    };
    int[][] types = {
        { 1800, 0 },
        { 3600, 0 },
        { 7200, 1 }
    };
    // The expected DST savings is the difference between the DST offset (which includes the
    // raw offset) and the preceding non-DST offset (which should just be the raw offset).
    // Or in other words (7200 - 3600) * 1000
    Duration expectedDSTSavings = offsetFromSeconds(7200 - 3600);

    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);

    assertDSTSavings(zoneInfoData, expectedDSTSavings, timeFromSeconds(4500));

    // Now check the DST savings a few milliseconds before the DST transition to make sure that
    // rounding errors don't cause a problem.
    assertDSTSavings(zoneInfoData, expectedDSTSavings, timeFromSeconds(6000).minusMillis(5));
  }

  /**
   * Checks that creating a {@link ZoneInfoData} with past DST transitions but no future DST
   * transitions where the transition times are negative is not affected by rounding issues.
   */
  public void testReadTimeZone_HasPastDST_NoFutureDST_NegativeTransitions() throws Exception {
    long[][] transitions = {
        { -5000, 0 },
        { -2000, 1 },
        { -500, 0 },
        { 0, 2 },
    };
    int[][] types = {
        { 3600, 0 },
        { 1800, 1 },
        { 5400, 0 }
    };
    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);

    assertNoDSTSavings(zoneInfoData, timeFromSeconds(-1));

    // Now check the DST savings a few milliseconds before the DST transition to make sure that
    // rounding errors don't cause a problem.
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(-2000).plusMillis(5));
  }

  /**
   * Checks that creating a {@link ZoneInfoData} with past DST transitions but no future DST
   * transitions where the transition times are positive is not affected by rounding issues.
   */
  public void testReadTimeZone_HasPastDST_NoFutureDST_PositiveTransitions() throws Exception {
    long[][] transitions = {
        { 1000, 0 },
        { 4000, 1 },
        { 5500, 0 },
        { 6000, 2 },
    };
    int[][] types = {
        { 3600, 0 },
        { 1800, 1 },
        { 5400, 0 }
    };
    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);

    assertNoDSTSavings(zoneInfoData, timeFromSeconds(4700));

    // Now check the DST savings a few milliseconds after the DST transition to make sure that
    // rounding errors don't cause a problem.
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(4000).plusMillis(5));
  }

  /**
   * TimeZone APIs use Java long times in millis.
   *
   * <p>Android has historically mishandled the lookup of offset for times before Integer.MIN_VALUE
   * seconds for various reasons. The logic has been corrected. This test would fail on versions of
   * Android <= P.
   */
  public void testReadTimeZone_Bug118835133_extraFirstTransition() throws Exception {
    // A time before the first representable time in seconds with a java int.
    Instant before32BitTime = timeFromSeconds(Integer.MIN_VALUE).minusMillis(1);

    // Times around the 32-bit seconds minimum.
    Instant[] earlyTimes = {
            timeFromSeconds(Integer.MIN_VALUE),
            timeFromSeconds(Integer.MIN_VALUE).plusMillis(1),
    };

    Instant firstRealTransitionTime = timeFromSeconds(1000);
    Instant afterFirstRealTransitionTime = firstRealTransitionTime.plusSeconds(1);
    Instant[] afterFirstRealTransitionTimes = {
            firstRealTransitionTime,
            afterFirstRealTransitionTime,
    };

    Duration type0Offset = offsetFromSeconds(0);
    Duration type1Offset = offsetFromSeconds(1800);
    Duration type2Offset = offsetFromSeconds(3600);
    int[][] types = {
            { offsetToSeconds(type0Offset), 0 }, // 1st type, used before first known transition.
            { offsetToSeconds(type1Offset), 0 },
            { offsetToSeconds(type2Offset), 0 },
    };

    // Simulates a zone with a single transition.
    {
      long[][] transitions = {
              { timeToSeconds(firstRealTransitionTime), 2 /* type 2 */ },
      };
      ZoneInfoData oldZoneInfoData = createZoneInfoData(transitions, types);
      assertRawOffset(oldZoneInfoData, type2Offset);

      // We use the first non-DST type for times before the first transition.
      assertOffsetAt(oldZoneInfoData, type0Offset, before32BitTime);
      assertOffsetAt(oldZoneInfoData, type0Offset, earlyTimes);

      // This is after the first transition, so type 2.
      assertOffsetAt(oldZoneInfoData, type2Offset, afterFirstRealTransitionTimes);
    }

    // Simulation a zone where there is an explicit transition at Integer.MIN_VALUE seconds. This
    // used to be common when Android used 32-bit data from the TZif file.
    {
      long[][] transitions = {
              { Integer.MIN_VALUE, 1 /* type 1 */ },
              { timeToSeconds(firstRealTransitionTime), 2 /* type 2 */ },
      };
      ZoneInfoData newZoneInfoData = createZoneInfoData(transitions, types);
      assertRawOffset(newZoneInfoData, type2Offset);

      // We use the first non-DST type for times before the first transition.
      assertOffsetAt(newZoneInfoData, type0Offset, before32BitTime);

      // After the first transition, so type 1.
      assertOffsetAt(newZoneInfoData, type1Offset, earlyTimes);

      // This is after the second transition, so type 2.
      assertOffsetAt(newZoneInfoData, type2Offset, afterFirstRealTransitionTimes);
    }
  }

  /**
   * Newer versions of zic after 2014b sometime introduce an explicit transition at
   * Integer.MAX_VALUE seconds.
   */
  public void testReadTimeZone_Bug118835133_extraLastTransition() throws Exception {
    // An arbitrary time to use as currentTime. Not important for this test.
    Instant currentTime = timeFromSeconds(4000);

    // Offset before time 1000 should be consistent.
    Instant[] timesToCheck = {
            timeFromSeconds(2100), // arbitrary time > 2000
            timeFromSeconds(Integer.MAX_VALUE).minusMillis(1),
            timeFromSeconds(Integer.MAX_VALUE),
            timeFromSeconds(Integer.MAX_VALUE).plusMillis(1),
    };

    int latestOffsetSeconds = 3600;
    int[][] types = {
            { 1800, 0 },
            { latestOffsetSeconds, 0 },
    };
    Duration expectedLateOffset = offsetFromSeconds(latestOffsetSeconds);

    // Create a simulation of a zone where there is no explicit transition at Integer.MAX_VALUE
    // seconds.
    {
      long[][] transitions = {
              { 1000, 0 },
              { 2000, 1 },
      };
      ZoneInfoData oldZoneInfoData = createZoneInfoData(transitions, types);
      assertOffsetAt(oldZoneInfoData, expectedLateOffset, timesToCheck);
    }

    // Create a simulation of a zone where there is an explicit transition at Integer.MAX_VALUE
    // seconds.
    {
      long[][] transitions = {
              { 1000, 0 },
              { 2000, 1 },
              { Integer.MAX_VALUE, 1}, // The extra transition.
      };
      ZoneInfoData newZoneInfoData = createZoneInfoData(transitions, types);
      assertOffsetAt(newZoneInfoData, expectedLateOffset, timesToCheck);
    }
  }

  /**
   * Checks that the latest DstSavings in the future/currently is always returned.
   */
  public void testGetLatestDstSavings() throws Exception {
    long[][] transitions = {
            { -5000, 0 },
            { -2000, 2 },
            { 0, 1 },
            { 3000, 3 },
            { 5000, 1 },
    };
    int[][] types = {
            { 3600, 0 },
            { 7200, 0 },
            { 5400, 1 },
            { 10800, 1 },
    };
    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);
    // The last transits from type 3 to 1.
    Duration expectedDstSavings = offsetFromSeconds(types[3][0] - types[1][0]);

    // getLatestDstSavings() returns the same DST savings until the time at 3000s.
    assertDSTSavings(zoneInfoData, expectedDstSavings, timeFromSeconds(-5001));
    assertDSTSavings(zoneInfoData, expectedDstSavings, timeFromSeconds(-5000));
    assertDSTSavings(zoneInfoData, expectedDstSavings, timeFromSeconds(-2000));
    assertDSTSavings(zoneInfoData, expectedDstSavings, timeFromSeconds(0));
    assertDSTSavings(zoneInfoData, expectedDstSavings, timeFromSeconds(3000));

    // TODO: Is it an existing bug/expected behavior that the API reports no DST savings
    // during the last "summer" time?
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(3001));
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(4999));
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(5000).minusMillis(5));

    // No DST savings after the time at 5000s.
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(5000));
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(5001));
    assertNoDSTSavings(zoneInfoData, timeFromSeconds(Integer.MAX_VALUE));
  }

  /**
   * Checks to make sure that ZoneInfoData can handle up to 256 types.
   */
  public void testReadTimeZone_MaxTypeCount() throws Exception {
    long[][] transitions = {
        { -2000, 255 },
    };
    // Create 256 types, each with zero offset and without DST except the last, which is offset by
    // one hour but also without DST.
    int[][] types = new int[256][];
    Arrays.fill(types, new int[2]);
    types[255] = new int[] { 3600, 0 };

    ZoneInfoData zoneInfoData = createZoneInfoData(getName(), transitions, types);

    assertNoDSTSavings(zoneInfoData, timeFromSeconds(Integer.MIN_VALUE));

    // Make sure that WallTime works properly with a ZoneInfoData with 256 types.
    WallTime wallTime = new WallTime();
    wallTime.localtime(0, zoneInfoData);
    wallTime.mktime(zoneInfoData);
  }

  /**
   * Create an instance for every available time zone for which we have data to ensure that they
   * can all be handled correctly.
   *
   * <p>This is to ensure that ZoneInfoData can read all time zone data without failing, it doesn't
   * check that it reads it correctly or that the data itself is correct. This is a confidence test
   * to ensure that any additional checks added to the code that reads the data source and
   * creates the {@link ZoneInfoData} instances does not prevent any of the time zones being loaded.
   */
  public void testReadTimeZone_All() throws Exception {
    ZoneInfoDb instance = ZoneInfoDb.getInstance();
    String[] availableIDs = instance.getAvailableIDs();
    Arrays.sort(availableIDs);
    for (String id : availableIDs) {
      BufferIterator bufferIterator = instance.getBufferIterator(id);

      ZoneInfoData zoneInfoData = ZoneInfoData.readTimeZone(id, bufferIterator);
      assertNotNull("TimeZone " + id + " was not created", zoneInfoData);
      assertEquals(id, zoneInfoData.getID());
      // Make sure that getLatestDstSavings() at the earliest possible time does not crash.
      zoneInfoData.getLatestDstSavingsMillis(Long.MIN_VALUE);
    }
  }

  public void testReadTimeZone_Valid() throws Exception {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid();
    assertNotNull(createZoneInfoData(getName(), builder.build()));
  }

  public void testReadTimeZone_BadMagic() {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setMagic(0xdeadbeef); // Bad magic.
    try {
        createZoneInfoData(getName(), builder.build());
      fail();
    } catch (IOException expected) {}
  }

  /**
   * Checks to make sure that ZoneInfoData rejects more than 256 types.
   */
  public void testReadTimeZone_TooManyTypes() {
    int typeCount = 257; // Max types allowed is 256
    int transitionCount = 5;
    long[][] transitions = createTransitions(transitionCount, typeCount);
    int[][] types = createTypes(typeCount);
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionsAndTypes(transitions, types);
    byte[] bytes = builder.build();
    try {
      createZoneInfoData(getName(), bytes);
      fail("Did not detect too many types");
    } catch (IOException expected) {
    }
  }

  /**
   * Checks to make sure that ZoneInfoData rejects more than 2000 transitions.
   */
  public void testReadTimeZone_TooManyTransitions() {
    int typeCount = 5;
    int transitionCount = 2001; // Max transitions allowed is 2000.
    long[][] transitions = createTransitions(transitionCount, typeCount);
    int[][] types = createTypes(typeCount);
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionsAndTypes(transitions, types);
    byte[] bytes = builder.build();
    try {
      createZoneInfoData(getName(), bytes);
      fail("Did not detect too many transitions");
    } catch (IOException expected) {
    }
  }

  public void testReadTimeZone_TransitionsNotSorted() {
    long[][] transitions = {
            { 1000, 0 },
            { 3000, 1 }, // Out of transition order.
            { 2000, 0 },
    };
    int[][] types = {
            { 3600, 0 },
            { 1800, 1 },
    };

    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionsAndTypes(transitions, types);

    byte[] bytes = builder.build();
    try {
      createZoneInfoData(getName(), bytes);
      fail();
    } catch (IOException expected) {
    }
  }

  public void testReadTimeZone_InvalidTypeIndex() {
    long[][] transitions = {
            { 1000, 0 },
            { 2000, 2 }, // Invalid type index - only 0 and 1 defined below.
            { 3000, 0 },
    };
    int[][] types = {
            { 3600, 0 },
            { 1800, 1 },
    };

    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionsAndTypes(transitions, types);

    byte[] bytes = builder.build();
    try {
      createZoneInfoData(getName(), bytes);
      fail();
    } catch (IOException expected) {
    }
  }

  public void testReadTimeZone_InvalidIsDst() {
    long[][] transitions = {
            { 1000, 0 },
            { 2000, 1 },
            { 3000, 0 },
    };
    int[][] types = {
            { 3600, 0 },
            { 1800, 2 }, // Invalid isDst - must be 0 or 1
    };

    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionsAndTypes(transitions, types);

    byte[] bytes = builder.build();
    try {
      createZoneInfoData(getName(), bytes);
      fail();
    } catch (IOException expected) {
    }
  }

  public void testCreateCopyWithRawOffset() throws Exception {
    long[][] transitions = {
            { 0, 0 },
            { 5, 1 },
    };
    int[][] types = {
            { 1800, 0 },
            { 3600, 1 },
    };
    ZoneInfoData zoneInfoData = createZoneInfoData(transitions, types);
    ZoneInfoData copyWithSameOffset = zoneInfoData.createCopyWithRawOffset(
            zoneInfoData.getRawOffset());
    assertNotSame(zoneInfoData, copyWithSameOffset);
    assertTrue("zoneInfoData does not have the same rule as its copy",
            zoneInfoData.hasSameRules(copyWithSameOffset));

    Duration originalOffset = offsetFromSeconds(1800);
    Duration newOffset = offsetFromSeconds(7200);
    ZoneInfoData copyWithDiffOffset = zoneInfoData.createCopyWithRawOffset(
            (int) newOffset.toMillis());
    assertRawOffset(zoneInfoData, originalOffset);
    assertRawOffset(copyWithDiffOffset, newOffset);
    assertFalse("zoneInfoData has different raw offsets, and should not have the same rules.",
            zoneInfoData.hasSameRules(copyWithDiffOffset));
  }

  public void testSerialization() throws IOException, ClassNotFoundException {
    String tzId = TestSerialization.TZID;
    ZoneInfoData zone = ZoneInfoDb.getInstance().makeZoneInfoData(tzId);
    assertNotNull(zone);
    TestSerialization testObj = new TestSerialization(zone);

    final byte[] data;
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(testObj);
      data = baos.toByteArray();
    }

    final TestSerialization newTestObj;
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
         ObjectInputStream ois = new ObjectInputStream(bais)) {
      newTestObj = (TestSerialization) ois.readObject();
    }

    assertEquals(zone, newTestObj.data);
    assertEquals(testObj, newTestObj);
  }

  private static class TestSerialization implements Serializable {

    private static final String TZID = "Europe/London";

    private static final ObjectStreamField[] serialPersistentFields =
            ZoneInfoData.ZONEINFO_SERIALIZED_FIELDS;

    private ZoneInfoData data;

    private TestSerialization(ZoneInfoData data) {
      this.data = data;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      ObjectInputStream.GetField getField = in.readFields();
      data = ZoneInfoData.createFromSerializationFields(TZID, getField);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      ObjectOutputStream.PutField putField = out.putFields();
      data.writeToSerializationFields(putField);
      out.writeFields();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TestSerialization)) {
        return false;
      }

      return data.equals(((TestSerialization) obj).data);
    }
  }

  private static void assertRawOffset(ZoneInfoData zoneInfoData, Duration expectedOffset) {
    assertEquals(expectedOffset.toMillis(), zoneInfoData.getRawOffset());
  }

  private static void assertNoDSTSavings(ZoneInfoData zoneInfoData, Instant time) {
    assertNull(zoneInfoData.getLatestDstSavingsMillis(time.toEpochMilli()));
  }

  private static void assertDSTSavings(ZoneInfoData zoneInfoData, Duration expectedDSTSavings,
          Instant time) {
    Integer expectedLatestDstSavings = (int) expectedDSTSavings.toMillis();
    assertEquals(expectedLatestDstSavings,
            zoneInfoData.getLatestDstSavingsMillis(time.toEpochMilli()));
  }

  private static void assertInDaylightTime(ZoneInfoData zoneInfoData, Instant time,
      boolean expectedValue) {
    assertEquals(expectedValue, zoneInfoData.isInDaylightTime(time.toEpochMilli()));
  }

  private static void assertOffsetAt(
          ZoneInfoData zoneInfoData, Duration expectedOffset, Instant... times) {
    for (Instant time : times) {
      assertEquals("Unexpected offset at " + time,
              expectedOffset.toMillis(), zoneInfoData.getOffset(time.toEpochMilli()));
    }
  }

  private static void assertOffsetAt( ZoneInfoData zoneInfoData, Duration expectedStandardOffset,
          Duration expectedDstOffset, Instant... times) {
    int[] offsets = new int[2];
    for (Instant time : times) {
      zoneInfoData.getOffsetsByUtcTime(time.toEpochMilli(), offsets);
      assertEquals("Unexpected standard offset at " + time,
              expectedStandardOffset.toMillis(), offsets[0]);
      assertEquals("Unexpected Dst offset at " + time, expectedDstOffset.toMillis(), offsets[1]);
    }
  }

  private static Instant timeFromSeconds(long timeInSeconds) {
    return Instant.ofEpochSecond(timeInSeconds);
  }

  private static long timeToSeconds(Instant time) {
    return time.getEpochSecond();
  }

  private static Duration offsetFromSeconds(int offsetSeconds) {
    return Duration.ofSeconds(offsetSeconds);
  }

  private static int offsetToSeconds(Duration offset) {
    long seconds = offset.getSeconds();
    if (seconds < Integer.MIN_VALUE || seconds > Integer.MAX_VALUE) {
      fail("Offset out of seconds range: " + offset);
    }
    return (int) seconds;
  }

  private ZoneInfoData createZoneInfoData(long[][] transitions, int[][] types) throws Exception {
    return createZoneInfoData(getName(), transitions, types);
  }

  private ZoneInfoData createZoneInfoData(String name, long[][] transitions, int[][] types)
          throws Exception {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .setTransitionsAndTypes(transitions, types);
    return createZoneInfoData(name, builder.build());
  }

  private static ZoneInfoData createZoneInfoData(String name, byte[] bytes)
          throws IOException {
    ByteBufferIterator bufferIterator = new ByteBufferIterator(ByteBuffer.wrap(bytes));
    return ZoneInfoData.readTimeZone("TimeZone for '" + name + "'", bufferIterator);
  }

  private ZoneInfoData createInstance(long[][] transitionMap, int[][] typeMap) {
    long[] transitions = new long[transitionMap.length];
    byte[] types =  new byte[transitionMap.length];
    for (int i = 0; i < transitionMap.length; ++i) {
      transitions[i] = transitionMap[i][0];
      types[i] = (byte) transitionMap[i][1];
    }
    int[] offsets = new int[typeMap.length];
    boolean[] isDsts = new boolean[typeMap.length];
    for (int i = 0; i < typeMap.length; ++i) {
      offsets[i] = typeMap[i][0];
      isDsts[i] = (typeMap[i][1] != 0);
    }

    return ZoneInfoData.createInstance(getName(), transitions, types, offsets, isDsts);
  }

  /**
   * Creates {@code typeCount} "types" for use with
   * {@link ZoneInfoTestHelper.ZicDataBuilder#setTypes(int[][])} and related methods. Each type is
   * given an arbitrary offset and "isDst" value.
   */
  private static int[][] createTypes(int typeCount) {
    int[][] types = new int[typeCount][2];
    for (int i = 0; i < typeCount; i++) {
      // [0] holds the offset from UTC in seconds.
      types[i][0] = typeCount;
      // [1] holds isDst: 0 == STD, 1 == DST
      types[i][1] = typeCount % 2;
    }
    return types;
  }

  /**
   * Creates {@code transitionCount} "transition pairs" for use with
   * {@link ZoneInfoTestHelper.ZicDataBuilder#setTransitions(long[][])} and related methods. Each
   * transition is given an arbitrary (but increasing) time referencing an arbitrary type.
   */
  private static long[][] createTransitions(int transitionCount, int typeCount) {
    long[][] transitions = new long[transitionCount][2];
    for (int i = 0; i < transitionCount; i++) {
      // [0] holds the transition time.
      transitions[i][0] = (i * 3600) + 100;
      // [1] holds the type index to use. Must be > 0 and < typeCount to be valid.
      transitions[i][1] = i % typeCount;
    }
    return transitions;
  }

  /**
   * A {@link BufferIterator} that wraps a {@link ByteBuffer}.
   */
  private static class ByteBufferIterator extends BufferIterator {

    private final ByteBuffer buffer;

    public ByteBufferIterator(ByteBuffer buffer) {
      this.buffer = buffer;
    }

    @Override
    public void seek(int offset) {
      buffer.position(offset);
    }

    @Override
    public void skip(int byteCount) {
      buffer.position(buffer.position() + byteCount);
    }

    @Override
    public int pos() {
      return buffer.position();
    }

    @Override
    public void readByteArray(byte[] bytes, int arrayOffset, int byteCount) {
      buffer.get(bytes, arrayOffset, byteCount);
    }

    @Override
    public byte readByte() {
      return buffer.get();
    }

    @Override
    public int readInt() {
      int value = buffer.asIntBuffer().get();
      // Using a separate view does not update the position of this buffer so do it
      // explicitly.
      skip(Integer.BYTES);
      return value;
    }

    @Override
    public void readIntArray(int[] ints, int arrayOffset, int intCount) {
      buffer.asIntBuffer().get(ints, arrayOffset, intCount);
      // Using a separate view does not update the position of this buffer so do it
      // explicitly.
      skip(Integer.BYTES * intCount);
    }

    @Override
    public void readLongArray(long[] longs, int arrayOffset, int longCount) {
      buffer.asLongBuffer().get(longs, arrayOffset, longCount);
      // Using a separate view does not update the position of this buffer so do it
      // explicitly.
      skip(Long.BYTES * longCount);
    }

    @Override
    public short readShort() {
      short value = buffer.asShortBuffer().get();
      // Using a separate view does not update the position of this buffer so do it
      // explicitly.
      skip(Short.BYTES);
      return value;
    }
  }
}
