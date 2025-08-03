<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- FileName: axes116 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.5 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test of //@ sequences -->

<xsl:output method="xml" encoding="utf-8"/>

<xsl:template match="/">
  <out><xsl:text>&#10;</xsl:text>
    <all-attribs><xsl:value-of select="count(//@*)"/></all-attribs><xsl:text>&#10;</xsl:text>
    <all-titles><xsl:value-of select="count(//@title)"/></all-titles><xsl:text>&#10;</xsl:text>
    <all-sect-attribs><xsl:value-of select="count(//section//@*)"/></all-sect-attribs><xsl:text>&#10;</xsl:text>
    <all-sect-titles><xsl:value-of select="count(//section//@title)"/></all-sect-titles><xsl:text>&#10;</xsl:text>
    <!-- The above two, respectively, must equal the sums of sect-*-attribs and sect-*-titles below. -->
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="chapter">
  <chap-attribs><xsl:value-of select="count(.//@*)"/></chap-attribs><xsl:text>&#10;</xsl:text>
  <chap-titles><xsl:value-of select="count(.//@title)"/></chap-titles><xsl:text>&#10;</xsl:text>
  <!-- Rather than iterate, we want to have a sub-element name lead the path expression. -->
  <sect-1-attribs><xsl:value-of select="count(section[1]//@*)"/></sect-1-attribs><xsl:text>&#10;</xsl:text>
  <sect-1-titles><xsl:value-of select="count(section[1]//@title)"/></sect-1-titles><xsl:text>&#10;</xsl:text>
  <sect-2-attribs><xsl:value-of select="count(section[2]//@*)"/></sect-2-attribs><xsl:text>&#10;</xsl:text>
  <sect-2-titles><xsl:value-of select="count(section[2]//@title)"/></sect-2-titles><xsl:text>&#10;</xsl:text>
  <sect-3-attribs><xsl:value-of select="count(section[3]//@*)"/></sect-3-attribs><xsl:text>&#10;</xsl:text>
  <sect-3-titles><xsl:value-of select="count(section[3]//@title)"/></sect-3-titles><xsl:text>&#10;</xsl:text>
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
