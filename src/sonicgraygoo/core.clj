(ns sonicgraygoo.core
  (:use [clojure.java.io :only [reader]]
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

; the last detected noise event, [x-coordinate loudness] tuples
(def noise-event (atom nil))

(def defaults {:c 343.2 :d 4.00 :l 2.00 :hunger 3 :throb 30})
(def config (into defaults (read (java.io.PushbackReader. (reader "config.cfg")))))

(defn new-goo
  "Creates some new goo, adds it to the world and returns it"
  ([v2 f] (new-goo (.x v2) (.y v2) f))
  ([x y f]
   (let [goo (.createCircle world x y 6)]
      (.applyForce world goo (Vec2. (rand 200) (rand 200)))
      (swap! population assoc goo {:body goo :freq f :hunger (:hunger config) :incubation nil}))))

(defn new-munch
  "Creates a new crunchy munch and adds it to the world"
  [x volume]
  {:pre (pos? volume)}
  (let [blob (.createCircle world x (- (height) 20) 2);(FBlob.)
        munch {:body blob :volume volume}]
    (swap! munchies conj munch)
    (.applyForce world blob (Vec2. (rand 300) -100))))

(defn feed
  "Feed the goo. Returns the number of bites the goo ate, or 0 if it wasn't hungry."
  [goo]
  (if (nil? (:incubation goo))
    (do ; feed
      (if (= 1 (:hunger goo))
        ; the goo is full!
        (do
          (println "stopping the goo! (replicating goo must stand still)")
          (.setLinearVelocity (:body goo) (Vec2. 0.0 0.0))
          (new-goo (.x (.getPosition world (:body goo))) (.y (.getPosition world (:body goo))) (:freq goo))
          ; start animation
          (swap! population assoc-in [(:body goo) :incubation] (millis))
          ; make goo hungry again, just in case
          (swap! population assoc-in [(:body goo) :hunger] (:hunger config)))
        ; just munch a little
        (swap! population update-in [(:body goo) :hunger] dec))
      1)
    0))

(defn eat-some
  "Have the munch get eaten"
  [munch bites]
  (let [body (:body munch)
        new-volume (- (:volume munch) bites)]
    (println munch "is being munched" bites "times. Mmmmmh.")
    (if (pos? new-volume)
      (let [new-shape (CircleDef.)]
        (println "Munch is smaller now.")
        (.destroyShape body (.getShapeList body))
        (set! (. new-shape radius) new-volume)
        (.createShape body new-shape))
      (do
        (println "Munch been all munched up, oops!")
        (swap! munchies #(remove (partial = munch) %))
        (.removeBody world body)))))

(defn do-eating
  "Let all the goos do the munchy eating they deserve"
  []
  (doseq [munch @munchies]
    ; what are we rubbing up against, and is it a goo?
    (let [eaters (keep identity (map #(get @population %) (.getBodiesInContact (:body munch))))
          ; give some food to the goo - but did it actually eat it?
          ; just how much of this munch to eat?
          bites (reduce + (map feed eaters))]
            (when (pos? bites) (eat-some munch bites)))))

(defn reset []
  ; remove bodies
  (doseq [body (concat (keys @population) (map :body @munchies))]
    (.removeBody world body))
  (swap! population (constantly {}))
  (swap! incubating (constantly {}))
  (swap! munchies (constantly []))
  (swap! framesrendered (fn [x] 0))
  (new-goo 150.0 50.0 100.0))

(definterface Renderer
  (^void draw [^org.jbox2d.dynamics.World w]))
(def g nil)

(def renderer
  (proxy [Renderer] []
    (draw [w]
      (.stroke g 200)
      (doseq [goo (vals @population)]
        (let [pos (.getPosition world (:body goo))]
          (.fill g 0.0 (* 80 (:hunger goo)) 0.0)
          (.ellipse g (.x pos) (.y pos) 12 12)))
      (.stroke g 255)
      (.fill g 255.0 255.0 255.0)
      (doseq [goo @munchies]
        (let [pos (.getPosition world (:body goo))]
          (.ellipse g (.x pos) (.y pos) 4 4))))))

(defn setup []
  (def g (current-graphics))
  (smooth)
  (frame-rate 30)
  ; 1. hard boundary at edges
  ; 2. world width/height (largest, blue line, things outside don't collide and might be destroyed)
  ; 3. border box width/height
  (def world (Physics. (current-applet) (width) (height) 0.0 0.0 (- (width) 20) (- (height) 20) (- (width) 20) (- (height) 20) 10.0))
  (.setCustomRenderingMethod world renderer "draw")
  ; decrease physics update rate
  (set! (. (.getSettings world) hz) 30.0)
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
          (when (apply not= (map #(contains? @population (.getBody %)) [(.shape1 p) (.shape2 p)]))
            ; munching!
            (println (class (.shape1 p)) (.shape2 p)))))
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
    (swap! bodiestowrap (fn [old] nil)))
  (do-eating)
  ; add stochastic baseline munchies!
  (when (< (rand) 0.03)
    (new-munch (rand-int (.width (current-applet))) 1))
  (doseq [weirdo @incubating]))

(defn clone-all []
  (println "Cloning up to 128 goos")
  (doseq [goo (take 128 (vals @population))]
    (new-goo (.getPosition world (:body goo)) (:freq goo))))

(defn keypress []
  (case (raw-key)
    (\C \c) (println (count @population))
    (\D \d) (clone-all)
    (\R \r) (reset)
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
