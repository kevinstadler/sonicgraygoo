#!/bin/sh
pidof scsynth > /dev/null
if [ $? -ne 0 ]; then
  ./scsynth.sh &
fi
java -jar goo.jar
