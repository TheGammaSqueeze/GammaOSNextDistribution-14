<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: namespace141 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.1.2 Creating Elements -->
  <!-- Creator: Gordon Chiu -->
  <!-- Purpose: Test for resetting of an unspecified default namespace by copy-of. -->
  <!-- extended variant of namespace137 to check special cases -->

<xsl:output method="xml" indent="no" encoding="UTF-8"/>

<xsl:template match = "/">
  <!-- create an RTF with a mix of namespaced and non-namespaced elements -->
  <xsl:variable name="x">
    <xsl:element name="hello1"/>
    <xsl:element name="hello2" namespace="http://literalURI">
      <xsl:element name="hiya" namespace=""/>
    </xsl:element>
    <xsl:text>&#10;</xsl:text>
    <xsl:element name="hello3" namespace="http://literalURI2">
      <xsl:element name="yo1" namespace="http://literalURI"/>
      <xsl:element name="yo2" namespace=""/>
    </xsl:element>
    <xsl:text>&#10;</xsl:text>
    <xsl:element name="hello4">
      <xsl:element name="hey" namespace=""/>
    </xsl:element>
  </xsl:variable>
  <!-- Now start an output tree, with a namespace node, and copy in the RTF -->
  <out>
    <xsl:text>&#10;</xsl:text>
    <xsl:element name="literalName" namespace="http://literalURI">
      <xsl:text>&#10;</xsl:text>
      <xsl:copy-of select="$x"/>
    </xsl:element>
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
