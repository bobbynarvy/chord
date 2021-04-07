(ns chord.core
  (:gen-class)
  (:require [chord.server :as server]
            [chord.client :as client]
            [taoensso.timbre :as timbre :refer [info]]))

(defn -main [& args]
  (let [[port host] args
        hostname (first (filter #(not (nil? %))
                                [host (System/getenv "HOSTNAME") "localhost"]))
        local-port (Integer. (first (filter #(not (nil? %))) [port (System/getenv "PORT")]))
        peer-host (System/getenv "PEER_HOST")
        peer-port (System/getenv "PEER_PORT")]
    (when (nil? local-port) (throw (Exception. "Port is not specified.")))
    (server/start hostname local-port)
    (Thread/sleep 5000)
    ;; Join a peer's chord ring
    (when (and (and peer-host peer-port)
               (not= hostname peer-host))
      (info "Joining peer: " (str peer-host ":" peer-port))
      (client/join hostname local-port peer-host peer-port))))
