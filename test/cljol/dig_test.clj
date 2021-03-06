(ns cljol.dig-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [loom.alg :as lalg]
            [ubergraph.core :as uber]
            [cljol.performance :as perf]
            [cljol.graph :as gr]
            [cljol.ubergraph-extras :as ubere]
            [cljol.dig9 :refer :all]))

(def ref-array (object-array 5))
(def prim-array (int-array 4 [2 4 6 8]))
(def two-dee-array (to-array-2d [[1 2 3] [4 5 6] [7 8 9]]))

(deftest a-test
  (are [x] (let [ex (reachable-objmaps [x] {})]
             (and (nil? (validate-obj-graph ex))
                  (not (any-objects-overlap? ex))
                  (not (any-object-moved? ex))))
       #{}
       #{5 7 12 13}
       #{nil}
       {5 7, 12 13}
       {5 false, nil 13}
       ref-array
       prim-array
       two-dee-array
       {{5 false, nil 13} "first-kv",
        "second-kv" {8 9, 10 11}}
       ))


(deftest ubergraph-successors-test
  ;; The current implementation of cljol.graph/edge-vectors assumes
  ;; that ubergraph.core/successors returns each successor node at
  ;; most once, even if there are multiple parallel edges to it.  Make
  ;; a test to check this.
  (let [g (uber/multidigraph [1 2]
                             [1 2]
                             [1 3]
                             [1 4]
                             [4 2]
                             [4 2])]
    (is (= false (nil? (seq (uber/successors g 1)))))
    (is (= #{2 3 4} (set (uber/successors g 1))))
    (is (= true (apply distinct? (uber/successors g 1))))
    (is (= false (apply distinct? (map uber/dest (uber/out-edges g 1)))))
    (is (= false (nil? (seq (uber/predecessors g 2)))))
    (is (= #{1 4} (set (uber/predecessors g 2))))
    (is (= true (apply distinct? (uber/predecessors g 2))))
    (is (= false (apply distinct? (map uber/src (uber/in-edges g 2)))))))


(defn compare-sccs-with-perf [g]
  (println "Comparing SCCs found multiple ways for graph with"
           (uber/count-nodes g) "nodes,"
           (uber/count-edges g) "edges")
  (let [{loom-scc :ret :as p} (perf/time (lalg/scc g))
        _ (do (print "Using loom.alg/scc, found" (count loom-scc) "SCCs in: ")
              (perf/print-perf-stats p))
        {scc-data :ret :as p} (perf/time (ubere/scc-tarjan g))
        _ (do (print "Using cljol.graph/scc-tarjan, found"
                     (count (:components scc-data)) "SCCs in: ")
              (perf/print-perf-stats p))]
    (is (= (set (map set loom-scc))
           (set (:components scc-data))))))


(deftest unique-owners-tests
  (is (= (uniquely-owned-values {:a [1 2] :b [2 3] :c [4 5]})
         {:owners {1 :a, 2 :cljol.dig9/multiple-owners, 3 :b, 4 :c, 5 :c},
          :uniquely-owned {:a #{1}, :b #{3}, :c #{4 5}}}))
  (is (= (uniquely-owned-values {:a [1 2] :b [2 3] :c [4 5] :d [3 4]})
         {:owners
          {1 :a,
           2 :cljol.dig9/multiple-owners,
           3 :cljol.dig9/multiple-owners,
           4 :cljol.dig9/multiple-owners,
           5 :c},
          :uniquely-owned {:a #{1}, :b #{}, :c #{5}, :d #{}}}))
  (is (= (uniquely-owned-values {:a [1 2]})
         {:owners {1 :a, 2 :a},
          :uniquely-owned {:a [1 2]}})))


(deftest scc-tests
  (let [g1 (uber/multidigraph
	    [1 2]
	    [3 4]
	    [5 6]
	    [5 7]
	    [8 4]
	    [8 2]
	    [9 5]
	    [9 8]
	    [2 10]
	    [2 3]
	    [4 10]
	    [4 1])
        fname2 "resources/dimultigraph-129k-nodes-272k-edges.edn"
        g2 (ubere/read-ubergraph-as-edges fname2)]
    (is (= (set (map set (lalg/scc g1)))
           (set (:components (ubere/scc-tarjan g1)))))
    (compare-sccs-with-perf g1)
    (compare-sccs-with-perf g2)))
