<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: idkeyerr13 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test for two xsl:key declarations with the same name attribute. -->
  <!-- The spec allows this. If the processor implements the feature, then 'titles' is
     one keyspace where the two types of 'use' nodes index their designated match nodes,
     but don't cross-index the others as would happen if | notation was used in one xsl:key. -->

<xsl:output indent="yes"/>

<xsl:key name="titles" match="div" use="title"/>
<xsl:key name="titles" match="@id" use="@id"/>

<xsl:template match="doc">
  <P>Reference numbers should match the titles, links should work.</P>
  <xsl:for-each select="div">
    <HR/>
    <H1 id="{generate-id(.)}">
      <xsl:number level="multiple" count="div" format="1.1. "/>
      <xsl:value-of select="title"/></H1>
    <xsl:apply-templates/>
  </xsl:for-each>
</xsl:template>

<xsl:template match="p">
  <P><xsl:apply-templates/></P>
</xsl:template>

<xsl:template match="divref">
  <A href="#{generate-id(key('titles', .))}">
    <xsl:for-each select="key('titles', .)">
      <xsl:number level="multiple" count="div" format="1.1. "/>
    </xsl:for-each>
    <xsl:value-of select="."/>
  </A>
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
