<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<!-- Filename: MinitestImport.xsl -->
<!-- Author: Paul_Dick@lotus.com -->


<xsl:template match="GrandKids" mode="orginal">
They have <xsl:value-of select="count(*)"/> grandchildren: <xsl:text/>
	<xsl:for-each select="gkid">
		<xsl:value-of select="key('KidInfo',(.))/Name/@First"/>
		<xsl:if test="not(position()=last())">, </xsl:if>
		<xsl:if test="(position()=last()-1)">and </xsl:if>
	</xsl:for-each>
</xsl:template>

<xsl:template match="GrandKids" mode="document">
They should have <xsl:value-of select="count(document('MinitestDocument.xml')/GrandKids/gkid)"/> grandchildren: <xsl:text/>
	<xsl:for-each select="gkid">
		<xsl:value-of select="key('KidInfo',.)/Name/@First"/>
		<xsl:if test="not(position()=last())">, </xsl:if>
		<xsl:if test="(position()=last()-1)">and </xsl:if>
	</xsl:for-each>
</xsl:template>

<xsl:template match="GrandKids" mode="doc">
They got <xsl:value-of select="count(document('MinitestDocument.xml')/GrandKids/gkid)"/> grandchildren: <xsl:text/>
	<xsl:for-each select="document('MinitestDocument.xml')/GrandKids/gkid">
		<xsl:value-of select="key('KidInfo',(.))/Name/@First"/>
		<xsl:if test="not(position()=last())">, </xsl:if>
		<xsl:if test="(position()=last()-1)">and </xsl:if>
	</xsl:for-each>
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
