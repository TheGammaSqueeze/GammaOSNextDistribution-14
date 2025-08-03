<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: position94 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Creator: Sergei Ivanov, adapted by David Marston -->
  <!-- Purpose: Test of position() and last() in xsl:with-param. -->

<xsl:output method="xml" encoding="UTF-8"/>

<xsl:template match="/root">
  <out>
    <xsl:apply-templates select="a" />
  </out>
</xsl:template>

<xsl:template match="a">
  <xsl:text>
</xsl:text>
  <xsl:text>In a, position() is </xsl:text>
  <xsl:value-of select="position()"/>
  <xsl:text> and last() is </xsl:text>
  <xsl:value-of select="last()"/><xsl:text>
</xsl:text>
  <xsl:apply-templates select="b">
    <xsl:with-param name="a.pos" select="position()"/>
    <xsl:with-param name="a.last" select="last()"/>
  </xsl:apply-templates>
</xsl:template>

<xsl:template match="b">
  <xsl:param name="a.pos" select="pos-bad"/>
  <xsl:param name="a.last" select="last-bad"/>
  <xsl:text>In b number </xsl:text>
  <xsl:value-of select="position()"/>
  <xsl:text>, $a.pos=</xsl:text>
  <xsl:value-of select="$a.pos"/>
  <xsl:text> and $a.last=</xsl:text>
  <xsl:value-of select="$a.last"/>
  <xsl:text>
</xsl:text>
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
