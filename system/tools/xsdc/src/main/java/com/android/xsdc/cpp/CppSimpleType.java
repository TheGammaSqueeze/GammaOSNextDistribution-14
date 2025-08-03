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

package com.android.xsdc.cpp;

class CppSimpleType implements CppType {
    final private String name;
    final private String fullName;
    final private String rawParsingExpression;
    final private boolean list;
    final private boolean isEnum;

    CppSimpleType(String name, String rawParsingExpression, boolean list, boolean isEnum) {
        this.rawParsingExpression = rawParsingExpression;
        this.list = list;
        this.name = name;
        this.fullName = list ? String.format("std::vector<%s>", name) : name;
        this.isEnum = isEnum;
    }

    CppSimpleType(String name, String rawParsingExpression, boolean list) {
        this(name, rawParsingExpression, list, false);
    }

    boolean isList() {
        return list;
    }

    boolean isEnum() {
        return isEnum;
    }

    CppSimpleType newListType() throws CppCodeGeneratorException {
        if (list) throw new CppCodeGeneratorException("list of list is not supported");
        return new CppSimpleType(name, rawParsingExpression, true);
    }

    public String getTypeName() {
        return name;
    }

    @Override
    public String getName() {
        return fullName;
    }

    @Override
    public String getParsingExpression() {
        StringBuilder expression = new StringBuilder();
        if (list) {
            expression.append(
                    String.format("%s _value;\n", getName()));
            expression.append(String.format("{\n"
                    + "std::istringstream _stream(_raw);\n"
                    + "for(std::string str; _stream >> str; ) {\n"
                    + "    _value.push_back(%s);\n"
                    + "}\n",
                    String.format(rawParsingExpression, "str")));
            expression.append("}\n");
        } else {
            expression.append(
                    String.format("%s %s_value = %s;\n", getName(),
                            this.name.equals("std::string") ? "&" : "",
                            String.format(rawParsingExpression, "_raw")));
        }
        return expression.toString();
    }

    @Override
    public String getWritingExpression(String getValue, String name) {
        StringBuilder expression = new StringBuilder();
        if (list) {
            expression.append("{\nint _count = 0;\n");
            expression.append(String.format("for (const auto& _v : %s) {\n", getValue));
            String value;
            if (isEnum) {
                value = String.format("%sToString(_v)", this.name);
            } else if (this.name.equals("char") || this.name.equals("unsigned char")) {
                value = "(int)_v";
            } else if (this.name.equals("bool")) {
                value = "(_v ? \"true\" : \"false\")";
            } else {
                value = "_v";
            }
            expression.append("if (_count != 0) {\n"
                    + "_out << \" \";\n}\n"
                    + "++_count;\n");
            expression.append(String.format("_out << %s;\n}\n}\n", value));
        } else {
            if (isEnum) {
                expression.append(String.format("_out << toString(%s);\n", getValue));
            } else if (this.name.equals("char") || this.name.equals("unsigned char")) {
                expression.append(String.format("_out << (int)%s;\n", getValue));
            } else if (this.name.equals("bool")) {
                expression.append(String.format("_out << (%s ? \"true\" : \"false\");\n", getValue));
            } else {
                expression.append(String.format("_out << %s;\n", getValue));
            }
        }
        return expression.toString();
    }
}
