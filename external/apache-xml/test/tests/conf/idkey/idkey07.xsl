<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: idkey07 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.4 Miscellaneous Additional Functions  -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test of generate-id() uniqueness. -->
  <!-- All IDs should be different. -->

<xsl:output method="xml" encoding="UTF-8" indent="no"/>

<xsl:template match="doc">
  <out>
    <xsl:choose>
      <xsl:when test="generate-id() = generate-id(a)">
        <xsl:text>FAIL on parent-child 'a' element: </xsl:text>
        <xsl:value-of select="generate-id()"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(a)"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id() = generate-id(b)">
        <xsl:text>FAIL on parent-child 'b' element: </xsl:text>
        <xsl:value-of select="generate-id()"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(b)"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id(a) = generate-id(b)">
        <xsl:text>FAIL on child 'a' to child 'b' element: </xsl:text>
        <xsl:value-of select="generate-id(a)"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(b)"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id() = generate-id(./@att)">
        <xsl:text>FAIL on parent-attribute: </xsl:text>
        <xsl:value-of select="generate-id()"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(./@att)"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id(./@att) = generate-id(a)">
        <xsl:text>FAIL on attribute-child 'a' element: </xsl:text>
        <xsl:value-of select="generate-id(./@att)"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(a)"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id(./@att) = generate-id(b)">
        <xsl:text>FAIL on attribute-child 'b' element: </xsl:text>
        <xsl:value-of select="generate-id(./@att)"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(b)"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id() = generate-id(./text()[1])">
        <xsl:text>FAIL on parent-text: </xsl:text>
        <xsl:value-of select="generate-id()"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(./text()[1])"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id(./text()[1]) = generate-id(a)">
        <xsl:text>FAIL on text-child 'a' element: </xsl:text>
        <xsl:value-of select="generate-id(./text()[1])"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(a)"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id(./text()[1]) = generate-id(b)">
        <xsl:text>FAIL on text-child 'b' element: </xsl:text>
        <xsl:value-of select="generate-id(./text()[1])"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(b)"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="generate-id(./@att) = generate-id(./text()[1])">
        <xsl:text>FAIL on attribute-text: </xsl:text>
        <xsl:value-of select="generate-id(./@att)"/><xsl:text>,  </xsl:text>
        <xsl:value-of select="generate-id(./text()[1])"/><xsl:text>&#10;</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>Success</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
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
