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
  (defn version-key? [k]
    (and (keyword? k)
         (= "version" (namespace k))))
  (is (false? (version-key? :clojure)))
  (is (true? (version-key? :version/clojure))))

(with-test
  (defn dependency-vector? [[group-artifact version]]
    (and (symbol? group-artifact)
         (or (string? version)
             (version-key? version))))
  (is (false? (dependency-vector? '[1 2 3])))
  (is (true? (dependency-vector? '[org.clojure/clojure "1.8.0"])))
  (is (true? (dependency-vector? '[org.clojure/clojure :version/clojure]))))

(with-test
  (defn version-key [[group-artifact version]]
    (when (version-key? version)
      version))
  (is (= :version/clojure
         (version-key '[org.clojure/clojure :version/clojure])))
  (is (nil? (version-key '[org.clojure/clojure "1.8.0"]))))

(with-test
  (defn validate-bundle [m]
    (assert (map? m) "Bundle should be a map")
    (let [grouped (group-by
                   (fn [[k v]] (version-key? k)) m)
          versions (into {} (grouped true))
          dependencies (into {} (grouped false))
          key-set (set (keys m))
          dependency-key-set (set (keys dependencies))
          dependency-values (vals dependencies)
          versions-key-set (set (keys versions))
          valid-version-key?
          #(if-let [k (version-key %)]
             (contains? versions-key-set k)
             true)
          assert-vector #(assert
                          (vector? %)
                          (format "Bundle value should be vector: %s" %))
          assert-valid-dependency
          #(do (assert (dependency-vector? %)
                       (format "Invalid dependency vector: %s" %))
               (assert (valid-version-key? %)
                       (format "Invalid version key in: %s" %)))]
      ;; validate versions
      (doseq [[k v] versions]
        (assert (string? v)
                (format "Version value should be string: %s" v)))
      ;; validate dependencies
      (assert (every? keyword? key-set)
              "Bundle keys should be keywords.")
      (doseq [value dependency-values]
        (assert-vector value)
        (if (dependency-vector? value)
          (assert-valid-dependency value)
          (doseq [v value]
            (if (keyword? v)
              (assert (contains? dependency-key-set v)
                      (format "Invalid key reference: %s" v))
              (assert-valid-dependency v))))))
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
  (is (validate-bundle '{:version/util "0.3.5"
                         :util     [util :version/util]}))
  (is (thrown? java.lang.AssertionError
               (validate-bundle 1)))
  (is (thrown? java.lang.AssertionError
               (validate-bundle {:foo 1})))
  (is (thrown? java.lang.AssertionError
               (validate-bundle {1 :foo})))
  (is (thrown? java.lang.AssertionError
               (validate-bundle {:foo [:bar]})))
  (is (thrown? java.lang.AssertionError
               (validate-bundle
                '{:clojure [org.clojure/clojure :version/clojure]}))))

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
  (defn get-version
    "Returns version defined by version key or version of single
    library defined by unqualified keyword."
    [k]
    (let [v (k (get-bundle-map))]
      (if (version-key? k) v
          (when (dependency-vector? v)
            (second v)))))
  (with-redefs [get-bundle-map (constantly
                                '{:version/util "0.1.0"
                                  :clojure [org.clojure/clojure "1.8.0"]})]
    (is (= "0.1.0" (get-version :version/util)))
    (is (= "1.8.0" (get-version :clojure)))
    (is (nil? (get-version :foo)))))

(with-test
  (defn expand-version [[group-artifact version & rest :as dependency]]
    (if (version-key? version)
      (into [group-artifact (get-version version)]
            rest)
      dependency))
  (with-redefs [get-bundle-map (constantly '{:version/util "0.1.0"})]
    (is (= '[util "0.1.0" :scope "test"]
           (expand-version '[util "0.1.0" :scope "test"])))
    (is (= '[util "0.1.0" :scope "test"]
         (expand-version '[util :version/util :scope "test"])))))

(with-test
  (defn expand-single-keyword
    "Expands keyword defined in bundle.
     Returns vector of dependencies."
    [k]
    (if-let [deps (k (get-bundle-map))]
      (mapv expand-version
            (expand-keywords
             (if (dependency-vector? deps)
               [deps]
               deps)))
      (throw
       (RuntimeException.
        (format "Invalid bundle key: %s"
                k)))))
  (with-redefs
    [get-bundle-map
     (constantly
      '{:spec     [clojure-future-spec "1.9.0-alpha13"]
        :clojure [[org.clojure/clojure "1.8.0"]
                  :spec]
        :version/pedestal "0.5.1"
        :pedestal [[io.pedestal/pedestal.service       :version/pedestal]
                   [io.pedestal/pedestal.service-tools :version/pedestal]
                   [io.pedestal/pedestal.jetty         :version/pedestal]
                   [io.pedestal/pedestal.immutant      :version/pedestal]
                   [io.pedestal/pedestal.tomcat        :version/pedestal]]})]
    (is (thrown? RuntimeException
                 (expand-single-keyword :foo)))
    (is (= '[[clojure-future-spec "1.9.0-alpha13"]]
           (expand-single-keyword :spec)))
    (is (= '[[io.pedestal/pedestal.service       "0.5.1"]
             [io.pedestal/pedestal.service-tools "0.5.1"]
             [io.pedestal/pedestal.jetty         "0.5.1"]
             [io.pedestal/pedestal.immutant      "0.5.1"]
             [io.pedestal/pedestal.tomcat        "0.5.1"]]
           (expand-single-keyword :pedestal)))))

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

#_(t/test-ns *ns*)
