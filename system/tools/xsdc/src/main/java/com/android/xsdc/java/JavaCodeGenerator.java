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

import com.android.xsdc.CodeWriter;
import com.android.xsdc.FileSystem;
import com.android.xsdc.XmlSchema;
import com.android.xsdc.XsdConstants;
import com.android.xsdc.tag.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

public class JavaCodeGenerator {
    private XmlSchema xmlSchema;
    private String packageName;
    private Map<String, JavaSimpleType> javaSimpleTypeMap;
    private boolean writer;
    private boolean showNullability;
    private boolean generateHasMethod;
    private boolean useHexBinary;
    private boolean booleanGetter;
    private String[] rootElements;

    public JavaCodeGenerator(XmlSchema xmlSchema, String packageName, boolean writer,
            boolean showNullability, boolean generateHasMethod, boolean booleanGetter,
            String[] rootElements)
            throws JavaCodeGeneratorException {
        this.xmlSchema = xmlSchema;
        this.packageName = packageName;
        this.writer = writer;
        this.showNullability = showNullability;
        this.generateHasMethod = generateHasMethod;
        this.booleanGetter = booleanGetter;
        this.rootElements = rootElements;
        useHexBinary = false;

        // class naming validation
        {
            Set<String> nameSet = new HashSet<>();
            nameSet.add("XmlParser");
            for (XsdType type : xmlSchema.getTypeMap().values()) {
                if ((type instanceof XsdComplexType) || (type instanceof XsdRestriction &&
                        ((XsdRestriction)type).getEnums() != null)) {
                    String name = Utils.toClassName(type.getName());
                    if (nameSet.contains(name)) {
                        throw new JavaCodeGeneratorException(
                                String.format("duplicate class name : %s", name));
                    }
                    nameSet.add(name);
                }
            }
            for (XsdElement element : xmlSchema.getElementMap().values()) {
                XsdType type = element.getType();
                if (type.getRef() == null && type instanceof XsdComplexType) {
                    String name = Utils.toClassName(element.getName());
                    if (nameSet.contains(name)) {
                        throw new JavaCodeGeneratorException(
                                String.format("duplicate class name : %s", name));
                    }
                    nameSet.add(name);
                }
            }
        }

        javaSimpleTypeMap = new HashMap<>();
        for (XsdType type : xmlSchema.getTypeMap().values()) {
            if (type instanceof XsdSimpleType) {
                XsdType refType = new XsdType(null, new QName(type.getName()));
                parseSimpleType(refType, true);
            }
        }
    }

    public void print(FileSystem fs)
            throws JavaCodeGeneratorException, IOException {
        for (XsdType type : xmlSchema.getTypeMap().values()) {
            if (type instanceof XsdComplexType) {
                String name = Utils.toClassName(type.getName());
                XsdComplexType complexType = (XsdComplexType) type;
                try (CodeWriter out = new CodeWriter(fs.getPrintWriter(name + ".java"))) {
                    out.printf("package %s;\n\n", packageName);
                    printClass(out, name, complexType, "");
                }
            } else if (type instanceof XsdRestriction &&
                    ((XsdRestriction)type).getEnums() != null) {
                String name = Utils.toClassName(type.getName());
                XsdRestriction restrictionType = (XsdRestriction) type;
                try (CodeWriter out = new CodeWriter(fs.getPrintWriter(name + ".java"))) {
                    out.printf("package %s;\n\n", packageName);
                    printEnumClass(out, name, restrictionType);
                }
            }
        }
        for (XsdElement element : xmlSchema.getElementMap().values()) {
            XsdType type = element.getType();
            if (type.getRef() == null && type instanceof XsdComplexType) {
                String name = Utils.toClassName(element.getName());
                XsdComplexType complexType = (XsdComplexType) type;
                try (CodeWriter out = new CodeWriter(fs.getPrintWriter(name + ".java"))) {
                    out.printf("package %s;\n\n", packageName);
                    printClass(out, name, complexType, "");
                }
            }
        }
        try (CodeWriter out = new CodeWriter(fs.getPrintWriter("XmlParser.java"))) {
            printXmlParser(out);
        }
        if (writer) {
            try (CodeWriter out = new CodeWriter(fs.getPrintWriter("XmlWriter.java"))) {
                printXmlWriter(out);
            }
        }
        if (useHexBinary) {
            try (CodeWriter out = new CodeWriter(fs.getPrintWriter("HexBinaryHelper.java"))) {
                printHexBinaryHelper(out);
            }
        }
    }

    private void printEnumClass(CodeWriter out, String name, XsdRestriction restrictionType)
            throws JavaCodeGeneratorException {
        if (restrictionType.isDeprecated()) {
            out.printf("@java.lang.Deprecated\n");
        }
        out.printf("public enum %s {", name);
        List<XsdEnumeration> enums = restrictionType.getEnums();

        for (XsdEnumeration tag : enums) {
            if (tag.isDeprecated()) {
                out.printf("@java.lang.Deprecated\n");
            }
            String value = tag.getValue();
            out.printf("\n%s(\"%s\"),", Utils.toEnumName(value), value);
        }
        out.printf(";\n\n");
        out.printf("private final String rawName;\n\n");
        out.printf("%s(%sString rawName) {\n"
                + "this.rawName = rawName;\n"
                + "}\n\n", name, getDefaultNullability(Nullability.NON_NULL));
        out.printf("public %sString getRawName() {\n"
                + "return rawName;\n"
                + "}\n\n", getDefaultNullability(Nullability.NON_NULL));

        out.printf("static %s%s fromString(%sString rawString) {\n"
                + "for (%s _f : values()) {\n"
                + "if (_f.getRawName().equals(rawString)) {\n"
                + "return _f;\n"
                + "}\n"
                + "}\n"
                + "throw new IllegalArgumentException(rawString);\n"
                + "}\n\n", getDefaultNullability(Nullability.NULLABLE), name,
                getDefaultNullability(Nullability.NON_NULL), name);

        if (writer) {
            out.printf("@Override\n"
                    + "public %sString toString() {\n"
                    + "return rawName;\n"
                    + "}\n", getDefaultNullability(Nullability.NON_NULL));
        }
        out.println("}");
    }

    private void printClass(CodeWriter out, String name, XsdComplexType complexType,
            String nameScope) throws JavaCodeGeneratorException {
        assert name != null;
        // need element, attribute name duplicate validation?

        String baseName = getBaseName(complexType);
        JavaSimpleType valueType = (complexType instanceof XsdSimpleContent) ?
                getValueType((XsdSimpleContent) complexType, false) : null;

        String finalString = getFinalString(complexType.isFinalValue());
        if (complexType.isDeprecated()) {
            out.printf("@java.lang.Deprecated\n");
        }
        if (nameScope.isEmpty()) {
            out.printf("public%s class %s ", finalString, name);
        } else {
            out.printf("public%s static class %s ", finalString, name);
        }
        if (baseName != null) {
            out.printf("extends %s {\n", baseName);
        } else {
            out.println("{");
        }

        // parse types for elements and attributes
        List<JavaType> elementTypes = new ArrayList<>();
        List<XsdElement> elements = new ArrayList<>();
        elements.addAll(getAllElements(complexType.getGroup()));
        elements.addAll(complexType.getElements());

        for (XsdElement element : elements) {
            JavaType javaType;
            XsdElement elementValue = resolveElement(element);
            if (element.getRef() == null && element.getType().getRef() == null
                    && element.getType() instanceof XsdComplexType) {
                // print inner class for anonymous types
                String innerName = Utils.toClassName(getElementName(element));
                XsdComplexType innerType = (XsdComplexType) element.getType();
                String innerNameScope = nameScope + name + ".";
                printClass(out, innerName, innerType, innerNameScope);
                out.println();
                javaType = new JavaComplexType(innerNameScope + innerName);
            } else {
                javaType = parseType(elementValue.getType(), getElementName(elementValue));
            }
            elementTypes.add(javaType);
        }
        List<JavaSimpleType> attributeTypes = new ArrayList<>();
        List<XsdAttribute> attributes =  new ArrayList<>();
        for (XsdAttributeGroup attributeGroup : complexType.getAttributeGroups()) {
            attributes.addAll(getAllAttributes(resolveAttributeGroup(attributeGroup)));
        }
        attributes.addAll(complexType.getAttributes());

        for (XsdAttribute attribute : attributes) {
            XsdType type = resolveAttribute(attribute).getType();
            attributeTypes.add(parseSimpleType(type, false));
        }

        // print member variables
        for (int i = 0; i < elementTypes.size(); ++i) {
            JavaType type = elementTypes.get(i);
            XsdElement element = elements.get(i);
            XsdElement elementValue = resolveElement(element);
            String typeName = element.isMultiple() ? String.format("java.util.List<%s>",
                    type.getNullableName()) : type.getNullableName();
            out.printf("%sprivate %s %s;\n", getNullabilityString(element.getNullability()),
                    typeName, Utils.toVariableName(getElementName(elementValue)));
        }
        for (int i = 0; i < attributeTypes.size(); ++i) {
            JavaType type = attributeTypes.get(i);
            XsdAttribute attribute = resolveAttribute(attributes.get(i));
            out.printf("%sprivate %s %s;\n", getNullabilityString(attribute.getNullability()),
                    type.getNullableName(), Utils.toVariableName(attribute.getName()));
        }
        if (valueType != null) {
            out.printf("private %s value;\n", valueType.getName());
        }

        // print getters and setters
        for (int i = 0; i < elementTypes.size(); ++i) {
            JavaType type = elementTypes.get(i);
            XsdElement element = elements.get(i);
            XsdElement elementValue = resolveElement(element);
            printGetterAndSetter(out, type, Utils.toVariableName(getElementName(elementValue)),
                    element.isMultiple(), element);
        }
        for (int i = 0; i < attributeTypes.size(); ++i) {
            JavaType type = attributeTypes.get(i);
            XsdAttribute attribute = resolveAttribute(attributes.get(i));
            printGetterAndSetter(out, type, Utils.toVariableName(attribute.getName()), false,
                    attribute);
        }
        if (valueType != null) {
            printGetterAndSetter(out, valueType, "value", false, null);
        }

        out.println();
        printParser(out, nameScope + name, complexType);
        if (writer) {
            printWriter(out, name, complexType);
        }

        out.println("}");
    }

    private void printParser(CodeWriter out, String name, XsdComplexType complexType)
            throws JavaCodeGeneratorException {
        JavaSimpleType baseValueType = (complexType instanceof XsdSimpleContent) ?
                getValueType((XsdSimpleContent) complexType, true) : null;
        List<XsdElement> allElements = new ArrayList<>();
        List<XsdAttribute> allAttributes = new ArrayList<>();
        stackComponents(complexType, allElements, allAttributes);

        // parse types for elements and attributes
        List<JavaType> allElementTypes = new ArrayList<>();
        for (XsdElement element : allElements) {
            XsdElement elementValue = resolveElement(element);
            JavaType javaType = parseType(elementValue.getType(), elementValue.getName());
            allElementTypes.add(javaType);
        }
        List<JavaSimpleType> allAttributeTypes = new ArrayList<>();
        for (XsdAttribute attribute : allAttributes) {
            XsdType type = resolveAttribute(attribute).getType();
            allAttributeTypes.add(parseSimpleType(type, false));
        }

        out.printf("static %s%s read(%sorg.xmlpull.v1.XmlPullParser _parser) " +
                "throws org.xmlpull.v1.XmlPullParserException, java.io.IOException, " +
                "javax.xml.datatype.DatatypeConfigurationException {\n",
                getDefaultNullability(Nullability.NON_NULL), name,
                getDefaultNullability(Nullability.NON_NULL));

        out.printf("%s _instance = new %s();\n"
                + "String _raw = null;\n", name, name);
        for (int i = 0; i < allAttributes.size(); ++i) {
            JavaType type = allAttributeTypes.get(i);
            XsdAttribute attribute = resolveAttribute(allAttributes.get(i));
            String variableName = Utils.toVariableName(attribute.getName());
            out.printf("_raw = _parser.getAttributeValue(null, \"%s\");\n"
                    + "if (_raw != null) {\n", attribute.getName());
            out.print(type.getParsingExpression());
            out.printf("_instance.set%s(_value);\n"
                    + "}\n", Utils.capitalize(variableName));
        }

        if (baseValueType != null) {
            out.print("_raw = XmlParser.readText(_parser);\n"
                    + "if (_raw != null) {\n");
            out.print(baseValueType.getParsingExpression());
            out.print("_instance.setValue(_value);\n"
                    + "}\n");
        } else if (!allElements.isEmpty()) {
            out.print("int outerDepth = _parser.getDepth();\n"
                    + "int type;\n"
                    + "while ((type=_parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT\n"
                    + "        && type != org.xmlpull.v1.XmlPullParser.END_TAG) {\n"
                    + "if (_parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) "
                    + "continue;\n"
                    + "String _tagName = _parser.getName();\n");
            for (int i = 0; i < allElements.size(); ++i) {
                JavaType type = allElementTypes.get(i);
                XsdElement element = allElements.get(i);
                XsdElement elementValue = resolveElement(element);
                String variableName = Utils.toVariableName(getElementName(elementValue));
                out.printf("if (_tagName.equals(\"%s\")) {\n", elementValue.getName());
                if (type instanceof JavaSimpleType) {
                    out.print("_raw = XmlParser.readText(_parser);\n");
                }
                out.print(type.getParsingExpression());
                if (element.isMultiple()) {
                    out.printf("_instance.get%s().add(_value);\n",
                            Utils.capitalize(variableName));
                } else {
                    out.printf("_instance.set%s(_value);\n",
                            Utils.capitalize(variableName));
                }
                out.printf("} else ");
            }
            out.print("{\n"
                    + "XmlParser.skip(_parser);\n"
                    + "}\n"
                    + "}\n");
            out.printf("if (type != org.xmlpull.v1.XmlPullParser.END_TAG) {\n"
                    + "throw new javax.xml.datatype.DatatypeConfigurationException(\"%s is not closed\");\n"
                    + "}\n", name);
        } else {
            out.print("XmlParser.skip(_parser);\n");
        }
        out.print("return _instance;\n"
                + "}\n");
    }

    private void printWriter(CodeWriter out, String name, XsdComplexType complexType)
            throws JavaCodeGeneratorException {
        JavaSimpleType baseValueType = (complexType instanceof XsdSimpleContent) ?
                getValueType((XsdSimpleContent) complexType, true) : null;
        List<XsdElement> allElements = new ArrayList<>();
        List<XsdAttribute> allAttributes = new ArrayList<>();
        stackComponents(complexType, allElements, allAttributes);

        // parse types for elements and attributes
        List<JavaType> allElementTypes = new ArrayList<>();
        for (XsdElement element : allElements) {
            XsdElement elementValue = resolveElement(element);
            JavaType javaType = parseType(elementValue.getType(), elementValue.getName());
            allElementTypes.add(javaType);
        }
        List<JavaSimpleType> allAttributeTypes = new ArrayList<>();
        for (XsdAttribute attribute : allAttributes) {
            XsdType type = resolveAttribute(attribute).getType();
            allAttributeTypes.add(parseSimpleType(type, false));
        }

        out.printf("\nvoid write(%sXmlWriter _out, %sString _name) " +
                "throws java.io.IOException {\n", getDefaultNullability(Nullability.NON_NULL),
                getDefaultNullability(Nullability.NON_NULL));

        out.print("_out.print(\"<\" + _name);\n");
        for (int i = 0; i < allAttributes.size(); ++i) {
            JavaType type = allAttributeTypes.get(i);
            boolean isList = allAttributeTypes.get(i).isList();
            XsdAttribute attribute = resolveAttribute(allAttributes.get(i));
            String variableName = Utils.toVariableName(attribute.getName());
            out.printf("if (has%s()) {\n", Utils.capitalize(variableName));
            out.printf("_out.print(\" %s=\\\"\");\n", attribute.getName());
            out.print(type.getWritingExpression(String.format("%s%s()",
                    getterName(type.getName()), Utils.capitalize(variableName)),
                    attribute.getName()));
            out.printf("_out.print(\"\\\"\");\n}\n");
        }
        out.printf("_out.print(\">\\n\");\n");

        if (!allElements.isEmpty()) {
            out.printf("_out.increaseIndent();\n");
            for (int i = 0; i < allElements.size(); ++i) {
                JavaType type = allElementTypes.get(i);
                XsdElement element = allElements.get(i);
                XsdElement elementValue = resolveElement(element);
                String elementName = getElementName(elementValue);
                String variableName = Utils.toVariableName(elementName);

                if (element.isMultiple()) {
                    out.printf("for (%s value : get%s()) {\n", type.getName(),
                            Utils.capitalize(variableName));
                    if (type instanceof JavaSimpleType) {
                        out.printf("_out.print(\"<%s>\");\n", elementValue.getName());
                    }
                    out.print(type.getWritingExpression("value", elementValue.getName()));
                    if (type instanceof JavaSimpleType) {
                        out.printf("_out.print(\"</%s>\\n\");\n", elementValue.getName());
                    }
                    out.print("}\n");
                } else {
                    out.printf("if (has%s()) {\n", Utils.capitalize(variableName));
                    if (type instanceof JavaSimpleType) {
                        out.printf("_out.print(\"<%s>\");\n", elementValue.getName());
                    }
                    out.print(type.getWritingExpression(String.format("%s%s()",
                              getterName(type.getName()), Utils.capitalize(variableName)),
                              elementValue.getName()));
                    if (type instanceof JavaSimpleType) {
                        out.printf("_out.print(\"</%s>\\n\");\n", elementValue.getName());
                    }
                    out.printf("}\n");
                }

            }
            out.printf("_out.decreaseIndent();\n");
        }
        out.print("_out.print(\"</\" + _name + \">\\n\");\n");
        out.print("}\n");
    }

    private void printGetterAndSetter(CodeWriter out, JavaType type, String variableName,
            boolean isMultiple, XsdTag tag) {
        String typeName = isMultiple ? String.format("java.util.List<%s>", type.getNullableName())
                : type.getName();
        boolean deprecated = tag == null ? false : tag.isDeprecated();
        boolean finalValue = tag == null ? false : tag.isFinalValue();
        Nullability nullability = tag == null ? Nullability.UNKNOWN : tag.getNullability();
        out.println();
        if (deprecated) {
            out.printf("@java.lang.Deprecated\n");
        }
        out.printf("public%s %s%s %s%s() {\n", getFinalString(finalValue),
                getNullabilityString(nullability), typeName, getterName(typeName),
                Utils.capitalize(variableName));
        if ((type instanceof JavaSimpleType && ((JavaSimpleType)type).isList()) || isMultiple) {
            out.printf("if (%s == null) {\n"
                    + "%s = new java.util.ArrayList<>();\n"
                    + "}\n", variableName, variableName);
        } else if (type.isPrimitiveType()) {
            out.printf("if (%s == null) {\n", variableName);
            if (typeName.equals("boolean")) {
                out.printf("return false;\n}\n", variableName);
            } else {
                out.printf("return (%s)0;\n}\n", typeName);
            }
        }
        out.printf("return %s;\n"
                + "}\n", variableName);

        if (isMultiple) return;
        out.println();
        out.printf("%sboolean has%s() {\n"
                + "if (%s == null) {\n"
                + "return false;\n"
                + "}\n"
                + "return true;\n}\n\n",
                generateHasMethod ? "public " : "",
                Utils.capitalize(variableName), variableName);
        if (deprecated) {
            out.printf("@java.lang.Deprecated\n");
        }
        out.printf("public%s void set%s(%s%s %s) {\n"
                        + "this.%s = %s;\n"
                        + "}\n",
                getFinalString(finalValue), Utils.capitalize(variableName),
                getNullabilityString(nullability), typeName, variableName,
                variableName, variableName);
    }

    private void printXmlParser(CodeWriter out) throws JavaCodeGeneratorException {
        out.printf("package %s;\n", packageName);
        out.println();
        out.println("public class XmlParser {");

        boolean isMultiRootElement = xmlSchema.getElementMap().values().size() > 1;
        for (XsdElement element : xmlSchema.getElementMap().values()) {
            // Skip parser if not specified as root.
            if (rootElements != null
                    && Arrays.asList(rootElements).indexOf(element.getName()) == -1) continue;
            JavaType javaType = parseType(element.getType(), element.getName());
            String elementName = element.getName();
            String typeName = javaType.getName();
            String readerName =
                    javaType instanceof JavaSimpleType ? Utils.toClassName(elementName) : typeName;
            out.printf("public static %s%s read%s(%sjava.io.InputStream in)"
                    + " throws org.xmlpull.v1.XmlPullParserException, java.io.IOException, "
                    + "javax.xml.datatype.DatatypeConfigurationException {\n"
                    + "org.xmlpull.v1.XmlPullParser _parser = org.xmlpull.v1.XmlPullParserFactory"
                    + ".newInstance().newPullParser();\n"
                    + "_parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, "
                    + "true);\n"
                    + "_parser.setInput(in, null);\n"
                    + "_parser.nextTag();\n"
                    + "String _tagName = _parser.getName();\n"
                    + "String _raw = null;\n", getDefaultNullability(Nullability.NULLABLE),
                    typeName, isMultiRootElement ? readerName : "",
                    getDefaultNullability(Nullability.NON_NULL));
            out.printf("if (_tagName.equals(\"%s\")) {\n", elementName);
            if (javaType instanceof JavaSimpleType) {
                out.print("_raw = XmlParser.readText(_parser);\n");
            }
            out.print(javaType.getParsingExpression());
            out.print("return _value;\n"
                    + "}\n"
                    + "return null;\n"
                    + "}\n");
            out.println();
        }

        out.printf(
                "public static %sjava.lang.String readText(%sorg.xmlpull.v1.XmlPullParser _parser)"
                        + " throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {\n"
                        + "String result = \"\";\n"
                        + "if (_parser.next() == org.xmlpull.v1.XmlPullParser.TEXT) {\n"
                        + "    result = _parser.getText();\n"
                        + "    _parser.nextTag();\n"
                        + "}\n"
                        + "return result;\n"
                        + "}\n", getDefaultNullability(Nullability.NULLABLE),
                        getDefaultNullability(Nullability.NON_NULL));
        out.println();

        out.printf(
                "public static void skip(%sorg.xmlpull.v1.XmlPullParser _parser)"
                        + " throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {\n"
                        + "if (_parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) {\n"
                        + "    throw new IllegalStateException();\n"
                        + "}\n"
                        + "int depth = 1;\n"
                        + "while (depth != 0) {\n"
                        + "    switch (_parser.next()) {\n"
                        + "        case org.xmlpull.v1.XmlPullParser.END_TAG:\n"
                        + "            depth--;\n"
                        + "            break;\n"
                        + "        case org.xmlpull.v1.XmlPullParser.START_TAG:\n"
                        + "            depth++;\n"
                        + "            break;\n"
                        + "    }\n"
                        + "}\n"
                        + "}\n", getDefaultNullability(Nullability.NON_NULL));

        out.println("}");
    }

    private void printXmlWriter(CodeWriter out) throws JavaCodeGeneratorException {
        out.printf("package %s;\n", packageName);
        out.println();
        out.println("public class XmlWriter implements java.io.Closeable {");

        out.printf("private java.io.PrintWriter out;\n"
                + "private StringBuilder outBuffer;\n"
                + "private int indent;\n"
                + "private boolean startLine;\n\n"
                + "public XmlWriter(%sjava.io.PrintWriter printWriter) {\n"
                + "    out = printWriter;\n"
                + "    outBuffer = new StringBuilder();\n"
                + "    indent = 0;\n"
                + "    startLine = true;\n"
                + "}\n\n"
                + "private void printIndent() {\n"
                + "    assert startLine;\n"
                + "    for (int i = 0; i < indent; ++i) {\n"
                + "        outBuffer.append(\"    \");\n"
                + "    }\n"
                + "    startLine = false;\n"
                + "}\n\n"
                + "void print(String code) {\n"
                + "    String[] lines = code.split(\"\\n\", -1);\n"
                + "    for (int i = 0; i < lines.length; ++i) {\n"
                + "        if (startLine && !lines[i].isEmpty()) {\n"
                + "            printIndent();\n"
                + "        }\n"
                + "        outBuffer.append(lines[i]);\n"
                + "        if (i + 1 < lines.length) {\n"
                + "            outBuffer.append(\"\\n\");\n"
                + "            startLine = true;\n"
                + "        }\n"
                + "    }\n"
                + "}\n\n"
                + "void increaseIndent() {\n"
                + "    ++indent;\n}\n\n"
                + "void decreaseIndent() {\n"
                + "    --indent;\n"
                + "}\n\n"
                + "void printXml() {\n"
                + "    out.print(outBuffer.toString());\n"
                + "}\n\n"
                + "@Override\n"
                + "public void close() {\n"
                + "    if (out != null) {\n"
                + "        out.close();\n"
                + "    }\n"
                + "}\n\n", getDefaultNullability(Nullability.NON_NULL));


        for (XsdElement element : xmlSchema.getElementMap().values()) {
            // Skip writer if not specified as root.
            if (rootElements != null
                    && Arrays.asList(rootElements).indexOf(element.getName()) == -1) continue;
            JavaType javaType = parseType(element.getType(), element.getName());
            String elementName = element.getName();
            String variableName = Utils.toVariableName(elementName);
            String typeName = javaType.getName();
            String writerName
                    = javaType instanceof JavaSimpleType ? Utils.toClassName(elementName) : "";
            out.printf("public static void write%s(%sXmlWriter _out, %s%s %s) "
                    + "throws java.io.IOException {",
                    writerName,
                    getDefaultNullability(Nullability.NON_NULL),
                    getDefaultNullability(Nullability.NON_NULL), typeName, variableName);
            out.print("\n_out.print(\"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\"?>\\n\");\n");
            out.printf("if (%s != null) {\n", variableName);
            if (javaType instanceof JavaSimpleType) {
                out.printf("_out.print(\"<%s>\");\n", elementName);
                out.print(javaType.getWritingExpression(variableName, ""));
                out.printf("_out.print(\"</%s>\\n\");\n", elementName);
            } else {
                out.printf("%s.write(_out, \"%s\");\n", variableName, elementName);
            }
            out.print("}\n");
            out.print("_out.printXml();\n}\n\n");
        }
        out.printf("}\n");
    }

    private void printHexBinaryHelper(CodeWriter out) throws JavaCodeGeneratorException {
        out.printf("package %s;\n", packageName);
        out.println();
        out.println("public class HexBinaryHelper {");
        out.print("public static byte[] hexStringToByteArray(String hexString) {\n"
                + "if (hexString.length() % 2 != 0) {\n"
                + "throw new IllegalArgumentException(\"length must be multiple of 2\");\n"
                + "}\n"
                + "byte[] outputBytes = new byte[hexString.length() / 2];\n"
                + "for (int i = 0; i < hexString.length(); i += 2) {\n"
                + "char c1 = hexString.charAt(i);\n"
                + "char c2 = hexString.charAt(i + 1);\n"
                + "outputBytes[i / 2] = (byte) ((Character.digit(c1, 16) << 4)"
                + " + Character.digit(c2, 16));\n"
                + "}\n"
                + "return outputBytes;"
                + "}\n\n"
                + "public static String byteArrayToHexString(byte[] b) {\n"
                + "StringBuffer s = new StringBuffer();\n"
                + "for (int i = 0; i < b.length; i++) {\n"
                + "s.append(Integer.toHexString(0x100 + (b[i] & 0xff)).substring(1));\n"
                + "}\n"
                + "return s.toString();\n"
                + "}\n"
                + "}\n");
    }

    private String getElementName(XsdElement element) {
        if (element instanceof XsdChoice) {
            return element.getName() + "_optional";
        } else if (element instanceof XsdAll) {
            return element.getName() + "_all";
        }
        return element.getName();
    }

    private String getFinalString(boolean finalValue) {
        if (finalValue) {
          return " final";
        }
        return "";
    }

    private String getDefaultNullability(Nullability nullability) {
        if (showNullability) {
            return getNullabilityString(nullability);
        }
        return "";
    }

    private String getNullabilityString(Nullability nullability) {
        if (nullability == Nullability.NON_NULL) {
            return "@android.annotation.NonNull ";
        } else if (nullability == Nullability.NULLABLE) {
            return "@android.annotation.Nullable ";
        } else if (showNullability) {
            return "@android.annotation.Nullable ";
        }
        return "";
    }

    private String getterName(String type) {
        if (type.equals("boolean") && booleanGetter) {
            return "is";
        }
        return "get";
    }

    private void stackComponents(XsdComplexType complexType, List<XsdElement> elements,
            List<XsdAttribute> attributes) throws JavaCodeGeneratorException {
        if (complexType.getBase() != null) {
            QName baseRef = complexType.getBase().getRef();
            if (baseRef != null && !baseRef.getNamespaceURI().equals(XsdConstants.XSD_NAMESPACE)) {
                XsdType parent = getType(baseRef.getLocalPart());
                if (parent instanceof XsdComplexType) {
                    stackComponents((XsdComplexType) parent, elements, attributes);
                }
            }
        }
        elements.addAll(getAllElements(complexType.getGroup()));
        elements.addAll(complexType.getElements());
        for (XsdAttributeGroup attributeGroup : complexType.getAttributeGroups()) {
            attributes.addAll(getAllAttributes(resolveAttributeGroup(attributeGroup)));
        }
        attributes.addAll(complexType.getAttributes());
    }

    private List<XsdAttribute> getAllAttributes(XsdAttributeGroup attributeGroup)
            throws JavaCodeGeneratorException {
        List<XsdAttribute> attributes = new ArrayList<>();
        for (XsdAttributeGroup attrGroup : attributeGroup.getAttributeGroups()) {
            attributes.addAll(getAllAttributes(resolveAttributeGroup(attrGroup)));
        }
        attributes.addAll(attributeGroup.getAttributes());
        return attributes;
    }

    private List<XsdElement> getAllElements(XsdGroup group) throws JavaCodeGeneratorException {
        List<XsdElement> elements = new ArrayList<>();
        if (group == null) {
            return elements;
        }
        elements.addAll(getAllElements(resolveGroup(group)));
        elements.addAll(group.getElements());
        return elements;
    }

    private String getBaseName(XsdComplexType complexType) throws JavaCodeGeneratorException {
        if (complexType.getBase() == null) return null;
        if (complexType.getBase().getRef().getNamespaceURI().equals(XsdConstants.XSD_NAMESPACE)) {
            return null;
        }
        XsdType base = getType(complexType.getBase().getRef().getLocalPart());
        if (base instanceof XsdComplexType) {
            return Utils.toClassName(base.getName());
        }
        return null;
    }

    private JavaSimpleType getValueType(XsdSimpleContent simpleContent, boolean traverse)
            throws JavaCodeGeneratorException {
        assert simpleContent.getBase() != null;
        QName baseRef = simpleContent.getBase().getRef();
        assert baseRef != null;
        if (baseRef.getNamespaceURI().equals(XsdConstants.XSD_NAMESPACE)) {
            return predefinedType(baseRef.getLocalPart());
        } else {
            XsdType parent = getType(baseRef.getLocalPart());
            if (parent instanceof XsdSimpleType) {
                return parseSimpleTypeReference(baseRef, false);
            }
            if (!traverse) return null;
            if (parent instanceof XsdSimpleContent) {
                return getValueType((XsdSimpleContent) parent, true);
            } else {
                throw new JavaCodeGeneratorException(
                        String.format("base not simple : %s", baseRef.getLocalPart()));
            }
        }
    }

    private JavaType parseType(XsdType type, String defaultName) throws JavaCodeGeneratorException {
        if (type.getRef() != null) {
            String name = type.getRef().getLocalPart();
            if (type.getRef().getNamespaceURI().equals(XsdConstants.XSD_NAMESPACE)) {
                return predefinedType(name);
            } else {
                XsdType typeValue = getType(name);
                if (typeValue instanceof XsdSimpleType) {
                    return parseSimpleTypeReference(type.getRef(), false);
                }
                return parseType(typeValue, name);
            }
        }
        if (type instanceof XsdComplexType) {
            return new JavaComplexType(Utils.toClassName(defaultName));
        } else if (type instanceof XsdSimpleType) {
            return parseSimpleTypeValue((XsdSimpleType) type, false);
        } else {
            throw new JavaCodeGeneratorException(
                    String.format("unknown type name : %s", defaultName));
        }
    }

    private JavaSimpleType parseSimpleType(XsdType type, boolean traverse)
            throws JavaCodeGeneratorException {
        if (type.getRef() != null) {
            return parseSimpleTypeReference(type.getRef(), traverse);
        } else {
            return parseSimpleTypeValue((XsdSimpleType) type, traverse);
        }
    }

    private JavaSimpleType parseSimpleTypeReference(QName typeRef, boolean traverse)
            throws JavaCodeGeneratorException {
        assert typeRef != null;
        String typeName = typeRef.getLocalPart();
        if (typeRef.getNamespaceURI().equals(XsdConstants.XSD_NAMESPACE)) {
            return predefinedType(typeName);
        }
        if (javaSimpleTypeMap.containsKey(typeName)) {
            return javaSimpleTypeMap.get(typeName);
        } else if (traverse) {
            XsdSimpleType simpleType = getSimpleType(typeName);
            JavaSimpleType ret = parseSimpleTypeValue(simpleType, true);
            javaSimpleTypeMap.put(typeName, ret);
            return ret;
        } else {
            throw new JavaCodeGeneratorException(String.format("unknown type name : %s", typeName));
        }
    }

    private JavaSimpleType parseSimpleTypeValue(XsdSimpleType simpleType, boolean traverse)
            throws JavaCodeGeneratorException {
        if (simpleType instanceof XsdList) {
            XsdList list = (XsdList) simpleType;
            return parseSimpleType(list.getItemType(), traverse).newListType();
        } else if (simpleType instanceof XsdRestriction) {
            // we don't consider any restrictions.
            XsdRestriction restriction = (XsdRestriction) simpleType;
            if (restriction.getEnums() != null) {
                String name = Utils.toClassName(restriction.getName());
                return new JavaSimpleType(name, name, name + ".fromString(%s)", "%s.toString()",
                        false);
            }
            return parseSimpleType(restriction.getBase(), traverse);
        } else if (simpleType instanceof XsdUnion) {
            // unions are almost always interpreted as java.lang.String
            // Exceptionally, if any of member types of union are 'list', then we interpret it as
            // List<String>
            XsdUnion union = (XsdUnion) simpleType;
            for (XsdType memberType : union.getMemberTypes()) {
                if (parseSimpleType(memberType, traverse).isList()) {
                    return new JavaSimpleType("java.lang.String", "%s", true);
                }
            }
            return new JavaSimpleType("java.lang.String", "%s", false);
        } else {
            // unreachable
            throw new IllegalStateException("unknown simple type");
        }
    }

    private XsdElement resolveElement(XsdElement element) throws JavaCodeGeneratorException {
        if (element.getRef() == null) return element;
        String name = element.getRef().getLocalPart();
        XsdElement ret = xmlSchema.getElementMap().get(name);
        if (ret != null) return ret;
        throw new JavaCodeGeneratorException(String.format("no element named : %s", name));
    }

    private XsdGroup resolveGroup(XsdGroup group) throws JavaCodeGeneratorException {
        if (group.getRef() == null) return null;
        String name = group.getRef().getLocalPart();
        XsdGroup ret = xmlSchema.getGroupMap().get(name);
        if (ret != null) return ret;
        throw new JavaCodeGeneratorException(String.format("no group named : %s", name));
    }

    private XsdAttribute resolveAttribute(XsdAttribute attribute)
            throws JavaCodeGeneratorException {
        if (attribute.getRef() == null) return attribute;
        String name = attribute.getRef().getLocalPart();
        XsdAttribute ret = xmlSchema.getAttributeMap().get(name);
        if (ret != null) return ret;
        throw new JavaCodeGeneratorException(String.format("no attribute named : %s", name));
    }

    private XsdAttributeGroup resolveAttributeGroup(XsdAttributeGroup attributeGroup)
            throws JavaCodeGeneratorException {
        if (attributeGroup.getRef() == null) return attributeGroup;
        String name = attributeGroup.getRef().getLocalPart();
        XsdAttributeGroup ret = xmlSchema.getAttributeGroupMap().get(name);
        if (ret != null) return ret;
        throw new JavaCodeGeneratorException(String.format("no attribute group named : %s", name));
    }

    private XsdType getType(String name) throws JavaCodeGeneratorException {
        XsdType type = xmlSchema.getTypeMap().get(name);
        if (type != null) return type;
        throw new JavaCodeGeneratorException(String.format("no type named : %s", name));
    }

    private XsdSimpleType getSimpleType(String name) throws JavaCodeGeneratorException {
        XsdType type = getType(name);
        if (type instanceof XsdSimpleType) return (XsdSimpleType) type;
        throw new JavaCodeGeneratorException(String.format("not a simple type : %s", name));
    }

    private JavaSimpleType predefinedType(String name) throws JavaCodeGeneratorException {
        switch (name) {
            case "string":
            case "token":
            case "normalizedString":
            case "language":
            case "ENTITY":
            case "ID":
            case "Name":
            case "NCName":
            case "NMTOKEN":
            case "anyURI":
            case "anyType":
            case "QName":
            case "NOTATION":
            case "IDREF":
                return new JavaSimpleType("java.lang.String", "%s", false);
            case "ENTITIES":
            case "NMTOKENS":
            case "IDREFS":
                return new JavaSimpleType("java.lang.String", "%s", true);
            case "date":
            case "dateTime":
            case "time":
            case "gDay":
            case "gMonth":
            case "gYear":
            case "gMonthDay":
            case "gYearMonth":
                return new JavaSimpleType("javax.xml.datatype.XMLGregorianCalendar",
                        "javax.xml.datatype.XMLGregorianCalendar",
                        "javax.xml.datatype.DatatypeFactory.newInstance()"
                                + ".newXMLGregorianCalendar(%s)",
                        "%s.toString()", false);
            case "duration":
                return new JavaSimpleType("javax.xml.datatype.Duration",
                        "javax.xml.datatype.Duration",
                        "javax.xml.datatype.DatatypeFactory.newInstance().newDuration(%s)",
                        "%s.toString()", false);
            case "decimal":
                return new JavaSimpleType("java.math.BigDecimal", "java.math.BigDecimal",
                        "new java.math.BigDecimal(%s)", "%s.toString()", false);
            case "integer":
            case "negativeInteger":
            case "nonNegativeInteger":
            case "positiveInteger":
            case "nonPositiveInteger":
            case "unsignedLong":
                return new JavaSimpleType("java.math.BigInteger", "java.math.BigInteger",
                        "new java.math.BigInteger(%s)", "%s.toString()", false);
            case "long":
            case "unsignedInt":
                return new JavaSimpleType("long", "java.lang.Long", "Long.parseLong(%s)",
                        "Long.toString(%s)", false);
            case "int":
            case "unsignedShort":
                return new JavaSimpleType("int", "java.lang.Integer", "Integer.parseInt(%s)",
                        "Integer.toString(%s)", false);
            case "short":
            case "unsignedByte":
                return new JavaSimpleType("short", "java.lang.Short", "Short.parseShort(%s)",
                        "Short.toString(%s)", false);
            case "byte":
                return new JavaSimpleType("byte", "java.lang.Byte", "Byte.parseByte(%s)",
                        "Byte.toString(%s)",false);
            case "boolean":
                return new JavaSimpleType("boolean", "java.lang.Boolean",
                        "Boolean.parseBoolean(%s)", "Boolean.toString(%s)", false);
            case "double":
                return new JavaSimpleType("double", "java.lang.Double", "Double.parseDouble(%s)",
                        "Double.toString(%s)", false);
            case "float":
                return new JavaSimpleType("float", "java.lang.Float", "Float.parseFloat(%s)",
                        "Float.toString(%s)", false);
            case "base64Binary":
                return new JavaSimpleType("byte[]", "byte[]",
                        "java.util.Base64.getDecoder().decode(%s)",
                        "java.util.Base64.getEncoder().encodeToString(%s)",
                        false);
            case "hexBinary":
                useHexBinary = true;
                return new JavaSimpleType("byte[]", "byte[]",
                        "HexBinaryHelper.hexStringToByteArray(%s)",
                        "HexBinaryHelper.byteArrayToHexString(%s)",
                        false);
        }
        throw new JavaCodeGeneratorException("unknown xsd predefined type : " + name);
    }
}
