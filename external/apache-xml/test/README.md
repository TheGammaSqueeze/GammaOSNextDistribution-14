# Apache XML tests


## The first version

The apache XML version in AOSP is `2.7.1`. However, it's at least 16 years old.
The `xalan-j_2_7_3-rc10` version of test sources is used because
there wasn't a clear manual how to build the tests in 2.7.1,
and many bugs are fixed.

The first version was copied with the following command:

```shell
mkdir -p test/trace-src/org/apache/xalan/
cp -R $REPO/xalan-java/src/org/apache/xalan/trace/ test/trace-src/org/apache/xalan/
cp -R $REPO/xalan-test/java/ test/
cp -R $REPO/xalan-test/tests/ test/
cp -R $REPO/xalan-test/tools/ test/
find test/java/ -type f -name "*.class" -delete # remove the unnecessary .class files
```

Note that org.apache.xalan.trace package in xalan-java
has to be imported due to dependency from the tests.

If the test sources needs to be updated from the upstream, try to
1. Remove the test sources
2. Re-run the above command.
3. Re-apply all the patches found in the `git`.