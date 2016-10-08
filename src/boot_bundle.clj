(ns boot-bundle
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def bundle-file-path (atom nil))
(def bundle-map (atom nil))

(reset! bundle-file-path
        (or (System/getProperty "boot.bundle.file")
            (System/getenv "BOOT_BUNDLE_FILE")
            "boot.bundle.edn"))

(defn read-from-file
  "Reads bundle file"
  ([]
   (read-from-file @bundle-file-path))
  ([file-path]
   (let [file (io/file file-path)]
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
                      file-path))))))))

(defn validate-bundle-map [m]
  (when-not
      (and (every? keyword? (keys m))
           (every? (fn [v]
                     (and
                      (vector? v)
                      (or (symbol? (first v))
                          (every? #(or (vector? %)
                                       (keyword? %))
                                  v))))
                   (vals m)))
    (throw (RuntimeException.
            "bundle map isn't valid"))))

(defn set-bundle-map! [m]
  (validate-bundle-map m)
  (reset! bundle-map m))

(defn get-bundle-map []
  (or @bundle-map
    (let [from-file (read-from-file)]
      (set-bundle-map! from-file))))

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
     (RuntimeException. (format "Invalid bundle key: %s"
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
