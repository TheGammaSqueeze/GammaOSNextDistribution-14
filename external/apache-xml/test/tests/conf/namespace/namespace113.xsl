<?xml version="1.0" encoding="ISO-8859-1"?>
 <xsl:stylesheet version="1.0"
      xmlns:ixsl="http://www.w3.org/1999/XSL/TransformAlias"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<!-- FileName: namespace113 -->
<!-- Document: http://www.w3.org/TR/xslt -->
<!-- DocVersion: 19991116 -->
<!-- Section: 7.1.1 Literal Result Elements -->
<!-- Creator: Gary L Peskin, based on test case from Jens Lautenbacher -->
<!-- Purpose: Verify that namespace-alias is honored in included stylesheets. -->

   <xsl:include href="incnamespace113.xsl"/>
   <xsl:namespace-alias stylesheet-prefix="ixsl" result-prefix="xsl"/>

<xsl:template match="/"> 
  <ixsl:stylesheet version="1.0">
    <xsl:apply-templates/>
  </ixsl:stylesheet>
</xsl:template>

<xsl:template match="gen_b">
  <ixsl:template>
    <xsl:attribute name="match"><xsl:value-of select="@name"/></xsl:attribute>
    <ixsl:text>Recognized <xsl:value-of select="@name"/></ixsl:text>
  </ixsl:template>
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
