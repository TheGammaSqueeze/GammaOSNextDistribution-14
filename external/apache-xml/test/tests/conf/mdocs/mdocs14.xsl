<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: mdocs14 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.1 Multiple Source Documents -->
  <!-- Author: Jeni Tennison -->
  <!-- Purpose: Use document() to perform an include-like operation between two files.
     At the conformance level, this shows that we can put the node-set from document()
     into a variable, then reference where a node-set is required. -->

<xsl:output method="xml"/>
<xsl:variable name="xml-source" select="/" />
<xsl:variable name="html-template" select="document('x14template.html')" />

<xsl:template match="/">
  <xsl:apply-templates select="$html-template" mode="copy" />
</xsl:template>

<xsl:template match="*" mode="copy">
  <xsl:copy>
    <xsl:copy-of select="@*" />
    <xsl:apply-templates mode="copy" />
  </xsl:copy>
</xsl:template>

<xsl:template match="xml-content" mode="copy">
  <xsl:text>XML</xsl:text><xsl:apply-templates select="$xml-source" mode="copy"/>
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
