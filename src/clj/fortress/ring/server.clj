(ns fortress.ring.server
  (:use fortress.util.clojure)
  (:require [clojure.tools.logging :as log]
            [fortress.ring.handler :as fhandler]
            [fortress.ring.writers :as writers]
            [clojure.java.io :as io])
  (:import [io.netty.bootstrap ServerBootstrap]
           [io.netty.channel ChannelOption]
           [io.netty.channel.nio NioEventLoopGroup]
           [io.netty.channel.socket.nio NioServerSocketChannel]
           [fortress.ring.handler FortressInitializer]
           [java.net InetSocketAddress]
           [java.util.concurrent ThreadFactory Executors TimeUnit]))

(def default-options {:threads 0
                      :host "0.0.0.0"
                      :port 3000
                      :ssl? false
                      :zero-copy? true
                      :temp-path (System/getProperty ":ava.io.tmpdir")
                      :error-fn (fn [_ _])
                      :thread-prefix "fortress-http"})

(defn- random-thread-name [prefix]
  (str prefix "-" (random-guid-str)))

(defn thread-factory [thread-name-prefix]
  (proxy [ThreadFactory] []
    (newThread [thunk]
      (Thread. thunk (random-thread-name thread-name-prefix)))))

(defonce deletion-executor
  (Executors/newSingleThreadScheduledExecutor
    (thread-factory "fortress-deleter")))

(defn delete-files
  "Delete files older than 15 minutes in temp path"
  [path]
  (fn []
    (let [file (io/file path)
          fs (file-seq file)]
      (try
        (doseq [f fs]
          (if (and (.isFile f)
                   (.startsWith (.getName f) "fortress")
                   (> (- (System/currentTimeMillis)
                         (.lastModified f))
                      (* 15 60 1000)))
            (io/delete-file f)))
        (catch Exception e
          (.printStackTrace e))))))

(defn start-deleting [path]
  (.scheduleAtFixedRate deletion-executor
                        (delete-files path)
                        15
                        5
                        TimeUnit/MINUTES))

(defn secure-channel-clone [bootstrap handler temp-path {:keys [host ssl? ssl-port zero-copy? max-size ssl-context error-fn]}]
  (when (and ssl? ssl-port)
    (let [bootstrap (doto (.clone bootstrap)
                      (.childHandler (FortressInitializer.
                                       ssl-context
                                       max-size
                                       false
                                       true
                                       handler
                                       error-fn
                                       nil
                                       temp-path)))
          address (InetSocketAddress. host ssl-port)
          future-channel (.bind bootstrap address)]
      (.syncUninterruptibly future-channel)
      (log/info "Secure channel started at port" ssl-port)
      {:future-secure-channel future-channel
       :secure-channel (.channel future-channel)})))

(defn create-channel [handler temp-path {:keys [port threads thread-prefix host zero-copy? error-fn max-size]
                                         :or {max-size (* 1024 1024)}
                                         :as options}]
  (let [address (InetSocketAddress. host port)
        full-options (assoc options
                            :port port
                            :threads threads
                            :thread-previx thread-prefix
                            :host host
                            :zero-copy? zero-copy?
                            :error-fn error-fn
                            :max-size max-size)
        group (NioEventLoopGroup. threads
                                  (thread-factory thread-prefix))
        bootstrap (doto (ServerBootstrap.)
                    (.group group)
                    (.channel NioServerSocketChannel)
                    (.childOption ChannelOption/SO_KEEPALIVE true)
                    (.childHandler (FortressInitializer.
                                     nil
                                     max-size
                                     zero-copy?
                                     false
                                     handler
                                     error-fn
                                     nil
                                     temp-path)))
        future-channel (.bind bootstrap address)]
    (.syncUninterruptibly future-channel)
    (log/info "Channel started at port" port)
    (merge {:future-channel future-channel
            :channel (.channel future-channel)
            :group group}
           (secure-channel-clone bootstrap handler temp-path full-options))))

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
  ([handler]
   (run-fortress handler {}))
  ([handler {:keys [debug-requests temp-path]
             :as options}]
   (start-deleting temp-path)
   (let [options (merge default-options options)]
     (reset! fhandler/debug-request debug-requests)
     (if debug-requests
       (log/info "Setting up requests debug"))
     (create-channel handler temp-path options))))

(defn stop-fortress [{:keys [group channel secure-channel]}]
  (.close channel)
  (if secure-channel
    (.close secure-channel))
  (-> group
      (.shutdownGracefully)
      (.sync))
  (log/info "Fortress stopped"))
