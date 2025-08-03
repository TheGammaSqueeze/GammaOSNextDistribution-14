<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: copy25 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.5 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Accumulate attributes from several places in the source. -->

<xsl:output method="xml" encoding="UTF-8" />

<xsl:template match="/">
  <out>
    <a>
      <!-- first, get all attribute nodes under the 'a' node in the source -->
      <xsl:for-each select="main/a/descendant-or-self::*/@*">
        <xsl:copy/>
      </xsl:for-each>
      <!-- next, get an attribute node from elsewhere -->
      <xsl:for-each select="main/size[@for='d']">
        <xsl:apply-templates select="@h | @w"/>
      </xsl:for-each>
    </a>
  </out>
</xsl:template>

<xsl:template match="@*">
  <xsl:copy/>
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
