<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0"
    xmlns:lxslt="http://xml.apache.org/xslt"
    xmlns:redirect="org.apache.xalan.lib.Redirect"
    extension-element-prefixes="redirect">

  <lxslt:component prefix="redirect" elements="write open close" functions="">
    <lxslt:script lang="javaclass" src="org.apache.xalan.lib.Redirect"/>
  </lxslt:component>  

  <xsl:param name="redirectOutputName" select="'Redirect1330.out'"/>
    
  <xsl:template match="doc">
    <out>
      <xsl:message>Your main output document should have a main-doc-comment and a main-doc-elem</xsl:message>
      <xsl:comment>main-doc-comment</xsl:comment>
        <redirect:write select="$redirectOutputName">
          <out>
            <xsl:message>Your redirected document <xsl:value-of select="$redirectOutputName"/> should have a redirect-doc-comment and a redirect-doc-elem</xsl:message>
            <xsl:comment>redirect-doc-comment</xsl:comment>
            <redirect-doc-elem/>
          </out>
        </redirect:write>
      <main-doc-elem/>
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
