(ns pangloss.transducers)

(defn doprocess
  "Like dorun for a transducer. Produces no intermediate sequence at all."
  [xform coll]
  (transduce xform (constantly nil) nil coll))

(defn merged
  "If the xform produces only one or more maps, return them merged into a single
  map."
  [xform coll]
  (transduce xform merge coll))

(defn counted
  "Return the count of elements in the result rather than the result itself."
  [xform coll]
  (transduce xform (completing (fn [n _] (inc n))) 0 coll))

(defn search
  "Returns the first result"
  [xform coll]
  (transduce xform (fn ([result] result) ([_ item] (reduced item))) nil coll))

(defn rf-branchable
  "Helper to adapt a reducing function to a branching transducer.

  Don't pass the completing of the rf through because completing multiple times
  is invalid and this transducer will do that after its child xforms have been
  completed."
  [rf]
  (fn ([result] result)
    ([result item] (rf result item))))

(defn cond-branch
  "Will route data down the first path whose predicate is truthy. The results are merged.

  Predicates are regular functions. They are called on each element that flows into this
  transducer until one of the predicates passes, causing the data to be routed down that
  predicate's xform."
  [& pred-xform-pairs]
  (let [pairs (partition-all 2 pred-xform-pairs)]
    (if (seq pairs)
      (fn [rf]
        (let [pairs (mapv (fn [[pred xform]]
                            (if xform
                              ;; if pred is not a fn, treat it as either always true or always false.
                              [(if (ifn? pred) pred (constantly (boolean pred)))
                               (xform (rf-branchable rf))]
                              ;; treat sole trailing xform as else:
                              [(constantly true) (pred (rf-branchable rf))]))
                      pairs)]

          (fn
            ([] (doseq [[_ xform] pairs] (xform)) (rf))
            ([result]
             (rf (reduce (fn [result [_ xform]] (xform result)) result pairs)))
            ([result input]
             (loop [[[pred xform] & pairs] pairs]
               (if pred
                 (if (pred input)
                   (xform result input)
                   (recur pairs))
                 result))))))
      (map identity))))

(defn lasts-by
  "A transducer that accomplishes the following but more efficiently
   (->> coll
        (group_by f)
        (map (fn [[k vals]] (last vals))))"
  ([] (lasts-by identity))
  ([f]
   (fn [rf]
     (let [matches (volatile! (transient (array-map)))]
       (fn
         ([] (rf))
         ([result] (rf (reduce rf result (vals (persistent! @matches)))))
         ([result x]
          (vswap! matches assoc! (f x) x)
          result)))))
  ([f coll]
   (into [] (lasts-by f) coll)))

(defn append
  "Append a set of raw data to the result of the transducer when the source data is completed. The data will not flow through any of the previous
   transducers in the chain, but will be processed by any subsquent ones."
  [coll]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf (reduce rf result coll)))
      ([result x] (rf result x)))))

(defn lookahead
  "Uses a nested transducer as the lookahead body"
  ([xform]
   (fn [rf]
     (let [look (xform (fn ([] nil) ([_] nil) ([_ item] (reduced true))))]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result item]
          (if (look nil item)
            (rf result item)
            result))))))
  ([{:keys [min max]} xform]
   (fn [rf]
     (let [finds (volatile! 0)
           look (xform (fn
                         ([] nil)
                         ([_] nil)
                         ([_ item]
                          ;; this gets called only when an item would be added to the collection
                          (vswap! finds inc))))]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result item]
          (vreset! finds 0)
          (look nil item)
          (if (<= min @finds max)
            (rf result item)
            result)))))))

(defn neg-lookahead
  "Ensure that the function does NOT produce a collection of at least one item.

   Use the arity 2 version to specify that there must NOT be at least min
   and/or at most max items in the route. If min or max is nil that limit will
   not be enforced. The arity 2 version of neg-lookahead is not really recommended
   as it is a little bit confusing."
  ([xform]
   (fn [rf]
     (let [look (xform (fn ([] nil) ([_] nil) ([_ item] (reduced true))))]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result item]
          (if (look nil item)
            result
            (rf result item)))))))
  ([{:keys [min max]} xform]
   (fn [rf]
     (let [finds (volatile! 0)
           look (xform (fn
                         ([] nil)
                         ([_] nil)
                         ([_ item]
                          ;; this gets called only when an item would be added to the collection
                          (vswap! finds inc))))]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result item]
          (vreset! finds 0)
          (look nil item)
          (if (<= min @finds max)
            result
            (rf result item))))))))

(defn branch
  "Allow a single chain of transducers to branch data out to be processed by
  multiple transducers, then merged back into a single one.

  The results of each of the branching xforms are merged into the resulting
  output in round-robin fashion. If any of the xforms produces multiple
  results for a single input, they will be sequential in the output.

  If an xform produces data when it is completed, that data will be included at
  the end of the result stream.

  The data pipeline looks something like this:

   (comp pre-xform
         (branch xform0 xform1 xform2)
         post-xform)

                 ,--> xform0 >--.
   pre-xform >------> xform1 >--->(round-robin merge)--> post-xform
                 `--> xform2 >--'
  "
  [& xforms]
  (fn [rf]
    (let [xforms (mapv #(% (rf-branchable rf)) xforms)]
      (fn
        ([]
         (doseq [xform xforms] (xform))
         (rf))
        ([result]
         (rf (reduce (fn [result xform] (xform result)) result xforms)))
        ([result item]
         (reduce (fn [result xform] (xform result item)) result xforms))))))


(defn grouped-by
  "A transducer that acts like group-by but includes the result as a single result in the stream.

   Options:

   :extract fn
     (grouped-by f :extract extract) is like this library's (group-by-extract f extract coll).

   :on-value fn
     apply a function to the final value (after extract is completed)

   :on-map fn
     apply a function to the final map (after extract and on-value are completed)

   :keys? false
     (grouped-by f :keys? false) is like (vals (group-by f coll))
     after extract, on-value and on-map

   :flat? false
     like :keys? false, but catenates each value into the result
     after extract, on-value and on-map
   "
  [f & {:keys [extract on-value on-map keys? flat?] :or {keys? true}}]
  (fn [rf]
    (let [group (volatile! (transient (array-map)))]
      (fn
        ([] (rf))
        ([result]
         (let [g (cond-> (persistent! @group)
                   on-value (update-vals on-value)
                   on-map on-map)]
           (rf
             (cond
               flat? (reduce rf result (vals g))
               keys? (rf result g)
               :else (rf result (vals g))))))
        ([result x]
         (vswap! group (fn [g]
                         (let [k (f x)
                               x (if extract (extract x) x)]
                           (if-let [v (get g k)]
                             (assoc! g k (conj v x))
                             (assoc! g k [x])))))
         result)))))

(defn- group-count* [->map assoc post-process f]
  (fn [rf]
    (let [group (volatile! (->map))
          update
          (if f
            (fn [item]
              (vswap! group
                (fn [g]
                  (let [k (f item)]
                    (assoc g k (inc (get g k 0)))))))
            (fn [item] (vswap! group (fn [g] (assoc g item (inc (get g item 0)))))))]
      (fn
        ([] (rf))
        ([result]
         (let [g (post-process @group)]
           (rf (rf result g))))
        ([result item]
         (update item)
         result)))))

(defn group-count
  "Return a map of {item count-equal-items} or {(f item) count-equal}.

  Arity 1 is basically identical to `frequencies`."
  {:see-also ["clojure.core/frequencies" "sorted-group-count" "group-by-count"]}
  ([] (group-count* #(transient (array-map)) assoc! persistent! nil))
  ([f] (group-count* #(transient (array-map)) assoc! persistent! f)))


(defn sorted-group-count
  "Return a map of {item count-equal-items} or {(f item) count-equal}"
  {:see-also ["group-count" "group-by-count"]}
  ([] (group-count* sorted-map assoc identity nil))
  ([f] (group-count* sorted-map assoc identity f)))

(defn- group-map-by-count
  ([m]
   (persistent!
     (reduce (fn [r [k count]]
               (assoc! r count (conj (get r count #{}) k)))
       (transient {})
       m))))

(defn- group-map-by-count-sorted [m]
  (reduce (fn [r [k count]]
            (assoc r count (conj (get r count #{}) k)))
    (sorted-map)
    m))

(defn group-by-count
  "Return a map of {count [all keys with that unique count]}"
  {:see-also ["group-count" "sorted-group-by-count" "group-by-count>1"]}
  ([]
   (group-count* #(transient {}) assoc!
     (comp group-map-by-count persistent!)
     nil))
  ([f]
   (group-count* #(transient {}) assoc!
     (comp group-map-by-count persistent!)
     f)))

(defn sorted-group-by-count
  "Return a map of {count [all keys with that unique count]}"
  {:see-also ["group-by-count" "group-by-count>1"]}
  ([]
   (group-count* #(transient {}) assoc!
     (comp group-map-by-count-sorted persistent!)
     nil))
  ([f]
   (group-count* #(transient {}) assoc!
     (comp group-map-by-count-sorted persistent!)
     f)))

(defn distinct-by
  "Removes duplicates based on the return value of key."
  ([] (distinct))
  ([key]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [k (key input)]
            (if (contains? @seen k)
              result
              (do (vswap! seen conj k)
                  (rf result input)))))))))
  ([f coll]
   (into [] (distinct-by f) coll)))

(defn duplicates-by
  "Return duplicated nodes based on the return value of key.

  Empty result means no dups."
  ([] (duplicates-by identity))
  ([key]
   (fn [rf]
     (let [marker (gensym)
           seen (volatile! {})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [k (key input)]
            (if (contains? @seen k)
              (do (let [first (@seen k)]
                    ;; only add the first node on the first dup instance
                    (when-not (identical? marker first)
                      (vswap! seen assoc k marker)
                      (rf result first)))
                  (rf result input))
              (do (vswap! seen assoc k input)
                  result))))))))
  ([f coll]
   (into [] (duplicates-by f) coll)))

(defn when-duplicated-by
  "Return only the first duplicated node based on the return value of key.

  Nil result means no dups."
  ([] (when-duplicated-by identity))
  ([key]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (when (nil? @seen) (rf result)))
         ([result input]
          (let [k (key input)]
            (if (contains? @seen k)
              (do (vreset! seen nil)
                  (reduced (rf result input)))
              (do (vswap! seen conj k)
                  result))))))))
  ([f coll]
   (into [] (duplicates-by f) coll)))

(defn sorted []
  (fn [rf]
    (let [a (java.util.ArrayList.)]
      (fn
        ([] (rf))
        ([result]
         (rf (reduce (fn [result item] (rf result item))
               result (sort a))))
        ([result item]
         (.add a item)
         result)))))

(defn sorted-by
  ([] (sorted))
  ([f]
   (fn [rf]
     (let [a (java.util.ArrayList.)]
       (fn
         ([] (rf))
         ([result]
          (rf (reduce (fn [result item] (rf result (second item)))
                result (sort-by first a))))
         ([result item]
          (.add a [(f item) item])
          result))))))

(defn section
  "Group the results of transforming each element into a collection per-element.

  Not a transducer."
  [xform]
  (map #(into [] xform [%])))

(defn section-map [xform coll]
  (into {}
    (comp (branch (map identity) (section xform))
      (partition-all 2))
    coll))

(defn map*
  "Map over the elements in a sequence of sequences.

  For instance in fermor (out-e*) produces a vector of edges for each node. This
  would let you work with those edges without flattening the vectors.

  Args:
    xform: a transducer compatible with the sequence type ie (map ...) if each item is a sequence
    empty-coll: a function that produces the sequence given to into for each item
    item-seq: a function that coerces each item into the type of sequence you want"
  ([xform]
   (map* empty identity xform))
  ([empty-coll xform]
   (map* empty-coll identity xform))
  ([empty-coll item-seq xform]
   (map (fn [item*]
          (into (empty-coll item*) xform (item-seq item*))))))
