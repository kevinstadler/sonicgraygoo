(ns sonicgraygoo.core
  (:gen-class)
  (:use [sonicgraygoo.audio :only [add-goo stop-goo start-detection shutdown]]
        [sonicgraygoo.localisation :only [input-event set-maxdiff]]
        [clojure.java.io :only [as-file resource]]
        [quil.core]
        [quil.applet :only [current-applet]]
        [overtone.core :only [recording-start recording-stop]])
  (:import (org.jbox2d.p5 Physics)
           (org.jbox2d.common Vec2)
           (org.jbox2d.dynamics ContactListener)
           (org.jbox2d.collision CircleShape PolygonShape)
           (org.jbox2d.collision.shapes CircleDef)
           (org.jbox2d.testbed ProcessingDebugDraw)
           (java.awt GraphicsEnvironment Rectangle Robot Toolkit)
           (javax.imageio ImageIO)))

(def logfile (str "goo" (System/currentTimeMillis) ".log"))

(def world nil)
(def framesrendered (atom 0))
(def population (atom nil))
(def incubating (atom nil))
(def munchies (atom nil))
(def munchtoadd (atom nil))
(def threshold (atom 0.0))

(defn read-file [file]
  (read-string (slurp file)))

(def mutate)
(defn select-mutation [key]
  (def mutate (key (read-file (resource "mutation.cfg")))))

(def aconfig (atom (read-file (resource "defaults.cfg"))))
(defn config [key]
  (@aconfig key))

(defn load-config []
  (swap! aconfig #(into % (read-file "config.cfg")))
  (select-mutation (config :mutation))
  (def throb (float-array (map #(inc (/ (Math/sin (/ (* % PI 2) (config :throb))) 2)) (range (config :throb)))))
  (set-maxdiff (config :c) (config :d)))

(defn throbbing
  ([] (throbbing @framesrendered))
  ([i] (nth throb (mod i (config :throb)))))

(defn dance []
;  (let [[x y] (repeatedly 2 #(- (rand (* 2 (config :dance))) (config :dance)))]
  (let [angle (rand (* 2 PI))]
    (Vec2. (* (Math/sin angle) (config :dance)) (* (Math/cos angle) (config :dance)))))

(defn get-contacts [body]
  (loop [collected (list (.getContactList body))]
    (if-let [current (first collected)]
      (recur (conj collected (.next (first collected))))
      (map #(.other %) (rest collected)))))

(def new-goo)
(defn reset [outro]
  (set! (. (.getSettings world) hz) 0)
  (frame-rate 0)
  (stop-goo outro) ; sleeps
  (doseq [body (concat (keys @population) (map :body (keys @incubating)) (map :body (vals @munchies)))]
    (.removeBody world body))
  (reset! framesrendered 0)
  (reset! threshold 0)
  (reset! population {})
  (reset! incubating {})
  (reset! munchies {})
  (reset! munchtoadd nil)

  ; set matching graphics+physics update rates
  (frame-rate (config :hz))
  (set! (. (.getSettings world) hz) (config :hz))
  (if (config :record)
    (println (recording-start (str (System/currentTimeMillis) ".wav"))))
  ; theatrical pause
  (Thread/sleep 2000)
;  (if outro
;    (start-detection (config :localisation)))
  (spit logfile "reset" :append true)
  ; be the center of my world
  (new-goo (/ (width) 2) (/ (height) 2) 100.0))

(defn new-goo
  "Creates some new goo and adds it to the world"
  ([v2 f] (new-goo (.x v2) (.y v2) f))
  ([x y f]
    (if (== (config :goolimit) (count @population))
      (do
        (reset (config :outro))
        false) ; signal non-creation
      (do
        (spit logfile (System/currentTimeMillis) :append true)
        (let [goo (.createCircle world x y (config :goosize))]
          ;(.applyForce world goo (Vec2. (rand 200) (rand 200)))
          (swap! population assoc goo {:body goo :freq f :hunger (config :hunger) :breath @framesrendered :synth (add-goo f)}))))))

(defn clone [goo]
  (let [mult (+ (- (rand 0.02) 0.01) (if (pos? (rand-int (count @population))) 1.2 1))
        new-freq (* mult (:freq goo))]
    (new-goo (.getPosition world (:body goo)) new-freq)))

(defn new-munch
  "Creates a new crunchy munch and adds it to the world"
  ([x] (new-munch x 1))
  ([x volume] (new-munch x 0 volume))
  ([x y volume]
   {:pre (pos? volume)}
   (let [new-munchs (repeatedly (round volume)
                      #(do {:body (.createCircle world x (* (- 1 y) (inc (height))) (config :munchsize)) :volume 1}))]
     (swap! munchies (partial apply assoc) (interleave (map #(:body %) new-munchs) new-munchs))
     (doseq [m new-munchs] (.applyForce world (:body m) (Vec2. (- (rand (* 2 (config :injectionforce))) (config :injectionforce)) (rand (config :injectionforce))))))))
;     (doseq [m new-munchs] (.applyForce world (:body m) (Vec2. (/ (* (+ 60 (rand 40)) (- (/ (width) 2) x)) (height)) (+ 60 (rand 40))))))))

(defn remove-munch [munchbody]
  (swap! munchies dissoc munchbody)
  (.removeBody world munchbody))

(defn feed
  "Feed the goo some munch, depending on how hungry it is."
  [goobody munchbody]
  ; has someone else munched you already?
  (when-let [munch (@munchies munchbody)]
    ; has the goo already had enough?
    (when-let [goo (@population goobody)]
      (let [bites (min (:hunger goo) (:volume munch))]
        ; feed the goo
        (if (== bites (:hunger goo))
          ; the goo is full!
          (do
            (swap! population dissoc goobody)
            ; start animation and make goo hungry again
            (swap! incubating assoc (assoc goo :hunger (config :hunger))
                                    (mod (dec @framesrendered) (config :incubation))))
          ; no big hunger, just munch a little
          (swap! population update-in [goobody :hunger] dec))
        ; have the munch get eaten
        (let [new-volume (- (:volume munch) bites)]
          (if (pos? new-volume)
            (let [new-shape (CircleDef.)]
              (println "Munch is smaller now.")
              (.destroyShape munchbody (.getShapeList munchbody))
              (set! (. new-shape radius) new-volume)
              (.createShape munchbody new-shape)) ; Cannot cast org.jbox2d.collision.shapes.CircleDef to org.jbox2d.collision.ShapeDef>
            (remove-munch munchbody)))))))

(defn do-eating
  "Let all the goos do the munchy eating they deserve"
  []
  (dorun (map #(when-let [contacts (seq (get-contacts %))]
                 (doseq [c contacts] (feed % c)))
              (keys @population))))
;  (dorun (map #(apply feed %) @eatingstohappen))
;  (reset! eatingstohappen []))

(defn draw-world [g]
  (.background g 0)
  (.stroke g 200)
  (let [goosize (* 2 (config :goosize))]
    (doseq [goo (vals @population)]
      (let [pos (.getPosition world (:body goo))
            grayness (- 150 (* (:hunger goo) 50))]
        ; hungerdiff should be 40+ to be visible
        (.fill g grayness grayness grayness)
        (.ellipse g (.x pos) (.y pos) goosize goosize)))
    (doseq [[goo start] @incubating]
      (let [pos (.getPosition world (:body goo))]
        (.fill g 100 100 20)
        (.fill g (* 100 (throbbing (+ @framesrendered start))) 100 20)
        (.ellipse g (.x pos) (.y pos) goosize goosize))))
  (.stroke g 255)
  (.fill g 255.0 255.0 255.0)
  (doseq [mn (vals @munchies)]
    (let [pos (.getPosition world (:body mn))]
      (.ellipse g (.x pos) (.y pos) (* 2 (config :munchsize)) (* 2 (config :munchsize))))))

(def graphics nil)

(definterface Renderer
  (^void draw [^org.jbox2d.dynamics.World w]))
(def renderer (proxy [Renderer] [] (draw [w] (draw-world graphics))))

; receive and return a (x volume) tuple
(def localisations {
  :mono #(list (rand) %2)
  :stereo #(list (+ 0.3 (* 0.4 %1)) %2)
  :time nil
  :intensity nil
  })

(defn setup []
  (def graphics (current-graphics))
;  (hint :disable-opengl-errors)
  (hint :disable-depth-test)
  (smooth)
  ; 1. hard boundary at edges
  ; 2. world width/height (largest, blue line, things outside don't collide and might be destroyed)
  ; 3. border box width/height
  (def world (Physics. (current-applet) (width) (height) 0.0 0.0 (+ (width) (config :buffer)) (+ (height) (config :buffer)) (+ (width) (config :buffer)) (+ (height) (config :buffer)) 10.0))
  (.setCustomRenderingMethod world renderer "draw")
  ; turn border into sensor
  (doseq [border (.getBorder world)]
    (loop [edge (.getShapeList border)]
      (when (not (nil? edge))
        (set! (. edge m_isSensor) true)
        (recur (.m_next edge)))))
  ; make bodies mobile
  (.setDensity world 1.0)
  ; make things go nice and fast (default 0.4)
  (.setFriction world (config :friction))
  (reset false)
  (start-detection 2 (config :localisation))
  (add-watch input-event :watch
    #(let [[x vol] %4]
      (when (> vol (max @threshold (config :threshold)))
        (swap! munchtoadd conj (apply ((config :localisation) localisations) %4)))
      (swap! threshold (fn [avg n] (let [x (+ (* 0.9 avg) (* 0.1 n))] (println x) x)) (second %4)))))

(defn wrap [pos]
  (- (Math/signum pos) pos))

(def sweeppos (atom nil))
(defn draw []
  "None of the drawing actually happens here, just updating the world model"
  (swap! framesrendered inc)
  (do-eating)
  ; do wrapping
  (when-let [contacts (seq (mapcat get-contacts (.getBorder world)))]
    (doseq [body contacts]
      (if-let [munch (@munchies body)]
        (remove-munch body) ; maybe only if floating off the top of the screen or munchlimit reached?
        (.setXForm body (Vec2. (wrap (.x (.getPosition body))) (wrap (.y (.getPosition body)))) 0.0))))
  ; make dancing happen
  (when (< (rand) (* (count @population) (config :restlessness)))
    (.applyForce world (:body (rand-nth (vals @population))) (dance)))
; -60.dbamp = 0.001 = 1 sone
; -40.dbamp = 0.01 = 4 sone
; -20.dbamp= 0.1 = 16 sone
; 0.dbamp= 1 = 64 sone
  (when-let [newmunchs (seq @munchtoadd)]
    (doseq [[x vol] newmunchs]
      (when (< (count @munchies) (config :munchlimit))
        (new-munch (* (width) x) (min (inc (round (/ vol (config :factor)))) (config :maxinjection)))))
    (reset! munchtoadd nil))
  (when @sweeppos
    (new-munch @sweeppos (config :sweepinject))
    (swap! sweeppos #(if (or (< % 0) (> % (width))) nil (+ % (config :sweepstep)))))
  ; add stochastic baseline munchies!
;  (when (< (rand) (config :spawn))
;    (new-munch (rand-int (.width (current-applet))) 1))
  (doseq [[weirdo start] @incubating]
    (when (= start (mod @framesrendered (config :incubation)))
      (swap! incubating dissoc weirdo)
      (swap! population assoc (:body weirdo) weirdo)
      (clone weirdo))))

(defn clone-all []
  (dorun (reduce #(when %1 (clone %2)) true (vals @population))))
;  (dorun (map clone (vals @population))))

(defn screenshot []
  (let [pdfscale 16
        pdf (create-graphics (* pdfscale (width)) (* pdfscale (height)) :pdf (str (System/currentTimeMillis) ".pdf"))]
    (.beginDraw pdf)
    (.scale pdf pdfscale)
    (draw-world pdf)
    (.endDraw pdf)
    (.dispose pdf))
  (save-frame (str (System/currentTimeMillis) ".png")))

;  (.save (current-applet) (str (System/currentTimeMillis) ".png")))
  ;(ImageIO/getWriterFormatNames)
;  (ImageIO/write (.createScreenCapture (Robot.) (Rectangle. (.getScreenSize (Toolkit/getDefaultToolkit)))) "png" (as-file (str (System/currentTimeMillis) ".png"))))

(defn keypress []
  (case (raw-key)
    (\1) (new-munch 0 10)
    (\2) (new-munch (* 1/6 (width)) 10)
    (\3) (new-munch (* 1/3 (width)) 10)
    (\4) (new-munch (* 1/2 (width)) 10)
    (\5) (new-munch (* 2/3 (width)) 10)
    (\6) (new-munch (* 5/6 (width)) 10)
    (\7) (new-munch (width) 10)
    (\8) (new-munch (* 1/3 (width)) 0.5 10)
    (\9) (new-munch (* 1/2 (width)) 0.5 10)
    (\0) (new-munch (* 2/3 (width)) 0.5 10)
    (\A \a) (screenshot)
    (\C \c) (println (count @population) "+" (count @incubating) "vs." (count @munchies))
    (\D \d) (clone-all)
    (\F \f) (println (current-frame-rate))
    (\M \m) (reset! sweeppos (* (width) (config :sweepstart)))
    (\R \r) (do (load-config) (reset false))
    (\S \s) (new-munch (rand (width)))
    (\W \w) (println "Recording:" (:record (swap! aconfig #(update-in % [:record] not))))
    \+ (frame-rate (inc (current-frame-rate)))
    \- (frame-rate (dec (current-frame-rate)))
    (println (raw-key))))

(defn -main
  "Let's get gooing."
  [& args]
  (load-config)
  (sketch
    :title "sonic gray goo"
    :renderer :opengl ; switch to :java2d for pdf screenshots
    :setup setup
    :draw draw
    :key-typed keypress
    :on-close shutdown
    :display (or (config :display) (dec (count (.getScreenDevices (GraphicsEnvironment/getLocalGraphicsEnvironment)))))
    :size (config :size)
    :features (if (= :fullscreen (config :size)) [:present] [])))
