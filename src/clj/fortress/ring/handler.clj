(ns fortress.ring.handler
  (:import [io.netty.channel ChannelHandler$Sharable SimpleChannelInboundHandler]
           [io.netty.handler.codec.http HttpServerCodec HttpObjectAggregator]))

(gen-class :name ^{ChannelHandler$Sharable {}}
                 fortress.ring.handler.FortressHttpRequestHandler
           :extends io.netty.channel.SimpleChannelInboundHandler
           :prefix "fhandler-")

(defn fhandler-channelRead0 [this ctx request]
  (println (.headers request)))

(gen-class :name ^{ChannelHandler$Sharable {}}
                 fortress.ring.handler.FortressInitializer
           :extends io.netty.channel.ChannelInitializer
           :state state
           :init "init"
           :constructors {[Long] []}
           :prefix "finit-")

(defn finit-init [max-size]
  [[] (atom {:max-size max-size})])

(defn finit-initChannel [this ch]
  (let [pipeline (.pipeline ch)
        state (.state this)
        max-size (:max-size @state)]
    (doto
      pipeline
      (.addLast "codec" (HttpServerCodec.))
      (.addLast "aggregator" (HttpObjectAggregator. max-size))
      (.addLast "http-handler" (fortress.ring.handler.FortressHttpRequestHandler.)))))
