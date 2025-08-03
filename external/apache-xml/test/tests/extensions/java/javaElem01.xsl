<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0"
    xmlns:lxslt="http://xml.apache.org/xslt"
    xmlns:javaElem="javaElem01"
    extension-element-prefixes="javaElem"
    exclude-result-prefixes="lxslt">

  <lxslt:component prefix="javaElem" 
        elements="putString putBoolean putDouble putNode" functions="getCounter">
    <lxslt:script lang="javaclass" src="javaElem01"/>
  </lxslt:component>  

<xsl:output method="xml" indent="yes"/>
                
  <xsl:template match="doc">
    <out>
      <extension-string>
        <javaElem:putString attr="attr-String"/>
      </extension-string> 
      <extension-ctr><xsl:value-of select="javaElem:getCounter()"/></extension-ctr> 
      <extension-boolean>
        <javaElem:putBoolean attr="attr-Boolean"/>
      </extension-boolean> 
      <extension-ctr><xsl:value-of select="javaElem:getCounter()"/></extension-ctr> 
      <extension-double>
        <javaElem:putDouble attr="attr-Double"/>
      </extension-double> 
      <extension-ctr><xsl:value-of select="javaElem:getCounter()"/></extension-ctr> 
      <extension-node>
        <javaElem:putNode attr="attr-Node"/>
      </extension-node> 
      <extension-ctr><xsl:value-of select="javaElem:getCounter()"/></extension-ctr> 
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
