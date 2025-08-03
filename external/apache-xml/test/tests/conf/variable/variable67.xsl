<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable67 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Creator: David Marston, from Roman Stawski's bug report (#7118) -->
  <!-- Purpose: Setting a variable in the midst of setting another should not alter any others -->

<xsl:output method="xml" indent="no" encoding="ISO-8859-1"/>

<!-- Two global variables: index should be unaffected; source contains settings for others -->
<xsl:variable name="index" select="'okay'"/>

<xsl:variable name="source">
  <xsl:text>
(Before) </xsl:text><xsl:value-of select="$index"/>
  <xsl:variable name="joke1" select="'error'" />
  <xsl:text>
(Between) </xsl:text><xsl:value-of select="$index"/>
  <xsl:variable name="joke2" select="'bug'"/>
  <xsl:text>
(After) </xsl:text><xsl:value-of select="$index"/>
</xsl:variable>

<xsl:template match="doc">
  <out>
    <Start><xsl:value-of select="$index"/></Start>
    <xsl:value-of select="$source"/>
    <xsl:text>
</xsl:text>
    <End><xsl:value-of select="$index"/></End>
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
