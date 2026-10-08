# StockPro — ClojureScript / Re-frame

Gestion de stocks multi-sites avec contrôle d'accès par rôle, import IA de documents et inventaire comptable.

## Stack
| Couche | Tech |
|--------|------|
| Langage | ClojureScript → JS |
| UI | Reagent (React wrapper) |
| State | Re-frame |
| Build | shadow-cljs |
| HTTP | cljs-ajax + http-fx |
| IA | Anthropic API (claude-sonnet-4) |

## Lancer

```bash
npm install
npm run dev
# → http://localhost:3000
```

## Rôles & permissions

| Rôle | Voir stock | Saisir mvt | Créer BC | Valider BC | Réceptionner | Inventaire | Clôturer | Admin users |
|------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Admin | ✓ tous sites | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Gestionnaire | ✓ son site | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ |
| Magasinier | ✓ son site | ✓ | ✓ | ✗ | ✗ | ✓ saisie | ✗ | ✗ |
| Lecteur | ✓ son site | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |

## Workflow BC avec import IA

```
Magasinier : importe PDF/photo → IA extrait → formulaire pré-rempli
           → crée BC (statut: "en-attente-validation")
Gestionnaire: valide BC → statut "validé"
           → réceptionne → lots FIFO créés en stock
```

## Fichiers src/app/

- `db.cljs`     — état initial, rôles, sites, catalogue, lots, bons
- `subs.cljs`   — subscriptions (stocks, valorisation, permissions)
- `events.cljs` — mutations (login, mvt FIFO, BC workflow, inventaire, IA)
- `views.cljs`  — UI Reagent (login, topbar, 6 onglets)
- `core.cljs`   — point d'entrée
