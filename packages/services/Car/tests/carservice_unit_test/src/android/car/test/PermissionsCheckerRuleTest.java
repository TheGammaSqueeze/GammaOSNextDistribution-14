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

package android.car.test;

import static android.car.test.JUnitHelper.newTestMethod;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.car.test.JUnitHelper.SimpleStatement;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;


import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

public final class PermissionsCheckerRuleTest extends AbstractExtendedMockitoTestCase {

    private static final String TAG = PermissionsCheckerRuleTest.class.getSimpleName();

    @Mock
    private UiAutomation mUiAutomation;

    private final SimpleStatement<Exception> mBaseStatement = new SimpleStatement<>();

    private PermissionsCheckerRule mRule;

    @Before
    public void setFixtures() {
        mRule = new PermissionsCheckerRule(mUiAutomation);
    }

    // NOTE: no need to override onSessionBuilder() to spy on Log because superclass already does it

    @Test
    public void testNoAnnotation() throws Throwable {
        Description testMethod = newTestMethod();

        mRule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
        verify(mUiAutomation, never()).adoptShellPermissionIdentity(any(String[].class));
        verify(mUiAutomation, never()).dropShellPermissionIdentity();
    }

    @Test
    public void testEnsureHasPermission_onePermission() throws Throwable {
        Description testMethod = newTestMethod(new EnsureHasPermissionAnnotation("To Kill"));

        mRule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
        verify(mUiAutomation).adoptShellPermissionIdentity("To Kill");
        verify(mUiAutomation).dropShellPermissionIdentity();
    }

    @Test
    public void testEnsureHasPermission_onePermissionTestThrows() throws Throwable {
        Description testMethod = newTestMethod(new EnsureHasPermissionAnnotation("To Kill"));
        Throwable exception = new Throwable("D'OH!");
        mBaseStatement.failWith(exception);

        Throwable actualException = assertThrows(Throwable.class,
                () -> mRule.apply(mBaseStatement, testMethod).evaluate());

        assertWithMessage("exception thrown").that(actualException).isSameInstanceAs(exception);
        verify(mUiAutomation).adoptShellPermissionIdentity("To Kill");
        verify(mUiAutomation).dropShellPermissionIdentity();
    }

    @Test
    public void testEnsureHasPermission_multiplePermissions() throws Throwable {
        Description testMethod = newTestMethod(new EnsureHasPermissionAnnotation("To", "Kill"));

        mRule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
        verify(mUiAutomation).adoptShellPermissionIdentity("To", "Kill");
        verify(mUiAutomation).dropShellPermissionIdentity();
    }

    @Test
    public void testEnsureHasPermission_permissionsAdoptedBefore() throws Throwable {
        Description testMethod = newTestMethod(new EnsureHasPermissionAnnotation("To Kill"));
        when(mUiAutomation.getAdoptedShellPermissions()).thenReturn(Set.of("Thou shalt not kill!"));
        ArgumentCaptor<String> logMessage = ArgumentCaptor.forClass(String.class);
        doReturn(666).when(() -> Log.w(eq(PermissionsCheckerRule.TAG), logMessage.capture()));

        mRule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
        verify(mUiAutomation).adoptShellPermissionIdentity("To Kill");
        verify(mUiAutomation).dropShellPermissionIdentity();
        verify(mUiAutomation).adoptShellPermissionIdentity("Thou shalt not kill!");

        assertWithMessage("Log message").that(logMessage.getValue())
                .contains("Thou shalt not kill!");
    }

    @Test
    public void testEnsureHasPermission_annotatedClass() throws Throwable {
        Description testMethod = Description.createTestDescription(SuperClass.class, "testIAm",
                new EnsureHasPermissionAnnotation("To", "Kill"));

        mRule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();

        // Must use a captor as there's no guarantee of the order in the set
        ArgumentCaptor<String[]> permissionsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(mUiAutomation).adoptShellPermissionIdentity(permissionsCaptor.capture());
        assertWithMessage("arguments of adoptShellPermissionIdentity() call")
                .that(permissionsCaptor.getAllValues())
                .containsExactly("SuPermission", "Common", "To", "Kill");
        verify(mUiAutomation).dropShellPermissionIdentity();
    }

    @Test
    public void testEnsureHasPermission_annotatedClassAndParent() throws Throwable {
        Description testMethod = Description.createTestDescription(SubClass.class, "testIAm",
                new EnsureHasPermissionAnnotation("To", "Kill"));

        mRule.apply(mBaseStatement, testMethod).evaluate();


        // Must use a captor as there's no guarantee of the order in the set
        ArgumentCaptor<String[]> permissionsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(mUiAutomation).adoptShellPermissionIdentity(permissionsCaptor.capture());
        assertWithMessage("arguments of adoptShellPermissionIdentity() call")
                .that(permissionsCaptor.getAllValues())
                .containsExactly("SuPermission", "Common", "WhatSub", "To", "Kill");
        verify(mUiAutomation).dropShellPermissionIdentity();
    }

    @EnsureHasPermission(value = {"SuPermission", "Common"})
    private static class SuperClass {

    }

    @EnsureHasPermission(value = {"Common", "WhatSub"})
    private static class SubClass extends SuperClass {

    }

    @SuppressWarnings("BadAnnotationImplementation") // We don't care about equals() / hashCode()
    private static final class EnsureHasPermissionAnnotation implements EnsureHasPermission {

        private final String[] mValue;

        EnsureHasPermissionAnnotation(String... value) {
            mValue = value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return EnsureHasPermission.class;
        }

        @Override
        public String[] value() {
            return mValue;
        }

        @Override
        public String toString() {
            return "@EnsureHasPermissionAnnotation(" + Arrays.toString(mValue) + ")";
        }
    }
}
