(ns boot-bundle.rewrite
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as rzip]
            [clojure.zip :as zip]))

(defn -main [& [in out]]
  (println in out))



(defn edit-by [loc pred f & args]
  (if-let [n (rzip/find-next loc rzip/next pred)]
    (let [edited (apply f n args)]
      (if (rzip/end? edited) edited
          (apply edit-by (rzip/next edited) pred f args)))
    loc))

(defn expand-tools-deps [m]
  (assoc m :expanded true))

;;;; Scratch

(comment
  (def z1 (rzip/of-file "deps.bundled.edn"))
  (map #(rzip/replace % :dude) (find-by z1 #(contains? #{:deps} (rzip/value %))))
  (rzip/string z1)
  (rzip/root z1)
  (rzip/root-string
   (edit-by z1 #(contains? #{:deps :extra-deps} (rzip/value %))
            (fn [v]
              (def v v)
              (let [deps-map-node (rzip/right v)
                    deps-map-node (rzip/edit deps-map-node expand-tools-deps)
                    ]
                deps-map-node))))
  v

  (-> (rzip/of-string "{:a {:b 1}}")
      (rzip/next)
      (rzip/right)
      (rzip/edit (fn [map]
                   (assoc map :foo 1)))
      (rzip/root-string))
  
  )
