<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: boolean86 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.4 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test =, !=, and not, comparing empty node-set to boolean. -->

<xsl:output method="xml" encoding="UTF-8"/>

<xsl:template match="doc">
  <out>
    <xsl:variable name="x" select="avj/none"/><!-- empty -->
    <e><xsl:value-of select="$x=true()"/></e>
    <ne><xsl:value-of select="not($x=true())"/></ne>
    <n><xsl:value-of select="$x!=true()"/></n>
    <nn><xsl:value-of select="not($x!=true())"/></nn><xsl:text>
</xsl:text><!-- Now reverse the order of the comparands -->
    <e><xsl:value-of select="true()=$x"/></e>
    <ne><xsl:value-of select="not(true()=$x)"/></ne>
    <n><xsl:value-of select="true()!=$x"/></n>
    <nn><xsl:value-of select="not(true()!=$x)"/></nn>
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
