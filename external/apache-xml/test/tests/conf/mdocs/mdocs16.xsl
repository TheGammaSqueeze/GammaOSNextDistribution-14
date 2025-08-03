<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="1.0">

  <!-- FileName: mdocsXXX -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.1 -->
  <!-- Purpose: Compare two evaluation sequences for variable containing
    node-set returned from document() call. -->
  <!-- Author: Norm Walsh -->

<xsl:output method="xml" indent="yes"/>

<xsl:param name="terms" select="document('terms.xml')"/>

<xsl:template match="/">
<out>
  <xsl:call-template name="gentext">
    <xsl:with-param name="key" select="'TableofContents'"/>
    <xsl:with-param name="lang" select="'en'"/>
  </xsl:call-template>
</out>
</xsl:template>

<xsl:template name="gentext">
  <xsl:param name="key" select="local-name(.)"/>
  <xsl:param name="lang" select="FOO"/>
  <xsl:variable name="lookup"
    select="($terms/table/expand[@language=$lang]/gentext[@key=$key])[1]"/>
  <xsl:variable name="l10n.name" select="$lookup/@text"/>
  <xsl:element name="key"><xsl:value-of select="$key"/></xsl:element>
  <xsl:element name="lang"><xsl:value-of select="$lang"/></xsl:element>
  <xsl:element name="var"><xsl:value-of select="$l10n.name"/></xsl:element>
  <xsl:element name="text"><xsl:value-of select="$lookup/@text"/></xsl:element>
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
