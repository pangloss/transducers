# transducers

A Clojure/ClojureScript library of transducers.

## Transducers

### `grouped-by`

A transducer that acts like group-by but includes the result as a single result in the stream.

Options:

:keys? false
  `(grouped-by f :keys? false)` is like `(vals (group-by f coll))`

:extract fn
  `(grouped-by f :extract extract)` is like this library's `(group-by-extract f extract coll)`.

### `group-by-extract`

Works just like group-by, but adds an extraction step. 

```clojure
 (group-by-extract f extract coll)
```

To do this with just group-by is relatively cumbersome.
```clojure
 (into {} (map (fn [[k v]] [k (mapv extract v)))
          (group-by f coll))
```
### `multiplex`

Allow a single chain of transducers to branch data out to be processed by multiple transducers, then merged back into a single one.

```clojure
(comp xform1
      (multiplex xform2 xform3 xform4)
      xform5)
```

Data pipeline looks something like this:
```
           ,-- xform2 --.
 xform1 --<--- xform3 --->-- xform5
           `-- xform4 --'
```

### `branch`

Will route data down one or another transducer path based on a predicate
and merge the results.

### `distinct-by`

Removes duplicates based on the return value of f.

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


## Utilities

### `xform`

Apply a transducer to get the first value (or nil) from 1 input. Will not work on transducers like grouped-by that need to be finalized.

### `doprocess`

Like `dorun` for a transducer. Produces no intermediate sequence at all.



## License

Copyright © 2022 Darrick Wiebe

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
