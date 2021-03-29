(ns chord.client
  (:require [clojure.java.io :as io])
  (:import [java.io StringWriter]
           [java.net Socket]))

(defn client
  [host port]
  (fn [msg]
    (with-open [sock (Socket. host port)
                writer (io/writer sock)
                reader (io/reader sock)
                response (StringWriter.)]
      (println (str "REQ " host ":" port " -> " msg))
      (.append writer msg)
      (.flush writer)
      (-> (.readLine reader)
          (println)
          (identity)))))

(defn get
  "Get the node of id"
  [client id]
  (client (str "GET " id)))

(defn join
  "Join another node's Chord ring"
  []
  (println "hello"))