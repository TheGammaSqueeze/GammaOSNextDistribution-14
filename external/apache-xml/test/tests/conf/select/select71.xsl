<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName:  select71 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.3 Nodesets. -->
  <!-- Purpose: Test union operator using overlapping node-sets. Results should
       always be output in doc order regardless of order of select attribute. -->
  <!-- Creator: Paul Dick -->

<xsl:output indent="no"/>

<xsl:template match="directions">
  <out><xsl:text>
    </xsl:text>
    <xsl:copy-of select="north/* | north/dup1 | north/dup2"/>,
    <xsl:copy-of select="north/dup2 | north/dup1 | north/*"/>,
    <xsl:copy-of select="//north/dup2 | south/preceding-sibling::*[4]/* | north/dup1 | north/*"/>,
    <xsl:copy-of select="north/dup2 | document('select71.xml')/south/preceding-sibling::*[4]/* | north/*"/>
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
