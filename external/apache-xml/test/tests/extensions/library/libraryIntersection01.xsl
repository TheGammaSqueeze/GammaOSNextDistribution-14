<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:xalan="http://xml.apache.org/xalan"
    exclude-result-prefixes="xalan">
<xsl:output indent="yes"/>

  <!-- FileName: libraryIntersection01.xsl -->
  <!-- Creator: Shane Curcuru -->
  <!-- Purpose: Basic test of intersection(ns, ns) extension function -->

<xsl:template match="doc">
  <out>
    <test desc="selects abc (abc, abc)">
      <xsl:copy-of select="xalan:intersection(list[@name='abc']/item, list[@name='abc']/item)"/>
      <xsl:text>, </xsl:text>
      <xsl:value-of select="xalan:intersection(list[@name='abc']/item, list[@name='abc']/item)"/>
    </test>
    <test desc="selects abc (abc, abc and parent)">
      <xsl:copy-of select="xalan:intersection(list[@name='abc'], list[@name='abc'])"/>
    </test>
    <test desc="selects nothing (abc, xyz)">
      <xsl:copy-of select="xalan:intersection(list[@name='abc']/item, list[@name='xyz']/item)"/>
    </test>
    <test desc="selects nothing (abc, abc2)">
      <xsl:copy-of select="xalan:intersection(list[@name='abc']/item, list[@name='abc2']/item)"/>
    </test>
    <test desc="selects a (abc, abc[1])">
      <xsl:copy-of select="xalan:intersection(list[@name='abc']/item, list[@name='abc']/item[1])"/>
    </test>
    <test desc="selects a (abc[1], abc)">
      <xsl:copy-of select="xalan:intersection(list[@name='abc']/item[1], list[@name='abc']/item)"/>
    </test>
    <test desc="selects a (abc[1], abc[1])">
      <xsl:copy-of select="xalan:intersection(list[@name='abc']/item[1], list[@name='abc']/item[1])"/>
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
