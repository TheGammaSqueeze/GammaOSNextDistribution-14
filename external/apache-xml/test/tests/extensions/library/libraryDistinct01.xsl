<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:xalan="http://xml.apache.org/xalan"
    exclude-result-prefixes="xalan">
<!-- Indent just for readability -->    
<xsl:output indent="yes"/>

  <!-- FileName: libraryDistinct01.xsl -->
  <!-- Creator: Shane Curcuru -->
  <!-- Purpose: Basic test of distinct(ns, ns) extension function -->

<xsl:template match="doc">
  <out>
    <test desc="selects abc">
      <xsl:copy-of select="xalan:distinct(list[@name='abc']/item)"/>
      <xsl:text>, </xsl:text>
      <xsl:value-of select="xalan:distinct(list[@name='abc']/item)"/>
    </test>
    <test desc="selects a from aaa">
      <xsl:copy-of select="xalan:distinct(list[@name='aaa']/item)"/>
    </test>
    <test desc="selects xyz">
      <xsl:copy-of select="xalan:distinct(list[@name='xyz']/item)"/>
    </test>
    <test desc="selects ab from aab">
      <xsl:copy-of select="xalan:distinct(list[@name='aab']/item)"/>
    </test>
    <test desc="selects abc from abc|abc2">
      <xsl:copy-of select="xalan:distinct(list[@name='abc']/item | list[@name='abc2']/item)"/>
    </test>
    <test desc="selects abcxyz from abc|aab|xyz">
      <xsl:copy-of select="xalan:distinct(list[@name='abc']/item | list[@name='aab']/item | list[@name='xyz']/item)"/>
    </test>
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
