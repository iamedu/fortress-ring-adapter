(ns fortress.ring.handler
  (:require [clojure.tools.logging :as log]
            [fortress.ring.writers :as writers]
            [fortress.ring.request :as request]
            [fortress.ring.response :as response]) 
  (:import [io.netty.channel ChannelHandler$Sharable SimpleChannelInboundHandler]
           [io.netty.handler.stream ChunkedWriteHandler]
           [io.netty.handler.codec.http HttpServerCodec HttpObjectAggregator HttpHeaders]
           [io.netty.handler.logging LoggingHandler]))

(def debug-request (atom false))

(defn- add-keep-alive [http-request ring-response]
  (if (HttpHeaders/isKeepAlive http-request)
    (do
      (assoc-in ring-response [:headers "Connection"] "Keep-Alive"))
    ring-response))


(gen-class :name ^{ChannelHandler$Sharable {}}
           fortress.ring.handler.FortressHttpRequestHandler
           :extends io.netty.channel.SimpleChannelInboundHandler
           :state state
           :init "init"
           :constructors {[Boolean clojure.lang.IFn] []}
           :prefix "fhandler-")

(defn fhandler-init [zero-copy? handler]
  [[] (atom {:zero-copy? zero-copy?
             :handler handler})])

(defn fhandler-channelRead0 [this ctx request]
  (let [{:keys [zero-copy? handler]} @(.state this)]
    (binding [writers/*zero-copy* zero-copy?]
      (->> request
           (request/create-ring-request ctx)
           (handler)
           (add-keep-alive request)
           (response/write-ring-response ctx)))))

(gen-class :name ^{ChannelHandler$Sharable {}}
           fortress.ring.handler.FortressInitializer
           :extends io.netty.channel.ChannelInitializer
           :state state
           :init "init"
           :constructors {[Long Boolean clojure.lang.IFn] []}
           :prefix "finit-")

(defn finit-init [max-size zero-copy? handler]
  [[] (atom {:max-size max-size
             :zero-copy? zero-copy?
             :handler handler})])

(defn finit-initChannel [this ch]
  (let [pipeline (.pipeline ch)
        state (.state this)
        {:keys [max-size handler zero-copy?]} @state]
    (doto
      pipeline
      (if @debug-request
        (.addLast "logger" (LoggingHandler.)))   
      (.addLast "codec" (HttpServerCodec.))
      (.addLast "aggregator" (HttpObjectAggregator. max-size))
      (.addLast "chunkedWriter"  (ChunkedWriteHandler.))
      (.addLast "http-handler" (fortress.ring.handler.FortressHttpRequestHandler.
                                 zero-copy?
                                 handler)))))

