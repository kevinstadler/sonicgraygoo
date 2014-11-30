(ns sonicgraygoo.core
  (:gen-class)
  (:use [sonicgraygoo.audio :only [add-goo stop-goo start-detection shutdown]]
        [sonicgraygoo.localisation :only [input-event set-maxdiff]]
        [clojure.java.io :only [as-file reader]]
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

(def world nil)
(def framesrendered (atom 0))
(def population (atom nil))
(def incubating (atom nil))
(def munchies (atom nil))
(def munchtoadd (atom nil))

(def aconfig (atom {:c 343.2 :d 4.00 :l 2.00 :dance 40 :hunger 3 :hz 60 :spawn 0.001 :throb 60 :incubation 0 :gsize 10 :msize 2 :limit 256 :localisation true :display (dec (count (.getScreenDevices (GraphicsEnvironment/getLocalGraphicsEnvironment)))) :size [800 600] :buffer 0 :record false}))
(defn config [key]
  (@aconfig key))

(defn load-config [file]
  (swap! aconfig #(into % (read (java.io.PushbackReader. (reader file)))))
  (def throb (float-array (map #(inc (/ (Math/sin (/ (* % PI 2) (config :throb))) 2)) (range (config :throb)))))
  (set-maxdiff (config :c) (config :d)))

(defn throbbing
  ([] (throbbing @framesrendered))
  ([i] (nth throb (mod i (config :throb)))))

(defn dance []
  (let [[x y] (repeatedly 2 #(- (rand (* 2 (config :dance))) (config :dance)))]
    (Vec2. x y)))

(defn get-contacts [body]
  (loop [collected (list (.getContactList body))]
    (if-let [current (first collected)]
      (recur (conj collected (.next (first collected))))
      (map #(.other %) (rest collected)))))

(def new-goo)
(defn reset [outro]
  (set! (. (.getSettings world) hz) 0)
  (stop-goo outro)
  (doseq [body (concat (keys @population) (map :body (keys @incubating)) (map :body (vals @munchies)))]
    (.removeBody world body))
  (reset! framesrendered 0)
  (reset! population {})
  (reset! incubating {})
  (reset! munchies {})

  ; set matching graphics+physics update rates
  (frame-rate (config :hz))
  (set! (. (.getSettings world) hz) (config :hz))
  ; theatrical pause
  (Thread/sleep 2000)
  (if (config :record)
    (println (recording-start (str (System/currentTimeMillis) ".wav"))))
  ; be the center of my world
  (new-goo (/ (width) 2) (/ (height) 2) 100.0))

(defn new-goo
  "Creates some new goo and adds it to the world"
  ([v2 f] (new-goo (.x v2) (.y v2) f))
  ([x y f]
    (if (== (config :limit) (count @population))
      (reset true)
      (do
        (println (System/currentTimeMillis))
        (let [goo (.createCircle world x y 6)]
          ;(.applyForce world goo (Vec2. (rand 200) (rand 200)))
          (swap! population assoc goo {:body goo :freq f :hunger (config :hunger) :breath @framesrendered :synth (add-goo f)}))))))

(defn clone [goo]
  (let [mult (+ (- (rand 0.02) 0.01) (if (pos? (rand-int (count @population))) 1.2 1))
        new-freq (* mult (:freq goo))]
    (new-goo (.getPosition world (:body goo)) new-freq)))

(defn new-munch
  "Creates a new crunchy munch and adds it to the world"
  ([x] (new-munch x 1))
  ([x volume]
   {:pre (pos? volume)}
   (let [new-munchs (repeatedly (round volume)
                      #(do {:body (.createCircle world x (inc (height)) 2) :volume 1}))]
     (swap! munchies (partial apply assoc) (interleave (map #(:body %) new-munchs) new-munchs))
     (doseq [m new-munchs] (.applyForce world (:body m) (Vec2. (/ (* (+ 60 (rand 40)) (- (/ (width) 2) x)) (height)) (+ 60 (rand 40))))))))

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
            (do
              (swap! munchies dissoc munchbody)
              (.removeBody world munchbody))))))))

(defn do-eating
  "Let all the goos do the munchy eating they deserve"
  []
  (dorun (map #(when-let [contacts (seq (get-contacts %))]
                 (doseq [c contacts] (feed % c)))
              (keys @population))))
;  (dorun (map #(apply feed %) @eatingstohappen))
;  (reset! eatingstohappen []))

(definterface Renderer
  (^void draw [^org.jbox2d.dynamics.World w]))
(def g nil)

(def renderer
  (proxy [Renderer] []
    (draw [w]
      (.stroke g 200)
      (doseq [goo (vals @population)]
        (let [pos (.getPosition world (:body goo))
              grayness (- 150 (* (:hunger goo) 50))]
          ; hungerdiff should be 40+ to be visible
          (.fill g grayness grayness grayness)
          (.ellipse g (.x pos) (.y pos) 12 12)))
      (doseq [[goo start] @incubating]
        (let [pos (.getPosition world (:body goo))]
          (.fill g 100 100 20)
          (.fill g (* 100 (throbbing (+ @framesrendered start))) 100 20)
          (.ellipse g (.x pos) (.y pos) 12 12)))
      (.stroke g 255)
      (.fill g 255.0 255.0 255.0)
      (doseq [mn (vals @munchies)]
        (let [pos (.getPosition world (:body mn))]
          (.ellipse g (.x pos) (.y pos) 4 4))))))

(defn setup []
  (def g (current-graphics))
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
  (.setFriction world 0.1)
  (start-detection (config :localisation))
  (reset false))

(defn wrap [pos]
  (- (Math/signum pos) pos))

(defn draw []
  "None of the drawing actually happens here, just updating the world model"
  (when (zero? @framesrendered)
    (add-watch input-event :watch
      (fn [_ _ _ [x vol]]
; -60.dbamp = 0.001 = 1 sone
; -40.dbamp = 0.01 = 4 sone
; -20.dbamp= 0.1 = 16 sone
; 0.dbamp= 1 = 64 sone
        (swap! munchtoadd conj [(rand 1024) vol]))))
  (swap! framesrendered inc)
  (background 0)
  (do-eating)
  ; do wrapping
  (when-let [contacts (seq (mapcat get-contacts (.getBorder world)))]
    (doseq [body contacts]
      (.setXForm body (Vec2. (wrap (.x (.getPosition body))) (wrap (.y (.getPosition body)))) 0.0)))
  ; make dancing happen
  (when (< (rand) (* (count @population) 0.01))
    (.applyForce world (:body (rand-nth (vals @population))) (dance)))
  ; add stochastic baseline munchies!
  (when-let [newmunchs (seq @munchtoadd)]
    (doseq [[x vol] newmunchs]
      (new-munch x (inc (round vol))))
    (reset! munchtoadd nil))
  (when (< (rand) (config :spawn))
    (new-munch (rand-int (.width (current-applet))) 1))
  (doseq [[weirdo start] @incubating]
    (when (= start (mod @framesrendered (config :incubation)))
      (swap! incubating dissoc weirdo)
      (swap! population assoc (:body weirdo) weirdo)
      (clone weirdo))))

(defn clone-all []
  (dorun (map clone (vals @population))))

(defn screenshot []
;  (.save (current-applet) (str (System/currentTimeMillis) ".png")))
  ;(ImageIO/getWriterFormatNames)
  (ImageIO/write (.createScreenCapture (Robot.) (Rectangle. (.getScreenSize (Toolkit/getDefaultToolkit)))) "png" (as-file (str (System/currentTimeMillis) ".png"))))

(def step 200)
(defn keypress []
  (case (raw-key)
    (\1) (new-munch step 10)
    (\2) (new-munch (* 2 step) 10)
    (\3) (new-munch (* 3 step) 10)
    (\4) (new-munch (* 4 step) 10)
    (\5) (new-munch (* 5 step) 10)
    (\A \a) (screenshot)
    (\C \c) (println (count @population) "+" (count @incubating))
    (\D \d) (clone-all)
    (\F \f) (println (current-frame-rate))
    (\R \r) (reset false)
    (\S \s) (new-munch (rand (width)))
    (\W \w) (println "Recording:" (:record (swap! aconfig #(update-in % [:record] not))))
    \+ (frame-rate (inc (current-frame-rate)))
    \- (frame-rate (dec (current-frame-rate)))
    (println (raw-key))))

(defn -main
  "Let's get gooing."
  [& args]
  (load-config "config.cfg")
  (sketch
    :title "sonic gray goo"
    :renderer :opengl
    :setup setup
    :draw draw
    :key-typed keypress
    :on-close shutdown
    :display (config :display)
    :size (config :size)
    :features (if (= :fullscreen (config :size)) [:present] [])))
