(ns packard.flag-parsing-test
  (:require
    [clojure.string :refer [split]]
    [clojure.test :refer [are deftest testing]]
    [packard.parsing.flags :as p.p.flags]
    [packard.spec :as p.spec]))

(def ->argv #(split % #" "))

(defn server-commands [server-name]
  {server-name {:flags {:mount ["m" "mount"]
                        :param ["p" "param"]}
                :commands {:set {}
                           :get {}}}})

(def cli-spec-simple
  {:usage "simple"
   :flags {:all?  ["a" "all" {:as :bool}]
           :basic ["A" "basic-auth"]}
   :run   #(println "RUN" %)})

(def cli-spec-complex
  {:flags {:basic-auth ["A" "basic-auth"]
           :verbose?   ["V" "verbose" {:as :bool}]
           :version?   [:v :version {:as :bool}]}
   :commands
   (merge
     {:list {:flags {:all? {:short :a
                            :long  :all
                            :as    :bool}
                     :ids  [:i :id {:as :set}]}}}
     (server-commands :server0)
     (-> (server-commands :server1)
         (assoc-in [:server1 :commands :login] {})))})

(deftest parsing-cli-kv-pairs
  (testing "parsing cli kv pairs"
    (are [input expected]
      (= expected (p.p.flags/str->kv input))
      "simple:kv"                  [:simple "kv"]
      "nested:colons:on:this:one"  [:nested "colons:on:this:one"]
      "qual/key:quali"             [:qual/key "quali"]
      "deep/nest/key:'some value'" [(keyword "deep/nest/key") "'some value'"]
      "key:value with spaces"      [:key "value with spaces"])))

(deftest gathering-flags
  (testing "gathering flags for simple cli spec"
    (are [input expected]
      (= expected (p.p.flags/gather
                    (p.p.flags/compile-flags-specs (p.spec/conform cli-spec-simple))
                    (->argv input)))
      "-a"
      [[]
       {:all? true}]

      "-a -A xyz"
      [[]
       {:all?  true
        :basic "xyz"}]

      "--all --basic-auth xyz"
      [[]
       {:all?  true
        :basic "xyz"}]

      "-a --basic-auth xyz"
      [[]
       {:all?  true
        :basic "xyz"}]

      "--all -A xyz some command --other value"
      [["some" "command" "--other" "value"]
       {:all?  true
        :basic "xyz"}]))

  (testing "gathering flags for complex cli spec"
    (are [input expected]
      (= expected (p.p.flags/gather
                    (p.p.flags/compile-flags-specs (p.spec/conform cli-spec-complex))
                    (->argv input)))
      "-V sub -v command -A basic:auth"
      [["sub" "command"]
       {:verbose?   true
        :version?   true
        :basic-auth "basic:auth"}]

      "-V sub --version -V command -v -A basic:auth --extra flag --eq=flag"
      [["sub" "command" "--extra" "flag" "--eq=flag"]
       {:verbose?   true
        :version?   true
        :basic-auth "basic:auth"}]))

  (testing "gathering flags for complex nested cli spec"
    (are [input expected]
      (= expected (p.p.flags/gather
                    (p.p.flags/compile-flags-specs
                      (p.spec/conform (get-in cli-spec-complex [:commands :list])))
                    (->argv input)))
      "-i 1 --id 2 -i 3 --id 2"
      [[]
       {:ids #{"1" "2" "3"}}]

      "--version -V --extra flag --eq=flag -i 1 --id 2 -i 3 --id 2 --all"
      [["--version" "-V" "--extra" "flag" "--eq=flag"]
       {:all?   true
        :ids  #{"1" "2" "3"}}])))

