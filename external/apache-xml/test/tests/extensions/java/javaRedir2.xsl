<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0"
    xmlns:lxslt="http://xml.apache.org/xslt"
    xmlns:redirect="org.apache.xalan.lib.Redirect"
    extension-element-prefixes="redirect"
    exclude-result-prefixes="lxslt">


<!-- Testing redirect:write append="" attribute -->

  <lxslt:component prefix="redirect" elements="write open close" functions="">
    <lxslt:script lang="javaclass" src="org.apache.xalan.lib.Redirect"/>
  </lxslt:component>  
    
  <xsl:template match="/doc">
    <standard-out>
      <p>Standard output:</p>
      <xsl:apply-templates select="list"/>
    </standard-out>
  </xsl:template>

  <xsl:template match="list">
      <xsl:apply-templates select="item"/>
  </xsl:template>

  <!-- redirected, using append -->
  <xsl:template match="item">
    <!-- Note append is treated as avt -->
    <redirect:write select="@file" append="{@append}">
      <item-out>
        <append><xsl:value-of select="@append"/></append>
        <data><xsl:value-of select="."/></data>
      </item-out>
    </redirect:write>
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
