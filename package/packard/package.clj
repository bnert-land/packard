(ns packard.package
  (:require
    [clojure.edn :as edn]
    [uberdeps.api :as uberdeps]))

(defn pkg [alias-name]
  (binding [uberdeps/exclusions (into uberdeps/uberdeps
                                      [#"\.DS_Store"])
            uberdeps/leve       :info]
    (uberdeps/package (edn/read-string (slurp "deps.edn"))
                      (format "target/%s.jar" alias-name))))

(defn -main [& [alias-name]]
  (pkg (or alias-name "packard")))
  
