#!/bin/bash

jobID=`cat $HOME/.twister2/last-job-id.txt`
firstPod=${jobID}-0-0

kubectl logs --follow $firstPod

