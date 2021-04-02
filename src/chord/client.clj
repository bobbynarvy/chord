(ns chord.client
  (:require [clojure.java.io :as io])
  (:import [java.io StringWriter]
           [java.net Socket]))

(defn send-request
  [host port msg]
  (with-open [sock (Socket. host port)
              writer (io/writer sock)
              reader (io/reader sock)
              response (StringWriter.)]
    (.append writer (str msg "\n"))
    (.flush writer)
    (io/copy reader response)
    (read-string (str response))))

(defn get
  "Get the node of id"
  [host port id]
  (send-request host port (str "GET " id)))

(defn join
  "Join another node's Chord ring"
  [host port peer-host peer-port]
  (send-request host port (str "JOIN " peer-host " " peer-port)))

(defn notify
  "Notify a node that it might need to have a
  different predecessor"
  [host port peer-host peer-port peer-id]
  (send-request host port (str "NOTIFY " peer-host " " peer-port " " peer-id)))