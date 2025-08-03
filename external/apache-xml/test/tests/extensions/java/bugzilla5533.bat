@echo Simpistic reproduction of Bugzilla#5533
@echo See http://nagoya.apache.org/bugzilla/show_bug.cgi?id=5533 for description
mkdir output
java -classpath %classpath%;..\..\..\..\java\bin\xml-apis.jar;..\..\..\..\java\bin\xerces.jar;..\..\..\..\java\build\xalan.jar org.apache.xalan.xslt.Process -in javaRedir1.xml -xsl javaRedir1.xsl -out output\bugzilla5533.out
@echo To verify: check that both of the output files from above, 
@echo   javaRedir1.out and javaRedir1a-from-build-extensions.out, 
@echo   are in the output subdirectory below here

