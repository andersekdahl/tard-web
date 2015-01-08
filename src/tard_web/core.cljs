(ns tard-web.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require
    [clojure.string :as str]
    [sablono.core :as html :refer-macros [html]]
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [taoensso.encore :as encore :refer (logf)]
    [taoensso.sente :as sente :refer (cb-success?)]
    [taoensso.sente.packers.transit :as sente-transit]))

(enable-console-print!)

;; sente stuff, extract this later

(def packer (sente-transit/get-flexi-packer :edn))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "localhost:8080/chsk" ; Note the same URL as before
        {:type :auto :packer packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(go-loop []
  (let [v (<! ch-chsk)]
    (println "val: " v))
  (recur))

;; Om stuff, extract this later

(def app-state (atom {:messages [{:id 1 :user "NickyB" :date "2 days ago" :message "Denna tarden är den bästa tarden!"}]}))

(defn post-message [ev message-field messages]
  (.preventDefault ev)
  (om/transact! messages #(conj % {:id (rand-int 1000) :user "Unknown" :message (.-value message-field)}))
  (set! (.-value message-field) ""))

(defn message-view [message owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html [:li 
              [:span {:class "meta"}
                [:span {:class "user"} (:user message)]
                [:span {:class "date"} (:date message)]]
              [:span {:class "message"} (:message message)]]))))

(defn messages-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {})
    om/IRenderState
    (render-state [this state]
      (let [messages (:messages app)]
        (html [:div {:class "page-wrap"}
                [:nav {:id "main-nav"}
                  [:h1 "Tard"]
                  [:ul
                    [:li "Messages"]]]
                [:div {:id "main-content"}
                  [:h2 "Messages"]
                  [:div {:class "content"}
                    [:ol {:class "messages"}
                      (om/build-all message-view messages {:key :id})]
                    [:form {:class "new-message" :on-submit #(post-message % (om/get-node owner "message-field") messages)}
                      [:textarea {:ref "message-field" :placeholder "Write a tarded message here"}]
                      [:input {:type "submit" :value "Send"}]]]]])))))

(om/root messages-view app-state
  {:target (.querySelector js/document "body")})