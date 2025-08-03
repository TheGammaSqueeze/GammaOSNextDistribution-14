<xsl:stylesheet version="1.0"
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	    xmlns:redirect="org.apache.xalan.xslt.extensions.Redirect"
		extension-element-prefixes="redirect">		
<!-- Reproducing Bugzilla 3489 -->
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>
	<xsl:param name="output-dir"/>
	<xsl:template match="*|@*"/>
	<xsl:template match="/workspace">
		<xsl:apply-templates/>
	</xsl:template>
	
	
	<xsl:template match="/workspace/project">
		<xsl:variable name="basedir" select="/workspace/@basedir"/>
		<xsl:variable name="outputdir" select="/workspace/@outputdir"/>
		<xsl:variable name="cvsdir">
		  <xsl:value-of select="concat(/workspace/@cvsdir, '/')"/>
		  <xsl:choose>
		    <xsl:when test="cvs/@module">
		      <xsl:value-of select="cvs/@module"/>
		    </xsl:when>
		    <xsl:otherwise>
		      <xsl:value-of select="@srcdir"/>
		    </xsl:otherwise>
		  </xsl:choose>
		</xsl:variable>
		<xsl:message terminate="no">
		  <xsl:value-of select="concat('Creating Gump buildfile for ', @name)"/>
		</xsl:message>
		
		<redirect:write file="{$outputdir}/{@name}-gumpbuild.xml">
			<project name="{@name} Gump build file" default="gumpify" basedir="{$basedir}/{@srcdir}">
				
				<!-- initialize time stamp and replace it in the html page -->
				<target name="init">
					<tstamp>
						<format property="TIMESTAMP" pattern="HH:mm:ss"/>
					</tstamp>
					<replace file="{$outputdir}/status.xml" token="TAG-{@name}-TIME" value="${{TIMESTAMP}}"/>
					<touch file="{$outputdir}/{@name}.FAILED"/>
					
					<!--style in="{$basedir}/status.xml" 
						out="{$basedir}/{@name}.html"
						destdir="{$basedir}"
						style="{$basedir}/source-index-style.xsl">
						<param name="filename" expression="{@name}.xml"/>
					</style-->
				</target>
				
				<!-- check for all dependencies -->
				<target name="dependency-check">
					<xsl:apply-templates select="depend" mode="dependency-check"/>
				</target>
				
				<!-- generate the dependency failure targets -->
				<xsl:apply-templates select="depend" mode="failed-dependency"/>
				
				<!-- generate the main target that does everything -->
				<target name="gumpify" depends="init,dependency-check" unless="dependency-failure">
					<available file="{$cvsdir}" property="cvsmodule.{@name}.present"/>
					<echo message="In GUMP project: {@name}"/>
					<xsl:if test="cvs">
						<antcall target="cvscheckout"/>
						<!--<antcall target="cvsupdate"/>-->
						<copy todir="{$basedir}/{@srcdir}">
							<fileset dir="{$cvsdir}"/>
						</copy>
					</xsl:if>
					<replace file="{$outputdir}/status.xml" token="TAG-{@name}-CVS-TIME" value="${{TIMESTAMP}}"/>
					<antcall target="build"/>
					<antcall target="status-pages"/>
					<move file="{$outputdir}/{@name}.FAILED" tofile="{$outputdir}/{@name}.SUCCESS"/>
				</target>
				
				<xsl:apply-templates select="cvs">
					<xsl:with-param name="target" select="'cvscheckout'"/>
					<xsl:with-param name="command" select="'-z3 checkout -P'"/>
				</xsl:apply-templates>
				
				<xsl:apply-templates select="cvs">
					<xsl:with-param name="target" select="'cvsupdate'"/>
					<xsl:with-param name="command" select="'-z3 update -P -d -A'"/>
				</xsl:apply-templates>
				
				<!-- build targets -->
				<target name="build" depends="init">
					<xsl:apply-templates select="ant | script"/>
				</target>
				
				<!-- called if the build went fine it sets the status to SUCCESS in the html file -->
				<target name="status-pages">
				<replace file="{$outputdir}/status.xml" token="TAG-{@name}-STATUS" value="SUCCESS"/>				
				  <!-- <style in="" out="{/workspace/@basedir}/{@name}.html" style="" destdir="{/workspace/@basedir}"/> -->
				</target>
			</project>
		</redirect:write>
	</xsl:template>
	
	
	<!-- ===========================================================================================
		Execute a Ant build file/target as specified by the project
		 =========================================================================================== -->
	<xsl:template match="/workspace/project/ant">
		<!-- Ant build file directory -->
		<xsl:variable name="build.dir">
			<xsl:value-of select="concat(/workspace/@basedir, '/', ../@srcdir)"/>
			<xsl:if test="@basedir">
				<xsl:value-of select="concat('/', @basedir)"/>
			</xsl:if>
		</xsl:variable>
		
		<!-- copy project files -->
		<!--copy todir="{/workspace/@basedir}/{../@srcdir}">
			<fileset dir="{/workspace/@viewdir}/{../@srcdir}"/>
		</copy-->		
		
		<!-- execute the target needed to build the project -->
		<java classname="org.apache.tools.ant.Main" fork="yes" failonerror="yes"
			output="{/workspace/@outputdir}/{../@name}-buildresult.txt"
			dir="{$build.dir}">
			
			<!-- transmit the worspace's properties -->
			<xsl:for-each select="/workspace/property">
				<arg value="-D{@name}={@value}"/>
			</xsl:for-each>	
					
			<!-- a buildfile might be specified otherwise Ant will use its default -->
			<xsl:if test="@buildfile">
				<arg line="-buildfile {$build.dir}/{@buildfile}"/>
			</xsl:if>
			<arg line="-listener org.apache.tools.ant.XmlLogger -Dant.home={/workspace/@basedir}/dtools/ant -DXmlLogger.file={/workspace/@outputdir}/{../@name}-buildresult.xml"/>

			<!-- specific target name to perform the build -->
			<xsl:if test="@target">
				<arg value="{@target}"/>
			</xsl:if>
			<xsl:apply-templates select="property"/>
			
			<!-- Do the classpath thing here -->
			<classpath>
				<xsl:for-each select="../depend | ../option">
					<xsl:variable name="name" select="@project"/>
					<xsl:for-each select="/workspace/project[@name=$name]/jar">
						<pathelement location="{../home}/{@name}"/>
					</xsl:for-each>
				</xsl:for-each>
				<pathelement path="${{java.class.path}}"/>
			</classpath>
		</java>
	</xsl:template>
	
	
	<!-- ===========================================================================================
		Execute a script
		 =========================================================================================== -->	
	<xsl:template match="/workspace/project/script">
		<xsl:variable name="script.dir" select="concat(/workspace/@basedir, '/', ../@srcdir)"/>
		<xsl:variable name="script.sh" select="concat($script.dir, '/', ../@name, '.sh')"/>
		<chmod perm="ugo+rx" file="{$script.sh}"/>
		<exec dir="{$script.dir}" executable="{$script.sh}"
			output="{/workspace/@outputdir}/{../@name}-buildresult.txt"/>
	</xsl:template>
	
	
	<!-- ===========================================================================================
		CVS stuff, not sure what it is doing
		 =========================================================================================== -->		
	<xsl:template match="/workspace/project/cvs">
		<xsl:param name="target"/>
		<xsl:param name="command"/>
		<xsl:variable name="repo" select="@repository"/>
		<xsl:variable name="cvsmodule.present" select="concat('cvsmodule.', ../@name, '.present')"/>
		<target name="{$target}">
			<xsl:if test="$target='cvscheckout'">
				<xsl:attribute name="unless">
					<xsl:value-of select="$cvsmodule.present"/>
				</xsl:attribute>
			</xsl:if>
			<xsl:if test="$target='cvsupdate'">
				<xsl:attribute name="if">
					<xsl:value-of select="$cvsmodule.present"/>
				</xsl:attribute>
			</xsl:if>
			<replace file="{/workspace/@outputdir}/status.xml" token="TAG-{../@name}-CVS-TIME" value="${TIMESTAMP}"/>
			<cvs command="{$command}" quiet="true">
				<xsl:attribute name="cvsroot">
					<xsl:value-of select="/workspace/cvs-repository/tree[@name=$repo]/@root"/>
					<xsl:if test="@dir">
						<xsl:value-of select="concat('/', @dir)"/>
					</xsl:if>
				</xsl:attribute>
				<xsl:attribute name="dest">
					<xsl:value-of select="/workspace/@cvsdir"/>
				</xsl:attribute>
				<xsl:attribute name="package">
					<xsl:choose>
						<xsl:when test="@module">
							<xsl:value-of select="@module"/>
						</xsl:when>
						<xsl:otherwise>
							<xsl:value-of select="../@name"/>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:attribute>
				<xsl:if test="@tag">
					<xsl:attribute name="tag">
						<xsl:value-of select="@tag"/>
					</xsl:attribute>
				</xsl:if>
				<xsl:attribute name="output">
					<xsl:value-of select="concat(/workspace/@outputdir, '/', ../@name, '-cvsresult.txt')"/>
				</xsl:attribute>
			</cvs>
			<replace file="{/workspace/@outputdir}/status.xml" token="TAG-{../@name}-CVS-STATUS" value="SUCCESS"/>
		</target>
	</xsl:template>
	
	
	<!-- ===========================================================================================
		Check for a dependency availability and immediately call its
		dependency-check related target.
		 =========================================================================================== -->	
	<xsl:template match="/workspace/project/depend" mode="dependency-check">
		<xsl:variable name="project" select="@project"/>
		<xsl:variable name="dependfilename" select="concat(/workspace/@outputdir, '/', $project, '.SUCCESS')"/>
		<available file="{$dependfilename}" property="dependency.{$project}.present"/>
		<antcall target="{$project}-dependency"/>
	</xsl:template>
	
	
	<!-- ===========================================================================================
		Target called only if the related property is not set (ie the dependency
		is not verified) since it will fail and replace its tag status by a Prereq
		information in the html index file.
		 =========================================================================================== -->	
	<xsl:template match="/workspace/project/depend" mode="failed-dependency">
		<xsl:variable name="failed-project" select="@project"/>
		<target name="{$failed-project}-dependency" unless="dependency.{$failed-project}.present">
			<echo message="PREREQ Failure: Project depends on {$failed-project}"/>
			<available file="{/workspace/@outputdir}/{../@name}.FAILED" property="dependency-failure"/>
			<replace file="{/workspace/@outputdir}/status.xml" token="TAG-{../@name}-STATUS" value="Prereq Failure: {$failed-project}"/>
			<fail message="PREREQ Failure: Dependency on {$failed-project} could not be satisfied."/>
		</target>
	</xsl:template>


	<xsl:template match="/workspace/project/ant/property">
		<arg>
			<xsl:attribute name="value">
				<xsl:text>-D</xsl:text>
				<xsl:value-of select="@name"/>
				<xsl:text>=</xsl:text>
				<xsl:choose>
					<xsl:when test="@value">
						<xsl:value-of select="@value"/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:if test="@reference and @project">
			              <xsl:variable name="projname" select="@project"/>
			              <xsl:variable name="refname" select="@reference"/>
			              <xsl:choose>
			                <xsl:when test="@id">
			                  <xsl:variable name="propid" select="@id"/>
			                  <xsl:value-of select="/workspace/project[@name=$projname]/*[name()=$refname and @id=$propid]"/>
			                </xsl:when>
			                <xsl:otherwise>
			                  <xsl:value-of select="/workspace/project[@name=$name]/*[name()=$refname]"/>
			                </xsl:otherwise>
			              </xsl:choose>
						</xsl:if>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:attribute>
		</arg>
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
