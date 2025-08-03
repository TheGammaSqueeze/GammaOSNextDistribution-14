<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: namedtemplate09 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 6 Named Templates -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test of nested template calls. -->

<xsl:template match="doc">
  <out>
    <xsl:call-template name="tmplt1">
      <xsl:with-param name="par1" select="0"/>
    </xsl:call-template>
  </out>
</xsl:template>

<xsl:template name="tmplt1">
  <xsl:param name="par1">par1 default data</xsl:param>
  <in1>
    <xsl:value-of select="$par1"/>
    <xsl:call-template name="tmplt2">
      <xsl:with-param name="par1" select="1"/>
    </xsl:call-template>
  </in1>
</xsl:template>

<xsl:template name="tmplt2">
  <xsl:param name="par1">par1 default in tmplt2</xsl:param>
  <in2>
    <xsl:value-of select="$par1"/>
    <xsl:call-template name="tmplt3">
      <xsl:with-param name="par1" select="2"/>
    </xsl:call-template>
  </in2>
</xsl:template>

<xsl:template name="tmplt3">
  <xsl:param name="par1">par1 default in tmplt3</xsl:param>
  <in3>
    <xsl:value-of select="$par1"/>
    <xsl:call-template name="tmplt4">
      <xsl:with-param name="par1" select="3"/>
    </xsl:call-template>
  </in3>
</xsl:template>

<xsl:template name="tmplt4">
  <xsl:param name="par1">par1 default in tmplt4</xsl:param>
  <in4>
    <xsl:value-of select="$par1"/>
    <xsl:call-template name="tmplt5">
      <xsl:with-param name="par1" select="4"/>
    </xsl:call-template>
  </in4>
</xsl:template>

<xsl:template name="tmplt5">
  <xsl:param name="par1">par1 default in tmplt5</xsl:param>
  <in5>
    <xsl:value-of select="$par1"/>
    <xsl:call-template name="tmplt6">
      <xsl:with-param name="par1" select="5"/>
    </xsl:call-template>
  </in5>
</xsl:template>

<xsl:template name="tmplt6">
  <xsl:param name="par1">par1 default in tmplt6</xsl:param>
  <in6>
    <xsl:value-of select="$par1"/><xsl:text> - all the way in</xsl:text>
  </in6>
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
