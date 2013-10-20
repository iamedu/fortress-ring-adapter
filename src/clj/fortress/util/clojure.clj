(ns fortress.util.clojure)

(defn random-guid  []
    (java.util.UUID/randomUUID))

(defn random-guid-str []
  (str (random-guid)))
