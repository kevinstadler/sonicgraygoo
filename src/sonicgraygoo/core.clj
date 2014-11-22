(ns sonicgraygoo.core
  (:use [clojure.java.io :only [reader]]
        [quil.core]
        [quil.applet :only [current-applet]]))

; the last detected noise event, [x-coordinate loudness] tuples
(def noise-event (atom nil))

(def defaults {:c 343.2 :d 4.00 :l 2.00})
(def config (into defaults (read (java.io.PushbackReader. (reader "config.cfg")))))

(defn -main
  "I don't do a whole lot."
  [& args]
  (println "Hello, World!"))
