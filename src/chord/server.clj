(ns chord.server
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [chord.node :as n]
            [chord.client :as c]
            [taoensso.timbre :as timbre :refer [info debug]])
  (:import [java.net ServerSocket Socket]))

(timbre/set-level! (or (keyword (System/getenv "LOG_LEVEL"))
                       :debug))

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
               (fn [{host :host port :port p-id :id} id _]
                 (if (= (:id node) p-id)
                   node
                   (do
                     (info (str "Successor lookup in node: " host ":" port))
                     (c/get host port id))))))

(defn- join
  [node [peer-host peer-port]]
  (swap! node into (n/join @node #(c/get peer-host (Integer. peer-port) %)))
  {:join :ok})

(defn- notify
  [node [peer-host peer-port peer-id]]
  (let [pred (n/notify @node {:host peer-host
                              :port (Integer. peer-port)
                              :id peer-id})]
    (when (not= (get-in @node [:predecessor :id]) (:id pred))
      (info "Assigning new predecessor: " (str (:host pred) ":" (:port pred)))
      (swap! node assoc :predecessor pred)))
  {:notify :ok})

(defn- handle
  [msg-in node]
  (debug (str "Request: " msg-in))
  (let [payload (string/split msg-in #" ")
        request (first payload)
        args (rest payload)]
    (-> (case request
          "GET" (successor @node args)
          "JOIN" (join node args)
          "NOTIFY" (notify node args)
          "INFO" (select-keys @node [:host :port :id :successor :predecessor])
          "ERROR")
        (str))))

(defn- start-server
  [node port running?]
  (future
    (with-open [server-sock (ServerSocket. port)]
      (info (str "Serving at " port))
      (while @running?
        (with-open [sock (.accept server-sock)]
          (let [msg-in (receive sock)
                msg-out (handle msg-in node)]
            (debug (str "Response: " msg-out))
            (send-msg sock msg-out)))))
    (info "Server closing...")))

(defn- start-stabilization
  [node running?]
  (future
    (while @running?
      (-> (n/stabilize @node
                       (fn [succ]
                         (let [info (c/info (:host succ) (:port succ))]
                           (:predecessor info)))
                       (fn [{succ-host :host succ-port :port}
                            {node-host :host node-port :port node-id :id}]
                         (c/notify succ-host succ-port node-host node-port node-id)))
          ((fn [succ]
             (when (not= (get-in @node [:successor :id]) (:id succ))
               (info "Stabilizing with new successor: " (str (:host succ) ":" (:port succ)))
               (swap! node assoc :successor succ)))))
      (Thread/sleep 8000))))

;; (defn- start-fix-fingers)

(defn start [host port]
  (let [running? (atom true)
        node (atom (n/init host port))]
    (start-server node port running?)
    (Thread/sleep 1000) ;; Wait for server to start. TO DO: Improve this.
    (start-stabilization node running?)
    running?))