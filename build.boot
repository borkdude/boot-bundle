(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :resource-paths #{"src"}
 :dependencies '[[adzerk/bootlaces "0.1.11" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])
(bootlaces! +version+)

(task-options!
 pom {:project 'boot-bundle
      :version +version+
      :description "boot-bundle, DRY for dependencies"
      :url "https://github.com/borkdude/boot-bundle"
      :scm {:url "https://github.com/borkdude/boot-bundle"}
      :license {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
