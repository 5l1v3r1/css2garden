(ns css2garden.postcss-test
  (:require
   [clojure.test :refer [deftest is are]]
   [css2garden.mediaquery :as mq]
   [postcss :refer [parse]]
   [clojure.walk :refer [postwalk]]
   [css2garden.ast :refer [ast->clj]]))

(deftest parse-test
  (is
   (=
    {:nodes [{:name "media"
              :nodes [{:nodes [{:prop "font-size" :type :decl :value "12px"}] :selector "h1" :type :rule}]
              :params "not screen and (max-height: 300px)"
              :type :atrule}]
     :type :root}
    (ast->clj
     (parse
      "
          @media not screen and (max-height: 300px) {
            h1 {
              font-size: 12px
            }
          }"))))
  (is
   (= {:nodes [{:nodes [{:prop "font-size" :type :decl :value "12px"} {:prop "font-weight" :type :decl :value "bold"}]
                :selector "body, h1"
                :type :rule}]
       :type :root}
      (ast->clj (parse "body, h1 { font-size: 12px; font-weight: bold; }"))))
  (is (= {:nodes [{:nodes [{:prop "font-size" :type :decl :value "12px"}] :selector "body" :type :rule}
                  {:nodes [{:prop "font-family" :type :decl :value "\"Geneva\""}] :selector "h1" :type :rule}]
          :type :root}
         (ast->clj (parse "body { font-size: 12px } h1 { font-family: \"Geneva\"; }"))))
  (is (= {:nodes [{:nodes [{:prop "font-size" :type :decl :value "12px"}] :selector "body" :type :rule}]
          :type :root}
         (ast->clj (parse "body {font-size: 12px}"))))
  (is
   (= {:nodes [{:nodes [{:prop "font-size" :type :decl :value "12px"} {:prop "font-weight" :type :decl :value "bold"}]
                :selector "body"
                :type :rule}]
       :type :root}
      (ast->clj (parse "body { font-size: 12px; font-weight: bold; }"))))
  (is (= {:nodes [{:nodes [{:prop "background-image" :type :decl :value "url(http://image.jpg)"}]
                   :selector "body"
                   :type :rule}]
          :type :root}
         (ast->clj (parse "body { background-image: url(http://image.jpg) }")))))

(defmulti visit :type)

(defmethod visit :root [{:keys [nodes]}] nodes)

(defmethod visit :rule
  [{:keys [selector nodes]}]
  [selector (reduce (fn [accum {:keys [prop value]}] (assoc accum (keyword prop) value)) {} nodes)])

(defmethod visit :atrule
  [{:keys [params nodes]}]
  (list 'at-media (mq/ast->garden (mq/mediaquery->ast params)) (apply concat nodes)))

(defmethod visit :default [ast] ast)

(defn ast->garden [ast] (postwalk visit ast))

(deftest ast->garden-test
  (is (= [["body" {:font-size "12px"}]]
         (-> "body {font-size: 12px}"
             parse
             ast->clj
             ast->garden)))
  (is (= [["body" {:font-size "12px" :font-weight "bold"}]]
         (-> "body {font-size: 12px; font-weight: bold}"
             parse
             ast->clj
             ast->garden)))
  (is (= [["body, h1, h2" {:font-size "12px" :font-weight "bold"}]]
         (-> "body, h1, h2 {font-size: 12px; font-weight: bold}"
             parse
             ast->clj
             ast->garden)))
  (is
   (=
    [(list 'at-media {:max-height "300px" :screen false} ["h1" {:font-size "12px"} "h2" {:font-weight "bold"}])
     ["h3" {:font-style "italic"}]]
    (->
      "
          @media not screen and (max-height: 300px) {
            h1 {
              font-size: 12px;
            }
            h2 {
              font-weight: bold;
            }
          }
          h3 {
            font-style: italic;
          }
        "
      parse
      ast->clj
      ast->garden))))