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

package com.android.xsdc.tests;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.SAXException;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;


public class XmlParserTest {
    @Test
    public void testPurchaseSimple() throws Exception {
        purchase.simple.PurchaseOrderType orderType;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
                "purchase_simple.xml")) {
            orderType = purchase.simple.XmlParser.read(str);
        }

        assertThat(orderType.getOrderDate(),
                is(javax.xml.datatype.DatatypeFactory.newInstance().newXMLGregorianCalendar(
                "1900-01-01")));
        assertThat(orderType.getBillTo().getName(), is("billName"));
        assertThat(orderType.getBillTo().getZip(), is(new BigInteger("1")));
        assertThat(orderType.getShipTo().get(0).getStreet(), is("street1"));
        assertThat(orderType.getShipTo().get(1).getZip(),
                is(new BigInteger("-7922816251426433759")));

        String actualStr, expectedStr;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
            "purchase_simple.xml")) {
            expectedStr = new String(str.readAllBytes());
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try(purchase.simple.XmlWriter writer =
                    new purchase.simple.XmlWriter(new PrintWriter(baos))) {
                purchase.simple.XmlWriter.write(writer, orderType);
            }
            actualStr = new String(baos.toByteArray());
        }

        assertThat(new String(actualStr), is(expectedStr));
    }

    @Test
    public void testNestedType() throws Exception {
        nested.type.Employee employee;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
                "nested_type.xml")) {
            employee = nested.type.XmlParser.read(str);
        }

        assertThat(employee.getName(), is("Peter"));
        assertThat(employee.getAddress().getCountry(), is("US"));
        assertThat(employee.getAddress().getExtra().getLine2(), is("Good Street"));
    }

    @Test
    public void testSimpleComplexContent() throws Exception {
        simple.complex.content.Person person;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
                "simple_complex_content.xml")) {
            person = simple.complex.content.XmlParser.readPerson(str);
        }

        assertThat(person.getName(), is("Petr"));
        assertThat(person.getUSAddressP().getStreet(), is("street fighter"));
        assertThat(person.getUSAddressP().getZipcode(), is(new BigInteger("323232318329852")));
        assertThat(person.getKRAddress().getStreet(), is("Nokdu Street"));

        String actualStr, expectedStr;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
            "simple_complex_content.xml")) {
            expectedStr = new String(str.readAllBytes());
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try(simple.complex.content.XmlWriter writer =
                    new simple.complex.content.XmlWriter(new PrintWriter(baos))) {
                simple.complex.content.XmlWriter.write(writer, person);
            }
            actualStr = new String(baos.toByteArray());
        }

        assertThat(new String(actualStr), is(expectedStr));
    }

    @Test
    public void testPredefinedTypes() throws Exception {
        predefined.types.Types type;

        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
                "predefined_types.xml")) {
            type = predefined.types.XmlParser.read(str);
        }

        {
            predefined.types.StringTypes stringTypes = type.getStringTypes();

            assertThat(stringTypes.getString(), is("abcd"));
            assertThat(stringTypes.getToken(), is("abcd"));
            assertThat(stringTypes.getNormalizedString(), is("abcd"));
            assertThat(stringTypes.getLanguage(), is("abcd"));
            assertThat(stringTypes.getEntity(), is("abcd"));
            assertThat(stringTypes.getEntities(), is(Arrays.asList("a", "b", "c", "d")));
            assertThat(stringTypes.getId(), is("abcd"));
            assertThat(stringTypes.getName(), is("abcd"));
            assertThat(stringTypes.getNcname(), is("abcd"));
            assertThat(stringTypes.getNmtoken(), is("abcd"));
            assertThat(stringTypes.getNmtokens(), is(Arrays.asList("a", "b", "c", "d")));
        }

        {
            predefined.types.DateTypes dateTypes = type.getDateTypes();
            DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

            assertThat(dateTypes.getDate(),
                    is(datatypeFactory.newXMLGregorianCalendar("2018-06-18")));
            assertThat(dateTypes.getDateTime(),
                    is(datatypeFactory.newXMLGregorianCalendar("2018-06-18T21:32:52")));
            assertThat(dateTypes.getDuration(), is(datatypeFactory.newDuration("P3M")));
            assertThat(dateTypes.getGDay(), is(datatypeFactory.newXMLGregorianCalendar("---18")));
            assertThat(dateTypes.getGMonth(), is(datatypeFactory.newXMLGregorianCalendar("--06")));
            assertThat(dateTypes.getGMonthDay(),
                    is(datatypeFactory.newXMLGregorianCalendar("--06-18")));
            assertThat(dateTypes.getGYear(), is(datatypeFactory.newXMLGregorianCalendar("2018")));
            assertThat(dateTypes.getGYearMonth(),
                    is(datatypeFactory.newXMLGregorianCalendar("2018-06")));
            assertThat(dateTypes.getTime(),
                    is(datatypeFactory.newXMLGregorianCalendar("21:32:52")));
        }
        {
            predefined.types.NumericTypes numericTypes = type.getNumericTypes();

            assertThat(numericTypes.getDecimal(), is(new BigDecimal("1234.57")));
            assertThat(numericTypes.getInteger(), is(new BigInteger("1234567890123456789")));
            assertThat(numericTypes.get_long(), is(9223372036854775807L));
            assertThat(numericTypes.get_int(), is(2147483647));
            assertThat(numericTypes.get_short(), is((short) 32767));
            assertThat(numericTypes.get_byte(), is((byte) 127));
            assertThat(numericTypes.getNegativeInteger(), is(new BigInteger("-1234")));
            assertThat(numericTypes.getNonNegativeInteger(), is(new BigInteger("1234")));
            assertThat(numericTypes.getPositiveInteger(), is(new BigInteger("1234")));
            assertThat(numericTypes.getNonPositiveInteger(), is(new BigInteger("-1234")));
            assertThat(numericTypes.getUnsignedLong(), is(new BigInteger("1234")));
            assertThat(numericTypes.getUnsignedInt(), is(1234L));
            assertThat(numericTypes.getUnsignedShort(), is(1234));
            assertThat(numericTypes.getUnsignedByte(), is((short) 255));
        }

        {
            predefined.types.MiscTypes miscTypes = type.getMiscTypes();

            assertThat(miscTypes.get_double(), is(1234.57));
            assertThat(miscTypes.getAnyURI(), is("https://www.google.com"));
            assertThat(miscTypes.getBase64Binary(), is(Base64.getDecoder().decode("Z29vZ2xl")));
            assertThat(miscTypes.get_boolean(), is(true));
            assertThat(miscTypes.getHexBinary(),
                    is(predefined.types.HexBinaryHelper.hexStringToByteArray("016a75cb56d7e7")));
            assertThat(miscTypes.getQName(), is("abcd"));
            assertThat(miscTypes.getIDREF(), is("abcd"));
            assertThat(miscTypes.getIDREFS(), is(Arrays.asList("abcd", "abcd")));
            assertThat(miscTypes.getAnyType(), is("abcd"));
        }

        {
            predefined.types.ListPrimitiveTypes listPrimitiveTypes =
                    type.getListPrimitiveTypes();

            assertThat(listPrimitiveTypes.getListLong(),
                    is(Arrays.asList(-9223372036854775808L, 9223372036854775807L)));
            assertThat(listPrimitiveTypes.getListInt(),
                    is(Arrays.asList(-2147483648, 2147483647)));
            assertThat(listPrimitiveTypes.getListShort(),
                    is(Arrays.asList((short) -32768, (short) 32767)));
            assertThat(listPrimitiveTypes.getListByte(),
                    is(Arrays.asList((byte) -128, (byte) 127)));
            assertThat(listPrimitiveTypes.getListDouble(), is(Arrays.asList(1234.56, 5678.12)));
            assertThat(listPrimitiveTypes.getListFloat(), is(Arrays.asList(123.4f, 456.1f)));
            assertThat(listPrimitiveTypes.getListBoolean(), is(Arrays.asList(true, false)));
        }

        String actualStr, expectedStr;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
            "predefined_types.xml")) {
            expectedStr = new String(str.readAllBytes());
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try(predefined.types.XmlWriter writer =
                    new predefined.types.XmlWriter(new PrintWriter(baos))) {
                predefined.types.XmlWriter.write(writer, type);
            }
            actualStr = new String(baos.toByteArray());
        }

        assertThat(new String(actualStr), is(expectedStr));
    }

    @Test
    public void testSimpleType() throws Exception {
        simple.type.SimpleTypes simples;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
                "simple_type.xml")) {
            simples = simple.type.XmlParser.readSimpleTypes(str);
        }

        assertThat(simples.getListInt(), is(Arrays.asList(1, 2, 3, 4, 5)));
        assertThat(simples.getUnionTest(), is(Arrays.asList("100")));

        String actualStr, expectedStr;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
            "simple_type.xml")) {
            expectedStr = new String(str.readAllBytes());
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try(simple.type.XmlWriter writer = new simple.type.XmlWriter(new PrintWriter(baos))) {
                simple.type.XmlWriter.write(writer, simples);
            }
            actualStr = new String(baos.toByteArray());
        }

        assertThat(new String(actualStr), is(expectedStr));
    }

    @Test
    public void testSimpleTypeRoot() throws Exception {
        BigInteger percent;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
                "simple_type_root.xml")) {
            percent = simple.type.XmlParser.readPercent(str);
        }

        assertThat(percent, is(BigInteger.valueOf(100)));

        String actualStr, expectedStr;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
            "simple_type_root.xml")) {
            expectedStr = new String(str.readAllBytes());
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try(simple.type.XmlWriter writer = new simple.type.XmlWriter(new PrintWriter(baos))) {
                simple.type.XmlWriter.writePercent(writer, percent);
            }
            actualStr = new String(baos.toByteArray());
        }

        assertThat(new String(actualStr), is(expectedStr));
    }

    @Test
    public void testReference() throws Exception {
        reference.Class _class;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
                "reference.xml")) {
            _class = reference.XmlParser.read(str);
        }

        assertThat(_class.getStudent(), is(Arrays.asList("Sam", "Paul", "Peter")));

        String actualStr, expectedStr;
        try (InputStream str = this.getClass().getClassLoader().getResourceAsStream(
            "reference.xml")) {
            expectedStr = new String(str.readAllBytes());
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try(reference.XmlWriter writer = new reference.XmlWriter(new PrintWriter(baos))) {
                reference.XmlWriter.write(writer, _class);
            }
            actualStr = new String(baos.toByteArray());
        }

        assertThat(new String(actualStr), is(expectedStr));
    }
}
