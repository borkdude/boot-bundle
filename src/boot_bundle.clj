(ns boot-bundle
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def bundle-file-path (atom nil))
(def bundle-map (atom nil))

(reset! bundle-file-path
        (or (System/getProperty "boot.bundle.file")
            (System/getenv "BOOT_BUNDLE_FILE")))

(defn read-from-file
  "Reads bundle file"
  ([]
   (if-let [file-path @bundle-file-path]
     (read-from-file file-path)
     (throw (RuntimeException.
             "boot-bundle file path not set"))))
  ([file-path]
   (let [file (io/file file-path)]
     (if (.exists file)
       (do (println "boot-bundle is using bundle file:"
                    (.getAbsolutePath file))
           ;; TODO, some verification
           (edn/read-string (slurp file)))
       (throw (RuntimeException.
               (str "boot-bundle file not found at "
                    file-path)))))))

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
