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
 
(def messages (atom []))
(def user (atom {:name nil}))

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
  (some #(= (:id %) (:id message)) @messages))

(defn parse-date [date]
  (format/parse (format/formatters :date-hour-minute-second) (str date)))

 (go-loop []
   ; todo: ?data in v can either be a vector or a map, which
   ; leads to this somewhat ugly code.
   (let [v (<! ch-chsk)
         data (vec (:?data v))
         message (nth data 1)]
     (and (= :messages/new (nth data 0)) (not (message-exists? message))
          (swap! messages conj (assoc message :date (parse-date (:date message))))))
   (recur))

(defn format-date [date]
  (format/unparse (format/formatter "yyyy-MM-dd HH:mm:ss") date))

(defn create-message [message user]
  {:id (rand-int 1000) :username user :message message :date (time/now)})

(defn save [message]
  (swap! messages conj message))

(defn post-message [message username]
  (let [mess (create-message @message username)]
    (save mess)
    (chsk-send! [:messages/new (assoc mess :date (format/unparse (format/formatters :date-hour-minute-second) (:date mess)))])))

(defn message-input [user]
  (let [message (atom "")]
    (fn []
      [:div.new-message
       [:textarea {:ref "message-field" :value @message :placeholder "Write a tarded message here" :on-change #(reset! message (-> % .-target .-value))}]
       [:input {:type "button" :on-click #(do (post-message message user) (reset! message "")) :value "Send"}]])))


(defn login! [user-state password-state]
  (do
    (sente/ajax-call "http://localhost:8080/login"
                     {:method :post
                      :params {:name (str @user-state)
                               :password (str @password-state)}}
                     (fn [ajax-resp]
                       (swap! user assoc :name @user-state)
                       (println "name: " @user))
                    )
    (sente/chsk-reconnect! chsk)))

(defn message-view [message]
  [:li
   [:span.meta
    [:span.user (:username message)]
    [:span.date (format-date (:date message))]]
   [:span.message (:message message)]])

(defn login-view []
  (let [username (atom "")
        pass (atom "")]
    [:div
     [:h1 "Welcome to the Tard!"]
     [:div
      [:input {:type "text" :placeholder "Username" :on-change #(reset! username (-> % .-target .-value))}  ]
      [:input {:type "password" :placeholder "Password" :on-change #(reset! pass (-> % .-target .-value))} ]
      [:input {:type "submit" :value "Login" :on-click #(login! username pass) }]]]))

(defn messages-view []
  (let [username (:name @user)]
    (do
      (println "login" messages username)
      (if (nil? (:name @user))
        (do
          (println "wow:" (:name @user))
          [login-view])
        [:div {:class "page-wrap"}
         [:nav {:id "main-nav"}
          [:h1 "Tard"]
          [:ul
           [:li "Messages"]]]
         [:div {:id "main-content"}
          [:h2 "Messages"]
          [:div {:class "content"}
           [:ol {:class "messages"}
            (println "mess: " @messages)
            (for [message @messages]
              ^{:key message} [message-view message])]
           [message-input username]]]]))))

(defn render-simple []
  (regen/render-component [messages-view]
                          (.-body js/document)))

(render-simple)

