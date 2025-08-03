<?xml version="1.0" encoding='iso-8859-1'?>

<!-- this stylesheet looks the first a element (html anchor) whose
     name attribute starts with the string 'prefix_', then copies this
     anchor to the beginning of the document (right after the body
     start tag) and removes the anchor element from the original
     place. the rest of the input is copied identically. -->

<xsl:stylesheet version="1.0" 
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 xmlns:html="http://www.w3.org/TR/REC-html40" >

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template name="beginning-of-body" >
    <xsl:variable name="first-anchor" select="//html:a[starts-with
(@name, 'prefix_')][1]/@name" />
    <xsl:if test="$first-anchor" >
      <a name="{$first-anchor}" />
    </xsl:if>
  </xsl:template>

  <xsl:template match="//html:a[starts-with(@name, 'prefix_')][not
(preceding::html:a[starts-with(@name, 'prefix_')])]" > 
    <xsl:apply-templates select="node()" />
  </xsl:template>

  <xsl:template match="html:body" >
    <xsl:copy>
      <xsl:call-template name="beginning-of-body" />
      <xsl:apply-templates select="@*|node()" />
    </xsl:copy>
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
