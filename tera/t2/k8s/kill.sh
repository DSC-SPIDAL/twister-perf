#!/bin/bash

jobID=`cat $HOME/.twister2/last-job-id.txt`

# kill the job
${T2_HOME}/bin/twister2 kill kubernetes $jobID
