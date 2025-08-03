<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: position111 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5 -->
  <!-- Creator: David Bertoni, amended by David Marston -->
  <!-- Purpose: Data Model requires that namespace nodes precede attribute nodes. -->

<xsl:output encoding="UTF-8" method="xml" indent="no"/>

<xsl:template match="Doc">
  <out>
    <xsl:for-each select="namespace::* | attribute::*" >
      <xsl:choose>
        <xsl:when test="contains(.,'http')">
          <!-- it's a namespace node -->
          <xsl:if test="position() &lt;= 3">
            <xsl:text> OK</xsl:text>
          </xsl:if>
        </xsl:when>
        <xsl:when test="contains(.,'attr')">
          <!-- it's an attribute node -->
          <xsl:if test="position() &gt; 3">
            <xsl:text> OK</xsl:text>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>&#10;BAD VALUE: </xsl:text><xsl:value-of select="."/>
        </xsl:otherwise>
      </xsl:choose>
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
