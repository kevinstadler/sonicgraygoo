(ns sonicgraygoo.core
  (:gen-class)
  (:use [clojure.java.io :only [as-file reader]]
        [quil.core]
        [quil.applet :only [current-applet]])
  (:import (org.jbox2d.p5 Physics)
           (org.jbox2d.common Vec2)
           (org.jbox2d.dynamics ContactListener)
           (org.jbox2d.collision CircleShape PolygonShape)
           (org.jbox2d.collision.shapes CircleDef)
           (org.jbox2d.testbed ProcessingDebugDraw)))

(def world nil)
(def framesrendered (atom 0))
(def population (atom nil))
(def incubating (atom nil))
(def munchies (atom nil))
(def bodiestowrap (atom nil))
(def eatingstohappen (atom nil))

; the last detected noise event, [x-coordinate loudness] tuples
(def noise-event (atom nil))

(def defaults {:c 343.2 :d 4.00 :l 2.00 :dance 40 :hunger 3 :hz 60 :spawn 0.001 :throb 60 :incubation 0})

(defn load-config []
  (if (.exists (as-file "config.cfg"))
    (def config (into defaults (read (java.io.PushbackReader. (reader "config.cfg"))))))
  (def throb (float-array (map #(inc (/ (Math/sin (/ (* % PI 2) (config :throb))) 2)) (range (config :throb))))))
;  (def throb (float-array (map #(Math/sin (/ (* % PI) (config :throb))) (range (config :throb))))))
;    (def hungerdiff (/ 1 (* (config :hunger) 50))))

(defn throbbing
  ([] (throbbing @framesrendered))
  ([i] (nth throb (mod i (config :throb)))))

(defn dance []
  (let [[x y] (repeatedly 2 #(- (rand (* 2 (config :dance))) (config :dance)))]
    (Vec2. x y)))

(defn new-goo
  "Creates some new goo and adds it to the world"
  ([v2 f] (new-goo (.x v2) (.y v2) f))
  ([x y f]
   (println (System/currentTimeMillis))
   (let [goo (.createCircle world x y 6)]
      (.applyForce world goo (Vec2. (rand 200) (rand 200)))
      (swap! population assoc goo {:body goo :freq f :hunger (:hunger config) :breath @framesrendered}))))

(defn clone [goo]
  (new-goo (.getPosition world (:body goo)) (inc (:freq goo))))

(defn new-munch
  "Creates a new crunchy munch and adds it to the world"
  [x volume]
  {:pre (pos? volume)}
  (let [blob (.createCircle world x (- (height) 20) 2)
        munch {:body blob :volume volume}]
    (swap! munchies assoc blob munch)
    (.applyForce world blob (Vec2. (- (rand 200) 100) 30))))

(defn feed
  "Feed the goo some munch, depending on how hungry it is."
  [goo munch]
  (let [bites (min (:hunger goo) (:volume munch))]
    ; feed the goo
    (if (== bites (:hunger goo))
      ; the goo is full!
      (do
;        (.setLinearVelocity (:body goo) (Vec2. 0.0 0.0))
        ; start animation
        (swap! population dissoc (:body goo))
        ; make goo hungry again
        (swap! incubating assoc (assoc goo :hunger (config :hunger))
                                (mod (dec @framesrendered) (config :incubation))))
      ; no big hunger, just munch a little
      (swap! population update-in [(:body goo) :hunger] dec))
    ; have the munch get eaten
    (let [body (:body munch)
          new-volume (- (:volume munch) bites)]
      (if (pos? new-volume)
        (let [new-shape (CircleDef.)]
          (println "Munch is smaller now.")
          (.destroyShape body (.getShapeList body))
          (set! (. new-shape radius) new-volume)
          (.createShape body new-shape))
        (do
          (swap! munchies dissoc body)
          (.removeBody world body))))))

(defn do-eating
  "Let all the goos do the munchy eating they deserve"
  []
  (doseq [[goo munch] @eatingstohappen]
    (feed goo munch))
  (swap! eatingstohappen (constantly [])))

(defn reset []
  ; remove bodies
  (doseq [body (concat (keys @population) (map :body (vals @munchies)))]
    (.removeBody world body))
  (swap! population (constantly {}))
  (swap! incubating (constantly {}))
  (swap! munchies (constantly {}))
  (swap! framesrendered (fn [x] 0))
  (load-config)
  ; set matching graphics+physics update rates
  (frame-rate (:hz config))
  (set! (. (.getSettings world) hz) (:hz config))
  ; be the center of my world
  (new-goo (/ (width) 2) (/ (height) 2) 100.0))

(definterface Renderer
  (^void draw [^org.jbox2d.dynamics.World w]))
(def g nil)

(def renderer
  (proxy [Renderer] []
    (draw [w]
      (.stroke g 200)
      (doseq [goo (vals @population)]
        (let [pos (.getPosition world (:body goo))]
          ; hungerdiff should be 40+ to be visible
          (.fill g (- 180 (* (:hunger goo) 50)) 30 20)
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
  (smooth)
  ; 1. hard boundary at edges
  ; 2. world width/height (largest, blue line, things outside don't collide and might be destroyed)
  ; 3. border box width/height
  (def world (Physics. (current-applet) (width) (height) 0.0 0.0 (- (width) 20) (- (height) 20) (- (width) 20) (- (height) 20) 10.0))
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
  (.setContactListener (.getWorld world)
    (proxy [ContactListener] []
      (add [p]
        ; did something hit a wall?
        (if (instance? PolygonShape (.shape1 p))
          ; can't modify world within callback, queue bodies for wrapping
          (swap! bodiestowrap conj (.getBody (.shape2 p)))
          ; did a goo bite a munchy munch?
          (let [goos (map #(@population (.getBody %)) [(.shape1 p) (.shape2 p)])]
            (when (apply not= (map nil? goos))
              ; make sure munch wasn't already eaten away by a quicker and hungrier goo
              (if-let [munch (@munchies (.getBody (if (nil? (first goos)) (.shape1 p) (.shape2 p))))]
                ; munching!
                (swap! eatingstohappen conj [(first (keep identity goos)) munch]))))))
      (persist [p]
        ; is there a persistent munching?
        )
      (remove [p])
      (result [p])))
  (reset))

(defn wrap [pos]
  (- (Math/signum pos) pos))

(defn draw []
  "None of the drawing actually happens here, just updating the world model"
  (swap! framesrendered inc)
  (background 0)
  ; wrap the world around!
  (when (seq @bodiestowrap)
    (doseq [body @bodiestowrap]
      (.setXForm body (Vec2. (wrap (.x (.getPosition body))) (wrap (.y (.getPosition body)))) 0.0))
    (swap! bodiestowrap (fn [_] nil)))
  ; has the contactlistener found us some eating?
  (do-eating)
  ; make dancing happen
  (when (< (rand) (* (count @population) 0.01))
    (.applyForce world (:body (rand-nth (vals @population))) (dance)))
  ; add stochastic baseline munchies!
  (when (< (rand) (config :spawn))
    (new-munch (rand-int (.width (current-applet))) 1))
  (doseq [[weirdo start] @incubating]
    (when (= start (mod @framesrendered (config :incubation)))
      (swap! incubating dissoc weirdo)
      (swap! population assoc (:body weirdo) weirdo)
      (clone weirdo))))

(defn clone-all []
  (dorun (map clone (vals @population))))

(defn keypress []
  (case (raw-key)
    (\C \c) (println (count @population))
    (\D \d) (clone-all)
    (\F \f) (println (current-frame-rate))
    (\R \r) (reset)
    (\S \s) (new-munch (rand (width)) 1)
    \+ (frame-rate (inc (current-frame-rate)))
    \- (frame-rate (dec (current-frame-rate)))
    (println (raw-key))))

(defn -main
  "Let's get gooing."
  [& args]
  (sketch
    :title "sonic gray goo"
    :renderer :opengl
    :setup setup
    :draw draw
    :key-typed keypress
    :size [800 600])) ; :fullscreen
