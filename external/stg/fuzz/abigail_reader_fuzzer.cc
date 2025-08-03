// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2021-2022 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Matthias Maennich

#include <string>

#include <libxml/parser.h>
#include <libxml/tree.h>
#include "abigail_reader.h"
#include "error.h"
#include "graph.h"

static void DoNothing(void*, const char*, ...) {}

extern "C" int LLVMFuzzerTestOneInput(char* data, size_t size) {
  xmlParserCtxtPtr ctxt = xmlNewParserCtxt();
  // Suppress libxml error messages.
  xmlSetGenericErrorFunc(ctxt, (xmlGenericErrorFunc) DoNothing);
  xmlDocPtr doc = xmlCtxtReadMemory(
      ctxt, data, size, nullptr, nullptr,
      XML_PARSE_NOERROR | XML_PARSE_NONET | XML_PARSE_NOWARNING);
  xmlFreeParserCtxt(ctxt);

  // Bail out if the doc XML is invalid.
  if (!doc) {
    return 0;
  }

  xmlNodePtr root = xmlDocGetRootElement(doc);
  if (root) {
    try {
      stg::Graph graph;
      stg::abixml::Abigail(graph).ProcessRoot(root);
    } catch (const stg::Exception&) {
      // Pass as this is us catching invalid XML properly.
    }
  }

  xmlFreeDoc(doc);

  return 0;
}
