<?xml version="1.0" ?> 
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<!-- "computational (recursive) template is very slow compared to xalan 1.2.2" c.mallwitz@intershop.de -->
  <xsl:template match="/">
    <foo>
    <xsl:call-template name="func1">
      <xsl:with-param name="list" select="//x" /> 
    </xsl:call-template>
    </foo>
  </xsl:template>

  <xsl:template name="func1">
    <xsl:param name="list" /> 
    <xsl:choose>
      <xsl:when test="$list">
        <xsl:call-template name="func1">
          <xsl:with-param name="list" select="$list[position()!=1]" /> 
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>0</xsl:otherwise> 
    </xsl:choose>
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
