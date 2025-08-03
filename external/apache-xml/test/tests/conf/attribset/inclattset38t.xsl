<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: InclAttSet38t -->
  <!-- Purpose: Holds template and attribute-set definition to be included. -->

<xsl:template match="doc">
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="foo">
  <xsl:element name="foocopy" use-attribute-sets="colorset decoset fontset">
    <xsl:value-of select="."/>
  </xsl:element>
</xsl:template>

<xsl:attribute-set name="fontset">
  <xsl:attribute name="font-size">14pt</xsl:attribute>
</xsl:attribute-set>


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
