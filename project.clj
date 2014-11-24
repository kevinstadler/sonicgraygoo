(defproject sonicgraygoo "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.org/kevinstadler/sonicgraygoo"
  :main sonicgraygoo.core
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [quil "2.2.4"]
                 [overtone "0.9.1"]
                 ; we need 2.0.1, e.g. because of boxwrap2d's call to step()
                 [org.jbox2d/jbox2d-library "2.0.1"]
                 [org.jbox2d/boxwrap2d "0.0.1"]])
; box2wrap2d is not available from maven repositories, download and install
; into your local mvn repository: http://www.jbox2d.org/processing/
; mvn install:install-file -Dfile=path/to/boxwrap2d.jar -DgroupId=org.jbox2d -DartifactId=boxwrap2d -Dversion=0.0.1 -Dpackaging=jar
; also its pre-2.1 jbox2d dependency
; mvn install:install-file -Dfile=path/to/jbox2d.jar -DgroupId=org.jbox2d -DartifactId=jbox2d-library -Dversion=2.0.1 -Dpackaging=jar
