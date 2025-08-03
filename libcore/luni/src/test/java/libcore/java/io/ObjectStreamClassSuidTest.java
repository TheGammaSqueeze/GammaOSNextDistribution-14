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
package libcore.java.io;

import java.io.ObjectStreamClass;
import java.io.ObjectStreamClass.DefaultSUIDCompatibilityListener;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import libcore.junit.util.SwitchTargetSdkVersionRule;
import libcore.junit.util.SwitchTargetSdkVersionRule.TargetSdkVersion;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(JUnitParamsRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ObjectStreamClassSuidTest {

  @Rule
  public TestRule switchTargetSdkVersionRule = SwitchTargetSdkVersionRule.getInstance();

  /**
   * The default SUID for this should not be affected by the b/29064453 patch.
   */
  public static class BaseWithStaticInitializer implements Serializable {
    static {
      System.out.println(
          "Static initializer for " + BaseWithoutStaticInitializer.class.getCanonicalName());
    }
  }

  /**
   * The default SUID for this should not be affected by the b/29064453 patch.
   */
  public static class BaseWithoutStaticInitializer implements Serializable {
  }

  /**
   * The default SUID for this should not be affected by the b/29064453 patch.
   */
  public static class WithStaticInitializer extends BaseWithoutStaticInitializer {
    static {
      System.out.println(
          "Static initializer for " + WithStaticInitializer.class.getCanonicalName());
    }
  }

  /**
   * The default SUID for this should not be affected by the b/29064453 patch.
   */
  public static class WithoutStaticInitializer extends BaseWithoutStaticInitializer {
  }

  /**
   * The default SUID for this should be affected by the b/29064453 patch and so should differ
   * between version <= 23 and version > 23.
   */
  public static class InheritStaticInitializer extends BaseWithStaticInitializer {
  }

  public static Object[][] defaultSUIDs() {
    return new Object[][] {
        // The default SUID for BaseWithStaticInitializer should not be affected by the b/29064453
        // patch.
        { BaseWithStaticInitializer.class, 1857698805282079740L, 1857698805282079740L },

        // The default SUID for BaseWithoutStaticInitializer should not be affected by the
        // b/29064453 patch.
        { BaseWithoutStaticInitializer.class, -4805670618654058372L, -4805670618654058372L },

        // The default SUID for WithStaticInitializer should not be affected by the b/29064453
        // patch.
        { WithStaticInitializer.class, 8758222524306909802L, 8758222524306909802L },

        // The default SUID for WithStaticInitializer should not be affected by the
        // b/29064453 patch.
        { WithoutStaticInitializer.class, -6923417559496792279L, -6923417559496792279L },

        // The default SUID for the InheritStaticInitializer should be affected by the b/29064453
        // patch and so should differ between version <= 23 and version > 23.
        { InheritStaticInitializer.class, 509356435664048990L, -6712883765570708525L },
    };
  }

  @Parameters(method = "defaultSUIDs")
  @Test
  public void computeDefaultSUID_current(Class<?> clazz, long suid,
      @SuppressWarnings("unused") long suid23) {
    checkSerialVersionUID(suid, clazz, false);
  }

  @Parameters(method = "defaultSUIDs")
  @Test
  @TargetSdkVersion(23)
  public void computeDefaultSUID_targetSdkVersion_23(Class<?> clazz, long suid, long suid23) {
    // If the suid and suid23 hashes are different then a warning is expected to be logged.
    boolean expectedWarning = suid23 != suid;
    checkSerialVersionUID(suid23, clazz, expectedWarning);
  }

  private static void checkSerialVersionUID(
      long expectedSUID, Class<?> clazz, boolean expectedWarning) {
    // Use reflection to call the private static computeDefaultSUID method directly to avoid the
    // caching performed by ObjectStreamClass.lookup(Class).
    long defaultSUID;
    DefaultSUIDCompatibilityListener savedListener
        = ObjectStreamClass.suidCompatibilityListener;
    try {
      ObjectStreamClass.suidCompatibilityListener = (c, hash) -> {
        // Delegate to the existing listener so that the warning is logged.
        savedListener.warnDefaultSUIDTargetVersionDependent(clazz, hash);
        if (expectedWarning) {
          assertEquals(clazz, c);
          assertEquals(expectedSUID, hash);
        } else {
          fail("Unexpected warning for " + c + " with defaultSUID " + hash);
        }
      };

      Method computeDefaultSUIDMethod =
          ObjectStreamClass.class.getDeclaredMethod("computeDefaultSUID", Class.class);
      computeDefaultSUIDMethod.setAccessible(true);

      defaultSUID = (Long) computeDefaultSUIDMethod.invoke(null, clazz);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    } finally {
      ObjectStreamClass.suidCompatibilityListener = savedListener;
    }
    assertEquals(expectedSUID, defaultSUID);
  }
}
