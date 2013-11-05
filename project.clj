(defproject fortress-ring-adapter "0.1.0-SNAPSHOT"
  :description "Ring adapter for netty 4 (part of the fortress distribution)"
  :url "https://github.com/iamedu/fortress-ring-adapter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :java-source-paths  ["src/java"]
  :source-paths  ["src/clj"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.netty/netty-all "4.0.11.Final"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [org.javassist/javassist "3.18.1-GA"]]
  :profiles {:dev {:dependencies [[clj-http "0.7.7"]
                                  [compojure "1.1.5"]
                                  [org.clojure/tools.nrepl "0.2.3"]
                                  [clojure-complete "0.2.3"]
                                  [ch.qos.logback/logback-core "1.0.13"]
                                  [ch.qos.logback/logback-classic "1.0.13"]]}}
  :jvm-opts ["-Xbootclasspath/p:lib/npn-boot-1.1.6.v20130911.jar"]
  :aot [fortress.ring.handler
        fortress.ring.spdy])
