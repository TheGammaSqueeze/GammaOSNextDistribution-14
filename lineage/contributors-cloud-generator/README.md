# LineageOS contributors cloud generator

To build the jar, run the following:

    cd source/
    mvn package
    cp target/contributors-cloud-generator-1.0.jar ../lib/

If you make changes in `source/`, this will need to be re-built

If you have a local mirror of all the LineageOS code repositories,
then you can reduce the processing time and additional storage space
required to build the cloud by telling the tool where your repository
exists; otherwise, it will download hundreds of GB of data. Reference
your local mirror by doing the following:

    export MIRROR_ROOT=/path/to/mirror

If you have a Lineage Gerrit account and wish to authenticate with
your credentials, set them via environment variable as such, where
xxxxx and yyyyyy are the actual plain-text username and password
values:

    export GERRIT_USERNAME=xxxxx
    export GERRIT_PASSWORD=yyyyy

Not everyone can see every change on Gerrit. Some changes are private
for various reasons, some old changes are on branches that are hidden
for performance reasons. If you cannot see all the changes then a
cloud of 2024x2024 will not be filled; the base image will grow as
required, but it will not shrink. You can decrease the initial size by
creating a text file containing a single value. For example, a 900x900
initial size cloud can be created by doing the following:

    echo "900" > db/last_cloud_size.txt

To generate the code, open a linux console and type:

    ./generate_wordcloud.sh

This will parse the commit logs in all the LineageOS repos,
downloading as required, mix the data and generate a cloud.zip in the
output folder. This will take look long time the first time.

The file resources/well-known-accounts.txt allows merging multiple
Gerrit accounts into a single cloud entry. The format of this file
should match the name of the account and the list of known emails for
the account

    Name on Gerrit|nick|email1|email2|...

This project is based in a modified version of the
[kumo](https://github.com/kennycason/kumo) library.

Copyright © 2017-2019 The LineageOS Project
