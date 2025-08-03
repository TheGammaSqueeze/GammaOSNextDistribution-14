/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.annotationvisitor;

import com.google.common.base.Preconditions;

import org.apache.bcel.classfile.AnnotationElementValue;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ArrayElementValue;
import org.apache.bcel.classfile.ElementValue;
import org.apache.bcel.classfile.ElementValuePair;

/**
 * Handles a repeated annotation container.
 *
 * <p>The enclosed annotations are passed to the {@link #mWrappedHandler}.
 */
public class RepeatedAnnotationHandler extends AnnotationHandler {

    private static final String VALUE = "value";

    private final AnnotationHandler mWrappedHandler;
    private final String mInnerAnnotationName;

    public RepeatedAnnotationHandler(String innerAnnotationName, AnnotationHandler wrappedHandler) {
        mWrappedHandler = wrappedHandler;
        mInnerAnnotationName = innerAnnotationName;
    }

    @Override
    public void handleAnnotation(AnnotationEntry annotation, AnnotationContext context) {
        // Verify that the annotation has the form we expect
        ElementValuePair value = findValue(annotation);
        if (value == null) {
            context.reportError("No value found on %s", annotation.getAnnotationType());
            return;
        }
        Preconditions.checkArgument(value.getValue() instanceof ArrayElementValue);
        ArrayElementValue array = (ArrayElementValue) value.getValue();

        // call wrapped handler on each enclosed annotation:
        for (ElementValue v : array.getElementValuesArray()) {
            Preconditions.checkArgument(v instanceof AnnotationElementValue);
            AnnotationElementValue aev = (AnnotationElementValue) v;
            Preconditions.checkArgument(
                    aev.getAnnotationEntry().getAnnotationType().equals(mInnerAnnotationName));
            mWrappedHandler.handleAnnotation(aev.getAnnotationEntry(), context);
        }
    }

    private ElementValuePair findValue(AnnotationEntry a) {
        for (ElementValuePair property : a.getElementValuePairs()) {
            if (property.getNameString().equals(VALUE)) {
                return property;
            }
        }
        // not found
        return null;
    }
}
