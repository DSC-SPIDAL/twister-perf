#!/bin/bash

# cat nodelist.txt | grep compute | awk '{print $1, " slots=30"}' > nodes
sinfo -N | grep compute | awk '{print $1, " slots=30"}' > nodes

mv nodes conf/standalone/
