<?xml version="1.0"?>
<ped:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:ped="http://www.w3.org/1999/XSL/Transform" version="1.0">
<ped:output method="xml"/>
<ped:key name="tk" match="xyz" use="zyx"/>
<xsl:variable name="joe">2</xsl:variable>
<ped:param name="sam" select="' x 4'"/>

  <!-- FileName: namespace14 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.1 XSLT Namespace -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Have more than one prefix bound to the URI of XSLT namespace. -->

<ped:template match="doc">
  <out>
    <ped:value-of select="'Testing '"/>
    <xsl:for-each select="*">
      <ped:value-of select="."/><ped:text> </ped:text>
    </xsl:for-each>

    <ped:text>&#010;</ped:text>
    <xsl:value-of select="$joe"/>
    <ped:value-of select="$sam"/>

    <ped:text>&#010;</ped:text>
    <ped:call-template name="ThatTemp">
      <ped:with-param name="sam">quos</ped:with-param>
    </ped:call-template>

    <xsl:text>&#010;</xsl:text>
    <xsl:call-template name="ThatTemp"/>
  </out>
</ped:template>

<xsl:template name="ThatTemp">
  <xsl:param name="sam">unchanged</xsl:param>
  <ped:copy-of select="$sam"/>
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

</ped:stylesheet>
