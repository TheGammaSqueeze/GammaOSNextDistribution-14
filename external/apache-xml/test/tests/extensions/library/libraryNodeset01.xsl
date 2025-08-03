<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:cextend="http://xml.apache.org/xalan"
		        xmlns:test="http://www.cnn.com"
        		xmlns:default="http://www.hello.com"
                exclude-result-prefixes="test default cextend">

  <!-- FileName: libraryNodeset01 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 14 Extensions -->
  <!-- Purpose: Testing Lotus-specific extension "Nodeset". -->
 
<xsl:strip-space elements="*"/>
<xsl:output indent="yes"/>
             
<xsl:template match="/">
   <out>
	  <xsl:variable name="rtf">
		<docelem xmlns="http://www.hello.com" xmlns:test="http://www.cnn.com">
			<elem1>
				<elem1a>ELEMENT1A</elem1a>
				<elem1b>,ELEMENT1B</elem1b>
			</elem1>
			<elem2>
				<elem2a>ELEMENT2A</elem2a>
				<elem2b/>
			</elem2>
			<elem3>1</elem3>
			<elem3>2</elem3>
			<test:elem3/>
			<elem3>4</elem3>
			<elem3>5</elem3>
			<elem4>Yahoo</elem4>
		</docelem>
		<docelem>
			<elem1>
				<elem2>
					<elem3 attr1="A" attr2="B" attr3="C">Whooa</elem3>
					<elem3 attr1="Z" attr2="Y" attr3="X">Aoohw</elem3>
				</elem2>
			</elem1>
		</docelem>
	  </xsl:variable>

	  <xsl:element name="Count"> 	  
	  	<xsl:value-of select="count(cextend:nodeset($rtf)/default:docelem/default:elem3)"/>
	  </xsl:element>

	  <xsl:element name="Sum"> 	  
	  	<xsl:value-of select="sum(cextend:nodeset($rtf)/default:docelem/default:elem3)"/>
	  </xsl:element>

	  <xsl:element name="Number"> 	  
	  	<xsl:value-of select="number(cextend:nodeset($rtf)/default:docelem/default:elem3[2])"/>
	  </xsl:element>

	  <xsl:element name="Name">  
	  	<xsl:value-of select="name(cextend:nodeset($rtf)/*)"/>
	  </xsl:element>

	  <xsl:element name="Local-name">
	  	<xsl:value-of select="local-name(cextend:nodeset($rtf)/*)"/>
	  </xsl:element>

	  <xsl:element name="Namespace-URIs">
	  	<xsl:attribute name="uri1">
	  		<xsl:value-of select="namespace-uri(cextend:nodeset($rtf)/default:docelem)"/>
	  	</xsl:attribute>
	  	<xsl:attribute name="uri2">
	  		<xsl:value-of select="namespace-uri(cextend:nodeset($rtf)/default:docelem/default:elem1)"/>
	  	</xsl:attribute>
	  	<xsl:attribute name="uri3">
	  		<xsl:value-of select="namespace-uri(cextend:nodeset($rtf)/default:docelem/test:elem3)"/>
	  	</xsl:attribute>
	  </xsl:element>

	  <xsl:element name="Value-DOCELEM-Elem1">
	  	<xsl:value-of select="cextend:nodeset($rtf)/default:docelem/default:elem1"/>
	  </xsl:element>

	  <xsl:element name="FE-DOCELEM-STAR">
	  	<xsl:for-each select="cextend:nodeset($rtf)/default:docelem/*">
		  <xsl:value-of select="name(.)"/><xsl:text> </xsl:text>
	  	</xsl:for-each>
	  </xsl:element>
	   
	  <xsl:element name="FE-DOCELEM-ELEM2-STAR">
	  	<xsl:for-each select="cextend:nodeset($rtf)/default:docelem/default:elem2/*">
		  <xsl:value-of select="name(.)"/><xsl:text> </xsl:text>
	  	</xsl:for-each>
	  </xsl:element>

	  <xsl:element name="AT-DOCELEM-ELEM4">
	  	<xsl:apply-templates select="cextend:nodeset($rtf)/default:docelem/default:elem4"/>
	  </xsl:element>

	  <xsl:element name="Copy-of-ELEM1B">
	  	<xsl:copy-of select="cextend:nodeset($rtf)/default:docelem/default:elem1/default:elem1b"/>
	  </xsl:element>

   </out>
</xsl:template>

<xsl:template match="default:elem4">
	  <xsl:value-of select="."/>
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
