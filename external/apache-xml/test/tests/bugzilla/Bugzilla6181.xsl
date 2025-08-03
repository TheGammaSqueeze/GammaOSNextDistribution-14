<?xml version="1.0"?>
<!--
  - Title:   action.xsl
  - Purpose: An XSL stylesheet for processing an intermediate XML file
  -          and generating the action classes required for our simple UI
  -          for each entity in the file.
  -
  - $Revision$
  - $Date$
  - $Author$
  -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:lxslt="http://xml.apache.org/xslt"
		xmlns:ns="Bugzilla6181"
		extension-element-prefixes="ns"
                version="1.0"
                exclude-result-prefixes="#default">
<xsl:output method="text" indent="no"/>

<xsl:template match="/">
  <xsl:apply-templates select="//component/dependent"/>
</xsl:template>

<xsl:template match="dependent">
	<xsl:variable name="parentName" select="id(associations/parent/@idref)/@name"/>
	<xsl:variable name="parentName2">
      <xsl:value-of select="id(associations/parent/@idref)/@name"/>
    </xsl:variable>

	<xsl:value-of select="$parentName"/>EditForm parentForm = (<xsl:value-of select="$parentName"/>EditForm) session.getAttribute( "<xsl:value-of select='ns:initcap($parentName)'/>EditForm" );

  	    <xsl:value-of select="$parentName2"/>Proxy sessionProxy = new <xsl:value-of select="ns:initcap($parentName2)"/>Proxy();
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
