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

(defn- id
  "Creates an ID for the given host"
  [host port hash-fn] (hash-fn host port))

(defn hash->int
  "Converts a hash to an integer"
  [hash]
  (bigint (read-string (str "0x" hash))))

(defn- int->hex
  "Converts an integer to hex"
  [int]
  (format "%x" (biginteger int)))

(defn- start
  [id k hash-bits]
  (-> (bigint (Math/pow 2 (dec k)))
      (+ (hash->int id))
      (mod (bigint (Math/pow 2 hash-bits)))
      (int->hex)))

(defn- finger-table
  [id hash-bits node]
  (vec (map (fn [k] {:start (start id k hash-bits)
                     :node node})
            (range 1 (inc hash-bits)))))

(defn init
  ([host port] (init host port {}))
  ([host port config]
   (let [id (id host port (conf config :hash-fn))
         node {:host host :port port :id id}]
     (merge node {:predecessor nil
                  :successor node
                  :finger-table (finger-table id (conf config :hash-bits) node)}))))

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
          node
          (recur (dec i)))))))

(defn successor
  "Ask a node to find the successor of id"
  ([node id recur-succ config]
   (let [node-succ (:successor node)]
     (-> (if (between? (inc (hash->int (:id node))) (hash->int (:id node-succ)) (hash->int id))
           node-succ
           (-> (closest-preceding-node node id config)
               (recur-succ id config)))
         (select-keys [:host :port :id]))))
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

(defn fix-fingers
  "Refresh a finger table entry.
  Returns the appropriate finger node."
  [node i succ-fn]
  (succ-fn (get-in node [:finger-table i :start])))