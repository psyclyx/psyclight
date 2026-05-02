(ns xyz.psyclyx.light.web.sse
  "Datastar SSE primitives. Datastar v1 patches the DOM with two event
   types: `datastar-patch-elements` (HTML fragments by selector / id)
   and `datastar-patch-signals` (JSON signal patches). Everything else
   is plain HTTP.

   This namespace is concerned with the wire format: builders that
   produce the byte sequences and a Ring response wrapping a sequence
   of events as a streaming SSE body."
  (:require [charred.api :as json]
            [clojure.core.async :as a]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [dev.onionpancakes.chassis.core :as h]
            [ring.core.protocols :as ring-proto]
            [xyz.psyclyx.light.event-bus :as bus])
  (:import [java.io OutputStream]))

;; -- Event builders -------------------------------------------------------

(defn patch-elements
  "Builds a `datastar-patch-elements` SSE event string. `html` may be
   a chassis hiccup form or a pre-rendered string. Optional :selector
   targets a specific element; otherwise datastar morphs by element id.
   :mode is one of \"morph\", \"append\", \"prepend\", etc."
  [html & {:keys [selector mode]}]
  (let [html-str (if (string? html) html (str (h/html html)))
        sb       (StringBuilder.)]
    (.append sb "event: datastar-patch-elements\n")
    (when selector
      (.append sb "data: selector ") (.append sb (str selector)) (.append sb "\n"))
    (when mode
      (.append sb "data: mode ") (.append sb (str mode)) (.append sb "\n"))
    (doseq [line (str/split-lines html-str)]
      (.append sb "data: elements ") (.append sb line) (.append sb "\n"))
    (.append sb "\n")
    (.toString sb)))

(defn patch-signals
  "Builds a `datastar-patch-signals` SSE event string. `signals` is a
   map of signal names to values; serialized as JSON."
  [signals]
  (str "event: datastar-patch-signals\n"
       "data: signals " (json/write-json-str signals) "\n\n"))

(defn keepalive
  "An SSE comment used as a heartbeat; ignored by the client but keeps
   intermediaries from culling the connection."
  []
  ": keepalive\n\n")

;; -- Ring streaming response ---------------------------------------------

(defn response
  "Wraps a sequence of SSE event strings as a streaming Ring response.
   The connection closes when the sequence is exhausted or the writer
   throws (typically on client disconnect)."
  [events]
  {:status  200
   :headers {"content-type"  "text/event-stream"
             "cache-control" "no-cache"
             "connection"    "keep-alive"
             "x-accel-buffering" "no"}
   :body
   (reify ring-proto/StreamableResponseBody
     (write-body-to-stream [_ _response os]
       (try
         (doseq [event events]
           (let [bs (.getBytes ^String (str event) "UTF-8")]
             (.write ^OutputStream os bs 0 (alength bs))
             (.flush ^OutputStream os)))
         (catch java.io.IOException _ ;; client disconnect
           )
         (finally
           (.close ^OutputStream os)))))})

;; -- Bus → SSE fan-out ----------------------------------------------------

(defn bus-stream
  "Returns a lazy seq of SSE event strings produced by mapping each
   bus event with `event->sse`. The seq terminates when the underlying
   channel closes; an initial `prelude` (e.g. an immediate state dump)
   is emitted before the live stream begins.

   `topics` — collection of bus topics to subscribe to.
   `event->sse` — fn from event map to SSE event string (or nil to skip)."
  [event-bus topics event->sse {:keys [prelude buf]
                                 :or   {prelude [] buf 64}}]
  (let [chans (mapv #(bus/subscribe! event-bus % (a/sliding-buffer buf))
                    topics)
        merged (a/merge chans)
        live   ((fn step []
                  (lazy-seq
                    (when-let [ev (a/<!! merged)]
                      (if-let [sse (try (event->sse ev)
                                        (catch Throwable t
                                          (log/error t "SSE encode failed")
                                          nil))]
                        (cons sse (step))
                        (step))))))]
    (concat prelude
            live
            (lazy-seq
              (do (doseq [[topic ch] (map vector topics chans)]
                    (bus/unsubscribe! event-bus topic ch))
                  nil)))))

;; -- Signal extraction ----------------------------------------------------

(defn signals
  "Extracts Datastar signals from a Ring request. GET passes them in
   the `datastar` query param; POST/PUT/PATCH pass them as the JSON
   body."
  [req]
  (case (:request-method req)
    :get (when-let [s (get-in req [:query-params "datastar"])]
           (json/read-json s :key-fn keyword))
    (when-let [body (:body req)]
      (json/read-json (slurp body) :key-fn keyword))))

(defn signal [req k] (get (signals req) k))
