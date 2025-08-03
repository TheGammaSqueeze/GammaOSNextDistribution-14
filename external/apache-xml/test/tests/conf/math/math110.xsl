<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: MATH110 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 4.4 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test of number() conversion function for small decimal numbers. -->

<xsl:output method="xml" indent="no" encoding="UTF-8"/>

<xsl:template match="doc">
  <out>
      <xsl:value-of select="number(1.75)"/>
      <xsl:text>|</xsl:text>
      <xsl:value-of select="number(7 div 4)"/>
      <xsl:text>|</xsl:text>
      <xsl:value-of select="(number(1.75) = (7 div 4))"/>
      <xsl:text>|&#10;</xsl:text>
      <xsl:value-of select="number(0.109375 * 16)"/>
      <xsl:text>|</xsl:text>
      <xsl:value-of select="(number(1.75) = (0.109375 * 16))"/>
      <xsl:text>|&#10;</xsl:text>
      <xsl:value-of select="number(k)"/>
      <xsl:text>|</xsl:text>
      <xsl:value-of select="number(4 div 10000)"/>
      <xsl:text>|</xsl:text>
      <xsl:value-of select="(number(k) = (4 div 10000))"/>
      <xsl:text>|&#10;</xsl:text>
      <xsl:value-of select="number(0.0001 * 4)"/>
      <xsl:text>|</xsl:text>
      <xsl:value-of select="(number(k) = (0.0001 * 4))"/>
      <xsl:text>|&#10;</xsl:text>
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
