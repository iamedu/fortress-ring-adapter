(ns fortress.ring.server
  (:use fortress.util.clojure)
  (:require [clojure.tools.logging :as log]
            [fortress.ring.handler :as fhandler]
            [fortress.ring.writers :as writers])
  (:import [io.netty.bootstrap ServerBootstrap]
           [io.netty.channel ChannelOption]
           [io.netty.channel.nio NioEventLoopGroup]
           [io.netty.channel.socket.nio NioServerSocketChannel]
           [fortress.ring.handler FortressInitializer]
           [java.net InetSocketAddress]
           [java.util.concurrent ThreadFactory]))

(def default-options {:threads 0
                      :host "0.0.0.0"
                      :port 3000
                      :ssl? false
                      :zero-copy? false
                      :thread-prefix "fortress-http"})

(defn- random-thread-name [prefix]
  (str prefix "-" (random-guid-str)))

(defn thread-factory [thread-name-prefix]
  (proxy [ThreadFactory] []
    (newThread [thunk]
      (Thread. thunk (random-thread-name thread-name-prefix)))))

(defn secure-channel-clone [bootstrap handler {:keys [host ssl? ssl-port zero-copy? ssl-context]}]
  (when (and ssl? ssl-port)
    (let [bootstrap (doto (.clone bootstrap)
                      (.childHandler (FortressInitializer.
                                       ssl-context
                                       (.longValue Integer/MAX_VALUE)
                                       zero-copy?
                                       true
                                       handler)))
          address (InetSocketAddress. host ssl-port)
          future-channel (.bind bootstrap address)]
      (.syncUninterruptibly future-channel)
      (log/info "Secure channel started at port" ssl-port)
      {:future-secure-channel future-channel
       :secure-channel (.channel future-channel)})))

(defn create-channel [handler {:keys [port threads thread-prefix host zero-copy?]
                               :as options}]
  (let [address (InetSocketAddress. host port)
        group (NioEventLoopGroup. threads
                                  (thread-factory thread-prefix))
        bootstrap (doto (ServerBootstrap.)
                    (.group group)
                    (.channel NioServerSocketChannel)
                    (.childOption ChannelOption/SO_KEEPALIVE true)
                    (.childHandler (FortressInitializer.
                                     nil
                                     (.longValue Integer/MAX_VALUE)
                                     zero-copy?
                                     false
                                     handler)))
        future-channel (.bind bootstrap address)]
    (.syncUninterruptibly future-channel)
    (log/info "Channel started at port" port)
    (merge {:future-channel future-channel
            :channel (.channel future-channel)
            :group group}
           (secure-channel-clone bootstrap handler options))))

(defn run-fortress
  "Creates a netty handler and starts it, receives a handler
  and a map of options. These are the supported options:
  :port           - The port to listen on (defaults to 3000)
  :host           - Host to listen to (defaults to 0.0.0.0)
  :ssl-port       - The SSL por to listen on 
  :ssl?           - Allows to handle https (defaults to false)
  :ssl-context    - SSL Context
  :threads        - Number of threads (defaults to cores * 2)
  :thread-prefix  - Thread prefix (defaults to fortress-http
  :debug-requests - Wether to debug requests (defaults to false)"
  [handler & {:keys [debug-requests]
              :as options}]
  (let [options (merge default-options options)]
    (reset! fhandler/debug-request debug-requests)
    (if debug-requests
      (log/info "Setting up requests debug"))
    (create-channel handler options)))

(defn stop-fortress [{:keys [group channel secure-channel]}]
  (.close channel)
  (if secure-channel
    (.close secure-channel))
  (-> group
      (.shutdownGracefully)
      (.sync))
  (log/info "Fortress stopped"))
