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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;

/**
 * A fake annotation used inside {@link Policy} and {@link BedsteadJUnit4} to inject
 * new parameterizations.
 */
public final class DynamicParameterizedAnnotation implements Annotation {
    private final String mName;
    private final Annotation[] mAnnotations;

    DynamicParameterizedAnnotation(String name, Annotation[] annotations) {
        mName = name;
        mAnnotations = Arrays.stream(annotations).filter(Objects::nonNull)
                .toArray(Annotation[]::new);
    }

    /** Get the parameterization name. */
    public String name() {
        return mName;
    }

    /** Get the annotations applied to the parameterization. */
    public Annotation[] annotations() {
        return mAnnotations;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        // This is special cased in BedsteadJUnit4 so will never be called
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicParameterizedAnnotation)) return false;
        DynamicParameterizedAnnotation that = (DynamicParameterizedAnnotation) o;
        return Objects.equals(mName, that.mName) && Arrays.equals(mAnnotations,
                that.mAnnotations);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mName);
        result = 31 * result + Arrays.hashCode(mAnnotations);
        return result;
    }

    @Override
    public String toString() {
        return "DynamicParameterizedAnnotation[" + mName + "]("
                + Arrays.toString(mAnnotations) + ")";
    }
}
