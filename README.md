# boot-bundle
Boot-bundle, DRY for dependencies.

> It's just data - Rich Hickey

Upgrade once, upgrade everywhere.

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

Load boot-bundle before you load your other dependencies in `build.boot`:

```clojure
(set-env! :dependencies
          '[[boot-bundle "0.1.0-SNAPSHOT" :scope "test"]
            ;; if you share your bundle via clojars, uncomment and change:
            ;; [your-bundle "0.1.0-SNAPSHOT" :scope "test"]
            ]
          ;; if you use a bundle file from the current project's classpath, uncomment:
          ;; :resource-paths #{"resources"}
          )

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
    ;; combine this with your remaining dependencies:
    [reagent "0.6.0"]
    ;; ...
   ]))
```      
By default boot-bundle searches for the file `boot.bundle.edn` on the classpath.
This can be overriden by setting either

- the system property `boot.bundle.file`:
```
BOOT_JVM_OPTIONS="-Dboot.bundle.file=../bundle.edn"
```
- the environment variable `BOOT_BUNDLE_FILE`:

``` clojure
BOOT_BUNDLE_FILE="../bundle.edn"
```

- the atom `bundle-file-path`:

``` clojure
(reset! boot-bundle/bundle-file-path "../bundle.edn")
```

Searching the local file system has priority over searching the classpath.

That's it. You can now use boot as you normally would.

## Advanced usage

### Manipulating the bundle map
Boot-bundle lets you set the bundle map if you want to. For example, just write

```clojure
(reset! boot-bundle/bundle-map
        (boot-bundle/read-from-file "../bundle.edn"))
```
Note that validation only happens when using `read-from-file`, so when doing
something else, you may want to validate yourself:
 
```clojure
(swap! boot-bundle/bundle-map
       #(boot-bundle/validate-bundle 
         (assoc % :schema '[prismatic/schema "1.1.3"])))
```

## Experimental

Features mentioned in this section are experimental and may be removed in future versions.

### Version values

Boot bundle supports version values. They are keywords in the `version` namespace with string values. 

Example usage:

In `boot.bundle.edn`:

```clojure
{:version/util "0.1.0-SNAPSHOT"
 :util [util :version/util]}
```
In the `util` library's `build.boot`, define `+version+` using the bundle:

```clojure
(set-env! :dependencies
          '[[boot-bundle "0.1.0-SNAPSHOT" :scope "test"]])
(require '[boot-bundle :refer [expand-keywords get-version]])
(def +version+ (get-version :version/util))
```

The only place you have to bump `util`'s version is now the bundle file. 
All users of `:util` are automatically upgraded.

## Funding

This software was commissioned and sponsored by [Doctor Evidence](http://doctorevidence.com/). The Doctor Evidence mission is to improve clinical outcomes by finding and delivering medical evidence to healthcare professionals, medical associations, policy makers and manufacturers through revolutionary solutions that enable anyone to make informed decisions and policies using medical data that is more accessible, relevant and readable.

## FAQ
### How can I distribute my bundle via clojars?

Check out [this example](https://github.com/borkdude/boot.bundle.edn).

### Why isn't boot-bundle eating its own dog food? 

Boot-bundle is a lightweight library without any external dependencies. 

### Can I use multiple bundles and merge them?

Sure!
```clojure
(reset! boot-bundle/bundle-map 
  (merge 
    (boot-bundle/read-from-file "bundle1.edn")
    (boot-bundle/read-from-file "bundle2.edn")))
```
### How do you use it?
At work we use it in a multi-project repository. We have a `bundle.edn` file in the root and refer to it from most of the Clojure projects.

### How can I opt out?

Start a REPL, eval the call to `expand-keywords` and substitute this result back into your `build.boot`.

```clojure
$ boot repl
boot.user=> (use 'clojure.pprint)
boot.user=> (pprint (expand-keywords '[:clojure]))
[[org.clojure/clojure "1.8.0"]
 [clojure-future-spec "1.9.0-alpha13"]
 [org.clojure/test.check "0.9.0"]
 [org.clojure/core.async "0.2.391"]]
nil
```

## License

Copyright Michiel Borkent 2016.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
