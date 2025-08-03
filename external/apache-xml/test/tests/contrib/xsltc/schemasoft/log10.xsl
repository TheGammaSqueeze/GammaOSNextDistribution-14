<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="text" />



  <!-- FileName: log10.xsl -->

  <!-- Author: Darryl Fuller, SchemaSoft -->

  <!-- Purpose: Torture test, compute log10 recursivly for the a bunch of numbers -->





	<xsl:template match="/">

		<xsl:for-each select="./*/*/*/*">

			<xsl:variable name="val" select="position()"/>

			<xsl:variable name="logval">

				<xsl:call-template name="log10">

					<xsl:with-param name="x" select="$val"/>

				</xsl:call-template>

			</xsl:variable>

Value: <xsl:value-of select="$val"/> Log10: <xsl:value-of select="$logval"/>

		</xsl:for-each>

	</xsl:template>



	<xsl:template name="ln" >

		<xsl:param name="x"/>

		<xsl:variable name="e">2.71828182845904</xsl:variable>

		<xsl:choose>

			<xsl:when test="$x = 0">0</xsl:when>

			<!-- technically, a negative number should be NaN, but we will

		     instead pretend we're just scaling a negative number

		     with the ln function -->

			<xsl:when test="$x &lt; 0">

				<xsl:variable name="scaled_answer">

					<xsl:call-template name="ln">

						<xsl:with-param name="x" select="$x * -1"/>

					</xsl:call-template>

				</xsl:variable>

				<xsl:value-of select="$scaled_answer * -1"/>

			</xsl:when>

			<!-- A table of some common values -->

			<xsl:when test="$x = 10">2.3025850929940</xsl:when>

			<xsl:when test="$x = 20">2.9957322735539</xsl:when>

			<xsl:when test="$x = 30">3.4011973816621</xsl:when>

			<xsl:when test="$x = 40">3.6888794541139</xsl:when>

			<xsl:when test="$x = 50">3.9120230054281</xsl:when>

			<xsl:when test="$x = 60">4.0943445622221</xsl:when>

			<xsl:when test="$x = 70">4.2484952420493</xsl:when>

			<xsl:when test="$x = 80">4.3820266346738</xsl:when>

			<xsl:when test="$x = 90">4.4998096703302</xsl:when>

			<xsl:when test="$x = 100">4.6051701859881</xsl:when>

			<xsl:when test="$x = 200">5.2983173665480</xsl:when>

			<xsl:when test="$x = 300">5.7037824746562</xsl:when>

			<xsl:when test="$x = 400">5.9914645471079</xsl:when>

			<xsl:when test="$x = 500">6.2146080984222</xsl:when>

			<xsl:when test="$x = 600">6.3969296552161</xsl:when>

			<xsl:when test="$x = 700">6.5510803350434</xsl:when>

			<xsl:when test="$x = 800">6.6846117276679</xsl:when>

			<xsl:when test="$x = 900">6.8023947633243</xsl:when>

			<xsl:when test="$x = 1000">6.90775527898213</xsl:when>

			<!-- scale the answer -->

			<xsl:when test="$x &gt; 20">

				<xsl:variable name="scaled_answer">

					<xsl:call-template name="ln">

						<xsl:with-param name="x" select="$x div $e"/>

					</xsl:call-template>

				</xsl:variable>

				<xsl:value-of select="$scaled_answer + 1"/>

			</xsl:when>

			<!-- scale the answer -->

			<xsl:when test="$x &lt; 0.005">

				<xsl:variable name="scaled_answer">

					<xsl:call-template name="ln">

						<xsl:with-param name="x" select="$x * $e"/>

					</xsl:call-template>

				</xsl:variable>

				<xsl:value-of select="$scaled_answer - 1"/>

			</xsl:when>

			<!-- The straight goods -->

			<xsl:otherwise>

				<xsl:variable name="z">

					<xsl:call-template name="z_value">

						<xsl:with-param name="x" select="$x"/>

					</xsl:call-template>

				</xsl:variable>

				<xsl:variable name="interim_answer">

					<xsl:call-template name="ln_recurse">

						<xsl:with-param name="z" select="$z"/>

						<xsl:with-param name="current" select="0"/>

						<xsl:with-param name="term" select="1"/>

					</xsl:call-template>

				</xsl:variable>

				<xsl:value-of select="$interim_answer * 2"/>

			</xsl:otherwise>

		</xsl:choose>

	</xsl:template>

	<xsl:template name="z_value">

		<xsl:param name="x"/>

		<xsl:value-of select="($x - 1) div ($x + 1)"/>

	</xsl:template>

	<xsl:template name="ln_recurse">

		<xsl:param name="z"/>

		<xsl:param name="current"/>

		<xsl:param name="term"/>

		<xsl:variable name="term_value">

			<xsl:call-template name="ln_term">

				<xsl:with-param name="z" select="$z"/>

				<xsl:with-param name="n" select="$term"/>

			</xsl:call-template>

		</xsl:variable>

		<xsl:variable name="val" select="$current + $term_value"/>

		<xsl:choose>

			<xsl:when test="$val = $current">

				<xsl:value-of select="$current"/>

			</xsl:when>

			<!-- Limiting the number of terms we calculate to is a trade

		     off of accuracy v.s. speed.  I'm currently sacrificing

		     accuracy for a bit o' speed to make this less painfully

		     slow -->

			<xsl:when test="$term &gt; 100">

				<xsl:value-of select="$current"/>

			</xsl:when>

			<xsl:otherwise>

				<xsl:call-template name="ln_recurse">

					<xsl:with-param name="z" select="$z"/>

					<xsl:with-param name="current" select="$val"/>

					<xsl:with-param name="term" select="$term + 2"/>

				</xsl:call-template>

			</xsl:otherwise>

		</xsl:choose>

	</xsl:template>

	<xsl:template name="ln_term">

		<xsl:param name="z"/>

		<xsl:param name="n"/>

		<xsl:variable name="numerator">

			<xsl:call-template name="pow_function">

				<xsl:with-param name="number" select="$z"/>

				<xsl:with-param name="power" select="$n"/>

			</xsl:call-template>

		</xsl:variable>

		<xsl:value-of select="$numerator div $n"/>

	</xsl:template>

	<xsl:template name="pow_function" ><!-- Power function.  

	Calculates number ^ power where power is an 

	integer. -->

		<xsl:param name="number"/>

		<xsl:param name="power"/>



		<xsl:variable name="int_power" select="round( $power )"/>

		<xsl:variable name="rest">

			<xsl:choose>

				<xsl:when test="$int_power &gt; 0">

					<xsl:call-template name="pow_function">

						<xsl:with-param name="number" select="$number"/>

						<xsl:with-param name="power" select="$int_power - 1"/>

					</xsl:call-template>

				</xsl:when>

				<xsl:when test="$int_power &lt; 0">

					<xsl:call-template name="pow_function">

						<xsl:with-param name="number" select="$number"/>

						<xsl:with-param name="power" select="$int_power + 1"/>

					</xsl:call-template>

				</xsl:when>

				<xsl:otherwise>1</xsl:otherwise>

			</xsl:choose>

		</xsl:variable>

		<xsl:variable name="result">

			<xsl:choose>

				<xsl:when test="$int_power &gt; 0">

					<xsl:value-of select="$rest * $number"/>

				</xsl:when>

				<xsl:when test="$int_power &lt; 0">

					<xsl:value-of select="$rest div $number"/>

				</xsl:when>

				<xsl:otherwise>

				1

			</xsl:otherwise>

			</xsl:choose>

		</xsl:variable>

		<xsl:value-of select="$result"/>

	</xsl:template>

	<xsl:template name="log10"><!-- Log (base 10). -->

		<xsl:param name="x"/>

			<xsl:variable name="numerator">

				<xsl:call-template name="ln">

					<xsl:with-param name="x" select="$x"/>

				</xsl:call-template>

			</xsl:variable>

			<!-- ln(10) -->

			<xsl:variable name="denominator" select="2.302585092994045684"/>

			<xsl:value-of select="$numerator div $denominator"/>

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
