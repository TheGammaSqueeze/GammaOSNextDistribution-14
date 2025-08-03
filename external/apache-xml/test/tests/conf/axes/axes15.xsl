<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: AXES15 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 -->
  <!-- Purpose: Test for completion of tree using all axes. -->

<xsl:template match="/">
  <out>
    <xsl:text>From root: 
</xsl:text>
    <xsl:call-template name="show-four-directions"/>
    <xsl:for-each select="descendant-or-self::*">
       <xsl:text>
------------------------------------------------
</xsl:text>
<xsl:variable name="ename">
       <xsl:text>From-</xsl:text>
       <xsl:value-of select="name()"/>
</xsl:variable>
<xsl:element name="{$ename}"><xsl:text>&#10;</xsl:text>
       <xsl:call-template name="show-four-directions"/>
</xsl:element>
    </xsl:for-each>
  </out>
</xsl:template>

<xsl:template name="show-four-directions">
  <xsl:text>ancestors: </xsl:text>
  <xsl:for-each select="ancestor::*">
    <xsl:value-of select="name()"/><xsl:text> </xsl:text>
  </xsl:for-each><xsl:text>
</xsl:text>
  <xsl:text>preceding: </xsl:text>
  <xsl:for-each select="preceding::*">
    <xsl:value-of select="name()"/><xsl:text> </xsl:text>
  </xsl:for-each><xsl:text>
</xsl:text>
  <xsl:text>self: </xsl:text>
  <xsl:for-each select="self::*">
    <xsl:value-of select="name()"/><xsl:text> </xsl:text>
  </xsl:for-each><xsl:text>
</xsl:text>
  <xsl:text>descendant: </xsl:text>
  <xsl:for-each select="descendant::*">
    <xsl:value-of select="name()"/><xsl:text> </xsl:text>
  </xsl:for-each><xsl:text>
</xsl:text>
  <xsl:text>following: </xsl:text>
  <xsl:for-each select="following::*">
    <xsl:value-of select="name()"/><xsl:text> </xsl:text>
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
