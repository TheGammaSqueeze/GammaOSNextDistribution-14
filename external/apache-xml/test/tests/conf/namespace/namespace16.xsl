<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
        xmlns:help="http://foobar.com"
        extension-element-prefixes="help">

  <!-- FileName: namespace16 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 Stylesheet Element -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: XSLT processor must ignore a top-level element without giving
        and error if it does not recognize the namespace URI. The prefix used
        must still resolve to a URI; but that URI may not be known. -->

<help:Header comment="Header would go here"/>
<help:TOC comment="Table of Contents"/>
<help:template comment="This is the main template" match="doc" process="children"/>

<xsl:template match="doc">
  <out>
    <xsl:value-of select="'Testing '"/>
    <xsl:for-each select="*">
      <xsl:value-of select="."/><xsl:text> </xsl:text>		
    </xsl:for-each>
    <xsl:call-template name="ThatTemp">
      <xsl:with-param name="sam">quos</xsl:with-param>
    </xsl:call-template>
  </out>
</xsl:template>

<help:template comment="Named template" match="*" name="ThatTemp" process="children"/>

<xsl:template name="ThatTemp">
  <xsl:param name="sam">bo</xsl:param>
  <xsl:copy-of select="$sam"/>
</xsl:template>

<help:Footer comment="Footer would go here"/>


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
