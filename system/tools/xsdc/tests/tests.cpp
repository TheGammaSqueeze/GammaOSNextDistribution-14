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

#include <iostream>
#include <fstream>
#include <string>
#include <optional>

#include <android-base/macros.h>
#include <gtest/gtest.h>
#include "nested_type.h"
#include "purchase_simple.h"
#include "simple_complex_content.h"

#include "predefined_types.h"
#include "reference.h"
#include "attr_group_simple.h"
#include "group.h"
#include "xmltest.h"

using namespace std;

TEST_F(XmlTest, Predefinedtypes) {
  using namespace predefined::types;
  Types type = *read(Resource("predefined_types.xml").c_str());

  StringTypes stringTypes = *type.getFirstStringTypes();
  DateTypes dateTypes = *type.getFirstDateTypes();
  NumericTypes numericTypes = *type.getFirstNumericTypes();
  MiscTypes miscTypes = *type.getFirstMiscTypes();
  ListPrimitiveTypes listPrimitiveTypes = *type.getFirstListPrimitiveTypes();

  EXPECT_EQ(stringTypes.getString(), "abcd");
  EXPECT_EQ(stringTypes.getToken(), "abcd");
  EXPECT_EQ(stringTypes.getNormalizedString(), "abcd");
  EXPECT_EQ(stringTypes.getLanguage(), "abcd");
  EXPECT_EQ(stringTypes.getEntity(), "abcd");
  EXPECT_EQ(stringTypes.getEntities()[0], "a");
  EXPECT_EQ(stringTypes.getEntities()[1], "b");
  EXPECT_EQ(stringTypes.getEntities()[2], "c");
  EXPECT_EQ(stringTypes.getEntities()[3], "d");
  EXPECT_EQ(stringTypes.getId(), "abcd");
  EXPECT_EQ(stringTypes.getName(), "abcd");
  EXPECT_EQ(stringTypes.getNcname(), "abcd");
  EXPECT_EQ(stringTypes.getNmtoken(), "abcd");
  EXPECT_EQ(stringTypes.getNmtokens()[0], "a");
  EXPECT_EQ(stringTypes.getNmtokens()[1], "b");
  EXPECT_EQ(stringTypes.getNmtokens()[2], "c");
  EXPECT_EQ(stringTypes.getNmtokens()[3], "d");


  EXPECT_EQ(dateTypes.getDate(), "2018-06-18");
  EXPECT_EQ(dateTypes.getDateTime(), "2018-06-18T21:32:52");
  EXPECT_EQ(dateTypes.getDuration(), "P3M");
  EXPECT_EQ(dateTypes.getGDay(), "---18");
  EXPECT_EQ(dateTypes.getGMonth(), "--06");
  EXPECT_EQ(dateTypes.getGMonthDay(), "--06-18");
  EXPECT_EQ(dateTypes.getGYear(), "2018");
  EXPECT_EQ(dateTypes.getGYearMonth(), "2018-06");
  EXPECT_EQ(dateTypes.getTime(), "21:32:52");

  EXPECT_EQ(numericTypes.getDecimal(), 1234.57);
  EXPECT_EQ(numericTypes.getInteger(), 1234567890123456789);
  EXPECT_EQ(numericTypes.get_long(), 9223372036854775807);
  EXPECT_EQ(numericTypes.get_int(), 2147483647);
  EXPECT_EQ(numericTypes.get_short(), 32767);
  EXPECT_EQ((int)numericTypes.getByte(), 127);
  EXPECT_EQ(numericTypes.getNegativeInteger(), -1234);
  EXPECT_EQ(numericTypes.getNonNegativeInteger(), 1234);
  EXPECT_EQ(numericTypes.getPositiveInteger(), 1234);
  EXPECT_EQ(numericTypes.getNonPositiveInteger(), -1234);
  EXPECT_EQ(numericTypes.getUnsignedLong(), 1234);
  EXPECT_EQ(numericTypes.getUnsignedInt(), 1234);
  EXPECT_EQ(numericTypes.getUnsignedShort(), 1234);
  EXPECT_EQ((int)(numericTypes.getUnsignedByte()), 255);

  EXPECT_EQ(miscTypes.get_double(), 1234.57);
  EXPECT_EQ(miscTypes.getAnyURI(), "https://www.google.com");
  EXPECT_EQ(miscTypes.getBase64Binary(), "Z29vZ2xl");
  EXPECT_TRUE(miscTypes.getBoolean());
  EXPECT_EQ(miscTypes.getHexBinary(), "016a75cb56d7e7");
  EXPECT_EQ(miscTypes.getQName(), "abcd");
  EXPECT_EQ(miscTypes.getIDREF(), "abcd");
  EXPECT_EQ(miscTypes.getIDREFS()[0], "abcd");
  EXPECT_EQ(miscTypes.getIDREFS()[1], "abcd");
  EXPECT_EQ(miscTypes.getAnyType(), "abcd");

  EXPECT_EQ(listPrimitiveTypes.getListInt()[0], -2147483648);
  EXPECT_EQ(listPrimitiveTypes.getListInt()[1], 2147483647);
  EXPECT_EQ(listPrimitiveTypes.getListShort()[0], -32768);
  EXPECT_EQ(listPrimitiveTypes.getListShort()[1], 32767);
  EXPECT_EQ((int)listPrimitiveTypes.getListByte()[0], -128);
  EXPECT_EQ((int)listPrimitiveTypes.getListByte()[1], 127);
  EXPECT_EQ(listPrimitiveTypes.getListDouble()[0], 1234.56);
  EXPECT_EQ(listPrimitiveTypes.getListDouble()[1], 5678.12);
  EXPECT_TRUE(listPrimitiveTypes.getListBoolean()[0]);
  EXPECT_FALSE(listPrimitiveTypes.getListBoolean()[1]);

  ofstream out("old_predefined_types.xml");
  write(out, type);
  Types type2 = *read("old_predefined_types.xml");

  NumericTypes numericTypes2 = *type.getFirstNumericTypes();
  ListPrimitiveTypes listPrimitiveTypes2 = *type.getFirstListPrimitiveTypes();

  EXPECT_EQ(numericTypes.getDecimal(), numericTypes2.getDecimal());
  EXPECT_EQ(numericTypes.getInteger(), numericTypes2.getInteger());
  EXPECT_EQ(numericTypes.get_long(), numericTypes2.get_long());
  EXPECT_EQ(numericTypes.get_int(), numericTypes2.get_int());
  EXPECT_EQ(numericTypes.get_short(), numericTypes2.get_short());
  EXPECT_EQ(numericTypes.getByte(), numericTypes2.getByte());
  EXPECT_EQ(numericTypes.getNegativeInteger(), numericTypes2.getNegativeInteger());
  EXPECT_EQ(numericTypes.getNonNegativeInteger(), numericTypes2.getNonNegativeInteger());
  EXPECT_EQ(numericTypes.getPositiveInteger(), numericTypes2.getPositiveInteger());
  EXPECT_EQ(numericTypes.getNonPositiveInteger(), numericTypes2.getNonPositiveInteger());
  EXPECT_EQ(numericTypes.getUnsignedLong(), numericTypes2.getUnsignedLong());
  EXPECT_EQ(numericTypes.getUnsignedInt(), numericTypes2.getUnsignedInt());
  EXPECT_EQ(numericTypes.getUnsignedShort(), numericTypes2.getUnsignedShort());
  EXPECT_EQ((numericTypes.getUnsignedByte()), numericTypes2.getUnsignedByte());

  EXPECT_EQ(listPrimitiveTypes.getListInt()[0], listPrimitiveTypes2.getListInt()[0]);
  EXPECT_EQ(listPrimitiveTypes.getListInt()[1], listPrimitiveTypes2.getListInt()[1]);
  EXPECT_EQ(listPrimitiveTypes.getListShort()[0], listPrimitiveTypes2.getListShort()[0]);
  EXPECT_EQ(listPrimitiveTypes.getListShort()[1], listPrimitiveTypes2.getListShort()[1]);
  EXPECT_EQ(listPrimitiveTypes.getListByte()[0], listPrimitiveTypes2.getListByte()[0]);
  EXPECT_EQ(listPrimitiveTypes.getListByte()[1], listPrimitiveTypes2.getListByte()[1]);
  EXPECT_EQ(listPrimitiveTypes.getListDouble()[0], listPrimitiveTypes2.getListDouble()[0]);
  EXPECT_EQ(listPrimitiveTypes.getListDouble()[1], listPrimitiveTypes2.getListDouble()[1]);
  EXPECT_EQ(listPrimitiveTypes.getListBoolean()[0], listPrimitiveTypes2.getListBoolean()[0]);
  EXPECT_EQ(listPrimitiveTypes.getListBoolean()[1], listPrimitiveTypes2.getListBoolean()[1]);
}

TEST_F(XmlTest, Nestedtype) {
  using namespace nested::type;
  Employee employee = *read(Resource("nested_type.xml").c_str());

  Employee::Address address = *employee.getFirstAddress();
  Employee::Address::Extra extra = *address.getFirstExtra();

  EXPECT_EQ((int)employee.getId(), 1);
  EXPECT_EQ(employee.getName(), "Peter");
  EXPECT_EQ(address.getCountry(), "US");
  EXPECT_EQ(address.getState(), "Mountain View");
  EXPECT_EQ(address.getZip(), 3342);
  EXPECT_EQ(extra.getLine1(), "Donga 303-111");
  EXPECT_EQ(extra.getLine2(), "Good Street");

  ofstream out("old_nested_type.xml");
  write(out, employee);
}

TEST_F(XmlTest, Purchasesimple) {
  using namespace purchase::simple;
  PurchaseOrderType orderType = *read(Resource("purchase_simple.xml").c_str());

  EXPECT_EQ(orderType.getOrderDate(), "1900-01-01");

  EXPECT_EQ(orderType.getShipTo()[0].getName(), "name1");
  EXPECT_EQ(orderType.getShipTo()[0].getStreet(), "street1");
  EXPECT_EQ(orderType.getShipTo()[0].getCity(), "city1");
  EXPECT_EQ(orderType.getShipTo()[0].getState(), "state1");
  EXPECT_EQ(orderType.getShipTo()[0].getZip(), 1);
  EXPECT_EQ(orderType.getShipTo()[0].getCountry(), "US");
  EXPECT_EQ(orderType.getShipTo()[1].getName(), "name2");
  EXPECT_EQ(orderType.getShipTo()[1].getStreet(), "street2");
  EXPECT_EQ(orderType.getShipTo()[1].getCity(), "city2");
  EXPECT_EQ(orderType.getShipTo()[1].getState(), "state2");
  EXPECT_EQ(orderType.getShipTo()[1].getZip(), -7922816251426433759);
  EXPECT_EQ(orderType.getShipTo()[1].getCountry(), "US");

  EXPECT_EQ(orderType.getBillTo()[0].getName(), "billName");
  EXPECT_EQ(orderType.getBillTo()[0].getStreet(), "billStreet");
  EXPECT_EQ(orderType.getBillTo()[0].getCity(), "billCity");
  EXPECT_EQ(orderType.getBillTo()[0].getState(), "billState");
  EXPECT_EQ(orderType.getBillTo()[0].getZip(), 1);
  EXPECT_EQ(orderType.getBillTo()[0].getCountry(), "US");

  ofstream out("old_purchase_simple.xml");
  write(out, orderType);

  PurchaseOrderType orderType2 = *read("old_purchase_simple.xml");

  EXPECT_EQ(orderType.getOrderDate(), orderType2.getOrderDate());

  EXPECT_EQ(orderType.getShipTo()[0].getName(), orderType2.getShipTo()[0].getName());
  EXPECT_EQ(orderType.getShipTo()[0].getStreet(), orderType2.getShipTo()[0].getStreet());
  EXPECT_EQ(orderType.getShipTo()[0].getCity(), orderType2.getShipTo()[0].getCity());
  EXPECT_EQ(orderType.getShipTo()[0].getState(), orderType2.getShipTo()[0].getState());
  EXPECT_EQ(orderType.getShipTo()[0].getZip(), orderType2.getShipTo()[0].getZip());
  EXPECT_EQ(orderType.getShipTo()[0].getCountry(), orderType2.getShipTo()[0].getCountry());
  EXPECT_EQ(orderType.getShipTo()[1].getName(), orderType2.getShipTo()[1].getName());
  EXPECT_EQ(orderType.getShipTo()[1].getStreet(), orderType2.getShipTo()[1].getStreet());
  EXPECT_EQ(orderType.getShipTo()[1].getCity(), orderType2.getShipTo()[1].getCity());
  EXPECT_EQ(orderType.getShipTo()[1].getState(), orderType2.getShipTo()[1].getState());
  EXPECT_EQ(orderType.getShipTo()[1].getZip(), orderType2.getShipTo()[1].getZip());
  EXPECT_EQ(orderType.getShipTo()[1].getCountry(), orderType2.getShipTo()[1].getCountry());

  EXPECT_EQ(orderType.getBillTo()[0].getName(), orderType2.getBillTo()[0].getName());
  EXPECT_EQ(orderType.getBillTo()[0].getStreet(), orderType2.getBillTo()[0].getStreet());
  EXPECT_EQ(orderType.getBillTo()[0].getCity(), orderType2.getBillTo()[0].getCity());
  EXPECT_EQ(orderType.getBillTo()[0].getState(), orderType2.getBillTo()[0].getState());
  EXPECT_EQ(orderType.getBillTo()[0].getZip(), orderType2.getBillTo()[0].getZip());
  EXPECT_EQ(orderType.getBillTo()[0].getCountry(), orderType2.getBillTo()[0].getCountry());
}

TEST_F(XmlTest, Reference) {
  using namespace reference;
  Class _class = *read(Resource("reference.xml").c_str());

  EXPECT_EQ(_class.getStudent()[0], "Sam");
  EXPECT_EQ(_class.getStudent()[1], "Paul");
  EXPECT_EQ(_class.getStudent()[2], "Peter");

  ofstream out("old_reference.xml");
  write(out, _class);
}

TEST_F(XmlTest, Simplecomplexcontent) {
  using namespace simple::complex::content;
  Person person = *readPerson(Resource("simple_complex_content.xml").c_str());
  USAddressP uSAddressP = *person.getFirstUSAddressP();
  KRAddress kRAddress = *person.getFirstKRAddress();
  SubAddress subAddress = *person.getFirstSubAddress();

  EXPECT_EQ(person.getName(), "Petr");

  EXPECT_EQ(uSAddressP.getName(), "404");
  EXPECT_EQ(uSAddressP.getStreet(), "street fighter");
  EXPECT_EQ(uSAddressP.getCity(), "New York");
  EXPECT_EQ(uSAddressP.getState(), "Washington");
  EXPECT_EQ(uSAddressP.getZipcode(), 323232318329852);

  EXPECT_EQ(kRAddress.getName(), "Donga Studio");
  EXPECT_EQ(kRAddress.getStreet(), "Nokdu Street");
  EXPECT_EQ(kRAddress.getCity(), "Seoul");

  EXPECT_EQ(subAddress.getChoice1_optional(), "Temp");

  ofstream out("old_simple_complex_content.xml");
  write(out, person);
}

TEST_F(XmlTest, Attrgroupsimple) {
  using namespace attr::group::simple;
  Student student = *read(Resource("attr_group_simple.xml").c_str());

  EXPECT_EQ(student.getName(), "Jun");
  EXPECT_EQ(student.getCity(), "Mountain View");
  EXPECT_EQ(student.getState(), "CA");
  EXPECT_EQ(student.getRoad(), "Street 101");
  EXPECT_EQ(student.getList()[0], 1);
  EXPECT_EQ(student.getList()[1], 2);
  EXPECT_EQ(student.getList()[2], 3);

  ofstream out("old_attr_group_simple.xml");
  write(out, student);
}
