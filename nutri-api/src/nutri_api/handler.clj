(ns nutri-api.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clj-http.client :as http]
            [cheshire.core :as json])) ; Para lidar com JSON

(defn buscar-alimentos [alimento]
  (let [url (str "https://caloriasporalimentoapi.herokuapp.com/api/calorias/?descricao=" alimento)
        response (http/get url {:as :json}) ; Pede resposta como JSON
        alimentos (:body response)]
    (take 5 alimentos))) ; Retorna os 5 primeiros alimentos

(defroutes app-routes
  (GET "/" [] "Para buscar alimentos, /alimento/:alimento")

  (GET "/alimento/:alimento" [alimento]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string (buscar-alimentos alimento))})



  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
