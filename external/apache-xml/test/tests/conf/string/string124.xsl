<?xml version = "1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0" >

  <!-- FileName: string124 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 4.2 String Functions -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Test of contains() function searching for an entity. -->

<xsl:strip-space elements = "*"/>
<xsl:output method = "xml"/>

<xsl:template match="text()[contains(., 'SYMBOL 180 \f &quot;')]" priority="2">
   Found match of entity
</xsl:template>

<xsl:template match="*">
  <out>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="text()" priority="1"/><!-- Suppress text copying if contains() fails -->


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
