<?xml version="1.0" encoding="UTF-8"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" indent="yes"/>

<!-- XML Encoding tests -->
<!-- The generic Identity transform -->

  <xsl:template match="chartables">
    <out>
      <xsl:apply-templates select="chars"/>
    </out>
  </xsl:template>


  <!-- Only bother with characters actually in this encoding -->
  <xsl:template match="chars">
    <chars-out>
      <xsl:attribute name="enc"><xsl:value-of select="@enc"/></xsl:attribute>
      <xsl:apply-templates select="char[c]"/>
    </chars-out>
  </xsl:template>

  <xsl:template match="char">
    <char-out>
      <xsl:attribute name="dec"><xsl:value-of select="@dec"/></xsl:attribute>
      <xsl:attribute name="desc"><xsl:value-of select="@desc"/></xsl:attribute>
      <xsl:apply-templates select="c | e"/>
    </char-out>
  </xsl:template>

  <xsl:template match="c">
    <c-out><xsl:call-template name="output-chars"/></c-out>
  </xsl:template>

  <xsl:template match="e">
    <e-out><xsl:call-template name="output-chars"/></e-out>
  </xsl:template>

  <!-- Avoid extra whitespace to limit test -->
  <xsl:template name="output-chars">
    <cpo><xsl:copy-of select="."/></cpo>
    <vo><xsl:value-of select="."/></vo>
    <vod><xsl:value-of disable-output-escaping="yes" select="."/></vod>
    <xsl:variable name="var" select="."/>
    <var><xsl:value-of select="$var"/></var>
    <vard><xsl:value-of disable-output-escaping="yes" select="$var"/></vard>
  </xsl:template>

<!-- Override plain text() processing -->
<xsl:template match="text()"></xsl:template>
     

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
