<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:ped="http://tester.com">

<xsl:import href="implre05.xsl"/>
<xsl:include href="inclre05.xsl"/>

  <!-- FileName: lre05 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.1.1 Literal Result Elements-->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Show that exclude-result-prefixes is scoped to just it's LRE. -->

  <!-- The designation of a namespace as an excluded namespace is 
       effective within the subtree of the stylesheet rooted at the element 
       bearing the exclude-result-prefixes or xsl:exclude-result-prefixes attribute.
       A subtree rooted at an xsl:stylesheet element does not include any 
       stylesheets imported or included by children of that xsl:stylesheet element. -->

  <!-- The ped namespace is excluded from main and foo, but not from the sub-elements that
    got placed in main by other templates. In fact, it has to be re-declared for each of
    those sub-elements. -->

<xsl:template match="docs">
  <main xsl:exclude-result-prefixes="ped">
     <foo/>
     <xsl:apply-templates/>
  </main>
</xsl:template>

<xsl:template match="doc1">
  <sub-element-in-main />
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
