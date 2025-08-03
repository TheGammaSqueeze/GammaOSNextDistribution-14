#!/bin/bash

## Declares
script_datestart=$(date +"%s")
MAXJOBS=16

OUT_DIR=out
OUT_CLOUD_ZIP=out/cloud.zip
mkdir -p $OUT_DIR

if [ -z "$1" ]; then
    ## Download new gerrit accounts
    ACCOUNTS_DIR=db/accounts
    ACCOUNTS_LAST=$ACCOUNTS_DIR/last.txt
    mkdir -p $ACCOUNTS_DIR
    FETCH_ACCOUNTS=false;
    if [ ! -f $ACCOUNTS_LAST ]; then
        FETCH_ACCOUNTS=true;
    else
        modsecs=$(date --utc --reference=$ACCOUNTS_LAST +%s) 2> /dev/null
        nowsecs=$(date +%s)
        delta=$(($nowsecs-$modsecs))
        if [ $delta -gt 86400 ]; then
            FETCH_ACCOUNTS=true;
        fi
    fi
    if [ "$FETCH_ACCOUNTS" = true ]; then
        ACCOUNT=`cat $ACCOUNTS_LAST 2> /dev/null`
        if [ $? -ne 0 ]; then
        ACCOUNT=0
        fi
        ERRORS=0
        echo "FETCHING NEW ACCOUNTS FROM $ACCOUNT ...";
        until [ $ERRORS -eq 30 ]; do
            let ACCOUNT+=1
            wget -O $ACCOUNTS_DIR/$ACCOUNT https://review.lineageos.org/accounts/$ACCOUNT
            if [ $? -ne 0 ]; then
                let ERRORS+=1
                rm $ACCOUNTS_DIR/$ACCOUNT
                continue;
            fi
            ERRORS=0
            echo $ACCOUNT > $ACCOUNTS_LAST
        done
    else
        echo "DONT FETCH NEW ACCOUNTS...";
    fi


    # Update repos
    STATS_DIR=db/stats
    PROJECTS_DIR=db/projects
    PROJECTS_LIST=$PROJECTS_DIR/list.txt
    PROJECTS_LIST_TMP=$PROJECTS_DIR/list.txt.tmp
    mkdir -p $PROJECTS_DIR
    rm -Rf $STATS_DIR
    mkdir -p $STATS_DIR
    wget -O $PROJECTS_LIST https://review.lineageos.org/projects/?p=LineageOS%2F \
        && perl -i -pe  's/%2F/\//g' $PROJECTS_LIST \
        && grep "\"id\":" $PROJECTS_LIST | awk -F"\"" '{print $4}' | grep -v "LineageOS\/\.\|LineageOS\/LineageOS" > $PROJECTS_LIST_TMP \
        && mv $PROJECTS_LIST_TMP $PROJECTS_LIST \
        && rm $PROJECTS_LIST.bak
    cat $PROJECTS_LIST | xargs -P $MAXJOBS -I % ./repo_fetch.sh %
fi

# Generate the cloud
java -Dfile.encoding=UTF-8 -classpath "./lib/*" CloudGenerator

# Done
echo ""
echo "====================================="
echo "Cloud generated: $OUT_CLOUD_ZIP"
script_dateend=$(date +"%s")
diff=$(($script_dateend-$script_datestart))
echo "$(($diff / 60)) minutes and $(($diff % 60)) seconds elapsed."
