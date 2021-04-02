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
  [node [id]]
  (n/successor node id
               (fn [pred-node id _]
                 (let [host (:host pred-node)
                       port (:port pred-node)]
                   (println (str "Successor lookup in node" host ":" port))
                   (c/get (:host pred-node) (:port pred-node) id)))))

(defn- join
  [node [peer-host peer-port]]
  (swap! node into (n/join @node #(c/get peer-host (Integer. peer-port) %)))
  {:join :ok})

(defn- notify
  [node [peer-host peer-port peer-id]]
  (swap! node assoc :predecessor (n/notify @node {:host peer-host
                                                  :port (Integer. peer-port)
                                                  :id peer-id}))
  {:notify :ok})

(defn- handle
  [msg-in node]
  (println (str "Request: " msg-in))
  (let [payload (string/split msg-in #" ")
        request (first payload)
        args (rest payload)
        n @node]
    (-> (case request
          "GET" (successor n args)
          "JOIN" (join node args)
          "NOTIFY" (notify node args)
          "ERROR")
        (str))))

;; (defn- start-periodic-tasks
;;   [node]
;;   ())

(defn serve-persistent [host port]
  (let [running (atom true)
        node (atom (n/init host port))]
    ;; (start-periodic-tasks)
    (future
      (println (str "Serving at " port))
      (with-open [server-sock (ServerSocket. port)]
        (while @running
          (with-open [sock (.accept server-sock)]
            (let [msg-in (receive sock)
                  msg-out (handle msg-in node)]
              (println (str "Response:" msg-out))
              (send-msg sock msg-out)))))
      (println "Server closing..."))
    running))