(ns taoensso.nippy.utils
  {:author "Peter Taoussanis"}
  (:require [clojure.string :as str])
  (:import  org.iq80.snappy.Snappy))

(defmacro case-eval
  "Like `case` but evaluates test constants for their compile-time value."
  [e & clauses]
  (let [;; Don't evaluate default expression!
        default (when (odd? (count clauses)) (last clauses))
        clauses (if default (butlast clauses) clauses)]
    `(case ~e
       ~@(map-indexed (fn [i# form#] (if (even? i#) (eval form#) form#))
                      clauses)
       ~(when default default))))

(defn pairs
  "Like (partition 2 coll) but faster and returns lazy seq of vector pairs."
  [coll]
  (lazy-seq
   (when-let [s (seq coll)]
     (let [n (next s)]
       (cons [(first s) (first n)] (pairs (next n)))))))

(defmacro time-ns
  "Returns number of nanoseconds it takes to execute body."
  [& body]
  `(let [t0# (System/nanoTime)]
     ~@body
     (- (System/nanoTime) t0#)))

(defmacro bench
  "Repeatedly executes form and returns time taken to complete execution."
  [num-laps form & {:keys [warmup-laps num-threads as-ms?]
                :or   {as-ms? true}}]
  `(try (when ~warmup-laps (dotimes [_# ~warmup-laps] ~form))
        (let [nanosecs#
              (if-not ~num-threads
                (time-ns (dotimes [_# ~num-laps] ~form))
                (let [laps-per-thread# (int (/ ~num-laps ~num-threads))]
                  (time-ns
                   (->> (fn [] (future (dotimes [_# laps-per-thread#] ~form)))
                        (repeatedly ~num-threads)
                        doall
                        (map deref)
                        dorun))))]
          (if ~as-ms? (Math/round (/ nanosecs# 1000000.0)) nanosecs#))
        (catch Exception e# (str "DNF: " (.getMessage e#)))))

(defn version-compare
  "Comparator for version strings like x.y.z, etc."
  [x y]
  (let [vals (fn [s] (vec (map #(Integer/parseInt %) (str/split s #"\."))))]
    (compare (vals x) (vals y))))

(defn version-sufficient?
  [version-str min-version-str]
  (try (>= (version-compare version-str min-version-str) 0)
       (catch Exception _ false)))

(defn compress-bytes   [^bytes ba] (Snappy/compress   ba))
(defn uncompress-bytes [^bytes ba] (Snappy/uncompress ba 0 (alength ba)))

(defn apply-memoized
  "A cross between `memoize` and `apply`. Operates like `apply` but accepts an
  optional {<args> <value> ...} cache atom."
  [cache f & args]
  (if-not cache
    (apply f args)
    (if-let [dv (@cache args)]
      @dv
      (let [dv (delay (apply f args))]
        (swap! cache assoc args dv)
        @dv))))

(defn ba-concat ^bytes [^bytes ba1 ^bytes ba2]
  (let [s1  (alength ba1)
        s2  (alength ba2)
        out (byte-array (+ s1 s2))]
    (System/arraycopy ba1 0 out 0  s1)
    (System/arraycopy ba2 0 out s1 s2)
    out))

(defn ba-split [^bytes ba ^Integer idx]
  [(java.util.Arrays/copyOfRange ba 0 idx)
   (java.util.Arrays/copyOfRange ba idx (alength ba))])

(comment (String. (ba-concat (.getBytes "foo") (.getBytes "bar")))
         (let [[x y] (ba-split (.getBytes "foobar") 3)]
           [(String. x) (String. y)]))