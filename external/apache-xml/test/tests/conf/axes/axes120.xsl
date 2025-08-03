<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
  				xmlns:a="name-a"
  				xmlns:b="name-b"
  				xmlns:c="namc-c">

  <!-- FileName: axes120 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 Axes-->
  <!-- Creator: Paul Dick (Revision of axes118) -->
  <!-- Purpose: Check that namespace nodes exist separately on each element. -->

<xsl:template match="/">
  <out>
    <xsl:text>&#010;</xsl:text>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="a:root">
  <xsl:for-each select="self::node()|child::*"><xsl:text>&#010;</xsl:text>
    <xsl:element name="{name(.)}"/><xsl:text>&#010;</xsl:text>
  	<xsl:for-each select="namespace::*">
    	<xsl:sort select="name(.)"/><xsl:text>&#09;</xsl:text>
    	<xsl:element name="{name(.)}"><xsl:value-of select="."/></xsl:element><xsl:text>,&#010;</xsl:text>
  	</xsl:for-each>
  </xsl:for-each>
</xsl:template>

<xsl:template match="text()"/><!-- To suppress empty lines -->


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
