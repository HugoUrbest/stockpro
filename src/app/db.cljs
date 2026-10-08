(ns app.db)

;; ═══════════════════════════════════════════════════════
;; RÔLES & PERMISSIONS
;; ═══════════════════════════════════════════════════════
;;
;; admin       → tout faire sur tous les sites
;; gestionnaire→ valider BC, clôturer inventaire, voir tous les stocks de son site
;; magasinier  → saisir entrées/sorties, créer BC, importer documents
;; lecteur     → lecture seule sur son site
;;
(def roles
  {:admin        {:label "Administrateur"  :color "#a78bfa" :bg "#1e1b4b"}
   :gestionnaire {:label "Gestionnaire"    :color "#34d399" :bg "#064e3b"}
   :magasinier   {:label "Magasinier"      :color "#60a5fa" :bg "#1e3a5f"}
   :lecteur      {:label "Lecteur"         :color "#94a3b8" :bg "#1e293b"}})

(def permissions
  ;; [role] → #{actions autorisées}
  {:admin        #{:voir-tous-sites :voir-stock :saisir-mvt :creer-bc :valider-bc
                   :receptionner-bc :importer-doc :lancer-inventaire
                   :saisir-inventaire :cloturer-inventaire :gerer-utilisateurs}
   :gestionnaire #{:voir-stock :saisir-mvt :creer-bc :valider-bc :receptionner-bc
                   :importer-doc :lancer-inventaire :saisir-inventaire :cloturer-inventaire}
   :magasinier   #{:voir-stock :saisir-mvt :creer-bc :importer-doc :saisir-inventaire}
   :lecteur      #{:voir-stock}})

(defn peut? [user action]
  (let [role (get permissions (:role user) #{})]
    (or (contains? role action)
        ;; admin voit tous les sites
        (and (= (:role user) :admin) (contains? role :voir-tous-sites)))))

;; ═══════════════════════════════════════════════════════
;; SITES
;; ═══════════════════════════════════════════════════════
(def sites
  [{:id "S1" :nom "Lycée Jean Moulin"        :ville "Valence"}
   {:id "S2" :nom "Collège Victor Hugo"      :ville "Romans-sur-Isère"}
   {:id "S3" :nom "Lycée pro René Cassin"    :ville "Valence"}])

;; ═══════════════════════════════════════════════════════
;; UTILISATEURS (simulation d'authentification)
;; ═══════════════════════════════════════════════════════
(def utilisateurs
  [{:id "U1" :nom "Sophie Martin"   :role :admin
    :sites #{"S1" "S2" "S3"} :email "s.martin@ac-grenoble.fr"}
   {:id "U2" :nom "Marc Dupont"     :role :gestionnaire
    :sites #{"S1"}            :email "m.dupont@ac-grenoble.fr"}
   {:id "U3" :nom "Amira Benali"    :role :magasinier
    :sites #{"S1"}            :email "a.benali@ac-grenoble.fr"}
   {:id "U4" :nom "Pierre Lefèvre"  :role :gestionnaire
    :sites #{"S2" "S3"}       :email "p.lefevre@ac-grenoble.fr"}
   {:id "U5" :nom "Julie Rousseau"  :role :lecteur
    :sites #{"S1"}            :email "j.rousseau@ac-grenoble.fr"}
   {:id "U6" :nom "Karim Ouidir"    :role :magasinier
    :sites #{"S2"}            :email "k.ouidir@ac-grenoble.fr"}])

;; ═══════════════════════════════════════════════════════
;; CATALOGUE PRODUITS
;; ═══════════════════════════════════════════════════════
(def categories ["Hygiène" "Badges & Accès" "Clés" "Pièces détachées" "Espaces verts" "Informatique"])

(def produits
  [{:id 1  :ref "HYG-001" :nom "Savon liquide 5L"               :cat "Hygiène"         :unite "bidon" :seuil 10 :tva 20}
   {:id 2  :ref "HYG-002" :nom "Papier toilette (colis 96)"     :cat "Hygiène"         :unite "colis" :seuil 5  :tva 5.5}
   {:id 3  :ref "HYG-003" :nom "Gel hydroalcoolique 5L"         :cat "Hygiène"         :unite "bidon" :seuil 8  :tva 20}
   {:id 4  :ref "BAD-001" :nom "Badge RFID étudiant"            :cat "Badges & Accès"  :unite "unité" :seuil 50 :tva 20}
   {:id 5  :ref "BAD-002" :nom "Lecteur badge USB"              :cat "Badges & Accès"  :unite "unité" :seuil 2  :tva 20}
   {:id 6  :ref "CLE-001" :nom "Clé salle de classe (lot 5)"    :cat "Clés"            :unite "lot"   :seuil 3  :tva 20}
   {:id 7  :ref "CLE-002" :nom "Cylindre serrure Bricard"       :cat "Clés"            :unite "unité" :seuil 2  :tva 20}
   {:id 8  :ref "PD-001"  :nom "Filtre à air CVC"               :cat "Pièces détachées":unite "unité" :seuil 4  :tva 20}
   {:id 9  :ref "PD-002"  :nom "Ampoule LED 10W"                :cat "Pièces détachées":unite "unité" :seuil 20 :tva 20}
   {:id 10 :ref "PD-003"  :nom "Courroie moteur ventilateur"    :cat "Pièces détachées":unite "unité" :seuil 2  :tva 20}
   {:id 11 :ref "EV-001"  :nom "Terreau universel 50L"          :cat "Espaces verts"   :unite "sac"   :seuil 8  :tva 5.5}
   {:id 12 :ref "EV-002"  :nom "Engrais granulé 25kg"           :cat "Espaces verts"   :unite "sac"   :seuil 3  :tva 5.5}
   {:id 13 :ref "INF-001" :nom "Cartouche toner HP LaserJet"    :cat "Informatique"    :unite "unité" :seuil 5  :tva 20}
   {:id 14 :ref "INF-002" :nom "Câble RJ45 cat6 (lot 10)"       :cat "Informatique"    :unite "lot"   :seuil 4  :tva 20}])

;; ═══════════════════════════════════════════════════════
;; LOTS INITIAUX (FIFO) — par site
;; ═══════════════════════════════════════════════════════
(def lots-initiaux
  ;; Site S1
  [{:id 1  :site-id "S1" :produit-id 1  :date "2024-09-01" :qte 50  :pu 8.50  :ref "BC-S1-001" :qte-rest 12}
   {:id 2  :site-id "S1" :produit-id 1  :date "2024-11-15" :qte 40  :pu 8.80  :ref "BC-S1-045" :qte-rest 40}
   {:id 3  :site-id "S1" :produit-id 2  :date "2024-09-01" :qte 20  :pu 18.00 :ref "BC-S1-001" :qte-rest 3}
   {:id 4  :site-id "S1" :produit-id 2  :date "2024-12-01" :qte 15  :pu 18.50 :ref "BC-S1-078" :qte-rest 15}
   {:id 5  :site-id "S1" :produit-id 4  :date "2024-08-20" :qte 200 :pu 2.50  :ref "BC-S1-000" :qte-rest 65}
   {:id 6  :site-id "S1" :produit-id 8  :date "2024-10-01" :qte 12  :pu 35.00 :ref "BC-S1-020" :qte-rest 6}
   {:id 7  :site-id "S1" :produit-id 9  :date "2024-09-01" :qte 60  :pu 3.20  :ref "BC-S1-001" :qte-rest 28}
   {:id 8  :site-id "S1" :produit-id 13 :date "2024-09-01" :qte 20  :pu 28.00 :ref "BC-S1-001" :qte-rest 7}
   ;; Site S2
   {:id 9  :site-id "S2" :produit-id 1  :date "2024-09-05" :qte 30  :pu 8.60  :ref "BC-S2-001" :qte-rest 8}
   {:id 10 :site-id "S2" :produit-id 3  :date "2024-09-05" :qte 20  :pu 14.00 :ref "BC-S2-001" :qte-rest 5}
   {:id 11 :site-id "S2" :produit-id 6  :date "2024-09-05" :qte 10  :pu 12.00 :ref "BC-S2-001" :qte-rest 7}
   {:id 12 :site-id "S2" :produit-id 11 :date "2024-03-01" :qte 20  :pu 9.90  :ref "BC-S2-R01" :qte-rest 11}
   ;; Site S3
   {:id 13 :site-id "S3" :produit-id 2  :date "2024-09-10" :qte 25  :pu 17.80 :ref "BC-S3-001" :qte-rest 9}
   {:id 14 :site-id "S3" :produit-id 9  :date "2024-09-10" :qte 40  :pu 3.10  :ref "BC-S3-001" :qte-rest 22}
   {:id 15 :site-id "S3" :produit-id 14 :date "2024-11-01" :qte 8   :pu 45.00 :ref "BC-S3-010" :qte-rest 5}])

;; ═══════════════════════════════════════════════════════
;; BONS DE COMMANDE INITIAUX
;; statuts : "brouillon" "en-attente-validation" "validé" "reçu" "annulé"
;; ═══════════════════════════════════════════════════════
(def bons-initiaux
  [{:id 1 :ref "BC-S1-079" :site-id "S1" :fournisseur "ProClean SARL"
    :date "2025-01-10" :statut "reçu" :cree-par "U3" :valide-par "U2"
    :lignes [{:produit-id 1 :qte 40 :pu 8.80} {:produit-id 2 :qte 15 :pu 18.50}]}
   {:id 2 :ref "BC-S1-080" :site-id "S1" :fournisseur "TechBadge SAS"
    :date "2025-01-18" :statut "en-attente-validation" :cree-par "U3" :valide-par nil
    :lignes [{:produit-id 4 :qte 100 :pu 2.45} {:produit-id 5 :qte 2 :pu 89.00}]}
   {:id 3 :ref "BC-S2-012" :site-id "S2" :fournisseur "GreenEcole"
    :date "2025-02-05" :statut "validé" :cree-par "U6" :valide-par "U4"
    :lignes [{:produit-id 11 :qte 15 :pu 9.50} {:produit-id 12 :qte 6 :pu 21.50}]}
   {:id 4 :ref "BC-S3-008" :site-id "S3" :fournisseur "Informatique Scolaire SA"
    :date "2025-02-10" :statut "brouillon" :cree-par "U4" :valide-par nil
    :lignes [{:produit-id 13 :qte 10 :pu 26.00} {:produit-id 14 :qte 5 :pu 43.00}]}])

;; ═══════════════════════════════════════════════════════
;; ÉTAT INITIAL DE L'APPLICATION
;; ═══════════════════════════════════════════════════════
(def default-db
  {;; Auth
   :session-user    nil          ; utilisateur connecté
   :login-form      {:user-id "" :error nil}

   ;; Navigation
   :tab             "Tableau de bord"
   :site-actif      nil          ; site sélectionné (nil = tous pour admin)

   ;; Données métier
   :lots            lots-initiaux
   :bons            bons-initiaux
   :next-lot-id     200
   :next-bc-id      20

   ;; Préférences comptables
   :methode         "FIFO"       ; ou "CMUP"

   ;; Filtres catalogue
   :filter-cat      "Toutes"

   ;; Formulaire mouvement
   :show-mvt        false
   :mvt-form        {:produit-id "" :qte "" :pu "" :type "entrée" :ref ""}

   ;; Formulaire bon de commande
   :show-bc         false
   :bc-form         {:fournisseur "" :date "" :site-id ""
                     :lignes [{:produit-id "" :qte "" :pu ""}]}

   ;; Import IA
   :ai-parsing      false
   :ai-error        nil
   :ai-preview      false
   :ai-doc-type     nil           ; :bl :facture :bc-interne

   ;; Inventaire
   :show-inventaire   false
   :inventaire-data   {}
   :inventaire-valide false

   ;; Gestion utilisateurs (admin seulement)
   :show-user-modal false
   :user-form       {:nom "" :email "" :role :lecteur :sites #{}}})
