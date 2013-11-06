(ns fortress.ring.spdy
  (:import [fortress.util NettyUtil]
           [fortress.ring.spdy SpdyChunkedWriteHandler SpdyResponseStreamIdHandler]
           [fortress.ring.http MultipartDiskHandler]
           [io.netty.handler.stream ChunkedWriteHandler]
           [io.netty.handler.codec.spdy InstrumentedSpdyHttpDecoder]
           [org.eclipse.jetty.npn NextProtoNego]))

(gen-class :name fortress.ring.spdy.DefaultSpdyOrHttpChooser
           :extends io.netty.handler.codec.spdy.SpdyOrHttpChooser
           :prefix "ch-"
           :init "init"
           :state state
           :exposes-methods {addSpdyHandlers parentAddSpdyHandlers
                             addHttpHandlers parentAddHttpHandlers}
           :constructors {[io.netty.channel.ChannelInboundHandler Integer Integer java.lang.String clojure.lang.IFn] [int int]})

(defn ch-init [handler max-spdy-content-length max-http-content-length temp-dir-path listener-builder]
  [[max-spdy-content-length max-http-content-length] {:handler handler
                                                      :max-http-content-length max-http-content-length
                                                      :max-spdy-content-length max-spdy-content-length
                                                      :temp-dir-path temp-dir-path
                                                      :listener-builder listener-builder}])

(defn ch-addSpdyHandlers [this ctx version]
  (let [state (.state this)
        {:keys [max-spdy-content-length temp-dir-path listener-builder]} state
        pipeline (NettyUtil/pipeline ctx)]
    (.parentAddSpdyHandlers this ctx version)
    (.addBefore pipeline "spdyHttpDecoder" "instrumentedSpdyHttpDecoder" (InstrumentedSpdyHttpDecoder.
                                                                           version
                                                                           max-spdy-content-length
                                                                           (java.io.File. temp-dir-path)
                                                                           listener-builder))
    (.remove pipeline "spdyHttpDecoder")
    (.addBefore pipeline "spdyStreamIdHandler" "fortressSpdyStreamIdHandler" (SpdyResponseStreamIdHandler.))
    (.remove pipeline "spdyStreamIdHandler")
    (.addBefore pipeline "httpRquestHandler" "chunkedWriter" (SpdyChunkedWriteHandler.))))

(defn ch-addHttpHandlers [this ctx]
  (let [state (.state this)
        {:keys [max-http-content-length temp-dir-path listener-builder]} state
        pipeline (NettyUtil/pipeline ctx)]
    (.parentAddHttpHandlers this ctx)
    (.addBefore pipeline "httpRquestHandler" "chunkedWriter" (ChunkedWriteHandler.))  
    (.addAfter pipeline "httpRquestDecoder" "multipart" (MultipartDiskHandler. (java.io.File. temp-dir-path)
                                                                            max-http-content-length
                                                                            (if-not (nil? listener-builder)
                                                                              (listener-builder))))))

(defn ch-getProtocol [this engine]
  (let [provider (NextProtoNego/get engine)
        protocol (.getSelectedProtocol provider)]
    (case protocol
      "spdy/2" io.netty.handler.codec.spdy.SpdyOrHttpChooser$SelectedProtocol/SPDY_2
      "spdy/3" io.netty.handler.codec.spdy.SpdyOrHttpChooser$SelectedProtocol/SPDY_3
      io.netty.handler.codec.spdy.SpdyOrHttpChooser$SelectedProtocol/HTTP_1_1)))

(defn ch-createHttpRequestHandlerForHttp [this]
  (let [state (.state this)
        handler (:handler state)]
    handler))

(gen-class :name fortress.ring.spdy.DefaultServerProvider
           :implements [org.eclipse.jetty.npn.NextProtoNego$ServerProvider]
           :prefix "sp-"
           :init "init"
           :methods [[getSelectedProtocol [] String]]
           :state state)

(defn sp-init []
  [[] (atom {:protocol nil})])

(defn sp-unsupported [this]
  (let [state (.state this)]
    (swap! state assoc :protocol "http/1.1")))

(defn sp-protocols [this]
  ["spdy/3" "spdy/2" "http/1.1"])

(defn sp-protocolSelected [this protocol]
  (let [state (.state this)]
    (swap! state assoc :protocol protocol)))

(defn sp-getSelectedProtocol [this]
  (let [state (.state this)]
    (:protocol @state)))

