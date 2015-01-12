(ns tard-web.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require
    [clojure.string :as str]
    [sablono.core :as html :refer-macros [html]]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [cljs-time.core :as time]
    [cljs-time.format :as format]
    [taoensso.encore :as encore :refer (logf)]
    [taoensso.sente :as sente :refer (cb-success?)]
    [taoensso.sente.packers.transit :as sente-transit]
    [reagent.core :as regen :refer [atom]]))

(enable-console-print!)

;; sente stuff, extract this later

(def app-state (atom {:messages [] :username nil}))

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

(defn message-exists? [message]
  (some #(= (:id %) (:id message)) (:messages @app-state)))

(defn parse-date [date]
  (format/parse (format/formatters :date-hour-minute-second) (str date)))

;; (go-loop []
;;   ; todo: ?data in v can either be a vector or a map, which
;;   ; leads to this somewhat ugly code.
;;   (let [v (<! ch-chsk)
;;         data (vec (:?data v))
;;         message (nth data 1)]

;;     (and (= :messages/new (nth data 0)) (not (message-exists? message))
;;       (om/transact!
;;         (om/to-cursor @app-state app-state [])
;;         :messages
;;         #(conj % (assoc message :date (parse-date (:date message))))))
;;   (recur)))

(defn format-date [date]
  (format/unparse (format/formatter "yyyy-MM-dd HH:mm:ss") date))

(defn create-message [message user]
  {:id (rand-int 1000) :username user :message message :date (time/now)})

(defn save [message]
  (let [messages (:messages @app-state)]
    (println "mess" message)
     (println "mess1" messages "app" @app-state)
     (swap! app-state assoc :messages (conj messages message))
     (println "mess2" messages "app1" @app-state)))

(defn post-message [message username]
  (let [mess (create-message @message username)]
    (save mess)
    (println "app" @app-state)
    (chsk-send! [:messages/new (assoc mess :date (format/unparse (format/formatters :date-hour-minute-second) (:date mess)))])))

(defn message-input [user]
  (let [message (atom "")]
    [:div {:class "new-message"}
     [:textarea {:ref "message-field" :placeholder "Write a tarded message here" :on-change #(reset! message (-> % .-target .-value))}]
     [:input {:type "button" :on-click #(do (post-message message user) (reset! message "")) :value "Send"}]]))


(defn login! [user-state password-state]
  (do
    (sente/ajax-call "http://localhost:8080/login"
                     {:method :post
                      :params {:username (str @user-state)
                               :password (str @password-state)}}
                     (fn [ajax-resp]
                       (swap! app-state assoc :username @user-state)
                       (println "login!" @app-state)))
    (sente/chsk-reconnect! chsk)))

(defn message-view [message]
  [:li
   [:span {:class "meta"}
    [:span {:class "user"} (:user message)]
    [:span {:class "date"} (format-date (:date message))]]
   [:span {:class "message"} (:message message)]])

(defn login-view []
  (let [user (atom "")
        pass (atom "")]
    [:div
     [:h1 "Welcome to the Tard!"]
     [:div
      [:input {:type "text" :placeholder "Username" :on-change #(reset! user (-> % .-target .-value))}  ]
      [:input {:type "password" :placeholder "Password" :on-change #(reset! pass (-> % .-target .-value))} ]
      [:input {:type "submit" :value "Login" :on-click #(login! user pass) }]]]))

(defn messages-view []
  (let [messages (:messages @app-state)
        username (:username @app-state)]
    (do
      (println "login" messages username)
      (if (nil? username)
        [login-view]
        [:div {:class "page-wrap"}
         [:nav {:id "main-nav"}
          [:h1 "Tard"]
          [:ul
           [:li "Messages"]]]
         [:div {:id "main-content"}
          [:h2 "Messages"]
          [:div {:class "content"}
           [:ol {:class "messages"}
            (do
              (println "rerend mess: " messages "use" username)
              (for [message (:messages @app-state)]
                (do
                  (println "messfor" message)
                  (message-view message))))]
           [message-input username]]]]))))

(defn render-simple []
  (regen/render-component [messages-view]
                          (.-body js/document)))

(render-simple)





