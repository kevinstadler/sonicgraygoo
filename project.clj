(defproject sonicgraygoo "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.org/kevinstadler/sonicgraygoo"
  :javac-target "1.6" ; for processing
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [quil "2.2.4"]
                 [overtone "0.9.1"]
                 [org.jbox2d/jbox2d-library "2.0.1"]
                 [fisica/fisica "0.1.14"]])
; fisica is not available from maven repositories, download and install
; into your local mvn repository: http://www.ricardmarxer.com/fisica/
; mvn2 install:install-file -Dfile=path/to/fisica.jar -DgroupId=fisica -DartifactId=fisica -Dversion=0.1.14 -Dpackaging=jar
; also its pre-2.1 jbox2d dependency
; mvn2 install:install-file -Dfile=path/to/jbox2d.jar -DgroupId=org.jbox2d -DartifactId=jbox2d-library -Dversion=2.0.1 -Dpackaging=jar
