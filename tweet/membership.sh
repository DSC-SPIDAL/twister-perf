#!/bin/bash

#if [ $# -ne "2" ]; then
#  echo "Please provide following parameters: numberOfWorkers memoryMB"
#  exit 1
#fi

T2_HOME=$HOME/twister2-test/twister2-0.8.0-SNAPSHOT

# copy conf files t2 conf directory
cp -f conf/common/* ${T2_HOME}/conf/common/
cp -f conf/standalone/* ${T2_HOME}/conf/standalone/

cd $T2_HOME

# total data size for all workers in GB
tweetDir=hdfs://172.29.200.200:9009/tweet/large/tweet
deleteDir=hdfs://172.29.200.200:9009/tweet/large/delete
outputDir=hdfs://172.29.200.200:9009/tweet/large/membership
workers=4
memoryMB=4096

startTime=$(date +%s)

${T2_HOME}/bin/twister2 submit standalone jar $HOME/t2-perf/target/twister-perf-0.1.0-SNAPSHOT.jar \
  iu.iuni.deletion.MembershipFinder4 $tweetDir $deleteDir $outputDir $workers $memoryMB

endTime=$(date +%s)
duration=$((endTime - startTime))
echo "Total Duration: $duration seconds."


