(ns app.events
  (:require [re-frame.core :as rf]
            [app.db :as db]
            [app.subs :as subs]
            [ajax.core :as ajax]
            [clojure.string :as str]))

;; ── Init ──────────────────────────────────────────────────────────────────
(rf/reg-event-db :initialise-db (fn [_ _] db/default-db))

;; ── Auth ──────────────────────────────────────────────────────────────────
(rf/reg-event-db
 :login
 (fn [db [_ user-id]]
   (let [user (first (filter #(= (:id %) user-id) db/utilisateurs))]
     (if user
       (-> db
           (assoc :session-user user)
           (assoc :login-form {:user-id "" :error nil})
           (assoc :site-actif (when (not= (:role user) :admin)
                                (first (:sites user)))))
       (assoc-in db [:login-form :error] "Utilisateur introuvable")))))

(rf/reg-event-db
 :logout
 (fn [db _]
   (-> db/default-db
       (assoc :session-user nil))))

(rf/reg-event-db
 :update-login-form
 (fn [db [_ k v]] (assoc-in db [:login-form k] v)))

;; ── Navigation ────────────────────────────────────────────────────────────
(rf/reg-event-db :set-tab        (fn [db [_ t]] (assoc db :tab t)))
(rf/reg-event-db :set-site-actif (fn [db [_ s]] (assoc db :site-actif s)))
(rf/reg-event-db :set-methode    (fn [db [_ m]] (assoc db :methode m)))
(rf/reg-event-db :set-filter-cat (fn [db [_ c]] (assoc db :filter-cat c)))

;; ── Mouvements ────────────────────────────────────────────────────────────
(rf/reg-event-db :set-show-mvt      (fn [db [_ v]] (assoc db :show-mvt v)))
(rf/reg-event-db :update-mvt-form   (fn [db [_ k v]] (assoc-in db [:mvt-form k] v)))

(rf/reg-event-db
 :ajouter-mouvement
 (fn [db _]
   (let [user   (:session-user db)
         form   (:mvt-form db)
         pid    (js/parseInt (:produit-id form))
         qte    (js/parseFloat (:qte form))
         pu     (js/parseFloat (or (:pu form) "0"))
         site   (or (:site-actif db) (first (:sites user)))
         today  (.slice (.toISOString (js/Date.)) 0 10)]
     (when (and pid (pos? qte) site (db/peut? user :saisir-mvt))
       (if (= (:type form) "entrée")
         (-> db
             (update :lots conj {:id (:next-lot-id db) :site-id site
                                 :produit-id pid :date today
                                 :qte qte :pu pu
                                 :ref (if (seq (:ref form)) (:ref form) "MVT-DIRECT")
                                 :qte-rest qte})
             (update :next-lot-id inc)
             (assoc :show-mvt false)
             (assoc :mvt-form {:produit-id "" :qte "" :pu "" :type "entrée" :ref ""}))
         ;; Sortie FIFO
         (let [lots-tries (->> (:lots db)
                               (filter #(and (= (:site-id %) site)
                                             (= (:produit-id %) pid)
                                             (pos? (:qte-rest %))))
                               (sort-by :date))
               [modifies _]
               (reduce (fn [[acc reste] lot]
                         (if (<= reste 0) [(conj acc lot) reste]
                             (let [prise (min (:qte-rest lot) reste)]
                               [(conj acc (update lot :qte-rest - prise)) (- reste prise)])))
                       [[] qte] lots-tries)
               ids (set (map :id modifies))]
           (-> db
               (update :lots (fn [ls]
                               (map #(if (ids (:id %))
                                       (first (filter (fn [m] (= (:id m) (:id %))) modifies))
                                       %) ls)))
               (assoc :show-mvt false)
               (assoc :mvt-form {:produit-id "" :qte "" :pu "" :type "entrée" :ref ""}))))))))

;; ── Bons de commande ──────────────────────────────────────────────────────
(rf/reg-event-db :set-show-bc      (fn [db [_ v]] (-> db (assoc :show-bc v) (cond-> (not v) (assoc :ai-preview false)))))
(rf/reg-event-db :update-bc-form   (fn [db [_ k v]] (assoc-in db [:bc-form k] v)))
(rf/reg-event-db :update-bc-ligne  (fn [db [_ i k v]] (assoc-in db [:bc-form :lignes i k] v)))
(rf/reg-event-db :add-bc-ligne     (fn [db _] (update-in db [:bc-form :lignes] conj {:produit-id "" :qte "" :pu ""})))
(rf/reg-event-db :remove-bc-ligne  (fn [db [_ i]]
                                     (update-in db [:bc-form :lignes]
                                                #(vec (concat (subvec % 0 i) (subvec % (inc i)))))))

(rf/reg-event-db
 :creer-bc
 (fn [db _]
   (let [user  (:session-user db)
         form  (:bc-form db)
         nid   (:next-bc-id db)
         site  (or (:site-id form) (:site-actif db) (first (:sites user)))
         lignes (->> (:lignes form)
                     (filter #(and (seq (:produit-id %)) (seq (:qte %))))
                     (mapv #(-> % (update :produit-id js/parseInt)
                                  (update :qte js/parseFloat)
                                  (update :pu js/parseFloat))))]
     (when (db/peut? user :creer-bc)
       (-> db
           (update :bons conj {:id nid
                               :ref (str "BC-" site "-" (+ 100 nid))
                               :site-id site
                               :fournisseur (:fournisseur form)
                               :date (:date form)
                               :statut "en-attente-validation"
                               :cree-par (:id user)
                               :valide-par nil
                               :lignes lignes})
           (update :next-bc-id inc)
           (assoc :show-bc false :ai-preview false)
           (assoc :bc-form {:fournisseur "" :date "" :site-id ""
                            :lignes [{:produit-id "" :qte "" :pu ""}]}))))))

(rf/reg-event-db
 :valider-bc
 (fn [db [_ bc-id]]
   (let [user (:session-user db)]
     (when (db/peut? user :valider-bc)
       (update db :bons
               (fn [bons]
                 (map #(if (= (:id %) bc-id)
                         (assoc % :statut "validé" :valide-par (:id user))
                         %) bons)))))))

(rf/reg-event-db
 :rejeter-bc
 (fn [db [_ bc-id]]
   (let [user (:session-user db)]
     (when (db/peut? user :valider-bc)
       (update db :bons
               (fn [bons]
                 (map #(if (= (:id %) bc-id)
                         (assoc % :statut "annulé")
                         %) bons)))))))

(rf/reg-event-db
 :receptionner-bc
 (fn [db [_ bc-id]]
   (let [user (:session-user db)
         bc   (first (filter #(= (:id %) bc-id) (:bons db)))]
     (when (and bc (= (:statut bc) "validé") (db/peut? user :receptionner-bc))
       (let [nouveaux-lots
             (map-indexed (fn [i ligne]
                            {:id           (+ (:next-lot-id db) i)
                             :site-id      (:site-id bc)
                             :produit-id   (:produit-id ligne)
                             :date         (:date bc)
                             :qte          (:qte ligne)
                             :pu           (:pu ligne)
                             :ref          (:ref bc)
                             :qte-rest     (:qte ligne)})
                          (:lignes bc))]
         (-> db
             (update :lots into nouveaux-lots)
             (update :next-lot-id + (count nouveaux-lots))
             (update :bons (fn [bons]
                             (map #(if (= (:id %) bc-id) (assoc % :statut "reçu") %) bons)))))))))

;; ── Inventaire ────────────────────────────────────────────────────────────
(rf/reg-event-db
 :lancer-inventaire
 (fn [db _]
   (let [user    (:session-user db)
         site    (or (:site-actif db) (first (:sites user)))
         stocks  (reduce (fn [acc lot]
                           (when (= (:site-id lot) site)
                             (update acc (:produit-id lot) (fnil + 0) (:qte-rest lot))))
                         {} (:lots db))
         init    (reduce #(assoc %1 (:id %2) (get stocks (:id %2) 0)) {} db/produits)]
     (when (db/peut? user :lancer-inventaire)
       (-> db
           (assoc :inventaire-data init)
           (assoc :inventaire-valide false)
           (assoc :show-inventaire true))))))

(rf/reg-event-db :update-inventaire-data
 (fn [db [_ pid val]] (assoc-in db [:inventaire-data pid] val)))

(rf/reg-event-db
 :valider-inventaire
 (fn [db _]
   (let [user   (:session-user db)
         site   (or (:site-actif db) (first (:sites user)))]
     (when (db/peut? user :cloturer-inventaire)
       (let [stocks (reduce (fn [acc lot]
                              (when (= (:site-id lot) site)
                                (update acc (:produit-id lot) (fnil + 0) (:qte-rest lot))))
                            {} (:lots db))
             inv    (:inventaire-data db)
             today  (.slice (.toISOString (js/Date.)) 0 10)]
         (let [db-final
               (reduce
                (fn [db-acc p]
                  (let [th  (get stocks (:id p) 0)
                        rl  (js/parseFloat (get inv (:id p) th))
                        ec  (- rl th)]
                    (cond
                      (pos? ec)
                      (update db-acc :lots conj
                              {:id (+ 9000 (:id p)) :site-id site
                               :produit-id (:id p) :date today
                               :qte ec :pu (subs/cmup (:lots db-acc) site (:id p))
                               :ref "INV-CLOTURE" :qte-rest ec})
                      (neg? ec)
                      (let [a-cons (- ec)
                            tries  (->> (:lots db-acc)
                                        (filter #(and (= (:site-id %) site)
                                                      (= (:produit-id %) (:id p))
                                                      (pos? (:qte-rest %))))
                                        (sort-by :date))
                            [mods _] (reduce
                                      (fn [[acc r] lot]
                                        (if (<= r 0) [(conj acc lot) r]
                                            (let [pr (min (:qte-rest lot) r)]
                                              [(conj acc (update lot :qte-rest - pr)) (- r pr)])))
                                      [[] a-cons] tries)
                            ids (set (map :id mods))]
                        (update db-acc :lots
                                #(map (fn [l]
                                        (if (ids (:id l))
                                          (first (filter (fn [m] (= (:id m) (:id l))) mods))
                                          l)) %)))
                      :else db-acc)))
                db db/produits)]
           (assoc db-final :inventaire-valide true)))))))

;; ── Import IA ─────────────────────────────────────────────────────────────
(rf/reg-event-db :set-ai-parsing  (fn [db [_ v]] (assoc db :ai-parsing v)))
(rf/reg-event-db :set-ai-error    (fn [db [_ e]] (assoc db :ai-error e)))
(rf/reg-event-db :clear-ai-error  (fn [db _]     (assoc db :ai-error nil)))

(rf/reg-event-db
 :ai-parse-success
 (fn [db [_ parsed doc-type]]
   (let [lignes (mapv #(-> {:produit-id    (if (:produit-id %) (str (:produit-id %)) "")
                            :qte           (str (or (:qte %) ""))
                            :pu            (str (or (:prix-unitaire %) ""))
                            :_designation  (:designation %)
                            :_confiance    (:confiance %)
                            :_produit-nom  (:produit-nom %)})
                      (:lignes parsed))]
     (-> db
         (assoc :ai-parsing false :ai-preview true :ai-doc-type doc-type :show-bc true)
         (assoc :bc-form {:fournisseur (or (:fournisseur parsed) "")
                          :date        (or (:date parsed) "")
                          :site-id     (or (:site-actif db) "")
                          :lignes      lignes})))))

(rf/reg-fx
 :call-anthropic
 (fn [{:keys [payload on-success on-error]}]
   (ajax/ajax-request
    {:uri             "https://api.anthropic.com/v1/messages"
     :method          :post
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :params          payload
     :on-success      on-success
     :on-failure      on-error})))

(rf/reg-event-fx
 :parse-document
 (fn [{:keys [db]} [_ {:keys [base64 media-type doc-type]}]]
   (let [cat-str (str/join "\n"
                  (map #(str "id:" (:id %) "|ref:" (:ref %) "|nom:\"" (:nom %) "\"|unite:" (:unite %))
                       db/produits))
         doc-label (case doc-type :bl "bon de livraison" :facture "facture" :bc-interne "bon de commande interne" "document")
         sys (str "Tu lis un " doc-label " pour un établissement scolaire.\n"
                  "Catalogue :\n" cat-str "\n\n"
                  "Retourne UNIQUEMENT du JSON valide (sans markdown) :\n"
                  "{\"fournisseur\":\"...\",\"date\":\"YYYY-MM-DD\",\"reference\":\"...\","
                  "\"lignes\":[{\"designation\":\"...\",\"produit-id\":null_ou_entier,"
                  "\"produit-nom\":null_ou_string,\"qte\":nombre,\"prix-unitaire\":nombre,"
                  "\"confiance\":\"haute\"|\"moyenne\"|\"faible\"}]}")
         cb  (if (= media-type "application/pdf")
               {:type "document" :source {:type "base64" :media_type media-type :data base64}}
               {:type "image"    :source {:type "base64" :media_type media-type :data base64}})]
     {:db (assoc db :ai-parsing true :ai-error nil)
      :call-anthropic
      {:payload    {:model "claude-sonnet-4-20250514" :max_tokens 1000 :system sys
                    :messages [{:role "user" :content [cb {:type "text" :text "Extrais les informations."}]}]}
       :on-success [::ai-ok doc-type]
       :on-error   [::ai-err]}})))

(rf/reg-event-fx
 ::ai-ok
 (fn [_ [_ doc-type response]]
   (try
     (let [text  (-> response :content first :text)
           clean (-> text (str/replace #"```json|```" "") str/trim)
           data  (js->clj (js/JSON.parse clean) :keywordize-keys true)]
       {:dispatch [:ai-parse-success data doc-type]})
     (catch :default _
       {:dispatch-n [[:set-ai-parsing false]
                     [:set-ai-error "Impossible d'analyser la réponse. Vérifiez le document."]]}))))

(rf/reg-event-db
 ::ai-err
 (fn [db _]
   (-> db (assoc :ai-parsing false)
          (assoc :ai-error "Erreur réseau. Vérifiez votre connexion."))))

;; ── Gestion utilisateurs (admin) ─────────────────────────────────────────
(rf/reg-event-db :set-show-user-modal (fn [db [_ v]] (assoc db :show-user-modal v)))
(rf/reg-event-db :update-user-form    (fn [db [_ k v]] (assoc-in db [:user-form k] v)))
