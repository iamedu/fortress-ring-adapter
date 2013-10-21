(ns fortress.ring.spdy
  (:import [fortress.util NettyUtil]
           [io.netty.handler.stream ChunkedWriteHandler]
           [org.eclipse.jetty.npn NextProtoNego]))

(gen-class :name fortress.ring.spdy.DefaultSpdyOrHttpChooser
           :extends io.netty.handler.codec.spdy.SpdyOrHttpChooser
           :prefix "ch-"
           :init "init"
           :state state
           :exposes-methods {addSpdyHandlers parentAddSpdyHandlers
                             addHttpHandlers parentAddHttpHandlers}
           :constructors {[io.netty.channel.ChannelInboundHandler Integer Integer] [int int]})

(defn ch-init [handler max-spdy-content-length max-http-content-length]
  [[max-spdy-content-length max-http-content-length] {:handler handler}])

(defn ch-addSpdyHandlers [this ctx version]
  (let [pipeline (NettyUtil/pipeline ctx)]
    (.parentAddSpdyHandlers this ctx version)
    (.addBefore pipeline "httpRquestHandler" "chunkedWriter" (ChunkedWriteHandler.))))

(defn ch-addHttpHandlers [this ctx]
  (let [pipeline (NettyUtil/pipeline ctx)]
    (.parentAddHttpHandlers this ctx)
    (.addBefore pipeline "httpRquestHandler" "chunkedWriter" (ChunkedWriteHandler.))))

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
  [#_"spdy/3" #_"spdy/2" "http/1.1"])

(defn sp-protocolSelected [this protocol]
  (let [state (.state this)]
    (swap! state assoc :protocol protocol)))

(defn sp-getSelectedProtocol [this]
  (let [state (.state this)]
    (:protocol @state)))

