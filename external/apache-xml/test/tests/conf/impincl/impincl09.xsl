<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:include href="q.xsl"/>

  <!-- FileName: impincl09 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.6.1 Stylesheet Inclusion -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: The resource located by the href attribute value is parsed as an 
       XML document, and the children of the xsl:stylesheet element in this document 
       replace the xsl:include element in the including document. (No namespaces 
       should be copied over. -->

<xsl:template match="/">
  <out>
    <xsl:element name="test" use-attribute-sets="set1 set2" namespace="www.lotus.com"/>
  </out>
</xsl:template>

<xsl:attribute-set name="set2">
  <xsl:attribute name="text-decoration">underline</xsl:attribute>
  <xsl:attribute name="test" ></xsl:attribute>
</xsl:attribute-set>

<xsl:attribute-set name="set1">
  <xsl:attribute name="color">black</xsl:attribute>
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
