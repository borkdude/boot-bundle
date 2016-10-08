# boot-bundle
`boot-bundle`, DRY for dependencies

> It's just data - Rich Hickey

## Why
You have many projects with overlapping dependencies. You don't want to repeat yourself. 

## Usage
Define a bundle file that contains a map of keywords to either:
- a single dependency
- a vector of dependencies and/or keywords

Example:
```clojure
{:clojure [[org.clojure/clojure "1.8.0"]
           [clojure-future-spec "1.9.0-alpha13"]
           [org.clojure/test.check "0.9.0"]
           [org.clojure/core.async "0.2.391"]]
 :schema [prismatic/schema "1.1.3"]
 :component [[com.stuartsierra/component "0.3.1"]
             [org.clojure/tools.nrepl "0.2.12"]
             [reloaded.repl "0.2.3"]]
 :base [:clojure
        :schema
        :component
        [com.taoensso/timbre "4.7.4"]]
 :clojurescript [org.clojure/clojurescript "1.9.229"]}
```

Load this library before you load your other dependencies in `build.boot`:

```clojure
(set-env! :dependencies
          '[[boot-bundle "0.1.0-SNAPSHOT" :scope "test"]])
(require '[boot-bundle :refer [expand-keywords]])          
```

Wrap the `dependencies` vector in `set-env!` with `expand-keywords`:

```clojure
(set-env!
 :source-paths #{"src"}
 :dependencies
 (expand-keywords
  '[:base
    :clojurescript
    [reagent "0.6.0"]
   ]))
```      

Set either the system property `boot.bundle.file` or the environment variable `BOOT_BUNDLE_FILE` to specify the location of the bundle file:
```
BOOT_JVM_OPTIONS="-Dboot.bundle.file=../bundles.edn"
```
Then run boot as you normally would. Everything just works. It's just data.

## Advanced usage

`boot-bundle` lets you set the bundle map if you want to. For example, just write

```clojure
(reset! boot-bundle/bundle-map
        (boot-bundle/read-from-file "../bundles.edn"))
```
or 
```clojure
(swap! boot-bundle/bundle-map
       assoc :schema '[prismatic/schema "1.1.3"])
```

in your `build.boot` file.

## Misc

`boot-bundle` is a lightweight library without any external dependencies. 

## License

Copyright Michiel Borkent 2016.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.


