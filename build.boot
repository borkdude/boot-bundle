(def +version+ "0.1.1-SNAPSHOT")

(set-env!
 :resource-paths #{"src"}
 :dependencies '[[adzerk/boot-test "1.1.2" :scope "test"]
                 [adzerk/bootlaces "0.1.11" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])
(bootlaces! +version+)

(require '[adzerk.boot-test :refer :all])

(task-options!
 pom {:project 'boot-bundle
      :version +version+
      :description "boot-bundle, DRY for dependencies"
      :url "https://github.com/borkdude/boot-bundle"
      :scm {:url "https://github.com/borkdude/boot-bundle"}
      :license {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
