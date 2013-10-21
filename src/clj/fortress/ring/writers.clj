(ns fortress.ring.writers
  (:require [clojure.java.io :as io])
  (:import [io.netty.channel Channel ChannelFutureListener ChannelFuture DefaultFileRegion]
           [io.netty.handler.codec.http HttpResponse DefaultHttpResponse DefaultFullHttpResponse HttpHeaders HttpHeaders$Names]
           [io.netty.handler.stream ChunkedStream ChunkedFile]
           [io.netty.buffer Unpooled]
           [java.io InputStream File RandomAccessFile]
           [java.net URLConnection]
           [java.nio.charset Charset]
           [clojure.lang ISeq]))

(def ^:dynamic *zero-copy* false)
(def default-charset (Charset/forName "UTF-8"))

(def charset-pattern
  "Regex to extract the charset from a content-type header.
  See: http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7"
  #";\s*charset=\"?([^\s;\"]+)\"?")

(defn ^Charset get-charset
  "Extracts the charset from the content-type header, if present.
  Returns nil if the charset cannot be discovered."
  [headers]
  (if-let [content-type (find headers #(= "content-type" (.toLowerCase %)))]
    (if-let [[_ charset] (re-find charset-pattern content-type)]
      (Charset/forName charset))))

(defn- keep-alive? [^HttpResponse response]
  (= "keep-alive" (HttpHeaders/getHeader response "connection")))

(defn- add-close-listener [^ChannelFuture future ^HttpResponse response]
  (if (keep-alive? response)
    (.addListener future ChannelFutureListener/CLOSE_ON_FAILURE)
    (.addListener future ChannelFutureListener/CLOSE)))

(defn- add-close-stream-listener [^ChannelFuture future ^InputStream stream]
  (let [listener (reify ChannelFutureListener (operationComplete [_ _] (.close stream)))]
    (.addListener future listener)
    (.addListener future ChannelFutureListener/CLOSE)))

(defn- write-response [^HttpResponse response ^Channel channel]
  (-> (.write channel response)
      (add-close-listener response))
  (.flush channel))

(defn set-headers [^DefaultHttpResponse response headers]
  (doseq [[key values] headers]
    (.set (.headers response) key values)))

(defprotocol ResponseWriter
  "Provides the best way to write a response for the give ring response body"
  (write [body headers version status ^Channel channel]))

(extend-type String
  ResponseWriter
  (write [body headers version status ^Channel channel]
    (let [charset (or (get-charset headers) default-charset)
          buffer (Unpooled/copiedBuffer body charset)
          response (DefaultFullHttpResponse. version status buffer)]
      (set-headers response headers)
      (HttpHeaders/setContentLength response (.readableBytes buffer))
      (write-response response channel))))

(extend-type ISeq
  ResponseWriter
  (write [body headers version status ^Channel channel]
    (write (apply str body) headers version status channel)))

(extend-type InputStream
  ResponseWriter
  (write [body headers version status ^Channel channel]
    (let [response (DefaultHttpResponse. version status)]
      (set-headers response headers)
      (.write channel response)
      (-> (.writeAndFlush channel (ChunkedStream. body))
          (add-close-stream-listener body)))))

(defn file-body [file]
  (let [random-access-file (RandomAccessFile. file "r")]
    (if *zero-copy*
      (DefaultFileRegion. (.getChannel random-access-file) 0 (.length file))
      (ChunkedFile. random-access-file))))

(extend-type File
  ResponseWriter
  (write [body headers version status ^Channel channel]
    (let [response (DefaultHttpResponse. version status)
          response-body (file-body body)]
      (set-headers response (merge headers {"Zero-Copy" *zero-copy*}))
      (HttpHeaders/setContentLength response (.length body))
      (.write channel response)
      (-> (.writeAndFlush channel response-body)
          (.addListener ChannelFutureListener/CLOSE)))))

(extend-type nil
  ResponseWriter
  (write [body headers version status ^Channel channel]
    (let [response (DefaultHttpResponse. version status)]
      (HttpHeaders/setContentLength response 0)
      (write-response response channel))))

