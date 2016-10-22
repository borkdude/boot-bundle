(ns boot-bundle
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [with-test
                                  is
                                  test-ns]]))

(def bundle-file-path (atom nil))
(def bundle-map (atom nil))

(reset! bundle-file-path
        (or (System/getProperty "boot.bundle.file")
            (System/getenv "BOOT_BUNDLE_FILE")
            "boot.bundle.edn"))

(with-test
  (defn dependency-vector? [v]
    (symbol? (first v)))
  (is (false? (dependency-vector? '[1 2 3])))
  (is (true? (dependency-vector? '[org.clojure/clojure "1.8.0"]))))

(with-test
  (defn validate-bundle [m]
    (assert (map? m) "Bundle should be a map")
    (let [assert-vector #(assert
                          (vector? %)
                          (format "Bundle value should be vector: %s" %))
                   key-set (set (keys m))
          values (vals m)]
      (assert (every? keyword? key-set)
              "Bundle keys should be keywords.")
      (doseq [value values]
        (assert-vector value)
        (or (dependency-vector? value)
            (doseq [v value]
              (if (keyword? v)
                (assert (contains? key-set v)
                        (format "Invalid key reference: %s" v))
                (assert-vector v))))))
    m)
  (is (validate-bundle {}))
  (is (validate-bundle '{:clj-time [clj-time "0.12.0"]}))
  (is (validate-bundle '{:clojure [[org.clojure/clojure "1.8.0"]
                                   [clojure-future-spec "1.9.0-alpha13"]
                                   [org.clojure/test.check "0.9.0"]
                                   [org.clojure/core.async "0.2.391"]]}))
  (is (validate-bundle '{:spec     [clojure-future-spec "1.9.0-alpha13"]
                         :clojure [[org.clojure/clojure "1.8.0"]
                                   :spec]}))
  (is (thrown? java.lang.AssertionError
               (validate-bundle 1)))
  (is (thrown? java.lang.AssertionError
               (validate-bundle {:foo 1})))
  (is (thrown? java.lang.AssertionError
               (validate-bundle {1 :foo})))
  (is (thrown? java.lang.AssertionError
               (validate-bundle {:foo [:bar]}))))

(defn read-from-file
  "Reads bundle file"
  ([]
   (read-from-file @bundle-file-path))
  ([file-path]
   (let [file (io/file file-path)]
     (validate-bundle
      (if (.exists file)
        (do (println "boot-bundle found bundle file:"
                     (.getAbsolutePath file))
            (edn/read-string (slurp file)))
        (if-let [resource (io/resource file-path)]
          (do (println "boot-bundle found resource on classpath:"
                       (str resource))
              (edn/read-string (slurp resource)))
          (throw (RuntimeException.
                  (str "boot-bundle file not found at "
                       file-path)))))))))

(defn get-bundle-map []
  (or @bundle-map
      (reset! bundle-map (read-from-file))))

(declare expand-keywords)

(with-test
  (defn expand-single-keyword
    "Expands keyword defined in bundle. 
     Returns vector of dependencies."
    [k]
    (if-let [deps (k (get-bundle-map))]
      (expand-keywords
       (if (every? #(or (vector? %)
                        (keyword? %))
                   deps)
         deps
         [deps]))
      (throw
       (RuntimeException.
        (format "Invalid bundle key: %s"
                k)))))
  (with-redefs [get-bundle-map
                (constantly
                 '{:spec     [clojure-future-spec "1.9.0-alpha13"]
                   :clojure [[org.clojure/clojure "1.8.0"]
                             :spec]})]
    (is (thrown? RuntimeException
                 (expand-single-keyword :foo)))
    (is (= '[[clojure-future-spec "1.9.0-alpha13"]]
           (expand-single-keyword :spec)))))

(with-test
  (defn expand-keywords
    "Expands keywords in the input seq to coordinates."
    [coordinates]
    (assert (coll? coordinates)
            (str "coordinates should be a collection of "
                 "dependencies or keywords"))
    (reduce (fn [acc c]
              (if (keyword? c)
                (into acc (expand-single-keyword c))
                (conj acc c)))
            []
            coordinates))
  (with-redefs [get-bundle-map
                (constantly
                 '{:spec     [clojure-future-spec "1.9.0-alpha13"]
                   :clojure [[org.clojure/clojure "1.8.0"]
                             :spec]})]
    (is (thrown? java.lang.AssertionError
                 (expand-keywords :foo)))
    (is (= '[[org.clojure/clojure "1.8.0"]
             [clojure-future-spec "1.9.0-alpha13"]]
           (expand-keywords [:clojure])))))

(comment (t/test-ns *ns*))
