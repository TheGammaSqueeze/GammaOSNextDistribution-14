<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- FileName: position92 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.3 -->
  <!-- Creator: John Howard -->
  <!-- Purpose: Test retrieving correct values from node-set variables by positional predicate. -->
 
  <xsl:output method="xml"/>

  <xsl:template match="/">
    <xsl:variable name="x1">
      <xsl:variable name="node" select="/foo/*/test/text()"/>
      <xsl:value-of select="$node[1]"/>
    </xsl:variable>
    <xsl:variable name="x6">
      <xsl:variable name="node" select="/foo/*/test/text()"/>
      <xsl:for-each select="$node[6]">
        <xsl:value-of select="."/>
      </xsl:for-each>
    </xsl:variable>
    <xsl:variable name="xL">
      <xsl:variable name="node" select="/foo/*/test/text()"/>
      <xsl:for-each select="$node[last()]">
        <xsl:value-of select="."/>
      </xsl:for-each>
    </xsl:variable>
    <out>
      <x1><xsl:value-of select="$x1"/></x1><xsl:text>&#10;</xsl:text>
      <x6><xsl:value-of select="$x6"/></x6><xsl:text>&#10;</xsl:text>
      <xL><xsl:value-of select="$xL"/></xL>
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
