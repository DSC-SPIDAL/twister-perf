#!/bin/bash

if [ $# -ne "3" ]; then
  echo "Please provide following parameters: numberOfWorkers memoryMB tuples"
  exit 1
fi

T2_HOME=$HOME/twister2-test/twister2-0.8.0-SNAPSHOT

# copy conf files t2 conf directory
cp -f conf/common/* ${T2_HOME}/conf/common/
cp -f conf/standalone/* ${T2_HOME}/conf/standalone/

cd $T2_HOME

# total data size for all workers in GB
inputDir=hdfs://172.29.200.200:9009/tweet
outputDir=hdfs://172.29.200.200:9009/tweet
workers=$1
memoryMB=$2
tuples=$3
date=2018-05

${T2_HOME}/bin/twister2 submit standalone jar $HOME/t2-perf/target/twister-perf-0.1.0-SNAPSHOT.jar \
  iu.iuni.deletion.TweetIDPartitionJob $inputDir $outputDir $workers $memoryMB $date $tuples

