#!/bin/sh
pidof jackd > /dev/null
if [ $? -ne 0 ]; then
  case `arch` in
    "x86_64") jackd -r -d alsa -r 44100 & ;; #-C hw:1 & ;;
    "armv7l") jackd -R -d alsa -r 44100 -s -P hw:0 -C hw:1 & ;; # -t2000
    *) echo "Error: unknown architecture, don't know how to start jackd" && exit 1 ;;
  #jackd -r -d alsa -r 44100 &
  esac
  # kill jackd when this shell script is terminated
  trap 'kill $!' INT KILL TERM
fi
export SC_JACK_DEFAULT_INPUTS="system:capture_1,system:capture_2"
export SC_JACK_DEFAULT_OUTPUTS="system:playback_1,system:playback_2"
# scsynth can be killed by the user with Ctrl+C, which will in turn kill jackd
scsynth -u 57110 -n 2048
