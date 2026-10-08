(ns app.subs
  (:require [re-frame.core :as rf]
            [app.db :as db]))

;; ── Auth ──────────────────────────────────────────────────────────────────
(rf/reg-sub :session-user    (fn [db _] (:session-user db)))
(rf/reg-sub :login-form      (fn [db _] (:login-form db)))

;; ── Navigation ────────────────────────────────────────────────────────────
(rf/reg-sub :tab             (fn [db _] (:tab db)))
(rf/reg-sub :site-actif      (fn [db _] (:site-actif db)))
(rf/reg-sub :methode         (fn [db _] (:methode db)))
(rf/reg-sub :filter-cat      (fn [db _] (:filter-cat db)))

;; ── Données brutes ────────────────────────────────────────────────────────
(rf/reg-sub :lots            (fn [db _] (:lots db)))
(rf/reg-sub :bons            (fn [db _] (:bons db)))
(rf/reg-sub :show-mvt        (fn [db _] (:show-mvt db)))
(rf/reg-sub :mvt-form        (fn [db _] (:mvt-form db)))
(rf/reg-sub :show-bc         (fn [db _] (:show-bc db)))
(rf/reg-sub :bc-form         (fn [db _] (:bc-form db)))
(rf/reg-sub :ai-parsing      (fn [db _] (:ai-parsing db)))
(rf/reg-sub :ai-error        (fn [db _] (:ai-error db)))
(rf/reg-sub :ai-preview      (fn [db _] (:ai-preview db)))
(rf/reg-sub :show-inventaire   (fn [db _] (:show-inventaire db)))
(rf/reg-sub :inventaire-data   (fn [db _] (:inventaire-data db)))
(rf/reg-sub :inventaire-valide (fn [db _] (:inventaire-valide db)))
(rf/reg-sub :show-user-modal (fn [db _] (:show-user-modal db)))
(rf/reg-sub :user-form       (fn [db _] (:user-form db)))

;; ── Sites visibles par l'utilisateur courant ──────────────────────────────
(rf/reg-sub
 :sites-visibles
 :<- [:session-user]
 (fn [user _]
   (if (nil? user)
     []
     (if (= (:role user) :admin)
       db/sites
       (filter #(contains? (:sites user) (:id %)) db/sites)))))

;; ── Lots filtrés par site actif ───────────────────────────────────────────
(rf/reg-sub
 :lots-site
 :<- [:lots]
 :<- [:site-actif]
 :<- [:session-user]
 (fn [[lots site-actif user] _]
   (cond
     (nil? user) []
     (= (:role user) :admin) (if site-actif
                               (filter #(= (:site-id %) site-actif) lots)
                               lots)
     :else (let [sites-user (:sites user)]
             (filter #(and (contains? sites-user (:site-id %))
                           (or (nil? site-actif) (= (:site-id %) site-actif)))
                     lots)))))

;; ── Bons filtrés par site et droits ──────────────────────────────────────
(rf/reg-sub
 :bons-visibles
 :<- [:bons]
 :<- [:site-actif]
 :<- [:session-user]
 (fn [[bons site-actif user] _]
   (cond
     (nil? user) []
     (= (:role user) :admin) (if site-actif
                               (filter #(= (:site-id %) site-actif) bons)
                               bons)
     :else (let [sites-user (:sites user)]
             (filter #(and (contains? sites-user (:site-id %))
                           (or (nil? site-actif) (= (:site-id %) site-actif)))
                     bons)))))

;; ── Stock par produit+site ────────────────────────────────────────────────
(rf/reg-sub
 :stocks
 :<- [:lots-site]
 (fn [lots _]
   (reduce (fn [acc lot]
             (update-in acc [(:site-id lot) (:produit-id lot)] (fnil + 0) (:qte-rest lot)))
           {}
           lots)))

;; ── Fonctions de valorisation ─────────────────────────────────────────────
(defn cmup [lots site-id produit-id]
  (let [rel   (filter #(and (= (:site-id %) site-id)
                            (= (:produit-id %) produit-id)
                            (pos? (:qte-rest %))) lots)
        tot-q (reduce + 0 (map :qte-rest rel))
        tot-v (reduce + 0 (map #(* (:qte-rest %) (:pu %)) rel))]
    (if (pos? tot-q) (/ tot-v tot-q) 0)))

(defn fifo-val [lots site-id produit-id]
  (->> lots
       (filter #(and (= (:site-id %) site-id)
                     (= (:produit-id %) produit-id)
                     (pos? (:qte-rest %))))
       (reduce #(+ %1 (* (:qte-rest %2) (:pu %2))) 0)))

(defn produit-val [lots methode site-id produit-id qte]
  (if (= methode "FIFO")
    (fifo-val lots site-id produit-id)
    (* (cmup lots site-id produit-id) qte)))

;; ── Valeur totale par site ────────────────────────────────────────────────
(rf/reg-sub
 :val-par-site
 :<- [:lots]
 :<- [:methode]
 (fn [[lots methode] _]
   (reduce (fn [acc site]
             (let [sid (:id site)
                   val (reduce (fn [s p]
                                 (let [lots-site (filter #(= (:site-id %) sid) lots)
                                       stocks-site (reduce (fn [a l]
                                                             (update a (:produit-id l) (fnil + 0) (:qte-rest l)))
                                                           {} lots-site)
                                       qte (get stocks-site (:id p) 0)]
                                   (+ s (produit-val lots-site methode sid (:id p) qte))))
                               0 db/produits)]
               (assoc acc sid val)))
           {}
           db/sites)))

;; ── Valeur totale (site actif ou tous) ───────────────────────────────────
(rf/reg-sub
 :val-totale
 :<- [:lots-site]
 :<- [:stocks]
 :<- [:methode]
 :<- [:site-actif]
 (fn [[lots stocks methode site-actif] _]
   (reduce (fn [sum p]
             (let [sites-a-calc (if site-actif [site-actif]
                                  (map :id db/sites))]
               (+ sum (reduce (fn [s sid]
                                (let [qte (get-in stocks [sid (:id p)] 0)]
                                  (+ s (produit-val lots methode sid (:id p) qte))))
                              0 sites-a-calc))))
           0
           db/produits)))

;; ── Produits filtrés ─────────────────────────────────────────────────────
(rf/reg-sub
 :produits-filtres
 :<- [:filter-cat]
 (fn [cat _]
   (if (= cat "Toutes") db/produits
       (filter #(= (:cat %) cat) db/produits))))

;; ── Permission helper ────────────────────────────────────────────────────
(rf/reg-sub
 :peut?
 :<- [:session-user]
 (fn [user [_ action]]
   (db/peut? user action)))
