<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <!-- FileName: URIResolverTest.xsl -->
  <!-- Author: shane_curcuru@lotus.com -->
  <!-- Purpose: Various kinds of URIs need to be resolved from this stylesheet. -->

<xsl:import href="impincl/SystemIdImport.xsl"/>
<xsl:include href="impincl/SystemIdInclude.xsl"/>

<xsl:template match="doc">
 <out>
  <head>
   <xsl:variable name="resolvedURI1" select="document('../impincl/SystemIdImport.xsl')/xsl:stylesheet/xsl:template[@match='list']"/>
   <xsl:variable name="resolvedURI2" select="document('impincl/SystemIdImport.xsl')/xsl:stylesheet/xsl:template[@match='list']"/>
   <xsl:variable name="resolvedURI3" select="document('systemid/impincl/SystemIdImport.xsl')/xsl:stylesheet/xsl:template[@match='list']"/>
   <xsl:text>Various document() calls: </xsl:text>
   <xsl:value-of select="$resolvedURI1"/>
   <xsl:text>, </xsl:text>
   <xsl:value-of select="$resolvedURI2"/>
   <xsl:text>, </xsl:text>
   <xsl:value-of select="$resolvedURI3"/>
   <xsl:text>.</xsl:text>
  </head>
  <xsl:apply-templates/>
 </out>
</xsl:template>

<xsl:template match="list" priority="-1">
 <main-list>
  <xsl:apply-templates/>
 </main-list>
</xsl:template>

<xsl:template match="item[not(@match-by)]">
  <matched-by-main>
    <xsl:value-of select="." />
  </matched-by-main>
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
