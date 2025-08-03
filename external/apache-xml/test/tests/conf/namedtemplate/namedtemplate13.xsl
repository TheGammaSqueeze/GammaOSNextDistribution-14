<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: namedtemplate13 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Ensure that we can use the default parameter value on some calls -->

<xsl:template match="/doc">
  <out>
    <xsl:text>Looking for doc...</xsl:text>
    <xsl:choose>
      <xsl:when test="//doc">
        <xsl:call-template name="status">
          <xsl:with-param name="X1">hasDocBelow</xsl:with-param>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="status"/>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:text>&#010;Looking for croc...</xsl:text>
    <xsl:choose>
      <xsl:when test="//croc">
        <xsl:call-template name="status">
          <xsl:with-param name="X1">hasCrocBelow</xsl:with-param>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="status"/>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:text>&#010;Looking for bloc...</xsl:text>
    <xsl:choose>
      <xsl:when test="//bloc">
        <xsl:call-template name="status">
          <xsl:with-param name="X1">hasBlocBelow</xsl:with-param>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="status"/>
      </xsl:otherwise>
    </xsl:choose>
  </out>
</xsl:template>

<xsl:template name="status">
  <xsl:param name="X1">noLowerNode</xsl:param>
  <xsl:text>X1=</xsl:text><xsl:value-of select="$X1"/>
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
