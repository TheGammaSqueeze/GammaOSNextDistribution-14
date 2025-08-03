<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: AttribSet37 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.1.4 -->
  <!-- Purpose: Set some attributes from an imported definition. -->
  <!-- Creator: David Marston -->

<xsl:import href="impattset37a.xsl"/><!-- defines colorset -->
<xsl:import href="impattset37t.xsl"/>

<xsl:template match="/">
  <out>
    <xsl:element name="test" use-attribute-sets="colorset decoset"/>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:attribute-set name="decoset">
  <xsl:attribute name="text-decoration">underline</xsl:attribute>
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
