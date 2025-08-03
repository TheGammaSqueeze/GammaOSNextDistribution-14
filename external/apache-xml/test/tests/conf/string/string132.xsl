<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: string132 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 4.2 String Functions -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test of default (no functions) conversion of integers. -->

<xsl:template match="/">
  <out>
    <set>
    <xsl:text>Positive numbers:&#10;</xsl:text>
    <xsl:value-of select="1"/><xsl:text>,</xsl:text>
    <xsl:value-of select="12"/><xsl:text>,</xsl:text>
    <xsl:value-of select="123"/><xsl:text>,</xsl:text>
    <xsl:value-of select="1234"/><xsl:text>,</xsl:text>
    <xsl:value-of select="12345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="123456"/><xsl:text>,</xsl:text>
    <xsl:value-of select="1234567"/><xsl:text>,</xsl:text>
    <xsl:value-of select="12345678"/><xsl:text>,</xsl:text>
    <xsl:value-of select="123456789"/><xsl:text>,</xsl:text>
    <xsl:value-of select="1234567890"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="12345678901"/><xsl:text>,</xsl:text>
    <xsl:value-of select="123456789012"/><xsl:text>,</xsl:text>
    <xsl:value-of select="1234567890123"/><xsl:text>,</xsl:text>
    <xsl:value-of select="12345678901234"/><xsl:text>,</xsl:text>
    <xsl:value-of select="123456789012345"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="1234567890123456"/><xsl:text>,</xsl:text>
    <xsl:value-of select="12345678901234567"/><xsl:text>,</xsl:text>
    <xsl:value-of select="123456789012345678"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:text>Negative numbers:&#10;</xsl:text>
    <xsl:value-of select="-1"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-12"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-123"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-1234"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-12345"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-123456"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-1234567"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-12345678"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-123456789"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-1234567890"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="-12345678901"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-123456789012"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-1234567890123"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-12345678901234"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-123456789012345"/></set><set><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="-1234567890123456"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-12345678901234567"/><xsl:text>,</xsl:text>
    <xsl:value-of select="-123456789012345678"/>
	</set>
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
