(ns app.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [app.events]
            [app.subs]
            [app.views :as views]))

(defn mount-root []
  (rf/clear-subscription-cache!)
  (rdom/render [views/main-panel]
               (.getElementById js/document "app")))

(defn init []
  (rf/dispatch-sync [:initialise-db])
  (mount-root))

(defn ^:dev/after-load re-render []
  (mount-root))
