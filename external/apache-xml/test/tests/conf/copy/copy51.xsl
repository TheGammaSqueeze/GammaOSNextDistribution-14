<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:target="name-b"
    exclude-result-prefixes="target"
    version="1.0">

  <!-- FileName: copy51 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.5 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test for xsl:copy on namespaces referenced directly. -->
  <!-- Elaboration: while namespace::* will include the implied declaration for
    The XML Namespace, it's not appropriate to serialize it as an explicit declaration.
    XML output from here could be pipelined to some other process that wants the XML
    namespace to be implicit only. -->

<xsl:output method="xml" encoding="UTF-8"/>

<xsl:template match="/">
  <out>
    <xsl:apply-templates select=".//target:sub"/>
  </out>
</xsl:template>

<xsl:template match="target:sub">
  <xsl:for-each select="namespace::*">
    <xsl:sort select="local-name(.)"/>
    <xsl:copy/>
  </xsl:for-each>
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
