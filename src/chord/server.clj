(ns chord.server
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [chord.node :as n]
            [chord.client :as c])
  (:import [java.net ServerSocket]))

(defn- receive
  "Read a line of textual data from the given socket"
  [socket]
  (.readLine (io/reader socket)))

(defn- send-msg
  "Send the given string message out over the given socket"
  [socket msg]
  (let [writer (io/writer socket)]
    (.write writer msg)
    (.flush writer)))

(defn- successor
  [node args]
  (let [[id] args]
    (-> (n/successor node id
                     (fn [pred-node id _]
                       (println "Passing request to node")
                       (c/get (c/client (:host pred-node) "port") id)))
        (:id))))

(defn- handle
  [msg-in node]
  (println (str "Request: " msg-in))
  (let [payload (string/split msg-in #" ")
        request (first payload)
        args (rest payload)
        node @node]
    (case (-> request)
      "GET" (successor node args)
      "JOIN"
      "NOTIFY"
      "ERROR")))

(defn- start-periodic-tasks
  [node]
  ())

(defn serve-persistent [host port]
  (let [running (atom true)
        node (atom (n/init (str host ":" port)))]
    ;; (start-periodic-tasks)
    (future
      (println "serving")
      (with-open [server-sock (ServerSocket. port)]
        (while @running
          (with-open [sock (.accept server-sock)]
            (let [msg-in (receive sock)
                  msg-out (handle msg-in node)]
              (println (str "Response:" msg-out))
              (send-msg sock msg-out))))))
    running))