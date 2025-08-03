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

package com.android.bedstead.harrier;

import org.junit.runners.model.FrameworkMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/**
 * A {@link FrameworkMethod} which forwards calls to a wrapped {@link FrameworkMethod} except
 * that it injects a parameter and adds the parameter to the name.
 */
public final class FrameworkMethodWithParameter extends FrameworkMethod {
    private final FrameworkMethod mWrappedFrameworkMethod;
    private final Object mInjectedParam;

    public FrameworkMethodWithParameter(FrameworkMethod frameworkMethod, Object injectedParam) {
        super(frameworkMethod.getMethod());
        mWrappedFrameworkMethod = frameworkMethod;
        mInjectedParam = injectedParam;
    }

    @Override
    public boolean isStatic() {
        if (mWrappedFrameworkMethod == null) {
            return super.isStatic();
        }
        return mWrappedFrameworkMethod.isStatic();
    }

    @Override
    public boolean isPublic() {
        if (mWrappedFrameworkMethod == null) {
            return super.isPublic();
        }
        return mWrappedFrameworkMethod.isPublic();
    }

    @Override
    public Method getMethod() {
        return mWrappedFrameworkMethod.getMethod();
    }

    @Override
    public Object invokeExplosively(Object target, Object... params) throws Throwable {
        Object[] allParams = params;
        if (mInjectedParam != null) {
            allParams = new Object[1 + params.length];
            allParams[0] = mInjectedParam;
            System.arraycopy(params, 0, allParams, 1, params.length);
        }

        return mWrappedFrameworkMethod.invokeExplosively(target, allParams);
    }

    @Override
    public String getName() {
        if (mInjectedParam != null) {
            return mWrappedFrameworkMethod.getName() + "[" + mInjectedParam + "]";
        }
        return mWrappedFrameworkMethod.getName();
    }

    @Override
    public void validatePublicVoidNoArg(boolean isStatic, List<Throwable> errors) {
        mWrappedFrameworkMethod.validatePublicVoidNoArg(isStatic, errors);
    }

    @Override
    public void validatePublicVoid(boolean isStatic, List<Throwable> errors) {
        mWrappedFrameworkMethod.validatePublicVoid(isStatic, errors);
    }

    @Override
    public Class<?> getReturnType() {
        return mWrappedFrameworkMethod.getReturnType();
    }

    @Override
    public Class<?> getType() {
        return mWrappedFrameworkMethod.getType();
    }

    @Override
    public Class<?> getDeclaringClass() {
        return mWrappedFrameworkMethod.getDeclaringClass();
    }

    @Override
    public void validateNoTypeParametersOnArgs(List<Throwable> errors) {
        mWrappedFrameworkMethod.validateNoTypeParametersOnArgs(errors);
    }

    @Override
    public boolean isShadowedBy(FrameworkMethod other) {
        return mWrappedFrameworkMethod.isShadowedBy(other);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof FrameworkMethodWithParameter) {
            FrameworkMethodWithParameter other = (FrameworkMethodWithParameter) obj;
            return Objects.equals(mWrappedFrameworkMethod, other.mWrappedFrameworkMethod)
                    && Objects.equals(mInjectedParam, other.mInjectedParam);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWrappedFrameworkMethod, mInjectedParam);
    }

    @Override
    public boolean producesType(Type type) {
        return mWrappedFrameworkMethod.producesType(type);
    }

    @Override
    public Annotation[] getAnnotations() {
        return mWrappedFrameworkMethod.getAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return mWrappedFrameworkMethod.getAnnotation(annotationType);
    }
}
