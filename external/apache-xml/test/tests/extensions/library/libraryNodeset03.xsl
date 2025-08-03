<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:cextend="http://xml.apache.org/xalan"
				xmlns:default="http://www.hello.com"
				xmlns:test="http://www.extension03.test"
				xmlns:BTM="www.btm.com"
                exclude-result-prefixes="cextend default test BTM">

  <!-- FileName: libraryNodeset03 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 14 Extensions -->
  <!-- Purpose: Testing Lotus-specific extension "Nodeset". More extensive RTF testing -->
 
<xsl:strip-space elements="*"/>
<xsl:output indent="yes"/>
<xsl:variable name="rtf">
	<docelem>
		<elem1>
			<elem2>
				<elem3 attr1="A" attr2="B" attr3="C">Elem3.1</elem3>
				<test:elem3 attr1="Z" attr2="Y" attr3="X">NS-Elem3.2</test:elem3>
				<elem3 attr1="D" attr2="E" attr3="F">Elem3.3</elem3>
				<test:elem3 attr1="W" attr2="V" attr3="U">NS-Elem3.4</test:elem3>
			</elem2>
		</elem1>
		<elem1>
			<elem2>1</elem2>
			<elem2>2</elem2>
			<elem2>3</elem2>
			<elem2>4</elem2>
			<BTM:BreakingTheMold/>
		</elem1>
		<elem1>
			<elem2>
				<elem4 attr1="G" attr2="H" attr3="I">Elem4.1</elem4>
				<test:elem4 attr1="T" attr2="S" attr3="R">NS-Elem4.2</test:elem4>
				<elem4 attr1="J" attr2="K" attr3="L">Elem4.3</elem4>
				<test:elem4 attr1="Q" attr2="P" attr3="O">NS-Elem4.4</test:elem4>
			</elem2>
		</elem1>
	</docelem>
	<docelem/>
	<docelem xmlns="http://www.hello.com">
		<elem3>1</elem3>
		<elem3>2</elem3>
		<test:elem3><elem3a/></test:elem3>
		<elem3>4</elem3>
	</docelem>
 </xsl:variable>

<xsl:template match="/">
   <out>

	  <xsl:element name="CountDOCELEM">
		<xsl:value-of select="count(cextend:nodeset($rtf)/docelem)"/>
	  </xsl:element>

	  <xsl:element name="CountELEM2andELEM3">
		<xsl:value-of select="count(cextend:nodeset($rtf)/docelem//elem2 |
									cextend:nodeset($rtf)/docelem//elem3 |
									cextend:nodeset($rtf)/docelem//test:elem3)"/>
	  </xsl:element>
	  <xsl:element name="SumELEM2">
		<xsl:value-of select="sum(cextend:nodeset($rtf)/docelem/elem1[2]/elem2)"/>
	  </xsl:element>

	  <xsl:element name="NumberELEM2">
		<xsl:value-of select="number(cextend:nodeset($rtf)/docelem/elem1[2])"/>
	  </xsl:element>

	  <xsl:element name="NameBTM">
		<xsl:value-of select="name(cextend:nodeset($rtf)/docelem/elem1[2]/*[5])"/>
	  </xsl:element>

	  <xsl:element name="LocalNameBTM">
		<xsl:value-of select="local-name(cextend:nodeset($rtf)/docelem/elem1[2]/*[5])"/>
	  </xsl:element>

	  <xsl:element name="Namespace-URIs">
	  	<xsl:attribute name="uri1">
	  		<xsl:value-of select="namespace-uri(cextend:nodeset($rtf)/docelem/elem1/elem2/test:elem3)"/>
	  	</xsl:attribute>
	  	<xsl:attribute name="uri2">
	  		<xsl:value-of select="namespace-uri(cextend:nodeset($rtf)/docelem/elem1[2]/*[5])"/>
	  	</xsl:attribute>
	  </xsl:element>

	  <xsl:element name="ValueDOCELEM-STAR">
		<xsl:value-of select="cextend:nodeset($rtf)/docelem/*"/>
	  </xsl:element>

	  <xsl:element name="ValueELEM4">
		<xsl:value-of select="cextend:nodeset($rtf)/docelem/elem1/elem2/test:elem4[@attr3='O']"/>
	  </xsl:element>

	  <xsl:element name="ValueTESTELEM4-1">
		<xsl:value-of select="cextend:nodeset($rtf)/docelem/elem1/elem2/test:elem4[1]"/>
	  </xsl:element>

	  <xsl:element name="SlashSlashELEM4">
		<xsl:value-of select="cextend:nodeset($rtf)//elem4"/>
	  </xsl:element>

	  <xsl:element name="SlashSlashELEM4-2Attrs-2">
		<xsl:value-of select="cextend:nodeset($rtf)//test:elem4[2]/@*[2]"/>
	  </xsl:element>

	  <Axis_Tests>
	    <xsl:element name="Ancestor">
		 <xsl:for-each select="cextend:nodeset($rtf)/docelem/elem1[2]/*[5]/ancestor::*">
		  <xsl:copy/>
		 </xsl:for-each>
	    </xsl:element>

	    <xsl:element name="Ancestor-or-Self">
		 <xsl:for-each select="cextend:nodeset($rtf)//BTM:BreakingTheMold/ancestor-or-self::*">
		   <xsl:copy/>
		 </xsl:for-each>	  
	    </xsl:element>

	    <xsl:element name="Attribute">
		  <xsl:for-each select="cextend:nodeset($rtf)//test:elem4/attribute::*">
			<xsl:copy/>
		  </xsl:for-each>
	    </xsl:element>

	    <xsl:element name="Child">
		  <xsl:for-each select="cextend:nodeset($rtf)/docelem/*">
		  	<xsl:copy/>
		  </xsl:for-each>
	    </xsl:element>

	    <xsl:element name="Descendant">
		  <xsl:for-each select="cextend:nodeset($rtf)/docelem/elem1[2]/descendant::*">
		  	<xsl:copy/>
		  </xsl:for-each>
	    </xsl:element>

	    <xsl:element name="Descendant-or-Self">
		  <xsl:for-each select="cextend:nodeset($rtf)/docelem/elem1[2]/descendant-or-self::*">
		  	<xsl:copy/>
		  </xsl:for-each>
	    </xsl:element>

	    <xsl:element name="Following">
		  <xsl:for-each select="cextend:nodeset($rtf)/docelem[2]/elem1[2]/following::*">
		  	<xsl:copy/>
		  </xsl:for-each>
	    </xsl:element>

	    <xsl:element name="Following-Sibling">
		  <xsl:for-each select="cextend:nodeset($rtf)/docelem[2]/elem1[1]/following-sibling::*">
		  	<xsl:copy/>
		  </xsl:for-each>
	    </xsl:element>

	    <xsl:element name="Namespace">
		  <xsl:for-each select="cextend:nodeset($rtf)/docelem/elem1/elem2/*/namespace::* |
		                        cextend:nodeset($rtf)/docelem/elem1/*/namespace::*">
			<xsl:copy/>
		  </xsl:for-each>
	    </xsl:element>

	    <xsl:element name="Parent0">
		  <xsl:value-of select="name(cextend:nodeset($rtf)/docelem[2]/parent::*)"/>
	    </xsl:element>

	    <xsl:element name="Parent1">
		  <xsl:value-of select="name(cextend:nodeset($rtf)/docelem/elem1[3]/elem2/test:elem4[2]/parent::*)"/>
	    </xsl:element>

	    <xsl:element name="Preceding">
		  <xsl:value-of select="name(cextend:nodeset($rtf)//test:elem4[2]/preceding::elem1[2]/*/test:elem3[2])"/>
	    </xsl:element>

	    <xsl:element name="Preceding-Sibling">
		  <xsl:value-of select="cextend:nodeset($rtf)//BTM:BreakingTheMold/preceding-sibling::*[4]"/>
	    </xsl:element>

	    <xsl:element name="Self">
		  <xsl:value-of select="name(cextend:nodeset($rtf)//BTM:BreakingTheMold/self::*)"/>
	    </xsl:element>

	  </Axis_Tests>

	  <xsl:element name="AT-Elem3-Elem4">
	 	<xsl:apply-templates select="cextend:nodeset($rtf)/docelem/elem1/elem2/Elem4 |
	 								 cextend:nodeset($rtf)/docelem/elem1/elem2/Elem3"/>
	  </xsl:element>

	  <xsl:element name="AT-NSElem3-NSElem4">
	 	<xsl:apply-templates select="cextend:nodeset($rtf)/docelem/elem1/elem2/test:Elem4 |
	 								 cextend:nodeset($rtf)/docelem/elem1/elem2/test:Elem3"/>
	  </xsl:element> 
	  
	  <xsl:element name="AT-Elem3-NSElem4">
	 	<xsl:apply-templates select="cextend:nodeset($rtf)/docelem/elem1/elem2/Elem3 |
	 								 cextend:nodeset($rtf)/docelem/elem1/elem2/test:Elem4"/>
	  </xsl:element>	    
	  
	  <xsl:element name="FE-FE-AT-Mode">
		<xsl:for-each select="cextend:nodeset($rtf)/docelem[2]/elem1">
			<xsl:for-each select="elem2/*">
				<xsl:apply-templates select="current()" mode="fe"/><xsl:text> </xsl:text>
			</xsl:for-each>
		</xsl:for-each>
	  </xsl:element>

	  <xsl:element name="CopyElem1-1">
		<xsl:copy-of select="cextend:nodeset($rtf)/docelem/elem1[elem2[Elem3[@attr3='C']]]"/>
	  </xsl:element>

	  <xsl:element name="CopyElem3-2">
		<xsl:copy-of select="cextend:nodeset($rtf)/docelem/elem1/elem2/Elem3[2]"/>
	  </xsl:element>

	  <xsl:element name="Copy-of-RTF">
	  	<xsl:copy-of select="cextend:nodeset($rtf)/default:docelem"/>
	  </xsl:element>

	  <xsl:element name="Copy-of-TEST-ELEM3">
	  	<xsl:copy-of select="cextend:nodeset($rtf)/default:docelem/test:elem3"/>
	  </xsl:element>

	</out>
</xsl:template>

<xsl:template match="test:Elem3 | test:Elem4">
	  <xsl:value-of select="."/><xsl:text> modeless </xsl:text>
</xsl:template>
 
<xsl:template match="Elem3 | Elem4">
	  <xsl:value-of select="."/><xsl:text> modeless </xsl:text>
</xsl:template>
 
<xsl:template match="test:Elem3 | test:Elem4" mode="fe">
	  <xsl:value-of select="."/><xsl:text> fe </xsl:text>
</xsl:template>
 
<xsl:template match="Elem3 | Elem4" mode="fe">
	  <xsl:value-of select="."/><xsl:text> fe </xsl:text>
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
