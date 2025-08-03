<?xml version="1.0"?>

<!-- This is THE Smoke Test -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
				xmlns:dick="http://www.dicks.com"
				xmlns:weston="http://www.weston.com"
				exclude-result-prefixes="dick weston">

<xsl:import href="smokesubdir/famimp.xsl"/>
<xsl:include href="smokesubdir/faminc.xsl"/>

<xsl:output indent="yes"/>

<xsl:strip-space elements="Family_Geneologies Kids Males"/>
<xsl:variable name="root" select="'Families'"/>

<!-- Root xsl:template - start processing here -->
<xsl:key name="KidInfo" match="Kid" use="@ID"/>

<xsl:template match="Family_Geneologies">
  <out>
     <xsl:apply-templates select="Family"/>  
  </out>
</xsl:template>
 

<xsl:template match="Family">
<xsl:text>&#10;</xsl:text>

	<xsl:call-template name="tree">
	   <xsl:with-param name="Surname" select="@Surname"/>
	</xsl:call-template><xsl:text>&#10;</xsl:text>

	<xsl:value-of select="@Surname" />

    <xsl:apply-templates select="Parents">
	   <xsl:with-param name="Surname" select="@Surname"/>
	</xsl:apply-templates>

	<xsl:apply-templates select="Children/Child">
	   <xsl:sort select="Basic_Information/Name/@First"/>
	</xsl:apply-templates>

</xsl:template>

<xsl:template match="Child">
	<xsl:text>&#10;&#13;</xsl:text>
	<xsl:value-of select=".//Name"/>'s phone number is <xsl:value-of select=".//Phone/Home"/><xsl:text>.</xsl:text>
	<xsl:if test='Personal_Information/Sex[.="Male"]' ><xsl:text/>
He is <xsl:value-of select="Personal_Information/Age"/><xsl:text>. </xsl:text>
	</xsl:if>
	<xsl:if test='Personal_Information/Sex[.="Female"]' >
She is <xsl:value-of select="Personal_Information/Age"/><xsl:text>. </xsl:text>
	</xsl:if>
	<xsl:apply-templates select="Personal_Information/Married"/> 
</xsl:template>
					
<xsl:template match ="Personal_Information/Married">
  <xsl:variable name="spouse" select="following-sibling::*/Wife/Name | following-sibling::*/child::Husband/child::Name"/>  
	<xsl:if test='.="Yes"' >
		<xsl:value-of select="ancestor::Child/Basic_Information/Name/@First"/> is married to <xsl:value-of select="$spouse"/>
		<xsl:text>.</xsl:text> 
		<xsl:choose>
			<xsl:when test="./@Kids > 0">
Their children are <xsl:text/>
				<xsl:for-each select="following-sibling::*/child::Kids/child::Kid">
				   <!--xsl:value-of select="."/-->
				   <xsl:choose>
						<xsl:when test="(position()=last())">
							<xsl:value-of select="Name"/>:<xsl:value-of select="Age"/></xsl:when>
						<xsl:otherwise>
				   			<xsl:value-of select="Name"/>:<xsl:value-of select="Age"/><xsl:text>, </xsl:text></xsl:otherwise>
				   </xsl:choose>
				   <xsl:if test="(position()=last()-1)">and </xsl:if>
				</xsl:for-each>
			</xsl:when>
			<xsl:when test="./@Kids = 0">
They have no kids
			</xsl:when>
		</xsl:choose>											
	</xsl:if>
	<xsl:if test='.="No"'>
		<xsl:value-of select="ancestor::Child/child::Basic_Information/child::Name/@First"/> is not married. 
		<!-- The following is another way to print out the same message
		<xsl:value-of select="../..//Basic_Information/Name/@First"/> is not married   -->
	</xsl:if>
</xsl:template>

<xsl:template match ="Parents">
   <xsl:param name="Surname" select="'Default'"/>
The parents are: <xsl:value-of select="Father/@First"/> and <xsl:value-of select="Mother"/>
    <xsl:choose>
       <xsl:when test="contains($Surname,'Dicks')">
          <xsl:apply-templates select="GrandKids" mode="document"/>
	   </xsl:when>
	   <xsl:otherwise>
          <xsl:apply-templates select="GrandKids" mode="orginal"/>
	   </xsl:otherwise>
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
