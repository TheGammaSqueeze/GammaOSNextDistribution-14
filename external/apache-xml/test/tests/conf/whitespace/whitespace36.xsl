<?xml version="1.0" encoding="UTF-8"?>                                                                                   
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  version="1.0">

  <!-- FileName: whitespace36 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.4 -->
  <!-- Creator: Dmitry Hayes (dmitryh@ca.ibm.com) and David Bertoni (dbertoni@apache.org) -->
  <!-- Purpose: Test for whitespace stripping from source documents when converting
                a node set to a string. -->
  <!-- Elaboration: Xalan-C neglected to strip whitespace text nodes that were
       descendants of a document or element node when calculating the string value. -->

<xsl:strip-space elements="*" />                                                                                

<xsl:template match="/">
  <out>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="*"> 
  <xsl:if test="string-length(.) != 0 and . != '' and string(.) != ''"> 
    <xsl:text>Whitespace text nodes for for the element node "</xsl:text>
    <xsl:value-of select="name(.)"/>
    <xsl:text>" were not stripped!"</xsl:text> 
  </xsl:if>
  <xsl:apply-templates/>
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
