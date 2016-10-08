(ns boot-bundle
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def bundle-file-path (atom nil))
(def bundle-map (atom nil))

(reset! bundle-file-path
        (or (System/getProperty "boot.bundle.file")
            (System/getenv "BOOT_BUNDLE_FILE")
            "boot.bundle.edn"))

(defn validate-bundle [m]
  (let [assert-vector #(assert
                        (vector? %)
                        (format "Bundle value should be vector: %s" %))
        key-set (set (keys m))
        values (vals m)]
    (assert (every? keyword? key-set)
            "Bundle keys should be keywords.")
    (doseq [value values]
      (assert-vector value)
      (or (symbol? (first value))
          (doseq [v value]
            (if (keyword? v)
              (assert (contains? key-set v)
                      (format "Invalid key reference: %s" v))
              (assert-vector v))))))
  m)

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
        (if-let [file (io/file (io/resource file-path))]
          (do (println "boot-bundle found bundle file on classpath:"
                       file-path)
              (edn/read-string (slurp file)))
          (throw (RuntimeException.
                  (str "boot-bundle file not found at "
                       file-path)))))))))

(defn get-bundle-map []
  (or @bundle-map
      (reset! bundle-map (read-from-file))))

(declare expand-keywords)

(defn bundle
  "Returns (expanded) bundle its key identifier."
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

(defn expand-keywords
  "Expands keywords in the input seq to coordinates."
  [coordinates]
  (reduce (fn [acc c]
            (if (keyword? c)
              (into acc (bundle c))
              (conj acc c)))
          []
          coordinates))
