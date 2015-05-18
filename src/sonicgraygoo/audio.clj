(ns sonicgraygoo.audio
  "Grabs harmonic human foodstuffs and turns them into munch, all the while making gooey sounds!"
  (:use [sonicgraygoo.localisation]
        [overtone.core]))

;(boot-external-server)
(connect-external-server)

(clear)

; it might get loud
(fx-compressor 0 0.2 0.6 0.2) ; was: 1 0.2 1.0 0.3
(fx-compressor 1 0.2 0.6 0.2)

(definst goo [freq 100]
  (sin-osc freq))

(defsynth noisedetector [bus 0]
  (let [input (sound-in bus)
        f (fft (buffer 1024) input)
;        onsets (coyote input)]
        onsets (onsets f :relaxtime 3 :floor 0.2)]
    (send-trig onsets bus (loudness f))))

(defn stop-detection []
  (println (remove-event-handler :inputhandler)))

(defn start-detection [inputs localisation]
;  (kill noisedetector)
  (stop-detection)
  (dotimes [i inputs]
    (noisedetector i))
  ; register event handler
  (sync-event "/tr")
  (println (on-event "/tr" (if (= :time localisation) stereohandler monohandler) :inputhandler)))

(defn add-goo [f]
  (goo f))

(defn stop-goo [outro]
  (kill goo)
  (recording-stop)
  (when outro
;    (stop-detection)
    ; everybody loves theatrical pauses
    (Thread/sleep 2000)
    ((sample "100people.wav"))
    (Thread/sleep 124000)))

(defn shutdown []
  (stop-goo false)
  (println (kill-server)))
