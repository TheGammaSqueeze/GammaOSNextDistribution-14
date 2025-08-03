<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- Purpose: To be imported by ../ImpIncl17. -->

<xsl:key name="id" use="@id" match="LAMBDA"/>
<xsl:key name="annid" use="@of" match="Annotation"/>

<xsl:template match="a">
  <out>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="Definition"/><!-- Don't handle the LAMBDAs now -->

<!-- Alternate template for Definition...
Usual "apply" looping, which means we will visit the LAMBDAs -->
<!--
<xsl:template match="Definition">
  <xsl:apply-templates/>
</xsl:template>
-->

<xsl:template match="LAMBDA">
  <xsl:choose>
    <xsl:when test="key('annid',@id)">
      <xsl:text>NO BUG</xsl:text>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>Found one whose id has no annotation!</xsl:text>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template match="node">
  <xsl:variable name="id" select="@id"/>
  <xsl:text>On node whose id is </xsl:text>
  <xsl:value-of select="$id"/>
  <xsl:text> -nodes to apply: </xsl:text>
  <xsl:value-of select="count(key('id',$id))"/><xsl:text>
</xsl:text>
  <xsl:apply-templates select="key('id',$id)"/>
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
