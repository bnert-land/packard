(ns packard.commands-test
  (:require
    [packard.core :as commander]
    [clojure.test :refer [deftest is testing]]))

(deftest sanity
  (testing "if my setup is wokring"
    (is (= 1 1))))


(deftest strip-all-flags
  ;; In a commander pattern, there isn't really a (reliable) way to
  ;; distinguish bool flags/sub commands and values, without
  ;; knowing/accumulating the entire context of the command.
  ;;
  ;; Therefore, in order to reliably parse bool, value, and accumulation flags
  ;; we need to have a cli spec context.
  (testing "strip flags (bool and non-bool)"
    (let [cmd0 ["sub" "--bool" "--var" "var" "-B" "--other=thing" "command"]]
      (is (= (commander/strip-flags cmd0) ["sub" "command"])))))

#_(deftest simple-flags
  (testing "simple flag definition for various types"
    (let [flags ["-h"
                 "--thing"
                 #_"--no-other"
                 "-i"      "image1"
                 "--image" "image2"
                 "-i"      "image3,image4"
                 "-k"      "key:value"
                 "--kv"    "zhu:li"]
          cli   {:usage "do the thing zhu li"
                 :flags {:thing    ["t" "thing" {:as :bool}]
                         :no-other ["n" "no-other" {:as :bool}]
                         :images   ["i" "image" {:as     :hash-set
                                                 :valid? seq}]
                         :kv       ["k" "kv"    {:as :map}]}
                 :run    (fn [{:keys [flags positional]}]
                           (is (= [] positional))
                           (let [{:keys [thing no-other images kv]} flags]
                             (is (true? thing))
                             (is (nil? no-other))
                             (is (= images ["image1" "image2" "image3" "image4"]))
                             (is (= kv     {:key "value"
                                            :zhu "li"}))))}]
      (commander/exec cli flags))))

