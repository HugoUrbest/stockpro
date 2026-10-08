(ns app.views
  (:require [re-frame.core :as rf]
            [app.db :as db]
            [app.subs :as subs]
            [clojure.string :as str]))

;; ═══════════════════════════════════════════════════════
;; UTILITAIRES
;; ═══════════════════════════════════════════════════════
(defn fmt [n]
  (.toLocaleString (or n 0) "fr-FR" #js{:minimumFractionDigits 2 :maximumFractionDigits 2}))

(defn fmt-date [d]
  (when (seq d) (.toLocaleDateString (js/Date. d) "fr-FR")))

(def status-cfg
  {"brouillon"             {:label "Brouillon"           :color "#94a3b8" :bg "#1e293b"}
   "en-attente-validation" {:label "En attente valid."   :color "#f59e0b" :bg "#451a03"}
   "validé"               {:label "Validé"              :color "#34d399" :bg "#064e3b"}
   "reçu"                 {:label "Reçu"                :color "#60a5fa" :bg "#1e3a5f"}
   "annulé"               {:label "Annulé"              :color "#f87171" :bg "#450a0a"}})

;; Styles partagés
(def card  {:background "#111827" :border "1px solid #1f2937" :border-radius 12 :padding 20})
(def s-in  {:background "#0f172a" :border "1px solid #1f2937" :border-radius 6
            :padding "8px 10px" :color "#e2e8f0" :font-size 13 :width "100%" :box-sizing "border-box"})
(def s-sel (assoc s-in :width "100%"))
(def btn-p {:background "linear-gradient(135deg,#4f46e5,#7c3aed)" :border "none"
            :border-radius 7 :padding "9px 18px" :color "#fff" :font-size 13
            :font-weight 600 :cursor "pointer"})
(def btn-s {:background "#1e293b" :border "1px solid #334155" :border-radius 7
            :padding "9px 18px" :color "#94a3b8" :font-size 13 :cursor "pointer"})
(def btn-g {:background "linear-gradient(135deg,#065f46,#059669)" :border "none"
            :border-radius 7 :padding "9px 18px" :color "#fff" :font-size 13
            :font-weight 600 :cursor "pointer"})

(defn nom-site [sid] (:nom (first (filter #(= (:id %) sid) db/sites))))
(defn nom-produit [pid] (:nom (first (filter #(= (:id %) pid) db/produits))))
(defn nom-user [uid] (:nom (first (filter #(= (:id %) uid) db/utilisateurs))))

;; ═══════════════════════════════════════════════════════
;; PAGE DE CONNEXION
;; ═══════════════════════════════════════════════════════
(defn login-page []
  (let [form @(rf/subscribe [:login-form])]
    [:div {:style {:min-height "100vh" :display "flex" :align-items "center"
                   :justify-content "center" :background "#0a0c10"}}
     [:div {:style {:width 420 :animation "fadeIn .4s ease"}}
      ;; Logo
      [:div {:style {:text-align "center" :margin-bottom 40}}
       [:div {:style {:width 64 :height 64 :border-radius 16 :margin "0 auto 16px"
                      :background "linear-gradient(135deg,#4f46e5,#7c3aed)"
                      :display "flex" :align-items "center" :justify-content "center" :font-size 28}}
        "📦"]
       [:h1 {:style {:font-size 26 :font-weight 700 :color "#fff" :letter-spacing "-0.5px"}} "StockPro"]
       [:p {:style {:font-size 13 :color "#64748b" :margin-top 4}} "Gestion de stocks multi-sites"]]

      ;; Formulaire
      [:div {:style (merge card {:padding 32})}
       [:h2 {:style {:font-size 16 :font-weight 600 :margin-bottom 20 :color "#e2e8f0"}} "Connexion"]
       [:div {:style {:margin-bottom 16}}
        [:label {:style {:font-size 11 :color "#64748b" :display "block" :margin-bottom 6
                         :letter-spacing "0.8px" :text-transform "uppercase"}} "Sélectionner un compte"]
        [:select {:value     (:user-id form)
                  :on-change #(rf/dispatch [:update-login-form :user-id (-> % .-target .-value)])
                  :style s-sel}
         [:option {:value ""} "-- Choisir un utilisateur --"]
         (for [u db/utilisateurs]
           ^{:key (:id u)}
           [:option {:value (:id u)}
            (str (:nom u) " · " (get-in db/roles [(:role u) :label]))])]]
       (when (:error form)
         [:div {:style {:color "#f87171" :font-size 12 :margin-bottom 12}} (:error form)])
       [:button {:on-click #(rf/dispatch [:login (:user-id form)])
                 :style (assoc btn-p :width "100%" :padding "11px 0" :font-size 14)}
        "Se connecter"]

       ;; Légende des rôles
       [:div {:style {:margin-top 24 :padding-top 20 :border-top "1px solid #1f2937"}}
        [:p {:style {:font-size 11 :color "#475569" :margin-bottom 12 :text-transform "uppercase" :letter-spacing 1}} "Rôles disponibles"]
        (for [[role-key cfg] db/roles]
          ^{:key role-key}
          [:div {:style {:display "flex" :align-items "center" :gap 8 :margin-bottom 6}}
           [:span {:style {:width 8 :height 8 :border-radius "50%" :background (:color cfg) :flex-shrink 0}}]
           [:span {:style {:font-size 12 :color (:color cfg) :font-weight 500}} (:label cfg)]
           [:span {:style {:font-size 11 :color "#334155"}}
            (case role-key
              :admin "· Accès complet tous sites"
              :gestionnaire "· Valider BC, clôturer inventaire"
              :magasinier "· Saisir mouvements, importer docs"
              :lecteur "· Consultation uniquement"
              "")]])]]]]))

;; ═══════════════════════════════════════════════════════
;; BARRE SUPÉRIEURE
;; ═══════════════════════════════════════════════════════
(defn topbar []
  (let [user        @(rf/subscribe [:session-user])
        tab         @(rf/subscribe [:tab])
        site-actif  @(rf/subscribe [:site-actif])
        sites-vis   @(rf/subscribe [:sites-visibles])
        val-tot     @(rf/subscribe [:val-totale])
        tabs        (cond-> ["Tableau de bord" "Catalogue" "Mouvements" "Achats" "Inventaire" "Valorisation"]
                      (db/peut? user :gerer-utilisateurs) (conj "Utilisateurs"))]
    [:div {:style {:background "#0d1117" :border-bottom "1px solid #161b22"
                   :position "sticky" :top 0 :z-index 100}}
     ;; Ligne haute
     [:div {:style {:display "flex" :align-items "center" :justify-content "space-between"
                    :padding "12px 28px"}}
      ;; Logo
      [:div {:style {:display "flex" :align-items "center" :gap 12}}
       [:div {:style {:width 36 :height 36 :border-radius 9
                      :background "linear-gradient(135deg,#4f46e5,#7c3aed)"
                      :display "flex" :align-items "center" :justify-content "center" :font-size 17}}
        "📦"]
       [:div
        [:div {:style {:font-family "JetBrains Mono" :font-size 16 :font-weight 700 :color "#fff"}} "StockPro"]
        [:div {:style {:font-size 10 :color "#475569" :letter-spacing 1 :text-transform "uppercase"}} "Multi-sites"]]]

      ;; Centre : sélecteur de site
      [:div {:style {:display "flex" :align-items "center" :gap 10}}
       [:span {:style {:font-size 11 :color "#475569"}} "Site :"]
       (when (= (:role user) :admin)
         [:button {:on-click #(rf/dispatch [:set-site-actif nil])
                   :style {:padding "5px 12px" :border-radius 6 :border "1px solid" :font-size 12
                           :background (if (nil? site-actif) "#1e1b4b" "transparent")
                           :border-color (if (nil? site-actif) "#4f46e5" "#1f2937")
                           :color (if (nil? site-actif) "#a5b4fc" "#64748b")
                           :cursor "pointer"}} "Tous"])
       (for [s sites-vis]
         ^{:key (:id s)}
         [:button {:on-click #(rf/dispatch [:set-site-actif (:id s)])
                   :style {:padding "5px 12px" :border-radius 6 :border "1px solid" :font-size 12
                           :background (if (= site-actif (:id s)) "#0f2744" "transparent")
                           :border-color (if (= site-actif (:id s)) "#3b82f6" "#1f2937")
                           :color (if (= site-actif (:id s)) "#93c5fd" "#64748b")
                           :cursor "pointer"}} (:nom s)])]

      ;; Droite : valeur + user
      [:div {:style {:display "flex" :align-items "center" :gap 12}}
       [:div {:style {:background "#0f172a" :border "1px solid #1f2937" :border-radius 8
                      :padding "6px 14px" :font-size 12}}
        [:span {:style {:color "#64748b"}} "Stock "]
        [:span {:style {:color "#a5b4fc" :font-family "JetBrains Mono" :font-weight 600}}
         (str (fmt val-tot) " €")]]
       [:div {:style {:display "flex" :align-items "center" :gap 8 :cursor "pointer"}
              :on-click #(rf/dispatch [:logout])}
        [:div {:style {:width 32 :height 32 :border-radius "50%"
                       :background (get-in db/roles [(:role user) :bg] "#1e293b")
                       :border (str "1px solid " (get-in db/roles [(:role user) :color] "#334155"))
                       :display "flex" :align-items "center" :justify-content "center"
                       :font-size 12 :font-weight 700 :color (get-in db/roles [(:role user) :color] "#94a3b8")}}
         (str (first (:nom user)))]
        [:div
         [:div {:style {:font-size 12 :font-weight 500 :color "#e2e8f0"}} (:nom user)]
         [:div {:style {:font-size 10 :color (get-in db/roles [(:role user) :color] "#64748b")}}
          (get-in db/roles [(:role user) :label])]]
        [:span {:style {:font-size 10 :color "#334155"}} "↪ Déco"]]]]

     ;; Onglets
     [:div {:style {:display "flex" :gap 1 :padding "0 28px"}}
      (for [t tabs]
        ^{:key t}
        [:button {:on-click #(rf/dispatch [:set-tab t])
                  :style {:padding "9px 18px" :font-size 13 :font-weight 500
                          :border "none" :cursor "pointer" :border-radius "7px 7px 0 0"
                          :background (if (= t tab) "#111827" "transparent")
                          :color      (if (= t tab) "#a5b4fc" "#475569")
                          :border-bottom (if (= t tab) "2px solid #4f46e5" "2px solid transparent")}}
         t])]]))

;; ═══════════════════════════════════════════════════════
;; TABLEAU DE BORD
;; ═══════════════════════════════════════════════════════
(defn tableau-de-bord []
  (let [user      @(rf/subscribe [:session-user])
        stocks    @(rf/subscribe [:stocks])
        bons      @(rf/subscribe [:bons-visibles])
        val-site  @(rf/subscribe [:val-par-site])
        sites-vis @(rf/subscribe [:sites-visibles])]
    [:div
     [:h2 {:style {:margin "0 0 20px" :font-size 20 :font-weight 700}}
      (str "Bonjour, " (first (str/split (:nom user) #" ")) " 👋")]

     ;; KPIs
     [:div {:style {:display "grid" :grid-template-columns "repeat(4,1fr)" :gap 14 :margin-bottom 24}}
      ;; Total stock
      [:div {:style (merge card {:border-color "#1e1b4b"})}
       [:div {:style {:font-size 11 :color "#6366f1" :letter-spacing 1 :text-transform "uppercase" :margin-bottom 8}} "Valeur totale"]
       [:div {:style {:font-size 26 :font-weight 700 :font-family "JetBrains Mono" :color "#a5b4fc"}}
        (str (fmt (apply + (vals val-site))) " €")]
       [:div {:style {:font-size 11 :color "#475569" :margin-top 4}} (str (count db/produits) " références")]
      ]
      ;; BC en attente
      (let [en-att (count (filter #(= (:statut %) "en-attente-validation") bons))]
        [:div {:style (merge card (when (pos? en-att) {:border-color "#78350f"}))}
         [:div {:style {:font-size 11 :color "#f59e0b" :letter-spacing 1 :text-transform "uppercase" :margin-bottom 8}} "BC en attente"]
         [:div {:style {:font-size 26 :font-weight 700 :font-family "JetBrains Mono"
                        :color (if (pos? en-att) "#fbbf24" "#475569")}} en-att]
         [:div {:style {:font-size 11 :color "#475569" :margin-top 4}} "validation requise"]])
      ;; Ruptures
      (let [ruptures (count (for [p db/produits
                                  s sites-vis
                                  :when (zero? (get-in stocks [(:id s) (:id p)] 0))] p))]
        [:div {:style (merge card (when (pos? ruptures) {:border-color "#7f1d1d"}))}
         [:div {:style {:font-size 11 :color "#f87171" :letter-spacing 1 :text-transform "uppercase" :margin-bottom 8}} "Ruptures"]
         [:div {:style {:font-size 26 :font-weight 700 :font-family "JetBrains Mono"
                        :color (if (pos? ruptures) "#f87171" "#475569")}} ruptures]
         [:div {:style {:font-size 11 :color "#475569" :margin-top 4}} "articles à 0"]])
      ;; Sites
      [:div {:style card}
       [:div {:style {:font-size 11 :color "#34d399" :letter-spacing 1 :text-transform "uppercase" :margin-bottom 8}} "Sites gérés"]
       [:div {:style {:font-size 26 :font-weight 700 :font-family "JetBrains Mono" :color "#6ee7b7"}}
        (count sites-vis)]
       [:div {:style {:font-size 11 :color "#475569" :margin-top 4}} "établissements"]]]

     ;; Valeur par site
     [:div {:style {:display "grid" :grid-template-columns "repeat(3,1fr)" :gap 14 :margin-bottom 24}}
      (for [s sites-vis]
        ^{:key (:id s)}
        [:div {:style (merge card {:cursor "pointer"}
                             (when (= (:id s) @(rf/subscribe [:site-actif])) {:border-color "#4f46e5"}))
               :on-click #(rf/dispatch [:set-site-actif (:id s)])}
         [:div {:style {:font-size 12 :font-weight 600 :color "#e2e8f0" :margin-bottom 4}} (:nom s)]
         [:div {:style {:font-size 11 :color "#64748b" :margin-bottom 12}} (:ville s)]
         [:div {:style {:font-size 20 :font-weight 700 :font-family "JetBrains Mono" :color "#a5b4fc"}}
          (str (fmt (get val-site (:id s) 0)) " €")]
         [:div {:style {:font-size 10 :color "#334155" :margin-top 4}}
          (let [ruptures (count (filter #(zero? (get-in stocks [(:id s) (:id %) ] 0)) db/produits))]
            (if (pos? ruptures)
              [:span {:style {:color "#f87171"}} (str ruptures " rupture(s)")]
              [:span {:style {:color "#34d399"}} "Aucune rupture"]))]])]

     ;; Derniers BC
     [:div {:style card}
      [:div {:style {:font-size 13 :font-weight 600 :margin-bottom 14}} "Derniers bons de commande"]
      (for [bc (take 4 (reverse bons))]
        (let [cfg (get status-cfg (:statut bc) {})]
          ^{:key (:id bc)}
          [:div {:style {:display "flex" :justify-content "space-between" :align-items "center"
                         :padding "10px 0" :border-bottom "1px solid #1f2937"}}
           [:div
            [:span {:style {:font-family "JetBrains Mono" :font-size 12 :color "#6366f1"}} (str (:ref bc) " ")]
            [:span {:style {:font-size 12 :color "#94a3b8"}} (:fournisseur bc)]
            [:span {:style {:font-size 11 :color "#334155" :margin-left 8}} (nom-site (:site-id bc))]]
           [:div {:style {:display "flex" :align-items "center" :gap 10}}
            [:span {:style {:font-size 11 :color "#64748b"}} (fmt-date (:date bc))]
            [:span {:style {:font-size 11 :font-weight 600 :padding "3px 9px" :border-radius 20
                            :background (:bg cfg) :color (:color cfg)}}
             (:label cfg)]]]))]]))

;; ═══════════════════════════════════════════════════════
;; CATALOGUE
;; ═══════════════════════════════════════════════════════
(defn catalogue []
  (let [produits   @(rf/subscribe [:produits-filtres])
        stocks     @(rf/subscribe [:stocks])
        lots       @(rf/subscribe [:lots-site])
        methode    @(rf/subscribe [:methode])
        filter-cat @(rf/subscribe [:filter-cat])
        site-actif @(rf/subscribe [:site-actif])]
    [:div
     [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom 20}}
      [:h2 {:style {:margin 0 :font-size 20 :font-weight 700}} "Catalogue"]
      [:div {:style {:display "flex" :gap 6}}
       (for [c (cons "Toutes" db/categories)]
         ^{:key c}
         [:button {:on-click #(rf/dispatch [:set-filter-cat c])
                   :style {:padding "5px 12px" :font-size 11 :border-radius 6 :cursor "pointer"
                           :background (if (= c filter-cat) "#1e1b4b" "transparent")
                           :border (str "1px solid " (if (= c filter-cat) "#4f46e5" "#1f2937"))
                           :color (if (= c filter-cat) "#a5b4fc" "#475569")}}
          c])]]

     [:div {:style {:display "grid" :grid-template-columns "repeat(auto-fill,minmax(260px,1fr))" :gap 12}}
      (for [p produits]
        (let [sid (or site-actif "")
              ;; Si pas de site actif, sommer tous les stocks
              qte (if site-actif
                    (get-in stocks [site-actif (:id p)] 0)
                    (reduce + 0 (map #(get-in stocks [(:id %) (:id p)] 0) db/sites)))
              alerte  (and (<= qte (:seuil p)) (pos? qte))
              rupture (zero? qte)
              cmup-v  (subs/cmup lots (or site-actif (-> db/sites first :id)) (:id p))
              val     (subs/produit-val lots methode (or site-actif (-> db/sites first :id)) (:id p) qte)]
          ^{:key (:id p)}
          [:div {:style (merge card {:position "relative" :overflow "hidden"
                                     :border-color (cond rupture "#7f1d1d" alerte "#78350f" :else "#1f2937")})}
           (when (or alerte rupture)
             [:div {:style {:position "absolute" :top 0 :left 0 :right 0 :height 3
                            :background (if rupture "#ef4444" "#f59e0b")}}])
           [:div {:style {:display "flex" :justify-content "space-between" :align-items "flex-start" :margin-bottom 10}}
            [:div
             [:div {:style {:font-size 9 :color "#4f46e5" :font-family "JetBrains Mono" :letter-spacing 1 :margin-bottom 3}} (:ref p)]
             [:div {:style {:font-weight 600 :font-size 13 :line-height 1.3 :color "#e2e8f0"}} (:nom p)]]
            [:span {:style {:font-size 9 :background "#0f172a" :border-radius 4 :padding "3px 7px" :color "#475569"}}
             (:cat p)]]
           [:div {:style {:display "flex" :justify-content "space-between" :align-items "center"
                          :padding-top 10 :margin-top 10 :border-top "1px solid #1f2937"}}
            [:div
             [:div {:style {:font-size 22 :font-weight 700 :font-family "JetBrains Mono"
                            :color (cond rupture "#ef4444" alerte "#f59e0b" :else "#34d399")}} qte]
             [:div {:style {:font-size 10 :color "#475569"}} (str (:unite p) " · seuil " (:seuil p))]]
            [:div {:style {:text-align "right"}}
             [:div {:style {:font-size 13 :font-weight 600 :color "#a5b4fc"}} (str (fmt val) " €")]
             [:div {:style {:font-size 10 :color "#334155"}} (str "CMUP " (fmt cmup-v) " €")]]]
           (when rupture [:div {:style {:margin-top 8 :font-size 10 :color "#f87171" :font-weight 600}} "⚠ RUPTURE"])
           (when alerte  [:div {:style {:margin-top 8 :font-size 10 :color "#f59e0b"}} "⚠ Seuil d'alerte"])]))]]))

;; ═══════════════════════════════════════════════════════
;; MOUVEMENTS
;; ═══════════════════════════════════════════════════════
(defn mouvements []
  (let [user     @(rf/subscribe [:session-user])
        show-mvt @(rf/subscribe [:show-mvt])
        form     @(rf/subscribe [:mvt-form])
        lots     @(rf/subscribe [:lots-site])]
    [:div
     [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom 20}}
      [:h2 {:style {:margin 0 :font-size 20 :font-weight 700}} "Mouvements de stock"]
      (when (db/peut? user :saisir-mvt)
        [:button {:on-click #(rf/dispatch [:set-show-mvt true]) :style btn-p} "+ Nouveau mouvement"])]

     (when show-mvt
       [:div {:style (merge card {:margin-bottom 20})}
        [:h3 {:style {:margin "0 0 16px" :font-size 14}} "Saisir un mouvement"]
        [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap 12}}
         [:div
          [:label {:style {:font-size 10 :color "#475569" :display "block" :margin-bottom 4 :text-transform "uppercase"}} "Type"]
          [:select {:value (:type form) :on-change #(rf/dispatch [:update-mvt-form :type (-> % .-target .-value)]) :style s-sel}
           [:option {:value "entrée"} "Entrée"] [:option {:value "sortie"} "Sortie"]]]
         [:div
          [:label {:style {:font-size 10 :color "#475569" :display "block" :margin-bottom 4 :text-transform "uppercase"}} "Article"]
          [:select {:value (:produit-id form) :on-change #(rf/dispatch [:update-mvt-form :produit-id (-> % .-target .-value)]) :style s-sel}
           [:option {:value ""} "-- Sélectionner --"]
           (for [p db/produits] ^{:key (:id p)} [:option {:value (:id p)} (str (:ref p) " – " (:nom p))])]]
         [:div
          [:label {:style {:font-size 10 :color "#475569" :display "block" :margin-bottom 4 :text-transform "uppercase"}} "Quantité"]
          [:input {:type "number" :value (:qte form) :placeholder "0"
                   :on-change #(rf/dispatch [:update-mvt-form :qte (-> % .-target .-value)]) :style s-in}]]
         (when (= (:type form) "entrée")
           [:div
            [:label {:style {:font-size 10 :color "#475569" :display "block" :margin-bottom 4 :text-transform "uppercase"}} "Prix unitaire HT (€)"]
            [:input {:type "number" :value (:pu form) :placeholder "0.00"
                     :on-change #(rf/dispatch [:update-mvt-form :pu (-> % .-target .-value)]) :style s-in}]])
         [:div
          [:label {:style {:font-size 10 :color "#475569" :display "block" :margin-bottom 4 :text-transform "uppercase"}} "Référence"]
          [:input {:type "text" :value (:ref form) :placeholder "BC-2025-XXX"
                   :on-change #(rf/dispatch [:update-mvt-form :ref (-> % .-target .-value)]) :style s-in}]]]
        [:div {:style {:display "flex" :gap 10 :margin-top 16}}
         [:button {:on-click #(rf/dispatch [:ajouter-mouvement]) :style btn-p} "Enregistrer"]
         [:button {:on-click #(rf/dispatch [:set-show-mvt false]) :style btn-s} "Annuler"]]])

     [:div {:style (merge card {:padding 0 :overflow "hidden"})}
      [:table {:style {:width "100%" :font-size 12}}
       [:thead [:tr {:style {:background "#0d1117" :border-bottom "1px solid #1f2937"}}
                (for [h ["Site" "Réf. bon" "Article" "Date" "Qté init." "Qté rest." "P.U." "Valeur"]]
                  ^{:key h} [:th {:style {:padding "11px 14px" :text-align "left" :color "#475569"
                                          :font-weight 500 :font-size 10 :text-transform "uppercase"}} h])]]
       [:tbody
        (for [[i lot] (map-indexed vector (reverse lots))]
          (let [p (first (filter #(= (:id %) (:produit-id lot)) db/produits))]
            ^{:key (:id lot)}
            [:tr {:style {:border-bottom "1px solid #111827" :background (if (even? i) "transparent" "#0d1117")}}
             [:td {:style {:padding "9px 14px" :color "#64748b" :font-size 11}} (nom-site (:site-id lot))]
             [:td {:style {:padding "9px 14px" :color "#4f46e5" :font-family "JetBrains Mono" :font-size 11}} (:ref lot)]
             [:td {:style {:padding "9px 14px"}} (:nom p)]
             [:td {:style {:padding "9px 14px" :color "#64748b"}} (fmt-date (:date lot))]
             [:td {:style {:padding "9px 14px" :font-family "JetBrains Mono"}} (:qte lot)]
             [:td {:style {:padding "9px 14px" :font-family "JetBrains Mono"
                           :color (if (zero? (:qte-rest lot)) "#334155" "#34d399")}} (:qte-rest lot)]
             [:td {:style {:padding "9px 14px" :font-family "JetBrains Mono"}} (str (fmt (:pu lot)) " €")]
             [:td {:style {:padding "9px 14px" :font-family "JetBrains Mono" :color "#a5b4fc"}}
              (str (fmt (* (:qte-rest lot) (:pu lot))) " €")]]))]]]]))

;; ═══════════════════════════════════════════════════════
;; ACHATS (BC + Import IA)
;; ═══════════════════════════════════════════════════════
(defn achats []
  (let [user       @(rf/subscribe [:session-user])
        show-bc    @(rf/subscribe [:show-bc])
        bc-form    @(rf/subscribe [:bc-form])
        bons       @(rf/subscribe [:bons-visibles])
        ai-parsing @(rf/subscribe [:ai-parsing])
        ai-error   @(rf/subscribe [:ai-error])
        ai-preview @(rf/subscribe [:ai-preview])
        site-actif @(rf/subscribe [:site-actif])
        file-ref   (atom nil)
        doc-type   (atom :bl)]

    [:div
     [:style "@keyframes fadeIn{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:translateY(0)}}"]

     [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom 20}}
      [:h2 {:style {:margin 0 :font-size 20 :font-weight 700}} "Bons de commande"]
      [:div {:style {:display "flex" :gap 8}}
       ;; Input fichier caché
       [:input {:type "file" :accept ".pdf,image/*" :ref #(reset! file-ref %)
                :style {:display "none"}
                :on-change (fn [e]
                             (let [file (-> e .-target .-files (aget 0))]
                               (when file
                                 (let [r (js/FileReader.)]
                                   (set! (.-onload r)
                                         #(let [res (-> % .-target .-result)
                                                b64 (second (str/split res #","))
                                                mt  (.-type file)]
                                            (rf/dispatch [:parse-document {:base64 b64 :media-type mt :doc-type @doc-type}])))
                                   (.readAsDataURL r file)))
                               (set! (.-value (.-target e)) "")))}]

       ;; Sélecteur type document
       (when (db/peut? user :importer-doc)
         [:select {:value (name @doc-type)
                   :on-change #(reset! doc-type (keyword (-> % .-target .-value)))
                   :style (merge s-sel {:width "auto" :padding "7px 10px" :font-size 12})}
          [:option {:value "bl"} "Bon de livraison"]
          [:option {:value "facture"} "Facture"]
          [:option {:value "bc-interne"} "BC interne"]])

       (when (db/peut? user :importer-doc)
         [:button {:on-click #(when @file-ref (.click @file-ref))
                   :disabled ai-parsing
                   :style (merge {:background (if ai-parsing "#1e293b" "linear-gradient(135deg,#0369a1,#0284c7)")
                                  :border "none" :border-radius 7 :padding "9px 16px"
                                  :color (if ai-parsing "#475569" "#fff") :font-size 13 :font-weight 600
                                  :cursor (if ai-parsing "not-allowed" "pointer")
                                  :display "flex" :align-items "center" :gap 8})}
          (if ai-parsing
            [:<> [:span {:class "spinner"}] "Analyse…"]
            [:<> "🤖" " Importer"])])

       (when (db/peut? user :creer-bc)
         [:button {:on-click #(rf/dispatch [:set-show-bc true]) :style btn-p} "+ Nouveau BC"])]]

     ;; Erreur IA
     (when ai-error
       [:div {:style {:background "#450a0a" :border "1px solid #7f1d1d" :border-radius 8
                      :padding "10px 16px" :margin-bottom 16 :font-size 12 :color "#fca5a5"
                      :display "flex" :justify-content "space-between"}}
        [:span (str "⚠ " ai-error)]
        [:button {:on-click #(rf/dispatch [:clear-ai-error])
                  :style {:background "none" :border "none" :color "#fca5a5" :cursor "pointer"}} "✕"]])

     ;; Formulaire BC
     (when show-bc
       [:div {:style (merge card {:margin-bottom 20
                                  :border-color (if ai-preview "#0369a1" "#1f2937")
                                  :animation "fadeIn .3s ease"})}
        (when ai-preview
          [:div {:style {:background "#0c2d48" :border "1px solid #0369a1" :border-radius 7
                         :padding "9px 14px" :margin-bottom 16 :display "flex"
                         :justify-content "space-between" :align-items "center"}}
           [:span {:style {:font-size 12 :color "#7dd3fc"}}
            [:strong "🤖 Document importé"] " — Vérifiez et corrigez avant de valider."]
           [:div {:style {:display "flex" :gap 8 :font-size 10}}
            [:span {:style {:color "#34d399"}} "● Haute"]
            [:span {:style {:color "#f59e0b"}} "● Moyenne"]
            [:span {:style {:color "#f87171"}} "● Faible"]]])

        [:h3 {:style {:margin "0 0 14px" :font-size 14}} (if ai-preview "BC importé" "Nouveau BC")]

        ;; Site + fournisseur + date
        [:div {:style {:display "grid" :grid-template-columns "1fr 1fr 1fr" :gap 10 :margin-bottom 14}}
         (when (= (:role user) :admin)
           [:div
            [:label {:style {:font-size 10 :color "#475569" :display "block" :margin-bottom 4 :text-transform "uppercase"}} "Site"]
            [:select {:value (or (:site-id bc-form) "")
                      :on-change #(rf/dispatch [:update-bc-form :site-id (-> % .-target .-value)])
                      :style s-sel}
             [:option {:value ""} "-- Site --"]
             (for [s db/sites] ^{:key (:id s)} [:option {:value (:id s)} (:nom s)])]])
         [:div
          [:label {:style {:font-size 10 :color "#475569" :display "block" :margin-bottom 4 :text-transform "uppercase"}} "Fournisseur"]
          [:input {:type "text" :value (:fournisseur bc-form) :placeholder "Nom fournisseur"
                   :on-change #(rf/dispatch [:update-bc-form :fournisseur (-> % .-target .-value)]) :style s-in}]]
         [:div
          [:label {:style {:font-size 10 :color "#475569" :display "block" :margin-bottom 4 :text-transform "uppercase"}} "Date"]
          [:input {:type "date" :value (:date bc-form)
                   :on-change #(rf/dispatch [:update-bc-form :date (-> % .-target .-value)]) :style s-in}]]]

        ;; Lignes
        [:div {:style {:margin-bottom 12}}
         [:label {:style {:font-size 10 :color "#475569" :text-transform "uppercase" :display "block" :margin-bottom 8}} "Lignes"]
         (for [[i ligne] (map-indexed vector (:lignes bc-form))]
           (let [cc ({"haute" "#34d399" "moyenne" "#f59e0b" "faible" "#f87171"} (:_confiance ligne))]
             ^{:key i}
             [:div {:style {:margin-bottom 8}}
              (when (and ai-preview (:_designation ligne))
                [:div {:style {:font-size 10 :color "#475569" :margin-bottom 3 :display "flex" :align-items "center" :gap 5}}
                 (when cc [:span {:style {:width 6 :height 6 :border-radius "50%" :background cc :display "inline-block"}}])
                 "Lu : " [:em {:style {:color "#64748b"}} (str "\"" (:_designation ligne) "\""  )]
                 (when (:_produit-nom ligne) [:span {:style {:color "#6366f1"}} (str " → " (:_produit-nom ligne))])
                 (when (not (seq (:produit-id ligne))) [:span {:style {:color "#f59e0b"}} " ⚠ À sélectionner"])])
              [:div {:style {:display "grid" :grid-template-columns "2fr 1fr 1fr auto" :gap 7}}
               [:select {:value (:produit-id ligne)
                         :on-change #(rf/dispatch [:update-bc-ligne i :produit-id (-> % .-target .-value)])
                         :style (merge s-sel (when (and ai-preview (not (seq (:produit-id ligne))))
                                               {:background "#1c0f02" :border-color "#78350f"}))}
                [:option {:value ""} "Article…"]
                (for [p db/produits] ^{:key (:id p)} [:option {:value (:id p)} (:nom p)])]
               [:input {:type "number" :placeholder "Qté" :value (:qte ligne)
                        :on-change #(rf/dispatch [:update-bc-ligne i :qte (-> % .-target .-value)])
                        :style (assoc s-in :width "auto")}]
               [:input {:type "number" :placeholder "P.U. HT" :value (:pu ligne)
                        :on-change #(rf/dispatch [:update-bc-ligne i :pu (-> % .-target .-value)])
                        :style (assoc s-in :width "auto")}]
               [:button {:on-click #(rf/dispatch [:remove-bc-ligne i])
                         :style {:background "#450a0a" :border "none" :border-radius 6
                                 :color "#fca5a5" :padding "0 11px" :cursor "pointer"}} "✕"]]]))
         [:button {:on-click #(rf/dispatch [:add-bc-ligne])
                   :style {:background "#0f172a" :border "1px dashed #1f2937" :border-radius 6
                           :padding "6px 14px" :color "#475569" :font-size 11 :cursor "pointer"}}
          "+ Ligne"]]
        [:div {:style {:display "flex" :gap 10}}
         [:button {:on-click #(rf/dispatch [:creer-bc]) :style btn-p} "Créer le BC"]
         [:button {:on-click #(rf/dispatch [:set-show-bc false]) :style btn-s} "Annuler"]]])

     ;; Liste des bons
     [:div {:style {:display "flex" :flex-direction "column" :gap 10}}
      (for [bc (reverse bons)]
        (let [cfg   (get status-cfg (:statut bc) {})
              total (reduce #(+ %1 (* (:qte %2) (:pu %2))) 0 (:lignes bc))]
          ^{:key (:id bc)}
          [:div {:style card}
           [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom 12}}
            [:div {:style {:display "flex" :gap 14 :align-items "center"}}
             [:span {:style {:font-family "JetBrains Mono" :font-size 13 :font-weight 700 :color "#a5b4fc"}} (:ref bc)]
             [:span {:style {:font-size 12 :color "#94a3b8"}} (:fournisseur bc)]
             [:span {:style {:font-size 11 :color "#334155"}} (nom-site (:site-id bc))]
             [:span {:style {:font-size 11 :color "#475569"}} (fmt-date (:date bc))]]
            [:div {:style {:display "flex" :gap 8 :align-items "center"}}
             ;; Créé par / validé par
             [:div {:style {:font-size 10 :color "#334155"}}
              (str "par " (nom-user (:cree-par bc)))
              (when (:valide-par bc) (str " · validé par " (nom-user (:valide-par bc))))]
             [:span {:style {:font-size 11 :font-weight 600 :padding "3px 9px" :border-radius 20
                             :background (:bg cfg) :color (:color cfg)}} (:label cfg)]
             [:span {:style {:font-size 13 :font-weight 700 :color "#e2e8f0"}} (str (fmt total) " € HT")]
             ;; Actions selon statut et rôle
             (when (and (= (:statut bc) "en-attente-validation") (db/peut? user :valider-bc))
               [:<>
                [:button {:on-click #(rf/dispatch [:valider-bc (:id bc)]) :style btn-g} "✓ Valider"]
                [:button {:on-click #(rf/dispatch [:rejeter-bc (:id bc)])
                          :style {:background "#450a0a" :border "none" :border-radius 7
                                  :padding "7px 14px" :color "#fca5a5" :font-size 12 :cursor "pointer"}}
                 "✕ Rejeter"]])
             (when (and (= (:statut bc) "validé") (db/peut? user :receptionner-bc))
               [:button {:on-click #(rf/dispatch [:receptionner-bc (:id bc)]) :style btn-g}
                "📦 Réceptionner"])]]
           [:div {:style {:display "flex" :gap 16 :flex-wrap "wrap"}}
            (for [l (:lignes bc)]
              ^{:key (:produit-id l)}
              [:span {:style {:font-size 11 :background "#0f172a" :border "1px solid #1f2937"
                              :border-radius 5 :padding "3px 9px" :color "#94a3b8"}}
               (str (nom-produit (:produit-id l)) " × " (:qte l) " · " (fmt (:pu l)) " €")])]]))]]  ))

;; ═══════════════════════════════════════════════════════
;; INVENTAIRE
;; ═══════════════════════════════════════════════════════
(defn inventaire []
  (let [user       @(rf/subscribe [:session-user])
        show-inv   @(rf/subscribe [:show-inventaire])
        inv-data   @(rf/subscribe [:inventaire-data])
        inv-valide @(rf/subscribe [:inventaire-valide])
        stocks     @(rf/subscribe [:stocks])
        lots       @(rf/subscribe [:lots-site])
        methode    @(rf/subscribe [:methode])
        val-tot    @(rf/subscribe [:val-totale])
        site-actif @(rf/subscribe [:site-actif])
        site-label (or (nom-site site-actif) "tous les sites")]
    [:div
     [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom 20}}
      [:div
       [:h2 {:style {:margin 0 :font-size 20 :font-weight 700}} "Inventaire de fin d'exercice"]
       [:p {:style {:margin "3px 0 0" :font-size 12 :color "#475569"}}
        (str "Site : " site-label " · Méthode : " methode)]]
      (when (and (not show-inv) (db/peut? user :lancer-inventaire))
        [:button {:on-click #(rf/dispatch [:lancer-inventaire]) :style btn-g}
         "🗂 Lancer l'inventaire"])]

     (if show-inv
       [:div
        [:div {:style {:background "#064e3b22" :border "1px solid #065f46" :border-radius 8
                       :padding "10px 16px" :margin-bottom 18 :font-size 12 :color "#6ee7b7"}}
         "Saisissez les quantités réelles comptées. Les écarts seront ajustés automatiquement à la clôture."]

        [:div {:style (merge card {:padding 0 :overflow "hidden" :margin-bottom 14})}
         [:table {:style {:width "100%" :font-size 12}}
          [:thead [:tr {:style {:background "#0d1117" :border-bottom "1px solid #1f2937"}}
                   (for [h ["Réf." "Article" "Catégorie" "Théorique" "Compté" "Écart" "Valeur théo."]]
                     ^{:key h} [:th {:style {:padding "10px 14px" :text-align "left" :color "#475569"
                                             :font-weight 500 :font-size 10 :text-transform "uppercase"}} h])]]
          [:tbody
           (for [[i p] (map-indexed vector db/produits)]
             (let [sid       (or site-actif (-> db/sites first :id))
                   theorique (get-in stocks [sid (:id p)] 0)
                   compte    (js/parseFloat (get inv-data (:id p) theorique))
                   ecart     (- compte theorique)
                   valeur    (subs/produit-val lots methode sid (:id p) theorique)]
               ^{:key (:id p)}
               [:tr {:style {:border-bottom "1px solid #111827" :background (if (even? i) "transparent" "#0d1117")}}
                [:td {:style {:padding "9px 14px" :color "#4f46e5" :font-family "JetBrains Mono" :font-size 10}} (:ref p)]
                [:td {:style {:padding "9px 14px" :font-weight 500}} (:nom p)]
                [:td {:style {:padding "9px 14px" :color "#475569"}} (:cat p)]
                [:td {:style {:padding "9px 14px" :font-family "JetBrains Mono" :text-align "right"}} theorique]
                [:td {:style {:padding "9px 14px" :text-align "right"}}
                 [:input {:type "number" :value (get inv-data (:id p) theorique)
                          :disabled inv-valide
                          :on-change #(rf/dispatch [:update-inventaire-data (:id p) (-> % .-target .-value)])
                          :style {:width 65 :background "#0f172a" :border "1px solid #1f2937"
                                  :border-radius 5 :padding "4px 7px" :color "#e2e8f0"
                                  :font-size 12 :text-align "right"}}]]
                [:td {:style {:padding "9px 14px" :text-align "right" :font-family "JetBrains Mono" :font-weight 600
                              :color (cond (pos? ecart) "#34d399" (neg? ecart) "#f87171" :else "#334155")}}
                 (str (if (pos? ecart) "+" "") (if (js/isNaN ecart) "—" ecart))]
                [:td {:style {:padding "9px 14px" :text-align "right" :font-family "JetBrains Mono" :color "#a5b4fc"}}
                 (str (fmt valeur) " €")]]))]
          [:tfoot [:tr {:style {:background "#0d1117" :border-top "2px solid #4f46e5"}}
                   [:td {:col-span 6 :style {:padding "11px 14px" :font-weight 700}} "TOTAL"]
                   [:td {:style {:padding "11px 14px" :text-align "right" :font-family "JetBrains Mono"
                                 :color "#a5b4fc" :font-weight 700 :font-size 14}}
                    (str (fmt val-tot) " €")]]]]]

        (if inv-valide
          [:div {:style {:background "#064e3b22" :border "1px solid #065f46" :border-radius 10
                         :padding 20 :text-align "center"}}
           [:div {:style {:font-size 28 :margin-bottom 8}} "✅"]
           [:div {:style {:font-size 15 :font-weight 700 :color "#6ee7b7"}} "Inventaire clôturé"]
           [:div {:style {:font-size 12 :color "#475569" :margin-top 4}}
            "Valorisation finale : " [:strong {:style {:color "#a5b4fc"}} (str (fmt val-tot) " €")]]]
          (when (db/peut? user :cloturer-inventaire)
            [:div {:style {:display "flex" :gap 10}}
             [:button {:on-click #(rf/dispatch [:valider-inventaire]) :style btn-g}
              "✓ Clôturer l'inventaire"]
             [:button {:on-click #(rf/dispatch [:set-show-inventaire false]) :style btn-s} "Annuler"]]))]

       ;; Vue résumé
       [:div {:style {:display "grid" :grid-template-columns "repeat(3,1fr)" :gap 12}}
        (for [cat db/categories]
          (let [prods  (filter #(= (:cat %) cat) db/produits)
                sid    (or site-actif (-> db/sites first :id))
                val    (reduce #(+ %1 (subs/produit-val lots methode sid (:id %2)
                                                        (get-in stocks [sid (:id %2)] 0)))
                               0 prods)
                ruptures (count (filter #(zero? (get-in stocks [sid (:id %) ] 0)) prods))]
            ^{:key cat}
            [:div {:style card}
             [:div {:style {:font-size 11 :color "#64748b" :margin-bottom 8}} cat]
             [:div {:style {:font-size 20 :font-weight 700 :font-family "JetBrains Mono" :color "#a5b4fc"}}
              (str (fmt val) " €")]
             [:div {:style {:font-size 10 :color "#334155" :margin-top 4}}
              (str (count prods) " art. · ")
              (if (pos? ruptures)
                [:span {:style {:color "#f87171"}} (str ruptures " rupture(s)")]
                [:span {:style {:color "#34d399"}} "Aucune rupture"])]]))])]  ))

;; ═══════════════════════════════════════════════════════
;; VALORISATION
;; ═══════════════════════════════════════════════════════
(defn valorisation []
  (let [lots     @(rf/subscribe [:lots-site])
        stocks   @(rf/subscribe [:stocks])
        methode  @(rf/subscribe [:methode])
        val-tot  @(rf/subscribe [:val-totale])
        site-actif @(rf/subscribe [:site-actif])]
    [:div
     [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom 20}}
      [:h2 {:style {:margin 0 :font-size 20 :font-weight 700}} "Valorisation comptable"]
      [:div {:style {:display "flex" :gap 7}}
       (for [m ["FIFO" "CMUP"]]
         ^{:key m}
         [:button {:on-click #(rf/dispatch [:set-methode m])
                   :style {:padding "7px 18px" :font-size 12 :font-weight 600 :border "1px solid"
                           :border-radius 7 :cursor "pointer"
                           :background   (if (= m methode) "#1e1b4b" "transparent")
                           :border-color (if (= m methode) "#4f46e5" "#1f2937")
                           :color        (if (= m methode) "#a5b4fc" "#475569")}} m])]]

     ;; Carte totale
     [:div {:style (merge card {:border-color "#1e1b4b" :margin-bottom 20
                                :display "flex" :justify-content "space-between" :align-items "center"})}
      [:div
       [:div {:style {:font-size 10 :color "#6366f1" :letter-spacing 1 :text-transform "uppercase" :margin-bottom 6}}
        (str "Valeur stock — " methode)]
       [:div {:style {:font-size 38 :font-weight 700 :font-family "JetBrains Mono" :color "#fff"}}
        (str (fmt val-tot) " €")]
       [:div {:style {:font-size 11 :color "#475569" :margin-top 4}}
        (str "HT · " (count db/produits) " références")]]
      ;; Répartition par catégorie
      [:div
       (for [cat db/categories]
         (let [sid  (or site-actif (-> db/sites first :id))
               val  (reduce #(+ %1 (subs/produit-val lots methode sid (:id %2)
                                                      (get-in stocks [sid (:id %2)] 0)))
                            0 (filter #(= (:cat %) cat) db/produits))
               pct  (if (pos? val-tot) (* (/ val val-tot) 100) 0)]
           ^{:key cat}
           [:div {:style {:display "flex" :justify-content "space-between" :gap 20 :margin-bottom 3}}
            [:span {:style {:font-size 11 :color "#64748b"}} cat]
            [:span {:style {:font-size 11 :font-family "JetBrains Mono" :color "#a5b4fc"}}
             (str (fmt val) " € ") [:span {:style {:color "#334155"}} (str "(" (.toFixed pct 1) "%)")]]]))]]

     ;; Tableau
     [:div {:style (merge card {:padding 0 :overflow "hidden"})}
      [:table {:style {:width "100%" :font-size 12}}
       [:thead [:tr {:style {:background "#0d1117" :border-bottom "1px solid #1f2937"}}
                (for [h ["Réf." "Article" "Catégorie" "Stock" "CMUP" "Valeur" "Part"]]
                  ^{:key h}
                  [:th {:style {:padding "10px 14px"
                                :text-align (if (#{"Réf." "Article" "Catégorie"} h) "left" "right")
                                :color "#475569" :font-weight 500 :font-size 10
                                :text-transform "uppercase"}} h])]]
       [:tbody
        (for [[i p] (map-indexed vector db/produits)]
          (let [sid    (or site-actif (-> db/sites first :id))
                qte    (get-in stocks [sid (:id p)] 0)
                cmup-v (subs/cmup lots sid (:id p))
                val    (subs/produit-val lots methode sid (:id p) qte)
                pct    (if (pos? val-tot) (* (/ val val-tot) 100) 0)]
            ^{:key (:id p)}
            [:tr {:style {:border-bottom "1px solid #111827" :background (if (even? i) "transparent" "#0d1117")}}
             [:td {:style {:padding "9px 14px" :color "#4f46e5" :font-family "JetBrains Mono" :font-size 10}} (:ref p)]
             [:td {:style {:padding "9px 14px"}} (:nom p)]
             [:td {:style {:padding "9px 14px" :color "#475569"}} (:cat p)]
             [:td {:style {:padding "9px 14px" :text-align "right" :font-family "JetBrains Mono"}}
              (str qte " " (:unite p))]
             [:td {:style {:padding "9px 14px" :text-align "right" :font-family "JetBrains Mono" :color "#64748b"}}
              (str (fmt cmup-v) " €")]
             [:td {:style {:padding "9px 14px" :text-align "right" :font-family "JetBrains Mono"
                           :color "#a5b4fc" :font-weight 600}} (str (fmt val) " €")]
             [:td {:style {:padding "9px 14px" :text-align "right"}}
              [:div {:style {:display "flex" :align-items "center" :gap 6 :justify-content "flex-end"}}
               [:div {:style {:width 50 :height 3 :background "#1f2937" :border-radius 2 :overflow "hidden"}}
                [:div {:style {:width (str (.toFixed pct 1) "%") :height "100%" :background "#4f46e5"}}]]
               [:span {:style {:font-size 10 :color "#475569" :font-family "JetBrains Mono"}}
                (str (.toFixed pct 1) "%")]]]]))]
       [:tfoot [:tr {:style {:background "#0d1117" :border-top "2px solid #4f46e5"}}
                [:td {:col-span 5 :style {:padding "12px 14px" :font-weight 700}} "TOTAL"]
                [:td {:style {:padding "12px 14px" :text-align "right" :font-family "JetBrains Mono"
                              :color "#a5b4fc" :font-weight 700 :font-size 14}} (str (fmt val-tot) " €")]
                [:td {:style {:padding "12px 14px" :text-align "right" :color "#475569"
                              :font-family "JetBrains Mono"}} "100%"]]]]]

     [:div {:style {:margin-top 14 :background "#0f172a" :border "1px solid #1f2937"
                    :border-radius 8 :padding 14 :font-size 11 :color "#475569"}}
      [:strong {:style {:color "#64748b"}} "ℹ "]
      [:strong {:style {:color "#a5b4fc"}} "FIFO"] " : valorise aux prix des derniers lots reçus. "
      [:strong {:style {:color "#a5b4fc"}} "CMUP"] " : prix moyen pondéré sur tous les lots en stock."]]))

;; ═══════════════════════════════════════════════════════
;; GESTION UTILISATEURS (admin seulement)
;; ═══════════════════════════════════════════════════════
(defn utilisateurs-panel []
  (let [user @(rf/subscribe [:session-user])]
    [:div
     [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom 20}}
      [:h2 {:style {:margin 0 :font-size 20 :font-weight 700}} "Gestion des utilisateurs"]
      [:div {:style {:font-size 12 :color "#64748b" :background "#0f172a" :border "1px solid #1f2937"
                     :border-radius 7 :padding "6px 12px"}}
       "🔒 Réservé aux administrateurs"]]

     [:div {:style {:display "grid" :grid-template-columns "repeat(auto-fill,minmax(300px,1fr))" :gap 12}}
      (for [u db/utilisateurs]
        (let [role-cfg (get db/roles (:role u))]
          ^{:key (:id u)}
          [:div {:style card}
           [:div {:style {:display "flex" :justify-content "space-between" :align-items "flex-start" :margin-bottom 12}}
            [:div {:style {:display "flex" :align-items "center" :gap 10}}
             [:div {:style {:width 36 :height 36 :border-radius "50%"
                            :background (:bg role-cfg)
                            :border (str "2px solid " (:color role-cfg))
                            :display "flex" :align-items "center" :justify-content "center"
                            :font-size 13 :font-weight 700 :color (:color role-cfg)}}
              (str (first (:nom u)))]
             [:div
              [:div {:style {:font-weight 600 :font-size 14 :color "#e2e8f0"}} (:nom u)]
              [:div {:style {:font-size 11 :color "#475569"}} (:email u)]]]
            [:span {:style {:font-size 11 :font-weight 600 :padding "3px 9px" :border-radius 20
                            :background (:bg role-cfg) :color (:color role-cfg)}}
             (:label role-cfg)]]
           [:div {:style {:font-size 11 :color "#64748b"}}
            "Sites : "
            [:span {:style {:color "#94a3b8"}}
             (str/join ", " (map nom-site (:sites u)))]]
           ;; Permissions
           [:div {:style {:margin-top 10 :padding-top 10 :border-top "1px solid #1f2937"}}
            [:div {:style {:font-size 10 :color "#475569" :text-transform "uppercase" :letter-spacing 1 :margin-bottom 6}} "Permissions"]
            [:div {:style {:display "flex" :flex-wrap "wrap" :gap 4}}
             (for [perm (get db/permissions (:role u) #{})]
               ^{:key perm}
               [:span {:style {:font-size 9 :background "#0f172a" :border "1px solid #1f2937"
                               :border-radius 4 :padding "2px 6px" :color "#64748b"}}
                (name perm)])]]]))]]))

;; ═══════════════════════════════════════════════════════
;; COMPOSANT RACINE
;; ═══════════════════════════════════════════════════════
(defn main-panel []
  (let [user @(rf/subscribe [:session-user])]
    (if (nil? user)
      [login-page]
      [:div {:style {:min-height "100vh" :background "#0a0c10" :color "#e2e8f0"}}
       [topbar]
       [:div {:style {:padding "24px 28px" :max-width 1280}}
        (case @(rf/subscribe [:tab])
          "Tableau de bord" [tableau-de-bord]
          "Catalogue"       [catalogue]
          "Mouvements"      [mouvements]
          "Achats"          [achats]
          "Inventaire"      [inventaire]
          "Valorisation"    [valorisation]
          "Utilisateurs"    [utilisateurs-panel]
          [tableau-de-bord])]])))
