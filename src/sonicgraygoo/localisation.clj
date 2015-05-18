(ns sonicgraygoo.localisation)

; the last detected noise event, [x-coordinate loudness] tuples
(def input-event (atom nil))

(defn monohandler [event]
  (println "m" (:args event))
  (reset! input-event (rest (:args event))))


(defn set-maxdiff [c d]
  ; time it takes sound to travel from one mic to the other, in milliseconds
  (def maxdiff (* 1000 (/ d c)))
  (println "With the current microphone distance and ms resolution"
    (dec (* 2 (int maxdiff))) "different positions can be distinguished"))

(set-maxdiff 343.0 7.0)

(defn diff-to-x
  [t later-side]
  ; assume origin on the x-axis
  (/ (inc (if (zero? later-side) t (- t))) 2))

; unprocessed onsets per channel, each storing [timestamp volume] tuples
(def spikes (atom [(clojure.lang.PersistentQueue/EMPTY) (clojure.lang.PersistentQueue/EMPTY)]))

(defn other [side]
  (if (zero? side) 1 0))

(defn peek-other [side]
  (peek (nth @spikes (other side))))

(defn stereohandler [event]
  "Human make sound!"
  (let [current (System/currentTimeMillis)
  	    [_ side level] (:args event)]
        ; is there a corresponding spike in the other queue?
    (loop [[othertime otherlevel] (peek-other side)]
      (if (nil? othertime)
        ; add to queue
        (swap! spikes #(update-in % [side] conj [current level]))
        ; else: investigate old spike
        (let [t (- current othertime)
              relt (/ t maxdiff)]
          (println "diff" t "relt" relt (other side) current othertime)
          (if (> relt 3)
            ; the other stored onset is an old, uncollected pulse
            (do
              (swap! spikes #(update-in % [(other side)] pop))
              (reset! input-event [(other side) otherlevel])
              (recur (peek-other side)))
            ; else: do some calculations!
            (do
;              (println "combine with" (other side) current othertime relt)
              (println (diff-to-x relt side))
              (reset! input-event [(diff-to-x relt side) otherlevel level])
              (swap! spikes #(update-in % [(other side)] pop)))))))))
