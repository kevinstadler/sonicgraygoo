(ns sonicgraygoo.audio
  "Grabs harmonic human foodstuffs and turns them into munch, all the while making gooey sounds!"
  (:use [overtone.live :exclude [config log]]
        [sonicgraygoo.core :only [config noise-event]]))

; make sure jack is running:
; jackd -r -d alsa -r 44100 -C hw:1

; unprocessed onsets per channel, each storing [timestamp volume] tuples
(def spikes (atom [(clojure.lang.PersistentQueue/EMPTY) (clojure.lang.PersistentQueue/EMPTY)]))

(defn other [side]
  (mod (inc side) 2))

(defn peek-other [side]
  (peek (nth @spikes (other side))))

; time it takes sound to travel from one mic to the other, in milliseconds
(def maxdiff (* 1000 (/ (config :d) (config :c))))
(println "With the current microphone distance and ms resolution"
  (dec (* 2 (int maxdiff))) "different positions can be distinguished")

(defn diff-to-x
  [t later-side]
  ; assume origin on the x-axis
  (/ (inc (if (zero? later-side) t (- t))) 2))

(defn spikehandler [event]
  "Human make sound!"
  (let [current (System/currentTimeMillis) ;(System/nanoTime)
  	    [_ side level] (:args event)]
        ; is there a corresponding spike in the other queue?
    (loop [[othertime otherlevel] (peek-other side)]
      (if (nil? othertime)
        ; add to queue
        (do (println "store" side current)
            (swap! spikes #(update-in % [side] conj [current level])))
        ; else: investigate old spike
        (let [t (- current othertime)
              relt (/ t maxdiff)]
          (if (> relt 2)
            ; the other stored onset is an old, uncollected pulse
            (do
              (println "old" (other side) othertime relt)
              (swap! spikes #(update-in % [(other side)] pop))
              (swap! noise-event (fn [_] [(other side) otherlevel]))
              (recur (peek-other side)))
            ; else: do some calculations!
            (do
              (println "combine with" (other side) current othertime relt)
              (swap! noise-event (fn [_] [(diff-to-x relt side) otherlevel level]))
              (swap! spikes #(update-in % [(other side)] pop)))))))))

; register event handler
(on-event "/tr" spikehandler :spikehandler)

(definst noisedetector [bus 0]
  (let [input (sound-in bus)
        f (fft (buffer 1024) input)
;        onsets (coyote input)]
        onsets (onsets f :relaxtime 3)]
    (send-trig onsets bus (loudness f))))
; -60.dbamp = 0.001 = 1 sone
; -40.dbamp = 0.01 = 4 sone
; -20.dbamp= 0.1 = 16 sone
; 0.dbamp= 1 = 64 sone
(noisedetector 0)
(noisedetector 1)
;(kill noisedetector)

(def outro (sample "100people.wav"))
