(defproject chord "0.1.0-SNAPSHOT"
  :description "Implementation of the Chord Protocol"
  :url "https://github.com/bobbynarvy/chord"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [digest "1.4.10"]
                 [com.taoensso/timbre "5.1.2"]]
  :main ^:skip-aot chord.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
