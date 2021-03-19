(ns chord.node
  (:gen-class)
  (:require [digest]))

(def state (atom {:id nil
                  :host nil
                  :successor nil
                  :predecessor nil}))

(def ^:private default-config
  {:hash-fn digest/sha-1
   :hash-bits 160
   :hash->int (fn [hash] (read-string (str "0x" hash)))})

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
  [host hash-fn] (hash-fn host))

(defn- start
  [id-int k hash-bits]
  (-> (Math/pow 2 (dec k))
      (+ id-int)
      (mod (pow 2 hash-bits))
      (bigint)))

(defn- finger
  [id-int k hash-bits]
  {:start (start id-int k hash-bits)
   :node nil})

(defn- finger-table
  [id-int hash-bits]
  (map (fn [k] (finger id-int k hash-bits)) (range 1 (inc hash-bits))))

(defn init
  ([host] (init host {}))
  ([host config]
   (let [id (id host (conf config :hash-fn))
         id-int ((conf config :hash->int) id)]
     {:host host
      :id id
      :predecessor nil
      :successor nil
      :finger-table (finger-table id-int (conf config :hash-bits))})))

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
    (let [hash->int (:hash->int config)
          id-int (hash->int (:id node))
          finger-i (nth (:finger-table node) i)
          finger-i-node (:node finger-i)]
      (if (between? (inc id-int) (dec (hash->int id)) (hash->int (:id finger-i-node)))
        finger-i-node
        (if (= i 0)
          id-int
          (recur (dec i)))))))

(defn successor
  "Ask a node to find the successor of id"
  [node id recur-succ config]
  (let [hash->int (:hash->int config)
        node-succ (:successor node)]
    (if (between? (inc (hash->int (:id node))) (hash->int (:id node-succ)) (hash->int id))
      node-succ
      (-> (closest-preceding-node node id config)
          (recur-succ id config)))))

(defn join
  "Make one node join another node's Chord ring"
  [node node0 recur-succ config]
  (assoc node
         :predecessor nil
         :successor (successor node0 (:id node) recur-succ config)))