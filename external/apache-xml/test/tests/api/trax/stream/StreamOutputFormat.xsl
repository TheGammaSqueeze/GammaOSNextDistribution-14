<?xml version="1.0" encoding="UTF-8"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<!-- Include all XSLT spec xsl:output attrs -->
<xsl:output method="xml"
            version="123.45"
            encoding="UTF-8"
            standalone="yes"
            doctype-public="this-is-doctype-public"
            doctype-system="this-is-doctype-system"
            cdata-section-elements="cdataHere"
            indent="yes"
            media-type="text/test/xml"
            omit-xml-declaration="no" />

  <xsl:template match="doc">
    <out>
      <cdataHere>CDATA? or not?</cdataHere>
      <xsl:text>foo</xsl:text>
      <xsl:copy-of select="cdataHere" />
      <out2>
        <xsl:text>bar</xsl:text>
        <xsl:copy-of select="selector" />
      </out2>
    </out>
  </xsl:template>

  <!--
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the  "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
  -->

</xsl:stylesheet>
