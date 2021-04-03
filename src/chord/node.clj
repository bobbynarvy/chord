(ns chord.node
  (:gen-class)
  (:require [digest]))

(def ^:private default-config
  {:hash-fn (fn [host port] (digest/sha-1 (str host ":" port)))
   :hash-bits 160})

(defn- conf
  [config prop]
  (if-let [p (prop config)]
    p
    (prop default-config)))

(defn- pow [b e]
  (-> (bigdec 2)
      (.pow e)
      (bigint)))

(defn- id
  "Creates an ID for the given host"
  [host port hash-fn] (hash-fn host port))

(defn hash->int
  "Converts a hash to an integer"
  [hash]
  (read-string (str "0x" hash)))

(defn- int->hex
  "Converts an integer to hex"
  [int]
  (format "%x" int))

(defn- start
  [id k hash-bits]
  (-> (Math/pow 2 (dec k))
      (+ (hash->int id))
      (mod (pow 2 hash-bits))
      (biginteger)
      (int->hex)))

(defn- finger
  [id k hash-bits]
  {:start (start id k hash-bits)
   :node nil})

(defn- finger-table
  [id hash-bits]
  (map (fn [k] (finger id k hash-bits)) (range 1 (inc hash-bits))))

(defn init
  ([host port] (init host port {}))
  ([host port config]
   (let [id (id host port (conf config :hash-fn))]
     {:host host
      :port port
      :id id
      :predecessor nil
      :successor {:host host
                  :port port
                  :id id}
      :finger-table (finger-table id (conf config :hash-bits))})))

(defn between?
  "Checks if a key k is in the closed interval [a, b]"
  [a b k]
  (if (> a b)
    (or (<= a k) (>= b k))
    (and (<= a k) (>= b k))))

(defn- closest-preceding-node
  "Search the local table for the highest 
  predecessor of id"
  [node id config]
  (loop [i (dec (:hash-bits config))]
    (let [id-int (hash->int (:id node))
          finger-i (nth (:finger-table node) i)
          finger-i-node (:node finger-i)]
      (if (between? (inc id-int) (dec (hash->int id)) (hash->int (:id finger-i-node)))
        finger-i-node
        (if (= i 0)
          id-int
          (recur (dec i)))))))

(defn successor
  "Ask a node to find the successor of id"
  ([node id recur-succ config]
   (let [node-succ (:successor node)]
     (if (between? (inc (hash->int (:id node))) (hash->int (:id node-succ)) (hash->int id))
       node-succ
       (-> (closest-preceding-node node id config)
           (recur-succ id config)))))
  ([node id recur-succ]
   (successor node id recur-succ default-config)))

(defn join
  "Make one node join another node's Chord ring"
  ([node peer-successor-fn config]
   {:predecessor nil
    :successor (peer-successor-fn (:id node))})
  ([node peer-successor-fn]
   (join node peer-successor-fn default-config)))

(defn stabilize
  "Verify the node's immediate successor
  and tell the successor about n. Should be
  called periodically. Returns the appropriate
  successor."
  [node get-predecessor notify]
  (let [successor (:successor node)
        predecessor (get-predecessor successor)
        id-int (hash->int (:id node))]
    (-> (if (and
             (not (nil? predecessor))
             (between? (inc id-int) (dec (hash->int (:id successor))) (hash->int (:id predecessor))))
          (select-keys predecessor [:host :port :id])
          (:successor node))
        (#(do
            (notify % node)
            %)))))

(defn notify
  "Notify node that peer-node might be its predecessor.
  Returns the appropriate predecessor."
  [node peer-node]
  (if (or
       (nil? (:predecessor node))
       (between?
        (inc (hash->int (get-in node [:predecessor :id])))
        (dec (hash->int (:id node)))
        (hash->int (:id peer-node))))
    peer-node
    (:predecessor node)))

(defn fix_fingers
  "Refresh a finger table entry"
  [node i succ-fn config]
  (update-in node [:finger-table i :node] #(succ-fn node % config))) ;; Figure out how to get hash of finger start