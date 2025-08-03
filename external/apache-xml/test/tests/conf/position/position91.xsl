<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- FileName: position91 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.3 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Apply a predicate to a node-set full of attribute nodes.
     If there were no parentheses, we would be asking for the 7th attribute
     named "title" on each element, but there can only be one attribute of a
     particular name on each, so we'd get the empty set. -->

<xsl:output method="xml" encoding="utf-8"/>

<xsl:template match="chapter">
  <out>
    <xsl:text>
</xsl:text>
    <xsl:for-each select="(section//@title)[7]">
      <noted>
        <xsl:value-of select="name(..)"/><xsl:text> </xsl:text>
        <xsl:value-of select="."/>
      </noted>
    </xsl:for-each>
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
