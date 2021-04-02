(ns chord.node-test
  (:require [clojure.test :refer :all]
            [chord.node :refer :all]))

(def config {:hash-fn (fn [host port] host)
             :hash-bits 3})

(deftest initialize
  (let [node (init "3" 8888 config)]
    (is (= "3" (:host node)))
    (is (= "3" (:id node)))))

;; Helper fns for the find-successor test.
;; They are meant to put the successor value into a node (in order
;; to act as if the nodes have already been stabilized).

(defn finger-node
  "Find the node of a finger"
  [finger nodes]
  ;; Find the first node that is >= finger start
  (-> (filter #(<= (hash->int (:start finger)) (hash->int (:id %))) nodes)
      (#(if (empty? %) (first nodes) (first %)))
      (select-keys [:host :id])))

(defn with-node-info
  "Map the nodes for each finger and assign the successor"
  [node nodes]
  (->> (map #(assoc % :node (finger-node % nodes)) (:finger-table node))
       (#(assoc node :finger-table % :successor (:node (first %))))))

(defn recur-succ
  "Return a function that will call the successor function recursively"
  [nodes]
  (fn [pred-node id config]
    (let [node (first (filter #(= (:id %) (:id pred-node)) nodes))]
      (successor node id (recur-succ nodes) config))))

(deftest find-successor
  (let [nodes [(init "0" 8888 config) (init "1" 8888 config) (init "3" 8888 config)]
        [n0 n1 n3 :as nodes0] (map #(with-node-info % nodes) nodes)
        recur-fn (recur-succ nodes0)
        succ (fn [node id] (:host (successor node id recur-fn config)))]
    (is (= "0" (succ n0 "0")))
    (is (= "1" (succ n1 "1")))
    (is (= "3" (succ n1 "2")))
    (is (= "0" (succ n1 "4")))
    (is (= "0" (succ n1 "6")))
    (is (= "3" (succ n3 "3")))
    (is (= "0" (succ n3 "7")))
    (is (= "1" (succ n3 "1")))))

(deftest joining
  (let [n0 (init "0" 8888 config)
        nj0 (with-node-info n0 [n0]) ;; as if n0 is the only available node so far
        n1 (init "1" 8888 config)
        nj1 (join n1 (fn [_] nj0))]
    (is (= "0" (get-in nj1 [:successor :host])))))

(deftest stabilization
  (testing "without existing predecessor"
    (let [n0 (init "0" 8888 config)]
      (is (= n0 (stabilize n0 (fn [succ node] ()) config)))))
  (testing "with successor that should lead to stabilization"
    (let [n0 (init "0" 8888 config)
          n0p (assoc n0 :successor {:host "3" :id "3" :predecessor {:host "2" :id "2"}})]
      (is (= "2" (get-in (stabilize n0p (fn [succ node] ()) config) [:successor :host])))))
  (testing "with successor that should not lead to stabilization"
    (let [n0 (init "0" 8888 config)
          n0p (assoc n0 :successor {:host "3" :id "3" :predecessor {:host "0" :id "0"}})]
      (is (= "3" (get-in (stabilize n0p (fn [succ node] ()) config) [:successor :host]))))))

(deftest notifying
  (testing "predecessor is nil"
    (let [n0 (init "0" 8888 config)
          n1 (init "1" 8888 config)]
      (is (= "1" (get-in (notify n0 n1 config) [:predecessor :host])))))
  (testing "with new predecessor"
    (let [n0 (assoc (init "0" 8888 config) :predecessor {:host "2" :id "2"})
          n3 (init "3" 8888 config)]
      (is (= "3" (get-in (notify n0 n3 config) [:predecessor :host])))))
  (testing "with same predecessor"
    (let [n0 (assoc (init "0" 8888 config) :predecessor {:host "2" :id "2"})
          n1 (init "1" 8888 config)]
      (is (= "2" (get-in (notify n0 n1 config) [:predecessor :host]))))))