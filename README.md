# transducers

A Clojure/ClojureScript library of transducers.

The v0.1.0 version from 2015 on Clojars is very outdated, until an update is released, please use via a git dep.

## Transducers

### `grouped-by`

A transducer that acts like group-by but includes the result as a single result in the stream.

Options:

:extract fn
  `(grouped-by f :extract extract)` is like this library's `(group-by-extract f extract coll)`.

:on-value fn
  apply a function to each grouped (and extracted) collection value in the map

  For instance, `frequencies` could be implemented as `(grouped-by f :on-value count)`

:on-map fn
  apply a function to the final map (after extract and on-value are completed)

:keys? 
  If `false`, this function produces a sequence of vectors of grouped values without the keys that were used to group them.

  `(grouped-by f :keys? false)` is like `(vals (group-by f coll))` after extract, on-value and on-map

:flat?
  If `true`, this function produces sequence of values without any grouping construct. This can be useful together with `:on-value`.

### `group-by-extract`

Works just like group-by, but adds an extraction step, which maps the `extract`
function over each value in the resulting grouped map.

```clojure
 (group-by-extract f extract coll)
```

To do this with just group-by is relatively cumbersome.
```clojure
 (into {} (map (fn [[k v]] [k (mapv extract v)))
          (group-by f coll))
```
### `branch`

Allow a single chain of transducers to branch data out to be processed by
multiple transducers, then merged back into a single one.

Allow a single chain of transducers to branch data out to be processed by
multiple transducers, then merged back into a single one.

The results of each of the branching xforms are merged into the resulting output
in round-robin fashion. If any of the xforms produces multiple results for a
single input, they will be sequential in the output.

If an xform produces data when it is completed, that data will be included at
the end of the result stream.

```clojure
(comp pre-xform
      (branch xform0 xform1 xform2)
      post-xform)
```

Data pipeline looks something like this:
```
                 ,--> xform0 >--.
   pre-xform >------> xform1 >-----(round-robin merge)--> post-xform
                 `--> xform2 >--'
```


### `cond-branch`

Will route data down the first path whose predicate is truthy. The results are merged.

Predicates are regular functions. They are called on each element that flows into this
transducer until one of the predicates passes, causing the data to be routed down that
predicate's xform.

``` clojure
(cond-branch 
  vector? (mapcat x)
  int?    (map x))
```

### `lookahead`

Filter a stream based on whether the xform produces at least one element. 

This is very useful when querying for data relationships, especially in trees or graphs.

``` clojure
(lookahead children)
```

Options

:min 1
Only match if at least this many elements are produced by the child xform.

:max
Only match if this many or less elements are produced by the child xform.

### `neg-lookahead`

Like `lookahead`, but inverted in the same way that `remove` inverts `filter`.

### `group-count` 

Like `frequencies`:

`(group-count)`

Or with a second arity which combines the behavior of `group-by` and `frequencies`.

`(group-count f)`

### `sorted-group-count`

Same as `group-count`, but accumulated into a `sorted-map`.

### `group-by-count`

Return a map of `{count [all keys with that unique count]}`. Has the same 2 arities as `group-count`.

### `sorted-group-by-count`

Same as `group-by-count` bu accumulated into a `sorted-map`.

### `distinct-by`

Removes duplicates based on the return value of f.

### `sorted-by`

Sorts the entire result just like `sort-by`. Produces no results until the data is completed.

### `sorted`

Sorts the entire result just like `sort`. Produces no results until the data is completed.

### `section`

Group the results of transforming each element into a collection per-element.

``` clojure
(into [] (section (mapcat range)) [0 1 2 3])
;; => [[] [0] [0 1] [0 1 2]]
```

### `lasts-by`

A transducer that accomplishes the following but more efficiently

```clojure
(->> coll
     (group_by f)
     (map (fn [[k vals]] (last vals))))
```

### `append`

Append a set of raw data to the result of the transducer when the source data is completed. The data will not flow through any of the previous
transducers in the chain, but will be processed by any subsquent ones.

### `map*`

Map over the elements in a sequence of sequences.

For instance in fermor (out-e*) produces a vector of edges for each node. This
would let you work with those edges without flattening the vectors.

Args:
  xform: a transducer compatible with the sequence type ie (map ...) if each item is a sequence
  empty-coll: a function that produces the sequence given to `into` for each item
  item-seq: a function that coerces each item into the type of sequence you want
  
``` clojure
(into [] 
  (map* (map inc))
  [[1 2] [3]]) 
;; => [[2 3] [4]]

(into [] 
  (map* (constantly [])
        range 
        (map inc))
  [2 3])
;; => [[1 2] [1 2 3]]
```

## Utilities

### `doprocess`

Like `dorun` for a transducer. Produces no intermediate sequence at all.

### `merged`

If the xform produces one or more maps, return them merged into a single map.

### `counted`

Return the count of elements in the result rather than the result itself.

### `section-map`

Attach the results of transforming each element to a map, keyed by the element transformed.

Duplicate elements will overwrite previous keys in the resulting map.

``` clojure
(section-map (mapcat range) [0 1 2])
;; => {0 [], 1 [0], 2 [0 1]}
```

## License

Copyright © 2022 Darrick Wiebe

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
