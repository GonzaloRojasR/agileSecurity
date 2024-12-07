package com.agilesecurity2024.model;

import java.io.Serializable;
package com.agilesecurity2024.model;

import java.io.Serializable;

public class Pais implements Serializable {

    private static final long serialVersionUID = -2988002029080131424L;

    private String country;  // Cambiado de "common" a "country" para mayor claridad
    private String mensaje;
    private String capital;
    private String region;

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getCapital() {
        return capital;
    }

    public void setCapital(String capital) {
        this.capital = capital;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
