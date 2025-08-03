<?xml version='1.0' encoding='utf-8' ?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  version="1.0">

  <!-- FileName: variable52 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Author: John Howard -->
  <!-- Purpose: test parameters call by reference and by value -->
 
<xsl:output method="xml" indent="no"/>

<xsl:template match="/">
  <out><xsl:text>&#10;</xsl:text>
    <xsl:variable name="foo" select="/data/point"/>

    <list m="ByReference">
      <xsl:call-template name="AllOrNothing">
        <xsl:with-param name="path" select="$foo"/>
      </xsl:call-template>
    </list><xsl:text>&#10;</xsl:text>
    <list m="ByValue">
      <xsl:call-template name="AllOrNothing">
        <xsl:with-param name="path" select="/data/point"/>
      </xsl:call-template>
    </list><xsl:text>&#10;</xsl:text>
    <list m="ByReference">
      <xsl:call-template name="AlmostAndNothing">
        <xsl:with-param name="path" select="$foo"/>
      </xsl:call-template>
    </list><xsl:text>&#10;</xsl:text>
    <list m="ByValue">
      <xsl:call-template name="AlmostAndNothing">
        <xsl:with-param name="path" select="/data/point"/>
      </xsl:call-template>
    </list><xsl:text>&#10;</xsl:text>
  </out>
</xsl:template>

<xsl:template name="AllOrNothing">
  <xsl:param name="path"/>

  <xsl:for-each select="/data/point">
    <xsl:variable name="pos" select="position()"/>

    <xsl:for-each select="$path[$pos]">
      <xsl:value-of select="."/><xsl:text>/</xsl:text>
    </xsl:for-each>
  </xsl:for-each>
</xsl:template>

<xsl:template name="AlmostAndNothing">
  <xsl:param name="path"/>

  <xsl:for-each select="$path">
    <xsl:variable name="pos" select="position()"/>

    <xsl:for-each select="$path[$pos]">
      <xsl:value-of select="."/><xsl:text>-</xsl:text>
    </xsl:for-each>
  </xsl:for-each>
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
