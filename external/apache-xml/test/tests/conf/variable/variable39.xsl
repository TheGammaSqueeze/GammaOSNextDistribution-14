<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable39 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.3 Using Variables and Parameters with xsl:copy-of -->
  <!-- Purpose: Build an RTF from instructions, then use xsl:copy-of. -->
  <!-- Author: David Marston -->

<xsl:template match="doc">
  <xsl:variable name="tree">
    <xsl:element name="main">
      <xsl:element name="first">
        <xsl:attribute name="type">text</xsl:attribute>
        <xsl:text>junk</xsl:text>
      </xsl:element>
      <xsl:element name="second">
        <xsl:attribute name="type">fetched</xsl:attribute>
        <xsl:value-of select="." />
      </xsl:element>
      <xsl:element name="third">
        <xsl:attribute name="type">comment</xsl:attribute>
        <xsl:comment>remarks</xsl:comment>
      </xsl:element>
    </xsl:element>
  </xsl:variable>
  <out>
    <xsl:copy-of select="$tree"/>
  </out>
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
