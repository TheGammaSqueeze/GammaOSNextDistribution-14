/*
 * Copyright (C) 2010 The Android Open Source Project
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

package libcore.java.util;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class ServiceLoaderTest extends junit.framework.TestCase {
  public static interface UnimplementedInterface { }
  public void test_noImplementations() {
    assertFalse(ServiceLoader.load(UnimplementedInterface.class).iterator().hasNext());
  }

  public static class Impl1 implements ServiceLoaderTestInterface { }
  public static class Impl2 implements ServiceLoaderTestInterface { }
  public void test_implementations() {
    ServiceLoader<ServiceLoaderTestInterface> loader = ServiceLoader.load(ServiceLoaderTestInterface.class);
    Iterator<ServiceLoaderTestInterface> it = loader.iterator();
    assertTrue(it.hasNext());
    assertTrue(it.next() instanceof Impl1);
    assertTrue(it.hasNext());
    assertTrue(it.next() instanceof Impl2);
    assertFalse(it.hasNext());
  }

  public void test_findFirst_nonEmptyLoader() {
    ServiceLoader<ServiceLoaderTestInterface> loader
            = ServiceLoader.load(ServiceLoaderTestInterface.class);

    var first = loader.findFirst();

    assertTrue(first.isPresent());
    assertTrue(first.get() instanceof Impl1);
  }

  public void test_findFirst_emptyLoader() {
    var serviceLoader = ServiceLoader.load(UnimplementedInterface.class);

    assertFalse(serviceLoader.findFirst().isPresent());
  }

  public void test_stream_emptyLoader() {
    var serviceLoader = ServiceLoader.load(UnimplementedInterface.class);

    var result = serviceLoader.stream().toList();

    assertTrue("Following services were found: " + result, result.isEmpty());
  }

  public void test_streamTypes_nonEmptyLoader() {
    var serviceLoader = ServiceLoader.load(ServiceLoaderTestInterface.class);

    var providerTypes = serviceLoader.stream().map(ServiceLoader.Provider::type).toList();

    assertEquals(List.of(Impl1.class, Impl2.class), providerTypes);
  }

  public void test_stream_providerGet_nonEmptyLoader() {
    var serviceLoader = ServiceLoader.load(ServiceLoaderTestInterface.class);

    var providedTypes = serviceLoader.stream()
            .map(ServiceLoader.Provider::get)
            .map(Object::getClass)
            .toList();

    assertEquals(List.of(Impl1.class, Impl2.class), providedTypes);
  }

  public void testLoadInstalled() {
    // We need to register a service in the System or Boot class loader in order to test this
    // properly. We only test the negative case here.
    ServiceLoader<ServiceLoaderTestInterface> loader = ServiceLoader.loadInstalled(
            ServiceLoaderTestInterface.class);
    Iterator<ServiceLoaderTestInterface> it = loader.iterator();
    // Impl1 and Impl2 are not in the System or Boot class loader.
    // Thus, ServiceLoader.loadInstalled shouldn't load any of them.
    assertFalse(it.hasNext());
  }

  // Something like "does.not.Exist", that is a well-formed class name, but just doesn't exist.
  public void test_missingRegisteredClass() throws Exception {
    try {
      ServiceLoader.load(ServiceLoaderTestInterfaceMissingClass.class).iterator().next();
      fail();
    } catch (ServiceConfigurationError expected) {
      assertTrue(expected.toString(), expected.getCause() instanceof ClassNotFoundException);
    }
  }

  // Something like "java.lang.String", that is a class that exists,
  // but doesn't implement the interface.
  public void test_wrongTypeRegisteredClass() throws Exception {
    try {
      ServiceLoader.load(ServiceLoaderTestInterfaceWrongType.class).iterator().next();
      fail();
    } catch (ServiceConfigurationError expected) {
      assertTrue(expected.toString(), expected.getCause() instanceof ClassCastException);
    }
  }

  // Something like "This is not a class name!" that's just a parse error.
  public void test_invalidRegisteredClass() throws Exception {
    try {
      ServiceLoader.load(ServiceLoaderTestInterfaceParseError.class).iterator().next();
      fail();
    } catch (ServiceConfigurationError expected) {
    }
  }
}
