/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.Property;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import androidx.test.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Objects;

@RunWith(JUnit4.class)
@AppModeFull(reason = "Instant applications cannot install other packages")
@AppModeNonSdkSandbox(reason = "Sandboxed SDKs cannot install other packages")
public class PackageManagerQueryPropertyTest {

    private static PackageManager sPackageManager;
    private static final String PROPERTY_APP1_PACKAGE_NAME = "com.android.cts.packagepropertyapp1";
    private static final String PROPERTY_APP2_PACKAGE_NAME = "com.android.cts.packagepropertyapp2";
    private static final String PROPERTY_APP3_PACKAGE_NAME = "com.android.cts.packagepropertyapp3";
    private static final TestApp PROPERTY_APP1 =
            new TestApp("PackagePropertyTestApp1", PROPERTY_APP1_PACKAGE_NAME, 30,
                    false, "PackagePropertyTestApp1.apk");
    private static final TestApp PROPERTY_APP2 =
            new TestApp("PackagePropertyTestApp2", PROPERTY_APP2_PACKAGE_NAME, 30,
                    false, "PackagePropertyTestApp2.apk");
    private static final TestApp PROPERTY_APP3 =
            new TestApp("PackagePropertyTestApp3", PROPERTY_APP3_PACKAGE_NAME, 30,
                    false, "PackagePropertyTestApp3.apk");
    private static void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES,
                        Manifest.permission.USE_SYSTEM_DATA_LOADERS);
    }

    private static void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @BeforeClass
    public static void setupClass() throws Exception {
        sPackageManager = InstrumentationRegistry
                .getInstrumentation()
                .getContext()
                .getPackageManager();
    }

    @Before
    public void setup() throws Exception {
        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP1_PACKAGE_NAME);
        Uninstall.packages(PROPERTY_APP2_PACKAGE_NAME);
        Uninstall.packages(PROPERTY_APP3_PACKAGE_NAME);
        Install.single(PROPERTY_APP1).commit();
        Install.single(PROPERTY_APP2).commit();
        dropShellPermissions();
    }

    @After
    public void teardown() throws Exception {
        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP1_PACKAGE_NAME);
        Uninstall.packages(PROPERTY_APP2_PACKAGE_NAME);
        Uninstall.packages(PROPERTY_APP3_PACKAGE_NAME);
        dropShellPermissions();
    }

    @Test
    public void testQueryApplicationProperties() throws Exception {
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryApplicationProperty("android.cts.PROPERTY_RESOURCE_XML");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_RESOURCE_XML",
                    PROPERTY_APP1_PACKAGE_NAME,
                    null,
                    PROPERTY_TYPE_RESOURCE, 0x7f060000);
            assertProperty(testPropertyList, "android.cts.PROPERTY_RESOURCE_XML",
                    PROPERTY_APP2_PACKAGE_NAME,
                    null,
                    PROPERTY_TYPE_RESOURCE, 0x7f060000);
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryApplicationProperty("android.cts.PROPERTY_INTEGER_VIA_RESOURCE");
            assertEquals(1, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_INTEGER_VIA_RESOURCE",
                    PROPERTY_APP1_PACKAGE_NAME,
                    null,
                    PROPERTY_TYPE_INTEGER, 123);
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryApplicationProperty("android.cts.PROPERTY_STRING2");
            assertEquals(1, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_STRING2",
                    PROPERTY_APP2_PACKAGE_NAME,
                    null,
                    PROPERTY_TYPE_STRING, "giraffe");
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryApplicationProperty("android.cts.PROPERTY_DOES_NOT_EXIST");
            assertEquals(0, testPropertyList.size());
        }
    }

    @Test
    public void testQueryActivityProperties() throws Exception {
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryActivityProperty("android.cts.PROPERTY_ACTIVITY");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_ACTIVITY",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivity",
                    PROPERTY_TYPE_INTEGER, 123);
            assertProperty(testPropertyList, "android.cts.PROPERTY_ACTIVITY",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivity",
                    PROPERTY_TYPE_INTEGER, 123);
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryActivityProperty("android.cts.PROPERTY_ACTIVITY_ALIAS");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_ACTIVITY_ALIAS",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivityAlias",
                    PROPERTY_TYPE_INTEGER, 123);
            assertProperty(testPropertyList, "android.cts.PROPERTY_ACTIVITY_ALIAS",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivityAlias",
                    PROPERTY_TYPE_INTEGER, 123);
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryActivityProperty("android.cts.PROPERTY_COMPONENT");
            assertEquals(3, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_COMPONENT",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivity",
                    PROPERTY_TYPE_INTEGER, 123);
            assertProperty(testPropertyList, "android.cts.PROPERTY_COMPONENT",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivityAlias",
                    PROPERTY_TYPE_INTEGER, 123);
            assertProperty(testPropertyList, "android.cts.PROPERTY_COMPONENT",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivityAlias",
                    PROPERTY_TYPE_BOOLEAN, true);
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryActivityProperty("android.cts.PROPERTY_STRING");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_STRING",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivity",
                    PROPERTY_TYPE_STRING, "koala activity");
            assertProperty(testPropertyList, "android.cts.PROPERTY_STRING",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyActivity",
                    PROPERTY_TYPE_STRING, "giraffe");
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryActivityProperty("android.cts.PROPERTY_SERVICE");
            assertEquals(0, testPropertyList.size());
        }
    }

    @Test
    public void testQueryProviderProperties() throws Exception {
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryProviderProperty("android.cts.PROPERTY_PROVIDER");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_PROVIDER",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyProvider",
                    PROPERTY_TYPE_INTEGER, 123);
            assertProperty(testPropertyList, "android.cts.PROPERTY_PROVIDER",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyProvider",
                    PROPERTY_TYPE_STRING, "giraffe");
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryProviderProperty("android.cts.PROPERTY_SERVICE");
            assertEquals(0, testPropertyList.size());
        }
    }

    @Test
    public void testQueryReceiverProperties() throws Exception {
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryReceiverProperty("android.cts.PROPERTY_RECEIVER");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_RECEIVER",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyReceiver",
                    PROPERTY_TYPE_INTEGER, 123);
            assertProperty(testPropertyList, "android.cts.PROPERTY_RECEIVER",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyReceiver",
                    PROPERTY_TYPE_INTEGER, 123);
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryReceiverProperty("android.cts.PROPERTY_STRING");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_STRING",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyReceiver",
                    PROPERTY_TYPE_STRING, "koala receiver");
            assertProperty(testPropertyList, "android.cts.PROPERTY_STRING",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyReceiver",
                    PROPERTY_TYPE_STRING, "koala receiver");
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryReceiverProperty("android.cts.PROPERTY_SERVICE");
            assertEquals(0, testPropertyList.size());
        }
    }

    @Test
    public void testQueryServiceProperties() throws Exception {
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryServiceProperty("android.cts.PROPERTY_SERVICE");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_SERVICE",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyService",
                    PROPERTY_TYPE_INTEGER, 123);
            assertProperty(testPropertyList, "android.cts.PROPERTY_SERVICE",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyService",
                    PROPERTY_TYPE_INTEGER, 123);
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryServiceProperty("android.cts.PROPERTY_COMPONENT");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_COMPONENT",
                    PROPERTY_APP1_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyService",
                    PROPERTY_TYPE_RESOURCE, 0x7f040000);
            assertProperty(testPropertyList, "android.cts.PROPERTY_COMPONENT",
                    PROPERTY_APP2_PACKAGE_NAME,
                    "com.android.cts.packagepropertyapp.MyService",
                    PROPERTY_TYPE_INTEGER, 123);
        }
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryServiceProperty("android.cts.PROPERTY_ACTIVITY");
            assertEquals(0, testPropertyList.size());
        }
    }

    @Test
    public void testPackageRemoval() throws Exception {
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryApplicationProperty("android.cts.PROPERTY_RESOURCE_XML");
            assertEquals(2, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_RESOURCE_XML",
                    PROPERTY_APP1_PACKAGE_NAME,
                    null,
                    PROPERTY_TYPE_RESOURCE, 0x7f060000);
            assertProperty(testPropertyList, "android.cts.PROPERTY_RESOURCE_XML",
                    PROPERTY_APP2_PACKAGE_NAME,
                    null,
                    PROPERTY_TYPE_RESOURCE, 0x7f060000);
        }

        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP2_PACKAGE_NAME);
        dropShellPermissions();
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryApplicationProperty("android.cts.PROPERTY_RESOURCE_XML");
            assertEquals(1, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_RESOURCE_XML",
                    PROPERTY_APP1_PACKAGE_NAME,
                    null,
                    PROPERTY_TYPE_RESOURCE, 0x7f060000);
        }

        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP1_PACKAGE_NAME);
        Install.single(PROPERTY_APP2).commit();
        dropShellPermissions();
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryApplicationProperty("android.cts.PROPERTY_RESOURCE_XML");
            assertEquals(1, testPropertyList.size());
            assertProperty(testPropertyList, "android.cts.PROPERTY_RESOURCE_XML",
                    PROPERTY_APP2_PACKAGE_NAME,
                    null,
                    PROPERTY_TYPE_RESOURCE, 0x7f060000);
        }

        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP2_PACKAGE_NAME);
        dropShellPermissions();
        {
            final List<Property> testPropertyList = sPackageManager
                    .queryApplicationProperty("android.cts.PROPERTY_STRING2");
            assertEquals(0, testPropertyList.size());
        }
    }

    @Test
    public void testInvalidArguments() throws Exception {
        try {
            final List<Property> testPropertyList = sPackageManager.queryApplicationProperty(null);
            fail("queryApplicationProperty() did not throw NullPointerException");
        } catch (NullPointerException expected) {
        }
        try {
            final List<Property> testPropertyList = sPackageManager.queryActivityProperty(null);
            fail("queryActivityProperty() did not throw NullPointerException");
        } catch (NullPointerException expected) {
        }
        try {
            final List<Property> testPropertyList = sPackageManager.queryProviderProperty(null);
            fail("queryProviderProperty() did not throw NullPointerException");
        } catch (NullPointerException expected) {
        }
        try {
            final List<Property> testPropertyList = sPackageManager.queryReceiverProperty(null);
            fail("queryReceiverProperty() did not throw NullPointerException");
        } catch (NullPointerException expected) {
        }
        try {
            final List<Property> testPropertyList = sPackageManager.queryServiceProperty(null);
            fail("queryServiceProperty() did not throw NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    private static final int PROPERTY_TYPE_BOOLEAN = 1;
    private static final int PROPERTY_TYPE_FLOAT = 2;
    private static final int PROPERTY_TYPE_INTEGER = 3;
    private static final int PROPERTY_TYPE_RESOURCE = 4;
    private static final int PROPERTY_TYPE_STRING = 5;
    private void assertProperty(Property testProperty, String propertyName,
            String packageName, String className, int propertyType, Object propertyValue) {
        assertNotNull(testProperty);
        assertEquals(propertyName, testProperty.getName());
        assertEquals(packageName, testProperty.getPackageName());
        assertTrue(Objects.equals(className, testProperty.getClassName()));
        assertEquals(propertyType, testProperty.getType());

        if (propertyType == PROPERTY_TYPE_BOOLEAN) {
            assertTrue(testProperty.isBoolean());
            assertFalse(testProperty.isFloat());
            assertFalse(testProperty.isInteger());
            assertFalse(testProperty.isResourceId());
            assertFalse(testProperty.isString());

            // assert the property's type is set correctly
            final Boolean boolValue = (Boolean) propertyValue;
            if (boolValue.booleanValue()) {
                assertTrue(testProperty.getBoolean());
            } else {
                assertFalse(testProperty.getBoolean());
            }
            // assert the other values have an appropriate default
            assertEquals(0.0f, testProperty.getFloat(), 0.0f);
            assertEquals(0, testProperty.getInteger());
            assertEquals(0, testProperty.getResourceId());
            assertEquals(null, testProperty.getString());
        } else if (propertyType == PROPERTY_TYPE_FLOAT) {
            assertFalse(testProperty.isBoolean());
            assertTrue(testProperty.isFloat());
            assertFalse(testProperty.isInteger());
            assertFalse(testProperty.isResourceId());
            assertFalse(testProperty.isString());

            // assert the property's type is set correctly
            final Float floatValue = (Float) propertyValue;
            assertEquals(floatValue.floatValue(), testProperty.getFloat(), 0.0f);
            // assert the other values have an appropriate default
            assertFalse(testProperty.getBoolean());
            assertEquals(0, testProperty.getInteger());
            assertEquals(0, testProperty.getResourceId());
            assertEquals(null, testProperty.getString());
        } else if (propertyType == PROPERTY_TYPE_INTEGER) {
            assertFalse(testProperty.isBoolean());
            assertFalse(testProperty.isFloat());
            assertTrue(testProperty.isInteger());
            assertFalse(testProperty.isResourceId());
            assertFalse(testProperty.isString());

            // assert the property's type is set correctly
            final Integer integerValue = (Integer) propertyValue;
            assertEquals(integerValue.intValue(), testProperty.getInteger());
            // assert the other values have an appropriate default
            assertFalse(testProperty.getBoolean());
            assertEquals(0.0f, testProperty.getFloat(), 0.0f);
            assertEquals(0, testProperty.getResourceId());
            assertEquals(null, testProperty.getString());
        } else if (propertyType == PROPERTY_TYPE_RESOURCE) {
            assertFalse(testProperty.isBoolean());
            assertFalse(testProperty.isFloat());
            assertFalse(testProperty.isInteger());
            assertTrue(testProperty.isResourceId());
            assertFalse(testProperty.isString());

            // assert the property's type is set correctly
            final Integer resourceValue = (Integer) propertyValue;
            assertEquals(resourceValue.intValue(), testProperty.getResourceId());
            // assert the other values have an appropriate default
            assertFalse(testProperty.getBoolean());
            assertEquals(0.0f, testProperty.getFloat(), 0.0f);
            assertEquals(0, testProperty.getInteger());
            assertEquals(null, testProperty.getString());
        } else if (propertyType == PROPERTY_TYPE_STRING) {
            assertFalse(testProperty.isBoolean());
            assertFalse(testProperty.isFloat());
            assertFalse(testProperty.isInteger());
            assertFalse(testProperty.isResourceId());
            assertTrue(testProperty.isString());

            // assert the property's type is set correctly
            final String stringValue = (String) propertyValue;
            assertEquals(stringValue, testProperty.getString());
            // assert the other values have an appropriate default
            assertFalse(testProperty.getBoolean());
            assertEquals(0.0f, testProperty.getFloat(), 0.0f);
            assertEquals(0, testProperty.getInteger());
            assertEquals(0, testProperty.getResourceId());
        } else {
            fail("Unknown property type");
        }
    }

    private void assertProperty(List<Property> properties, String propertyName,
            String packageName, String className, int propertyType, Object propertyValue) {
        int match = 0;
        for (int i = properties.size() - 1; i >= 0; i--) {
            final Property property = properties.get(i);
            try {
                assertProperty(property, propertyName, packageName, className, propertyType,
                        propertyValue);
                match++;
            } catch (Throwable ignore) {
            }
        }
        assertEquals("Property"
                + "; name: " + propertyName
                + ", package: " + packageName
                + ", class: " + className, 1, match);
    }
}
