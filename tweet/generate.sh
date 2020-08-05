#!/bin/bash

#if [ $# -ne "4" ]; then
#  echo "Please provide following parameters: numberOfWorkers memoryMB tuplesPerWorker deleteIDsPerWorker"
#  exit 1
#fi

T2_HOME=$HOME/twister2-test/twister2-0.8.0-SNAPSHOT

# copy conf files t2 conf directory
cp -f conf/common/* ${T2_HOME}/conf/common/
cp -f conf/standalone/* ${T2_HOME}/conf/standalone/

cd $T2_HOME

# total data size for all workers in GB
outputDir=hdfs://172.29.200.200:9009/tweet/large
workers=240
memoryMB=4096
tuplesPerWorker=200000000
deleteIDsPerWorker=1000

startTime=$(date +%s)

${T2_HOME}/bin/twister2 submit standalone jar $HOME/t2-perf/target/twister-perf-0.1.0-SNAPSHOT.jar \
  iu.iuni.deletion.generator.TweetIDGenerator $outputDir $workers $memoryMB $tuplesPerWorker $deleteIDsPerWorker

endTime=$(date +%s)
duration=$((endTime - startTime))
echo "Total Duration: $duration seconds."

# hdfs://v-login1:9009
