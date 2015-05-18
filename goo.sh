#!/bin/sh
pidof scsynth > /dev/null
if [ $? -ne 0 ]; then
  ./scsynth.sh &
fi
if [ -f goo.jar ]; then
  java -jar goo.jar
else
  lein run
fi
