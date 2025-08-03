<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: namedtemplate02 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 6 -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Test for xsl:call-template of one that has both match and name. -->

  <!-- Also verifies that xsl:call-template does not change
       the current node or the current node list resulting in
       'top-level-a' being printed twice during the first 
       instantiation of the named template. -->

<xsl:template match="doc">
  <out>
    <xsl:variable name="X1">
      <xsl:value-of select="a"/>
    </xsl:variable>

    <xsl:call-template name="ntmp1">
      <xsl:with-param name="pvar1" select="'Call-template'"/>
      <xsl:with-param name="pvar2" select="$X1"/>
    </xsl:call-template>

    <xsl:text>&#010;</xsl:text>

    <xsl:apply-templates select="doc1">
      <xsl:with-param name="pvar1" select="'Apply-templates'"/>
      <xsl:with-param name="pvar2" select="$X1"/>
    </xsl:apply-templates>
  </out>
</xsl:template>

<xsl:template name="ntmp1" match="doc1">
  <xsl:param name="pvar1">Default text in pvar1</xsl:param>
  <xsl:param name="pvar2">Default text in pvar2</xsl:param>

  <xsl:value-of select="$pvar1"/><xsl:text>,</xsl:text>
  <xsl:value-of select="$pvar2"/><xsl:text>,</xsl:text>
  <xsl:value-of select="a"/><xsl:text>,</xsl:text>
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
