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
#include "cross_libxml.h"
#include "cross_tinyxml.h"

#include "xmltest.h"

using namespace std;
namespace libxml = cross::libxml;
namespace tinyxml = cross::tinyxml;

TEST_F(XmlTest, LibXmlGenerateTinyXmlParse) {
  std::vector<libxml::USAddress> shipTo;
  shipTo.emplace_back("name1", "street1", "city1", "state1", 1, "US");
  shipTo.emplace_back("name2", "street2", "city2", "state2", 7922816251426433759, "US");

  std::vector<libxml::USAddress> billTo;
  billTo.emplace_back("billName", "billStree", "billCity", "billState", 1, "US");
  billTo.emplace_back("billName2", std::nullopt, std::nullopt,
      std::nullopt, std::nullopt, std::nullopt);

  libxml::PurchaseOrderType libXmlDoc(std::move(shipTo), std::move(billTo), "1900-01-01");

  ofstream out("libxml_generated.xml");
  write(out, libXmlDoc);

  tinyxml::PurchaseOrderType tinyXmlDoc = *tinyxml::read("libxml_generated.xml");

  EXPECT_EQ(libXmlDoc.getOrderDate(), tinyXmlDoc.getOrderDate());

  EXPECT_EQ(libXmlDoc.getShipTo().size(), tinyXmlDoc.getShipTo().size());
  EXPECT_EQ(libXmlDoc.getShipTo()[0].getName(), tinyXmlDoc.getShipTo()[0].getName());
  EXPECT_EQ(libXmlDoc.getShipTo()[0].getStreet(), tinyXmlDoc.getShipTo()[0].getStreet());
  EXPECT_EQ(libXmlDoc.getShipTo()[0].getCity(), tinyXmlDoc.getShipTo()[0].getCity());
  EXPECT_EQ(libXmlDoc.getShipTo()[0].getState(), tinyXmlDoc.getShipTo()[0].getState());
  EXPECT_EQ(libXmlDoc.getShipTo()[0].getZip(), tinyXmlDoc.getShipTo()[0].getZip());
  EXPECT_EQ(libXmlDoc.getShipTo()[0].getCountry(), tinyXmlDoc.getShipTo()[0].getCountry());
  EXPECT_EQ(libXmlDoc.getShipTo()[1].getName(), tinyXmlDoc.getShipTo()[1].getName());
  EXPECT_EQ(libXmlDoc.getShipTo()[1].getStreet(), tinyXmlDoc.getShipTo()[1].getStreet());
  EXPECT_EQ(libXmlDoc.getShipTo()[1].getCity(), tinyXmlDoc.getShipTo()[1].getCity());
  EXPECT_EQ(libXmlDoc.getShipTo()[1].getState(), tinyXmlDoc.getShipTo()[1].getState());
  EXPECT_EQ(libXmlDoc.getShipTo()[1].getZip(), tinyXmlDoc.getShipTo()[1].getZip());
  EXPECT_EQ(libXmlDoc.getShipTo()[1].getCountry(), tinyXmlDoc.getShipTo()[1].getCountry());

  EXPECT_EQ(libXmlDoc.getBillTo().size(), tinyXmlDoc.getBillTo().size());
  EXPECT_EQ(libXmlDoc.getBillTo()[0].getName(), tinyXmlDoc.getBillTo()[0].getName());
  EXPECT_EQ(libXmlDoc.getBillTo()[0].getStreet(), tinyXmlDoc.getBillTo()[0].getStreet());
  EXPECT_EQ(libXmlDoc.getBillTo()[0].getCity(), tinyXmlDoc.getBillTo()[0].getCity());
  EXPECT_EQ(libXmlDoc.getBillTo()[0].getState(), tinyXmlDoc.getBillTo()[0].getState());
  EXPECT_EQ(libXmlDoc.getBillTo()[0].getZip(), tinyXmlDoc.getBillTo()[0].getZip());
  EXPECT_EQ(libXmlDoc.getBillTo()[0].getCountry(), tinyXmlDoc.getBillTo()[0].getCountry());
  EXPECT_EQ(libXmlDoc.getBillTo()[1].getName(), tinyXmlDoc.getBillTo()[1].getName());
  EXPECT_EQ(libXmlDoc.getBillTo()[1].hasStreet(), tinyXmlDoc.getBillTo()[1].hasStreet());
  EXPECT_EQ(libXmlDoc.getBillTo()[1].hasCity(), tinyXmlDoc.getBillTo()[1].hasCity());
  EXPECT_EQ(libXmlDoc.getBillTo()[1].hasState(), tinyXmlDoc.getBillTo()[1].hasState());
  EXPECT_EQ(libXmlDoc.getBillTo()[1].hasZip(), tinyXmlDoc.getBillTo()[1].hasZip());
  EXPECT_EQ(libXmlDoc.getBillTo()[1].hasCountry(), tinyXmlDoc.getBillTo()[1].hasCountry());
}

TEST_F(XmlTest, TinyXmlGenerateLibXmlParse) {
  std::vector<tinyxml::USAddress> shipTo;
  shipTo.emplace_back("name1", "street1", "city1", "state1", 1, "US");
  shipTo.emplace_back("name2", "street2", "city2", "state2", 7922816251426433759, "US");

  std::vector<tinyxml::USAddress> billTo;
  billTo.emplace_back("billName", "billStree", "billCity", "billState", 1, "US");
  billTo.emplace_back("billName2", std::nullopt, std::nullopt,
      std::nullopt, std::nullopt, std::nullopt);

  tinyxml::PurchaseOrderType tinyXmlDoc(std::move(shipTo), std::move(billTo), "1900-01-01");

  ofstream out("tinyxml_generated.xml");
  write(out, tinyXmlDoc);

  libxml::PurchaseOrderType libXmlDoc = *libxml::read("tinyxml_generated.xml");

  EXPECT_EQ(tinyXmlDoc.getOrderDate(), libXmlDoc.getOrderDate());

  EXPECT_EQ(tinyXmlDoc.getShipTo().size(), libXmlDoc.getShipTo().size());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[0].getName(), libXmlDoc.getShipTo()[0].getName());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[0].getStreet(), libXmlDoc.getShipTo()[0].getStreet());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[0].getCity(), libXmlDoc.getShipTo()[0].getCity());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[0].getState(), libXmlDoc.getShipTo()[0].getState());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[0].getZip(), libXmlDoc.getShipTo()[0].getZip());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[0].getCountry(), libXmlDoc.getShipTo()[0].getCountry());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[1].getName(), libXmlDoc.getShipTo()[1].getName());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[1].getStreet(), libXmlDoc.getShipTo()[1].getStreet());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[1].getCity(), libXmlDoc.getShipTo()[1].getCity());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[1].getState(), libXmlDoc.getShipTo()[1].getState());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[1].getZip(), libXmlDoc.getShipTo()[1].getZip());
  EXPECT_EQ(tinyXmlDoc.getShipTo()[1].getCountry(), libXmlDoc.getShipTo()[1].getCountry());

  EXPECT_EQ(tinyXmlDoc.getBillTo().size(), libXmlDoc.getBillTo().size());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[0].getName(), libXmlDoc.getBillTo()[0].getName());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[0].getStreet(), libXmlDoc.getBillTo()[0].getStreet());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[0].getCity(), libXmlDoc.getBillTo()[0].getCity());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[0].getState(), libXmlDoc.getBillTo()[0].getState());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[0].getZip(), libXmlDoc.getBillTo()[0].getZip());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[0].getCountry(), libXmlDoc.getBillTo()[0].getCountry());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[1].getName(), libXmlDoc.getBillTo()[1].getName());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[1].hasStreet(), libXmlDoc.getBillTo()[1].hasStreet());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[1].hasCity(), libXmlDoc.getBillTo()[1].hasCity());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[1].hasState(), libXmlDoc.getBillTo()[1].hasState());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[1].hasZip(), libXmlDoc.getBillTo()[1].hasZip());
  EXPECT_EQ(tinyXmlDoc.getBillTo()[1].hasCountry(), libXmlDoc.getBillTo()[1].hasCountry());
}
