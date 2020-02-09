#!/bin/bash

if [ $# -ne "1" ]; then
  echo "Please provide: driverPodName"
  exit 1
fi

driver=$1

kubectl delete pod $driver
#kubectl delete service ${driver}-svc

# ${SPARK_HOME}/bin/spark-submit --kill $driver \
#   --master "${K8S_MASTER}" \
#   --conf spark.kubernetes.appKillPodDeletionGracePeriod=0
