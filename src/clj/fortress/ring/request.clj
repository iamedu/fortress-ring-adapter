(ns fortress.ring.request
  (:require [clojure.string :as s])
  (:import [io.netty.buffer ByteBufInputStream]
           [io.netty.channel ChannelHandlerContext]
           [io.netty.handler.codec.http HttpMethod DefaultFullHttpRequest HttpHeaders HttpHeaders$Names]))

(def method-mapping
  {HttpMethod/GET :get
   HttpMethod/POST :post
   HttpMethod/PUT :put
   HttpMethod/TRACE :trace
   HttpMethod/PATCH :patch
   HttpMethod/OPTIONS :options
   HttpMethod/DELETE :delete
   HttpMethod/HEAD :head
   HttpMethod/CONNECT :connect})

(defn method [^HttpMethod method]
  (if-let [method-keyword (method-mapping method)]
    method-keyword
    (-> method (.getName) (s/lower-case) (keyword))))

(defn url [request-uri]
  (let [regex #"([^?]+)[?]?([^?]+)?"
        [match uri query] (re-find regex request-uri)]
    [uri query]))

(defn hostname [^DefaultFullHttpRequest request]
  (when-let [host (HttpHeaders/getHost request)]
    (get (s/split host #":") 0)))

(defn local-address [^ChannelHandlerContext context]
  (-> context .channel .localAddress))

(defn server-name [^ChannelHandlerContext context ^DefaultFullHttpRequest request]
  (if-let [host (hostname request)]
    host
    (.getHostName (local-address context))))

(defn remote-address [^ChannelHandlerContext context]
  (-> context
    .channel
    .remoteAddress
    .getAddress
    .getHostAddress))

(defn scheme [^DefaultFullHttpRequest request]
  (let [scheme (HttpHeaders/getHeader request "X-Scheme" "http")]
    (keyword scheme)))

(defn content-type [^DefaultFullHttpRequest request]
  (if-let [type (HttpHeaders/getHeader request HttpHeaders$Names/CONTENT_TYPE)]
    (-> type
      (s/split #";")
      (get 0)
      s/trim
      s/lower-case)))

(defn content-length [^DefaultFullHttpRequest request]
  (let [length (HttpHeaders/getContentLength request 0)]
    (when (> length 0) length)))

(defn character-encoding [^DefaultFullHttpRequest request]
  (HttpHeaders/getHeader request HttpHeaders$Names/CONTENT_ENCODING))

(defn headers [^DefaultFullHttpRequest req]
  (let [headers (.headers req)
        keys (map (comp s/lower-case key) headers)
        vals (map val headers)]
    (zipmap keys vals)))

(defn create-ring-request [^ChannelHandlerContext context ^DefaultFullHttpRequest http-request]
  (let [[uri query] (url (.getUri http-request))]
    {:body (ByteBufInputStream. (.content http-request))
     :uri uri
     :query-string query
     :request-method (method (.getMethod http-request))
     :server-name (server-name context http-request)
     :server-port (.getPort (local-address context))
     :remote-addr (remote-address context)
     :scheme (scheme http-request)
     :content-type (content-type http-request)
     :content-length (content-length http-request)
     :character-encoding (character-encoding http-request)
     :headers (headers http-request)}))
