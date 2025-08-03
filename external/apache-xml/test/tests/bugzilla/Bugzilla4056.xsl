<?xml version="1.0" encoding="UTF-8"?> 
<!-- Bugzilla#4056 See also whitespace20.xsl for similar test of xml:space -->
<!-- $RCSfile: Bugzilla4056.xsl,v $ $Revision$ $Date$ -->
<xsl:stylesheet 
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  
 version="1.0"
  xmlns:java="http://xml.apache.org/xslt/java"
  exclude-result-prefixes="java">
  <xsl:strip-space elements="*"/>

  <xsl:template match="NewsML">
	<xsl:apply-templates select="NewsItem"/>
  </xsl:template>

  <xsl:template match="NewsItem">
    <EVENT xml:space="preserve">
	  <xsl:attribute name="event-id"><xsl:value-of select="Identification/NewsIdentifier/PublicIdentifier"/>:<xsl:value-of select="Identification/NewsIdentifier/RevisionId"/></xsl:attribute>
	  <DIR name="content">
		<STRING name="headline">
		  <xsl:choose>
			<xsl:when test="string-length( NewsComponent/NewsLines/HeadLine )>0">
			  <xsl:apply-templates select="NewsComponent/NewsLines/HeadLine/descendant-or-self::text()"/>
		  </xsl:when>
		  <xsl:otherwise>
			<xsl:apply-templates select="NewsComponent/NewsLines/NewsLine[NewsLineType/@FormalName='alert'][1]/NewsLineText/descendant-or-self::text()"/>
		  </xsl:otherwise>
		</xsl:choose>
		</STRING>
		<STRING name="story">
		  <xsl:apply-templates select="NewsComponent/ContentItem/DataContent/descendant-or-self::text()"/>
		</STRING>
	  </DIR>
	  <DIR name="meta">
		<xsl:apply-templates select="NewsComponent/TopicSet[@FormalName='ReutersMetaData']/Topic/Property[@FormalName='Code']" mode="meta"/>
		
		<STRING name="provider"><xsl:value-of select="Identification/NewsIdentifier/ProviderId"/></STRING>
		<STRING name="any"><xsl:value-of select="Identification/NewsIdentifier/ProviderId"/></STRING>
		
		<xsl:variable name="attribution" select="NewsComponent/AdministrativeMetadata/Source/Party/@FormalName"/>
		<xsl:if test="$attribution">
		  <STRING name="attribution"><xsl:value-of select="$attribution"/></STRING>
		  <STRING name="any"><xsl:value-of select="$attribution"/></STRING>
		</xsl:if>

		<xsl:variable name="language" select="NewsComponent/DescriptiveMetadata/Language[1]/@FormalName"/>
		<xsl:if test="$language">
		  <STRING name="language"><xsl:value-of select="$language"/></STRING>
		  <STRING name="any"><xsl:value-of select="$language"/></STRING>
		</xsl:if>
	  </DIR>
	  <DIR name="teasers">
		<STRING name="teaser0"/>
		<STRING name="teaser1"/>
		<STRING name="teaser2"/>
		<STRING name="teaser3"/>
		<STRING name="teaser4"/>
	  </DIR>
	</EVENT>
  </xsl:template>

  <xsl:template match="Property" mode="meta">
	<STRING name="{../TopicType/@FormalName}"><xsl:value-of select="@Value"/></STRING>
	<STRING name="any"><xsl:value-of select="@Value"/></STRING>
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
