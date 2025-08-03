<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: MDOCS05 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.1 -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Test document() function with path following. -->

<xsl:template match="catalog">
  <out>
    <xsl:apply-templates select="document(pointer/urlref/@urlstr)/market.participant/business.identity.group/business.name"/>
    <xsl:apply-templates select="document('../mdocs/compu.xml')/market.participant/address.set/*"/>
  </out>
</xsl:template>

<xsl:template match="location.in.street">
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="street">
  <xsl:apply-templates/><xsl:text></xsl:text>
</xsl:template>

<xsl:template match="city">
  <xsl:apply-templates/><xsl:text>, </xsl:text>
</xsl:template>

<xsl:template match="country.subentity">
  <xsl:apply-templates/><xsl:text> </xsl:text>
</xsl:template>

<xsl:template match="postcode">
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="telephone.number">
  <xsl:apply-templates/>
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
