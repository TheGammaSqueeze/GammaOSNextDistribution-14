<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<!-- FileName: axes131 -->
<!-- Document: http://www.w3.org/TR/xpath -->
<!-- DocVersion: 19991116 -->
<!-- Section: 2.2 -->
<!-- Creator: Henry Zongaro -->
<!-- Purpose: Test for descendant axis when siblings have some children or
     the context node has no siblings. -->

<xsl:output method="xml" encoding="UTF-8" indent="no" />

<xsl:template match="doc">
  <out>
    <xsl:for-each select="child">
      <xsl:text>&#10;</xsl:text>
      <child number="{position()}">
        <xsl:for-each select="grandchild">
          <xsl:text>&#10;</xsl:text>
          <grandchild number="{position()}">
            <xsl:attribute name="descendantaxiscount"><xsl:value-of select="count(descendant::*)"/></xsl:attribute>
            <xsl:attribute name="descendantorselfaxiscount"><xsl:value-of select="count(descendant-or-self::*)"/></xsl:attribute>
          </grandchild>
        </xsl:for-each>
        <xsl:text>&#10;</xsl:text>
      </child>
    </xsl:for-each>
    <xsl:text>&#10;</xsl:text>
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
