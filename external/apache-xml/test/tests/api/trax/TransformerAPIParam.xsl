<?xml version="1.0" encoding="UTF-8"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<!-- params with select=value -->
  <xsl:param name="param1s" select="'default1s'"/>
  <xsl:param name="param2s" select="'default2s'"/>
  <xsl:param name="param3s" select="default3s"/>
<!-- params with node values -->
  <xsl:param name="param1n">'default1n'</xsl:param>
  <xsl:param name="param2n">'default2n'</xsl:param>
  <xsl:param name="param3n">default3n</xsl:param>
  <xsl:template match="doc">
    <out><xsl:text>&#10;</xsl:text>
      <xsl:text> :param1s:</xsl:text><xsl:value-of select="$param1s"/>
      <xsl:text> :param2s:</xsl:text><xsl:value-of select="$param2s"/>
      <xsl:text> :param3s:</xsl:text><xsl:value-of select="$param3s"/>
      <xsl:text>&#10;</xsl:text>
      <xsl:text> :param1n:</xsl:text><xsl:value-of select="$param1n"/>
      <xsl:text> :param2n:</xsl:text><xsl:value-of select="$param2n"/>
      <xsl:text> :param3n:</xsl:text><xsl:value-of select="$param3n"/>
      <xsl:text>&#10;</xsl:text>
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
