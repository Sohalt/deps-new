;; copyright (c) 2021-2023 sean corfield, all rights reserved

(ns org.corfield.new
  "The next generation of clj-new. Uses tools.build and
  tools.deps heavily to provide a simpler 'shim'
  around template processing."
  {:org.babashka/cli {:coerce {:overwrite :keyword}}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.tools.build.api :as b]
            [org.corfield.new.impl :as impl]))

(set! *warn-on-reflection* true)

(s/def ::root string?)
(s/def ::description string?)
(s/def ::data-fn symbol?)
(s/def ::template-fn symbol?)
(s/def ::files (s/map-of string? string?))
(s/def ::open-close (s/tuple string? string?))
(s/def ::opts #{:only :raw})
(s/def ::dir-spec (s/cat :src string?
                         :target (s/? string?)
                         :files (s/? ::files)
                         :delims (s/? ::open-close)
                         :opts (s/* ::opts)))
(s/def ::transform (s/coll-of ::dir-spec :min-count 1))
(s/def ::template (s/keys :opt-un [::data-fn ::description ::root ::template-fn ::transform]))

(comment
  (s/conform ::transform [["root"]])
  (s/conform ::transform [["raw" "images" {} :raw]])
  (s/conform ::transform [["raw" "images" {} :only :raw]])
  (s/conform ::template {:transform [["resources" "resources"]
                                     ["images" "img" {"logo.png" "{{logo}}/main.png"} :raw]]})
  )

(defn create
  "Exec function to create a new project from a template.
  `:template` -- a symbol (or string) identifying the template,
  `:name` -- a symbol (or string) identifying the project name,
  `:src-dirs` -- optional sequence of directories to search for
      the template in addition to the classpath (default empty),
  `:target-dir` -- optional string identifying the directory to
      create the new project in,
  `:overwrite` -- whether to overwrite an existing directory or,
      for `:delete`, to delete it first; if `:overwrite` is `nil`
      or `false`, an existing directory will not be overwritten."
  [opts]
  (let [{:keys [src-dirs template] :as basic-opts}
        (impl/preprocess-options opts)
        [dir edn-file] (impl/find-root src-dirs template)
        _
        (when-not dir
          (throw (ex-info (str "Unable to find template.edn for " template) {})))

        [{:keys [target-dir template-dir overwrite] :as final-opts} edn]
        (impl/apply-template-fns dir
                                 basic-opts
                                 ;; this may throw for invalid EDN:
                                 (-> edn-file (slurp) (edn/read-string)))
        data       (impl/->subst-map final-opts)
        edn'       (s/conform ::template edn)]

    (when (s/invalid? edn')
      (throw (ex-info (str edn-file " is not a valid template file\n\n"
                           (s/explain-str ::template edn))
                      (s/explain-data ::template edn))))

    (when (.exists (io/file target-dir))
      (if overwrite
        (when (= :delete overwrite)
          (println "Deleting old" target-dir)
          (b/delete {:path target-dir}))
        (throw (ex-info (str target-dir " already exists (and :overwrite was not true).") {}))))

    (println "Creating project from" template "in" target-dir)

    (impl/copy-template-dir template-dir target-dir {:src (:root edn' "root")} data)
    (run! #(impl/copy-template-dir template-dir target-dir % data) (:transform edn'))))

(defn app
  "Exec function to create an application project.
  `:name` -- a symbol (or string) identifying the project name,
  `:target-dir` -- optional string identifying the directory to
      create the new project in,
  `:overwrite` -- whether to overwrite an existing directory or,
      for `:delete`, to delete it first; if `:overwrite` is `nil`
      or `false`, an existing directory will not be overwritten."
  [opts]
  (create (assoc opts :template 'app)))

(defn lib
  "Exec function to create a library project.
  `:name` -- a symbol (or string) identifying the project name,
  `:target-dir` -- optional string identifying the directory to
      create the new project in,
  `:overwrite` -- whether to overwrite an existing directory or,
      for `:delete`, to delete it first; if `:overwrite` is `nil`
      or `false`, an existing directory will not be overwritten."
  [opts]
  (create (assoc opts :template 'lib)))

(defn template
  "Exec function to create a template project.
  `:name` -- a symbol (or string) identifying the project name,
  `:target-dir` -- optional string identifying the directory to
      create the new project in,
  `:overwrite` -- whether to overwrite an existing directory or,
      for `:delete`, to delete it first; if `:overwrite` is `nil`
      or `false`, an existing directory will not be overwritten."
  [opts]
  (create (assoc opts :template 'template)))

(defn pom
  "Exec function to create just a `pom.xml` file.
  `:name` -- a symbol (or string) identifying the project name,
  `:target-dir` -- a string identifying the directory in which
      to create the `pom.xml` file,
  `:overwrite` -- defaults to `true` since you _do_ want to
      write into an existing directory!"
  [opts]
  (create (-> (merge {:overwrite true} opts)
              (assoc :template 'pom))))

(defn scratch
  "Exec function to create a minimal 'scratch' project.
  `:name` -- a symbol (or string) identifying the project name,
  `:target-dir` -- optional string identifying the directory to
      create the new project in,
  `:overwrite` -- whether to overwrite an existing directory or,
      for `:delete`, to delete it first; if `:overwrite` is `nil`
      or `false`, an existing directory will not be overwritten."
  [opts]
  (create (assoc opts :template 'scratch)))

(comment
  (let [[_dir edn] (impl/find-root [] 'org.corfield.new/app)]
    (s/conform ::template (edn/read-string (slurp edn))))
  (create {:template   'org.corfield.new/app
           :name       'org.bitbucket.wsnetworks/example
           :target-dir "new-out"
           :overwrite  :delete})
  )
