<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: match30 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.2 -->
  <!-- Purpose: Use multiple levels of child axis in match patterns.
     Intermix 'child::' and default, but only with child:: in the middle. -->
  <!-- Creator: Henry Zongaro -->

<xsl:output method="xml" encoding="UTF-8" indent="no"/>

  <xsl:template match="/">
    <out>
      <xsl:apply-templates/><!-- rely on some built-ins -->
    </out>
  </xsl:template>

  <xsl:template match="doc/l1/v2">
    <xsl:text>
</xsl:text>
    <match>
      <xsl:text>Rule doc/l1/v2; value of matched node:  </xsl:text>
      <xsl:value-of select='.'/>
    </match>
  </xsl:template>

  <xsl:template match="doc/l1//v3">
    <xsl:text>
</xsl:text>
    <match>
      <xsl:text>Rule doc/l1//v3; value of matched node:  </xsl:text>
      <xsl:value-of select='.'/>
    </match>
  </xsl:template>

  <xsl:template match="doc//l2/w3">
    <xsl:text>
</xsl:text>
    <match>
      <xsl:text>Rule doc//l2/w3; value of matched node:  </xsl:text>
      <xsl:value-of select='.'/>
    </match>
  </xsl:template>

  <xsl:template match="doc//l2//v4">
    <xsl:text>
</xsl:text>
    <match>
      <xsl:text>Rule doc//l2//v4; value of matched node:  </xsl:text>
      <xsl:value-of select='.'/>
    </match>
  </xsl:template>

  <xsl:template match="doc/child::l1/x2">
    <xsl:text>
</xsl:text>
    <match>
      <xsl:text>Rule doc/child::l1/x2; value of matched node:  </xsl:text>
      <xsl:value-of select='.'/>
    </match>
  </xsl:template>

  <xsl:template match="doc/child::l1//x3">
    <xsl:text>
</xsl:text>
    <match>
      <xsl:text>Rule doc/child::l1//x3; value of matched node:  </xsl:text>
      <xsl:value-of select='.'/>
    </match>
  </xsl:template>

  <xsl:template match="doc//child::l2/y3">
    <xsl:text>
</xsl:text>
    <match>
      <xsl:text>Rule doc//child::l2/y3; value of matched node:  </xsl:text>
      <xsl:value-of select='.'/>
    </match>
  </xsl:template>

  <xsl:template match="doc//child::l2//x4">
    <xsl:text>
</xsl:text>
    <match>
      <xsl:text>Rule doc//child::l2//x4; value of matched node:  </xsl:text>
      <xsl:value-of select='.'/>
    </match>
  </xsl:template>

  <xsl:template match='text()'/><!-- squelch direct replay of text -->


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
