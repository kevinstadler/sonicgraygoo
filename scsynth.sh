#!/bin/sh
pidof jackd > /dev/null
if [ $? -ne 0 ]; then
  jackd -r -d alsa -r 44100 -C hw:1 &
  # kill jackd when this shell script is terminated
  trap 'kill $!' INT KILL TERM
fi
export SC_JACK_DEFAULT_INPUTS="system:capture_1,system:capture_2"
export SC_JACK_DEFAULT_OUTPUTS="system:playback_1,system:playback_2"
# scsynth can be killed by the user with Ctrl+C
scsynth -u 57110 -n 2048
