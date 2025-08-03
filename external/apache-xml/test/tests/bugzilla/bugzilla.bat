@echo off
if "%1" == "" goto usage
@echo Simple wrapper to execute a single Bugzilla test #%1

@rem Always attempt Java first, since it might do additional validation
if exist Bugzilla%1.java goto dojava
if exist Bugzilla%1.xsl goto doxsl
goto error

:dojava
@echo javac -classpath ..\..\..\java\build\xalan.jar;..\..\..\java\bin\xml-apis.jar;..\..\..\java\bin\xercesImpl.jar;..\..\java\build\testxsl.jar;%CLASSPATH% -d build Bugzilla%1.java
javac -classpath ..\..\..\java\build\xalan.jar;..\..\..\java\bin\xml-apis.jar;..\..\..\java\bin\xercesImpl.jar;..\..\java\build\testxsl.jar;%CLASSPATH% -d build Bugzilla%1.java

@echo java -classpath build;..\..\..\java\build\xalan.jar;..\..\..\java\bin\xml-apis.jar;..\..\..\java\bin\xercesImpl.jar;..\..\java\build\testxsl.jar;%CLASSPATH% Bugzilla%1
java -classpath build;..\..\..\java\build\xalan.jar;..\..\..\java\bin\xml-apis.jar;..\..\..\java\bin\xercesImpl.jar;..\..\java\build\testxsl.jar;%CLASSPATH% Bugzilla%1
goto end

:doxsl
@echo java -classpath ..\..\..\java\build\xalan.jar;..\..\..\java\bin\xml-apis.jar;..\..\..\java\bin\xercesImpl.jar;%CLASSPATH% org.apache.xalan.xslt.Process -in Bugzilla%1.xml -xsl Bugzilla%1.xsl -out Bugzilla%1.output -v -edump
java -classpath ..\..\..\java\build\xalan.jar;..\..\..\java\bin\xml-apis.jar;..\..\..\java\bin\xercesImpl.jar;%CLASSPATH% org.apache.xalan.xslt.Process -in Bugzilla%1.xml -xsl Bugzilla%1.xsl -out Bugzilla%1.output -v -edump
@echo Output is in Bugzilla%1.output
dir Bugzilla%1.output
goto end

:error
@echo ERROR! Could not find file Bugzilla%1.java/xsl
:usage
@echo %0: compile and run a single BugzillaNNNN testlet/stylesheet
@echo Usage: bugzilla NNNN    (NNNN is the number of the bug)

:end