<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: conditional19 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 9.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test that xsl:choose can be nested. -->

<xsl:template match="/doc">
  <out>
    <xsl:for-each select=".//title">
      <xsl:choose>
        <xsl:when test=".='Level A'">*A+</xsl:when>
        <xsl:when test=".='Level B'">B+</xsl:when>
        <xsl:when test=".='Level C'">
          <xsl:choose><!-- When on a C, look ahead -->
            <xsl:when test="name(following-sibling::*[1])='d'">C+</xsl:when>
            <xsl:when test="name(../following-sibling::*[1])='c'">C:</xsl:when>
            <xsl:when test="name(../../following-sibling::*[1])='b'">C-</xsl:when>
            <xsl:otherwise>!Bad tree!</xsl:otherwise>
          </xsl:choose>
        </xsl:when>
        <xsl:when test=".='Level D'">
          <xsl:choose><!-- When on a D, look ahead -->
            <xsl:when test="name(following-sibling::*[1])='e'">D+</xsl:when>
            <xsl:when test="name(../following-sibling::*[1])='d'">D:</xsl:when>
            <xsl:otherwise><!-- We're backing up, but how far? -->
              <xsl:choose>
                <xsl:when test="name(../../following-sibling::*[1])='c'">D-</xsl:when>
                <xsl:otherwise>D|</xsl:otherwise>
              </xsl:choose>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:when>
        <xsl:when test=".='Level E'">E-</xsl:when>
        <xsl:otherwise>TREE: </xsl:otherwise>
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
