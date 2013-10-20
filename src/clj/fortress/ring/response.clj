(ns fortress.ring.response
  (:require [fortress.ring.writers :as w])
  (:import [io.netty.handler.codec.http
            HttpResponseStatus
            HttpVersion
            HttpHeaders
            DefaultFullHttpResponse]
           [io.netty.channel
            ChannelHandlerContext
            ChannelFutureListener]))

(defn write-ring-response [^ChannelHandlerContext context ring-response]
  (let [status (HttpResponseStatus/valueOf (ring-response :status 200))
        {:keys [body headers]} ring-response]
    (w/write body headers HttpVersion/HTTP_1_1 status (.channel context))))
