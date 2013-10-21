(ns fortress.ring.server-test
  (:refer-clojure :exclude [get])
  (:use clojure.test
        fortress.ring.server
        compojure.core)
  (:require [clj-http.client :as client]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.util.response :as response]))

(def ^:const server "http://localhost:8080")
(declare get post put make-request)

(deftest simple
  (is (= (get "/") "Hello World")))

(deftest request-method
  (is (= (get "/method") "get"))
  (is (= (post "/method") "post"))
  (is (= (put "/method") "put")))

(deftest uri
  (is (= (get "/uri/me") "/uri/me"))
  (is (= (get "/uri/you?help=me") "/uri/you")))

(deftest query-string
  (is (= (get "/query?you=me") "you=me"))
  (is (= (get "/query?me=you&you=I") "me=you&you=I")))

(deftest server-name
  (is (= (get "/serverName") "localhost")))

(deftest server-port
  (is (= (get "/port") "8080")))

(deftest remote-address
  (is (= (get "/remoteAddress") "127.0.0.1")))

(deftest scheme
  (is (= (get "/scheme") "http"))
  (is (= (make-request :get "/scheme" {:headers {"X-Scheme" "https"}}) "https")))

(deftest content-type
  (is (= (make-request :get "/contentType" {:content-type :json}) "application/json"))
  (is (= (make-request :get "/contentType" {:content-type :html}) "application/html")))

(deftest chracter-encoding
  (is (= (:body (client/post (str server "/characterEncoding") {:headers {"Content-Encoding" "UTF-8"}}) "UTF-8"))))

(deftest headers
  (is (= (get "/headers") "localhost:8080")))

(deftest request-body
  (is (= "Foo Bar" (make-request :post "/requestbody" {:body "Foo Bar"})))
  (is (= (slurp "./test/fortress/ring/response.txt") (make-request :post "/requestbody" {:body (io/input-stream "./test/fortress/ring/response.txt") :length -1}))))

(deftest response-headers
  (is (= "bar" (get-in (client/get (str server "/responseHeaders/single")) [:headers "foo"])))
  (is (= ["bar" "baz"] (get-in (client/get (str server "/responseHeaders/multiple")) [:headers "foo"]))))

(deftest response-body-types
  (is (= "agoodresponse" (get "/ISeqResponse")))
  (is (= "afineresponse" (get "/InputStreamResponse")))
  (is (= (slurp "./test/fortress/ring/response.txt") (get "/FileResponse/response.txt")))
  (is (= (slurp "./test/fortress/ring/response.json") (get "/FileResponse/response.json"))))

(deftest bad-responses
  (is (= "" (get "/EmptyResponse")))
  (is (= 500 (:status (client/get (str server "/Exception") {:throw-exceptions false})))))

(deftest keep-alive
  (client/with-connection-pool {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
    (is (= "agoodresponse" (get "/ISeqResponse")))
    (is (= "Keep-Alive" (get-in (client/get (str server "/headers")) [:headers "connection"])))
    (is (= "afineresponse" (get "/InputStreamResponse")))
    (is (= "" (get "/EmptyResponse")))
    (is (= (slurp "./test/fortress/ring/response.txt") (get "/FileResponse/response.txt")))))

(defn header-handler [request]
  (if (.contains (:uri request) "single")
    {:status 200 :headers {"foo" "bar"}}
    {:status 200 :headers {"foo" ["bar" "baz"]}}))

(defn exception-handler [request]
  (throw (Exception. "Bad things happen")))

(defroutes test-routes
  (GET "/" [] "Hello World")
  (ANY "/method" [] #(name (:request-method %)))
  (GET "/uri/*" [] #(:uri %))
  (GET "/query" [] #(:query-string %))
  (GET "/serverName" [] #(:server-name %))
  (GET "/port" [] #(str (:server-port %)))
  (GET "/remoteAddress" [] #(:remote-addr %))
  (GET "/scheme" [] #(name (:scheme %)))
  (GET "/contentType" [] #(:content-type %))
  (POST "/characterEncoding" [] #(:character-encoding %))
  (GET "/headers" [] #((:headers %) "host"))
  (POST "/requestbody" [] #(slurp (:body %)))
  (GET "/responseHeaders/*" [] header-handler)
  (GET "/ISeqResponse" [] {:status 200 :body '("a" "good" "response")})
  (GET "/InputStreamResponse" [] {:status 200 :body (io/input-stream (.getBytes "afineresponse"))})
  (GET "/FileResponse/:current-file" [current-file] (response/file-response (str "./test/fortress/ring/" current-file)))
  (GET "/EmptyResponse" [] {:status 200 :body nil})
  (GET "/Exception" [] exception-handler)
  (route/not-found "Unknown"))

(defn server-fixture [f]
  (let [fortress (run-fortress test-routes :port 8080)]
    (f)
    (stop-fortress fortress)))

(use-fixtures :each server-fixture)

(defn make-request [method path options]
  (:body (client/request (merge {:method method :url (str server path)} options))))

(defn request [f]
  #(:body (f (str server %))))

(def get (request client/get))
(def post (request client/post))
(def put (request client/put))
