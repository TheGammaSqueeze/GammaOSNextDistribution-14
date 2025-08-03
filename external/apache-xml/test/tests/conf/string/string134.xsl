<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: string134 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 4.2 String Functions -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test of default (no functions) conversion of decimal numbers with decimal point at all positions. -->

<xsl:template match="/">
  <out>
  <set>
   <xsl:text>Positive numbers:&#10;</xsl:text>
    <xsl:value-of select="9.87654321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="98.7654321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="987.654321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="9876.54321012345"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="98765.4321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="987654.321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="9876543.21012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="98765432.1012345"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="987654321.012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="9876543210.12345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="98765432101.2345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="987654321012.345"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="9876543210123.45"/><xsl:text>,</xsl:text>
    <xsl:value-of select="98765432101234.5"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:text>Negative numbers:&#10;</xsl:text>
    <xsl:value-of select="-9.87654321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-98.7654321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-987.654321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-9876.54321012345"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="-98765.4321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-987654.321012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-9876543.21012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-98765432.1012345"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="-987654321.012345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-9876543210.12345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-98765432101.2345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-987654321012.345"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="-9876543210123.45"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-98765432101234.5"/><xsl:text>&#10;</xsl:text>
  </set></out>
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
