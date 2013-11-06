(ns fortress.ring.handler
  (:require [clojure.tools.logging :as log]
            [fortress.ring.writers :as writers]
            [fortress.ring.request :as request]
            [fortress.ring.response :as response]) 
  (:import [fortress.ring.spdy DefaultServerProvider DefaultSpdyOrHttpChooser]
           [fortress.ring.http MultipartDiskHandler]
           [io.netty.channel ChannelHandler$Sharable SimpleChannelInboundHandler]
           [io.netty.handler.stream ChunkedWriteHandler]
           [io.netty.handler.codec.http DefaultHttpRequest HttpServerCodec HttpObjectAggregator HttpHeaders]
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
           :constructors {[Boolean clojure.lang.IFn clojure.lang.IFn] []}
           :prefix "fhandler-")

(defn fhandler-init [zero-copy? handler error-fn]
  [[] (atom {:zero-copy? zero-copy?
             :handler handler
             :error-fn error-fn})])

(defn fhandler-exceptionCaught [this ctx cause]
  (log/debug cause "Error occurred in Http I/O thread")
  (let [state (.state this)
        {:keys [error-fn]} @state]
    (try
      (do
        (if error-fn
          (error-fn ctx cause))  
        (when (-> ctx (.channel) (.isOpen))
          (response/write-ring-response nil ctx {:status 500})))
      (catch Exception e
        (log/fatal e "Error when handling exception" cause)))))

(defn fhandler-channelRead0 [this ctx request]
  (let [{:keys [zero-copy? handler]} @(.state this)
        plain-request (if (instance? DefaultHttpRequest request)
                        request
                        (.getRequest request))]
    (binding [writers/*zero-copy* zero-copy?]
      (->> request
           (request/create-ring-request ctx)
           (handler)
           (add-keep-alive plain-request)
           (response/write-ring-response plain-request ctx)))))

(gen-class :name ^{ChannelHandler$Sharable {}}
           fortress.ring.handler.FortressInitializer
           :extends io.netty.channel.ChannelInitializer
           :state state
           :init "init"
           :constructors {[javax.net.ssl.SSLContext Long Boolean Boolean clojure.lang.IFn clojure.lang.IFn clojure.lang.IFn java.lang.String] []}
           :prefix "finit-")

(defn finit-init [ssl-context max-size zero-copy? ssl? handler error-fn build-listener tmp-dir-path]
  [[] (atom {:max-size max-size
             :zero-copy? zero-copy?
             :ssl? ssl?
             :ssl-context ssl-context
             :error-fn error-fn
             :handler handler
             :build-listener build-listener
             :tmp-dir-path tmp-dir-path})])

(defn finit-initChannel [this ch]
  (let [pipeline (.pipeline ch)
        state (.state this)
        {:keys [max-size handler zero-copy?
                error-fn ssl? ssl-context
                build-listener tmp-dir-path]} @state]

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
                                         handler
                                         error-fn)
                                       (.intValue max-size)
                                       (.intValue max-size)
                                       tmp-dir-path
                                       build-listener))))

    (when-not (and ssl? ssl-context)
      (doto
        pipeline
        (.addLast "codec" (HttpServerCodec.))
        (.addLast "multipart" (MultipartDiskHandler. (java.io.File. tmp-dir-path)
                                                     max-size
                                                     (if-not (nil? build-listener)
                                                       (build-listener))))
        (.addLast "aggregator" (HttpObjectAggregator. max-size))
        (.addLast "chunkedWriter"  (ChunkedWriteHandler.))
        (.addLast "http-handler" (fortress.ring.handler.FortressHttpRequestHandler.
                                   zero-copy?
                                   handler
                                   error-fn))))))

