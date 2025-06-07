;Backend(funcionando , com tradução e com editr dados)

(ns nutri-api.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter]
           [java.net URLEncoder]))

(def historico-backend (atom []))
(def perfil-backend (atom {}))

(def formatter (DateTimeFormatter/ofPattern "dd/MM/yyyy"))

(defn parse-data [data-str]
  (LocalDate/parse data-str formatter))

(defn buscar-alimentos [alimento]
  (let [url (str "https://caloriasporalimentoapi.herokuapp.com/api/calorias/?descricao=" alimento)
        response (http/get url {:as :json})]
    (take 5 (:body response))))

(defn buscar-exercicio [atividade duracao]
  (let [url "https://api.api-ninjas.com/v1/caloriesburned"
        api-key "AvtpXzPAroZTe/Ew3EnwNg==7FdetbFmWOWlxZZj"
        response (http/get url
                           {:headers {"X-Api-Key" api-key}
                            :query-params {"activity" atividade
                                           "duration" (str duracao)}
                            :as :json
                            :throw-exceptions false})]
    (take 5 (:body response))))

(defn traduzir [texto idioma]
  (let [url (str "https://ftapi.pythonanywhere.com/translate?dl=" idioma "&text=" (URLEncoder/encode texto "UTF-8"))
        resp (http/get url {:as :json :throw-exceptions false})
        corpo (:body resp)]
    (:destination-text corpo)))

(defn traduzir-input [input]
  (let [traducao (traduzir input "en")]
    (println "Traduzir-input: [" input "] -> [" traducao "]")
    traducao))

(defn traduzir-output [output]
  (traduzir output "pt"))

(defroutes app-routes
  (GET "/" [] "Para buscar alimentos, use /alimento/:alimento")

  (GET "/alimento/:alimento" [alimento]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string (buscar-alimentos alimento))})

  (POST "/consumo" request
    (let [body (:body request)
          dados (json/parse-stream (io/reader body) true)]
      (swap! historico-backend conj (assoc dados :tipo "ganho"))
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:mensagem "Consumo registrado com sucesso"})}))

  (POST "/exercicio" request
  (let [body (:body request)
        dados (json/parse-stream (io/reader body) true)
        {:keys [atividade]} dados
        atividade-en (traduzir-input atividade)
        resultados (buscar-exercicio atividade-en 60)]
    (if (empty? resultados)
      {:status 400
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:erro "Nenhum exercício encontrado com esse nome."})}
      (let [resumo (mapv (fn [{:keys [name calories_per_hour]}]
                           {:nome (traduzir-output name)
                            :calorias_por_hora calories_per_hour})
                         resultados)]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string {:sugestoes resumo})}))))

(POST "/registrar-exercicio" request
  (let [body (:body request)
        dados (json/parse-stream (io/reader body) true)
        atividade (:atividade dados)
        duracao (:duracao dados)
        calorias-por-hora (:calorias-por-hora dados)
        data (:data dados)
        _ (println "Dados recebidos no backend:" dados)
        _ (println "atividade:" atividade ", duracao:" duracao ", calorias-por-hora:" calorias-por-hora ", data:" data)]
    (if (and atividade duracao calorias-por-hora data)
      (let [calorias-total (int (/ (* calorias-por-hora duracao) 60))
            exercicio {:tipo "perda"
                       :atividade atividade
                       :duracao duracao
                       :calorias-total calorias-total
                       :data data}]
        (swap! historico-backend conj exercicio)
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string {:mensagem "Exercício registrado com sucesso"
                                      :exercicio exercicio})})
      {:status 400
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:erro "Campos obrigatórios ausentes no JSON recebido."
                                    :dados dados})})))





  (GET "/perfil" []
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string @perfil-backend)})

  (POST "/perfil" request
    (let [body (:body request)
          dados (json/parse-stream (io/reader body) true)]
      (reset! perfil-backend dados)
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:mensagem "Perfil salvo com sucesso"})}))

  (GET "/extrato" [inicio fim]
    (let [inicio-data (parse-data inicio)
          fim-data (parse-data fim)
          no-periodo (filter (fn [{:keys [data]}]
                               (let [data (parse-data data)]
                                 (or (= data inicio-data)
                                     (= data fim-data)
                                     (and (.isAfter data inicio-data)
                                          (.isBefore data fim-data)))))
                             @historico-backend)]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string no-periodo)}))

  (GET "/saldo" [inicio fim]
    (let [inicio-data (parse-data inicio)
          fim-data (parse-data fim)
          no-periodo (filter (fn [{:keys [data]}]
                               (let [data (parse-data data)]
                                 (or (= data inicio-data)
                                     (= data fim-data)
                                     (and (.isAfter data inicio-data)
                                          (.isBefore data fim-data)))))
                             @historico-backend)
          ganho (reduce + 0 (map :calorias-total (filter #(= (:tipo %) "ganho") no-periodo)))
          perda (reduce + 0 (map :calorias-total (filter #(= (:tipo %) "perda") no-periodo)))
          saldo (- ganho perda)]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:ganho ganho :perda perda :saldo saldo})}))

  (route/not-found "Requisição não encontrada"))

(def app
  (wrap-defaults app-routes api-defaults))