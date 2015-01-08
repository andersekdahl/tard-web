(ns tard-web.core
  (:require-macros
	  [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require
	  [clojure.string :as str]
	  [cljs.core.async :as async :refer (<! >! put! chan)]
	  [taoensso.encore :as encore :refer (logf)]
	  [taoensso.sente :as sente :refer (cb-success?)]
    [taoensso.sente.packers.transit :as sente-transit]))

(enable-console-print!)

(def packer (sente-transit/get-flexi-packer :edn))

(let [{:keys [chsk ch-recv send-fn state]}
			(sente/make-channel-socket! "localhost:51228/chsk" ; Note the same URL as before
				{:type :auto :packer packer})]
	(def chsk chsk)
	(def ch-chsk ch-recv)
	(def chsk-send! send-fn)
	(def chsk-state state))

(defmulti event-msg-handler :id)

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
	(logf "Event: %s" event)
	(event-msg-handler ev-msg))

(defmethod event-msg-handler :default [{:as ev-msg :keys [event]}]
	(logf "Unhandled event: %s" event))

(defmethod event-msg-handler :chsk/state [{:as ev-msg :keys [?data]}]
	(if (= ?data {:first-open? true})
		(logf "Channel socket successfully established!")
		(logf "Channel socket state change: %s" ?data)))

(defmethod event-msg-handler :chsk/recv [{:as ev-msg :keys [?data]}]
	(logf "Push event from server: %s" ?data))

(when-let [target-el (.querySelector js/document "h1")]
	(.addEventListener target-el "click"
		(fn [ev]
			(logf "Button 1 was clicked (won't receive any reply from server)")
			(chsk-send! [:example/button1 {:had-a-callback? "nope"}]))))

(def router_ (atom nil))

(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
	(stop-router!)
	(reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

(defn start! []
	(start-router!))

(start!)