(ns fortress.ring.response
  (:require [fortress.ring.writers :as w])
  (:import [io.netty.handler.codec.http
            HttpResponseStatus
            HttpVersion
            HttpHeaders
            DefaultFullHttpResponse
            DefaultFullHttpRequest]
           [io.netty.channel
            ChannelHandlerContext
            ChannelFutureListener]))

(defn write-ring-response [^DefaultFullHttpRequest request ^ChannelHandlerContext context ring-response]
  (let [status (HttpResponseStatus/valueOf (ring-response :status 200))
        {:keys [body headers]} ring-response]
    (w/write body
             headers
             (or (and request (.getProtocolVersion request))
                 HttpVersion/HTTP_1_1)
             status
             (and request (w/spdy-request? request))
             (.channel context))))
