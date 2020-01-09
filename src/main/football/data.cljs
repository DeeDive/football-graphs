(ns football.data
  (:require
   [shadow.resource :as rc]
   [clojure.set :refer [project]]
   [football.utils :refer [hash-by logger]]))

(def sub-events
  {:lost-ball "Ground loose ball duel"})

(defn passes-count
  "Log da contagem de passes. Atual [464, 397]; Correta: [544 453]"
  [v]
  (-> (map (fn [x] (apply + (map (fn [y] (y :value)) x))) v) clj->js js/console.log)
  v)

(defn remove-bad-steal
  "Remove evento de roubada de bola fracassada"
  [coll]
  (let [index-coll (map #(vector %2 %1) coll (range))]
    (remove (fn [[i e]] (let [first? (= i 0)]
                          (if first?
                            false
                            (and
                             (= (-> e :subEventName) (-> sub-events :lost-ball))
                             (not= (-> e :teamId) (-> (nth index-coll (- i 1)) second :teamId))))))
            index-coll)))

(defn remove-cycle
  "Eventos como [24 -> 24 -> 24 -> 25] se transformam em [24 -> 25]"
  [coll]
  (let [index-coll (map #(vector %2 %1) coll (range))]
    (remove (fn [[i e]] (let [last? (= (+ i 1) (count coll))]
                          (if last?
                            false
                            (= (-> e :playerId) (get-in coll [(+ 1 i) :playerId])))))
            index-coll)))

(defn link-passes
  "Cria relacionamento de links"
  [teams]
  (map (fn [links]
         (map (fn [link]
                (map (fn [[source target]]
                       {:source (get-in source [:pos])
                        :target (get-in target [:pos])
                        :teamId (get-in source [:teamId])}) link))
              links))
       teams))

(defn remove-reflexivity
  "Remove links [24 -> 24]"
  [teams]
  (map
   #(remove (fn [{:keys [source target]}] (= source target)) %)
   teams))

(def matches (-> js/JSON
                 (.parse (rc/inline "../data/matches_World_Cup.json"))
                 (js->clj :keywordize-keys true)
                 ((fn [v] (filter #(= (-> % :wyId) 2057978) v)))
                 ((fn [v] (reduce (partial hash-by :wyId) (sorted-map) v)))))

(def players (-> js/JSON
                 (.parse (rc/inline "../data/players.json"))
                 (js->clj :keywordize-keys true)
                 ((fn [v] (reduce (partial hash-by :wyId) (sorted-map) v)))))

(def nodes (-> players
               vals
               (project [:pos :currentNationalTeamId])
               (#(map (fn [p] (merge p {:id (p :pos) :pos (-> p :pos keyword)})) %))
               (#(group-by :currentNationalTeamId %))
               vals
               reverse))

(defn links
  []
  (let [assoc-player-data #(assoc-in % [:pos] (get-in players [(-> % :playerId) :pos]))]
    (-> js/JSON
        (.parse (rc/inline "../data/events.json"))
        (js->clj :keywordize-keys true)
        ((fn [p] (map assoc-player-data p)))

        ; ####################################
        ; FIXME: Fix logic of passes
        ; Other events must be consider for passing network...

        ; Rules
        ; 1 - Remove "Ground loose ball duel" if previous teamID is different
        ; 2 - Remove cycles between players of the same team

        remove-bad-steal
        (#(map second %))
        (#(partition-by :teamId %))
        ((fn [v] (group-by #(-> % first :teamId) v)))
        vals
        ((fn [teams] (map (fn [team] (map remove-cycle team)) teams)))
        ((fn [teams] (map (fn [team] (map (fn [v] (map second v)) team)) teams)))
        ((fn [teams] (map (fn [team] (map #(partition 2 1 %) team)) teams)))
        link-passes
        ; ####################################

        ((fn [teams] (map (fn [team] (flatten team)) teams)))
        ((fn [teams] (map frequencies teams)))
        ((fn [teams] (map (fn [team] (map (fn [[ks v]] (merge ks {:value v})) team)) teams)))
        ((fn [teams] (map #(sort-by :value %) teams)))
        ; logger

        ; FIXME: this transformation MUST be remove at some point
        remove-reflexivity
        ; passes-count
        )))

(def data
  {:links (links)
   :matches matches
   :players players
   :nodes nodes})

(def brazil
  {:links (first (links))
   :nodes (first nodes)})

(def switzerland
  {:links (last (links))
   :nodes (last nodes)})
