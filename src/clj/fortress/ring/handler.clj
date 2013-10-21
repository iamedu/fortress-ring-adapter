(ns fortress.ring.handler
  (:require [clojure.tools.logging :as log]
            [fortress.ring.writers :as writers]
            [fortress.ring.request :as request]
            [fortress.ring.response :as response]) 
  (:import [fortress.ring.spdy DefaultServerProvider DefaultSpdyOrHttpChooser]
           [io.netty.channel ChannelHandler$Sharable SimpleChannelInboundHandler]
           [io.netty.handler.stream ChunkedWriteHandler]
           [io.netty.handler.codec.http HttpServerCodec HttpObjectAggregator HttpHeaders]
           [io.netty.handler.logging LoggingHandler]
           [io.netty.handler.ssl SslHandler]
           [org.eclipse.jetty.npn NextProtoNego]
           [javax.net.ssl SSLContext]))

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

(defn fhandler-exceptionCaught [this ctx cause]
  (log/error cause "Error occurred in Http I/O thread")
  (when (-> ctx (.channel) (.isOpen))
    (response/write-ring-response ctx {:status 500})))

(defn fhandler-channelRead0 [this ctx request]
  (log/info "Aqui estoy si!")
  (let [{:keys [zero-copy? handler]} @(.state this)]
    (binding [writers/*zero-copy* zero-copy?]
      (->> request
           (request/create-ring-request ctx)
           (handler)
           (add-keep-alive request)
           (response/write-ring-response request ctx)))))

(gen-class :name ^{ChannelHandler$Sharable {}}
           fortress.ring.handler.FortressInitializer
           :extends io.netty.channel.ChannelInitializer
           :state state
           :init "init"
           :constructors {[javax.net.ssl.SSLContext Long Boolean Boolean clojure.lang.IFn] []}
           :prefix "finit-")

(defn finit-init [ssl-context max-size zero-copy? ssl? handler]
  [[] (atom {:max-size max-size
             :zero-copy? zero-copy?
             :ssl? ssl?
             :ssl-context ssl-context
             :handler handler})])

(defn finit-initChannel [this ch]
  (let [pipeline (.pipeline ch)
        state (.state this)
        {:keys [max-size handler zero-copy? ssl? ssl-context]} @state]

    (if @debug-request
      (.addLast pipeline "logger" (LoggingHandler.)))

    (when (and ssl? ssl-context)
      (let [engine (.createSSLEngine ssl-context)]
        (.setUseClientMode engine false)
        (NextProtoNego/put engine (DefaultServerProvider.))
        (.addLast pipeline "ssl" (SslHandler. engine))
        (.addLast pipeline "chooser" (DefaultSpdyOrHttpChooser.
                                       (fortress.ring.handler.FortressHttpRequestHandler.
                                         zero-copy?
                                         handler)
                                       Integer/MAX_VALUE
                                       Integer/MAX_VALUE))))

    (when-not (and ssl? ssl-context)
      (doto
        pipeline
        (.addLast "codec" (HttpServerCodec.))
        (.addLast "aggregator" (HttpObjectAggregator. max-size))
        (.addLast "chunkedWriter"  (ChunkedWriteHandler.))
        (.addLast "http-handler" (fortress.ring.handler.FortressHttpRequestHandler.
                                   zero-copy?
                                   handler))))))

