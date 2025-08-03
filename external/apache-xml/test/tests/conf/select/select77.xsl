<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: select77 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.4 -->
  <!-- Creator: Chris McCabe -->
  <!-- Purpose: Try to select a non-existent attribute out of a node-set variable -->

<xsl:output method="xml" encoding="UTF-8"/>

<xsl:template match="/page">
  <out><xsl:text>calling...</xsl:text>
    <xsl:call-template name="BrandHeader">
      <xsl:with-param name="hotelnode" select="/page/contents/avail/hotel"/>
    </xsl:call-template>
  </out>
</xsl:template>

<xsl:template name="BrandHeader">
  <xsl:param name="hotelnode"/>
  <xsl:value-of select="$hotelnode/location/@country"/>
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
