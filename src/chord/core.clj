(ns chord.core
  (:gen-class)
  (:require [chord.server :as server]))

(defn -main [& args]
  (let [[port host] args
        hostname (or host "localhost")]
    (when (nil? port)
      (throw (Exception. "Port is not specified.")))
    (server/start hostname (Integer. port))))
