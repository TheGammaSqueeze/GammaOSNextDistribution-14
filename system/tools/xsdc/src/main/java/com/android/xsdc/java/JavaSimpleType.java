/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.xsdc.java;

class JavaSimpleType implements JavaType {
    final private String name;
    final private String nullableName;
    final private String rawParsingExpression;
    final private String rawWritingExpression;
    final private boolean list;
    final private String fullName;
    final private String nullableFullName;

    JavaSimpleType(String name, String nullableName, String rawParsingExpression,
            String rawWritingExpression, boolean list) {
        this.name = name;
        this.nullableName = nullableName;
        this.rawParsingExpression = rawParsingExpression;
        this.rawWritingExpression = rawWritingExpression;
        this.list = list;
        fullName = list ? String.format("java.util.List<%s>", nullableName) : name;
        nullableFullName = list ? String.format("java.util.List<%s>", nullableName) : nullableName;
    }

    JavaSimpleType(String name, String nullableName, String rawParsingExpression, boolean list) {
        this(name, nullableName, rawParsingExpression, "%s", list);
    }

    JavaSimpleType(String name, String rawParsingExpression, boolean list) {
        this(name, name, rawParsingExpression, list);
    }

    boolean isList() {
        return list;
    }

    JavaSimpleType newListType() throws JavaCodeGeneratorException {
        if (list) throw new JavaCodeGeneratorException("list of list is not supported");
        return new JavaSimpleType(name, nullableName, rawParsingExpression, rawWritingExpression,
                true);
    }

    @Override
    public String getName() {
        return fullName;
    }

    @Override
    public boolean isPrimitiveType() {
        return !fullName.equals(nullableName);
    }

    @Override
    public String getNullableName() {
        return nullableFullName;
    }

    @Override
    public String getParsingExpression() {
        StringBuilder expression = new StringBuilder();
        if (list) {
            expression.append(
                    String.format("%s _value = new java.util.ArrayList<>();\n", getName()));
            expression.append("for (String _token : _raw.split(\"\\\\s+\")) {\n");
            expression.append(String.format("_value.add(%s);\n",
                    String.format(rawParsingExpression, "_token")));
            expression.append("}\n");
        } else {
            expression.append(
                    String.format("%s _value = %s;\n", getName(),
                            String.format(rawParsingExpression, "_raw")));
        }
        return expression.toString();
    }

    @Override
    public String getWritingExpression(String getValue, String name) {
        StringBuilder expression = new StringBuilder();
        if (list) {
            expression.append("{\nint _count = 0;\n");
            expression.append(String.format("for (%s v : %s) {\n", this.name, getValue));
            expression.append("if (_count != 0) {\n"
                    + "_out.print(\" \");\n}\n"
                    + "++_count;\n");
            expression.append(String.format("_out.print(%s);\n}\n",
                    String.format(rawWritingExpression, "v")));
            expression.append("}\n");
        } else {
            expression.append(String.format("_out.print(%s);\n",
                    String.format(rawWritingExpression, getValue)));
        }
        return expression.toString();
    }
}
