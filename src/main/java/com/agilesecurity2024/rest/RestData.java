package com.agilesecurity2024.rest;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.agilesecurity2024.model.Pais;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@RestController
@RequestMapping(path = "/rest/mscovid")
public class RestData {

    private final static Logger LOGGER = Logger.getLogger("devops.subnivel.Control");

    @GetMapping(path = "/test", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody Pais getData(@RequestParam(name = "msg") String message) {
        LOGGER.log(Level.INFO, "Proceso exitoso de prueba");

        Pais response = new Pais();
        response.setMensaje("Mensaje Recibido: " + message);
        return response;
    }

    @GetMapping(path = "/estadoPais", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody Pais getPaisInfo(@RequestParam(name = "pais") String message) {
        RestTemplate restTemplate = new RestTemplate();

        // Llama a la API de RestCountries
        ResponseEntity<String> call = restTemplate.getForEntity("https://restcountries.com/v3.1/name/" + message, String.class);

        LOGGER.log(Level.INFO, "Consulta por país: " + message);

        // Inicializa el objeto de respuesta
        Pais response = new Pais();

        try {
            // Procesa la respuesta de la API
            JsonArray countries = JsonParser.parseString(call.getBody()).getAsJsonArray();

            if (!countries.isEmpty()) {
                JsonObject country = countries.get(0).getAsJsonObject();

                // Obtiene los valores del nombre común, la capital y el continente
                String common = country.getAsJsonObject("name").get("common").getAsString();
                String capital = country.has("capital") ? country.getAsJsonArray("capital").get(0).getAsString() : "No disponible";
                String continente = country.has("continents") ? country.getAsJsonArray("continents").get(0).getAsString() : "No disponible";

                // Llena el objeto de respuesta
                response.setCountry(common);
                response.setCapital(capital);
                response.setRegion(continente);
                response.setMensaje("ok");
            } else {
                response.setMensaje("No se encontró información para el país: " + message);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al procesar la respuesta de la API: " + e.getMessage(), e);
            response.setMensaje("Error al procesar la solicitud.");
        }

        return response;
    }
}
