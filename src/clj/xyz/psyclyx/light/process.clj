(ns xyz.psyclyx.light.process
  "Generic supervised child-process helper.

   psyclight supervises mosquitto and zigbee2mqtt as children. This
   namespace owns the OS-level process plumbing: spawning, pumping
   stdout/stderr to the logger, and stopping (SIGTERM with a timed
   SIGKILL fallback). Higher-level components compose this with
   config-file generation and bus integration."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [java.io BufferedReader InputStreamReader]
           [java.lang Process ProcessBuilder]
           [java.util.concurrent TimeUnit]))

(defn- pump-stream
  "Pumps lines from `stream` to the logger as `(log-fn line)`. Returns a
   future that completes when the stream closes."
  [stream log-fn]
  (future
    (try
      (with-open [r (BufferedReader. (InputStreamReader. stream "UTF-8"))]
        (loop []
          (when-let [line (.readLine r)]
            (log-fn line)
            (recur))))
      (catch Throwable t
        (log/warn t "Log pump terminated")))))

(defn spawn!
  "Spawns a child process. `opts`:
     :command — vector of strings, e.g. [\"mosquitto\" \"-c\" \"/etc/m.conf\"]
     :env     — map of additional env vars (merged onto inherited env)
     :dir     — working directory (string or File, optional)
     :name    — short name used as log prefix (defaults to (first command))

   Returns a handle suitable for `alive?` and `stop!`."
  [{:keys [command env dir name]}]
  (let [name (or name (first command))
        pb   (doto (ProcessBuilder. ^java.util.List command)
               (.redirectErrorStream false))]
    (when dir (.directory pb (io/file dir)))
    (when env
      (let [pe (.environment pb)]
        (doseq [[k v] env]
          (.put pe (str k) (str v)))))
    (log/info "Spawning" name (vec command))
    (let [^Process proc (.start pb)
          tag           (str "[" name "]")
          out-fut       (pump-stream (.getInputStream proc)
                                     #(log/info tag %))
          err-fut       (pump-stream (.getErrorStream proc)
                                     #(log/warn tag %))]
      {:name    name
       :process proc
       :readers [out-fut err-fut]})))

(defn alive? [{:keys [^Process process]}]
  (boolean (and process (.isAlive process))))

(defn stop!
  "Stops a spawned process. Sends SIGTERM, waits up to `grace-ms`
   milliseconds, then SIGKILL if the process is still alive."
  ([handle] (stop! handle 5000))
  ([{:keys [name ^Process process readers]} grace-ms]
   (when (and process (.isAlive process))
     (log/info "Stopping" name)
     (.destroy process)
     (when-not (.waitFor process grace-ms TimeUnit/MILLISECONDS)
       (log/warn name "did not exit within" grace-ms "ms; sending SIGKILL")
       (.destroyForcibly process)
       (.waitFor process)))
   (doseq [r readers]
     (deref r 1000 nil))
   nil))
