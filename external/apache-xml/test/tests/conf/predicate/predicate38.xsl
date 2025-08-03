<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" indent="yes"/>

  <!-- FileName: predicate38 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.4 -->
  <!-- Purpose: Stress test of nested and multiple predicates. The production rules
       allow such nesting. -->
  <!-- Author: Paul Dick -->

<xsl:template match="doc">
<out>
  <predicate1>
   <xsl:apply-templates select="foo[(bar[2])='this']"/>
  </predicate1>
  <predicate2>
   <xsl:apply-templates select="foo[(bar[2][(baz[2])='goodbye'])]"/>
  </predicate2>
</out>
</xsl:template>

<xsl:template match="foo[(bar[2])='this']">
 <xsl:text>1 is </xsl:text>	<xsl:for-each select="*">
    	<xsl:value-of select="@attr"/><xsl:text>,</xsl:text>
	</xsl:for-each>
</xsl:template>

<xsl:template match="foo[(bar[(baz[2])='goodbye'])]">
 <xsl:text>3 is </xsl:text>	<xsl:for-each select="*">
    	<xsl:value-of select="@attr"/><xsl:text>,</xsl:text>
	</xsl:for-each>
</xsl:template>

<xsl:template match="foo[(bar[2][(baz[2])='goodbye'])]">
 <xsl:text>2 is </xsl:text>	<xsl:for-each select="*">
    	<xsl:value-of select="@attr"/><xsl:text>,</xsl:text>
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
