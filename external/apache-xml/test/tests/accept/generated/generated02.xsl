<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: generated02 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.4 Miscellaneous Additional Functions  -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test of generate-id() with display of actual IDs -->
  <!-- Exact strings will vary by processor. All should meet the constraints of XML names.
    Use this test to catch unexplained changes in the naming scheme. -->

<xsl:output method="xml" encoding="UTF-8" indent="no"/>

<xsl:template match="/">
  <out>
    <xsl:value-of select="generate-id(doc)"/><xsl:text>,</xsl:text>
    <xsl:apply-templates select="doc/@*"/>
    <xsl:apply-templates select="doc/child::node()"/>
  </out>
</xsl:template>

<xsl:template match="node()">
  <xsl:value-of select="generate-id(.)"/><xsl:text>,</xsl:text>
  <xsl:apply-templates select="./@*"/>
  <xsl:apply-templates select="./child::node()"/><xsl:text>;</xsl:text>
</xsl:template>

<xsl:template match="@*">
  <xsl:value-of select="generate-id(.)"/><xsl:text>,</xsl:text>
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
