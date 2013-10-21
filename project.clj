(defproject fortress-ring-adapter "0.1.0-SNAPSHOT"
  :description "Ring adapter for netty 4 (part of the fortress distribution)"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :java-source-paths  ["src/java"]
  :source-paths  ["src/clj"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.netty/netty-all "4.0.10.Final"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.slf4j/slf4j-api "1.7.5"]]
  :profiles {:dev {:dependencies [[clj-http "0.7.7"]
                                  [compojure "1.1.5"]]}}
  :aot [fortress.ring.handler])
