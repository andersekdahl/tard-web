(ns tard-web.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require
    [clojure.string :as str]
    [sablono.core :as html :refer-macros [html]]
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [cljs-time.core :as time]
    [cljs-time.format :as format]
    [taoensso.encore :as encore :refer (logf)]
    [taoensso.sente :as sente :refer (cb-success?)]
    [taoensso.sente.packers.transit :as sente-transit]))

(enable-console-print!)

;; sente stuff, extract this later

(def packer (sente-transit/get-flexi-packer :edn))

(defn chsk-url-fn [path {:as window-location :keys [protocol pathname]} websocket?]
  (let [my-host "localhost:8080/chsk"]
    (str (if-not websocket? protocol (if (= protocol "https:") "wss:" "ws:"))
      "//" my-host (or path pathname))))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "" {:chsk-url-fn chsk-url-fn :type :auto :packer packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(go-loop []
  (let [v (<! ch-chsk)]
    (println "val: " v))
  (recur))

;; Om stuff, extract this later

(def app-state (atom {:messages [] :user nil}))

(defn format-date [date]
  (format/unparse (format/formatter "yyyy-MM-dd HH:mm:ss") date))

(defn create-message [message user]
  {:id (rand-int 1000) :user user :message message :date (time/now)})

(defn post-message [ev message-field messages user]
  (.preventDefault ev)
  (let [message (create-message (.-value message-field) (:username user))]
    (om/transact! messages #(conj % message))
    (set! (.-value message-field) "")
    (chsk-send! [::new-message (assoc message :date (format/unparse (format/formatters :date-hour-minute-second) (:date message)))])))

(defn message-view [message owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html [:li 
              [:span {:class "meta"}
                [:span {:class "user"} (:user message)]
                [:span {:class "date"} (format-date (:date message))]]
              [:span {:class "message"} (:message message)]]))))

(defn login! [ev username password app]
  (.preventDefault ev)
  (println "login" @chsk-state)
  (sente/ajax-call "http://localhost:8080/login"
    {:method :post
     :params {:username (str username)
              :password (str password)}}
    (fn [ajax-resp] 
      (om/update! app :user {:username username})))
  (sente/chsk-reconnect! chsk))

(defn login-view [user owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html [:div
              [:h1 "Welcome to the Tard!"]
              [:form {:on-submit #(login! % (.-value (om/get-node owner "username-field")) (.-value (om/get-node owner "password-field")) user)}
                [:input {:type "text" :placeholder "Username" :ref "username-field"}]
                [:input {:type "password" :placeholder "Password" :ref "password-field"}]
                [:input {:type "submit" :value "Login"}]]]))))

(defn messages-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {})
    om/IRenderState
    (render-state [this state]
      (let [messages (:messages app)
            user (:user app)]
        (if (nil? user)
          (om/build login-view app)
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
                      [:form {:class "new-message" :on-submit #(post-message % (om/get-node owner "message-field") messages @user)}
                        [:textarea {:ref "message-field" :placeholder "Write a tarded message here"}]
                        [:input {:type "submit" :value "Send"}]]]]]))))))

(om/root messages-view app-state
  {:target (.querySelector js/document "body")})