package com.battilana.nutribot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * DEF-15: horario de atencion de los asesores, en UN solo lugar (el backend).
 *
 * Antes la regla vivia en el frontend (usaba el reloj del navegador del cliente) y el backend
 * respondia "puedes hablar con un asesor con el boton de abajo" sin saber si habia asesor.
 * Fuera de horario el cliente veia dos mensajes contradictorios. Ahora el backend decide,
 * redacta el mensaje correcto y avisa al frontend (ChatResponse.asesorDisponible).
 *
 * Se configura en application.yml (nutribot.asesor.*): lunes a viernes y sabado con el formato
 * "HH:mm-HH:mm"; el domingo no hay atencion. La hora siempre se calcula en la zona de Peru,
 * sin importar la zona del servidor.
 */
@Component
public class HorarioAtencion {

    private final ZoneId zona;
    private final LocalTime semanaInicio;
    private final LocalTime semanaFin;
    private final LocalTime sabadoInicio;
    private final LocalTime sabadoFin;
    private final Clock reloj;

    @Autowired
    public HorarioAtencion(
            @Value("${nutribot.asesor.zona-horaria:America/Lima}") String zona,
            @Value("${nutribot.asesor.lunes-a-viernes:08:00-18:00}") String semana,
            @Value("${nutribot.asesor.sabado:08:00-13:00}") String sabado) {
        this(zona, semana, sabado, Clock.systemUTC());
    }

    /** Constructor para pruebas: permite fijar la fecha y hora con un reloj fijo. */
    HorarioAtencion(String zona, String semana, String sabado, Clock reloj) {
        this.zona = ZoneId.of(zona);
        LocalTime[] s = rango(semana);
        LocalTime[] sa = rango(sabado);
        this.semanaInicio = s[0];
        this.semanaFin = s[1];
        this.sabadoInicio = sa[0];
        this.sabadoFin = sa[1];
        this.reloj = reloj;
    }

    /** ¿Hay un asesor atendiendo en este momento? */
    public boolean disponible() {
        return disponibleEn(ZonedDateTime.now(reloj));
    }

    /** ¿Hay un asesor en ese instante? El inicio se incluye y el fin no (18:00 ya es fuera de horario). */
    boolean disponibleEn(ZonedDateTime instante) {
        ZonedDateTime local = instante.withZoneSameInstant(zona);
        LocalTime hora = local.toLocalTime();
        DayOfWeek dia = local.getDayOfWeek();
        if (dia == DayOfWeek.SUNDAY) {
            return false;
        }
        if (dia == DayOfWeek.SATURDAY) {
            return !hora.isBefore(sabadoInicio) && hora.isBefore(sabadoFin);
        }
        return !hora.isBefore(semanaInicio) && hora.isBefore(semanaFin);
    }

    /** Texto para el cliente, p. ej. "de lunes a viernes de 8:00 a 18:00 y los sábados de 8:00 a 13:00". */
    public String descripcion() {
        return "de lunes a viernes de " + hora(semanaInicio) + " a " + hora(semanaFin)
                + " y los sábados de " + hora(sabadoInicio) + " a " + hora(sabadoFin);
    }

    private static String hora(LocalTime t) {
        return t.getHour() + ":" + String.format("%02d", t.getMinute());
    }

    private static LocalTime[] rango(String texto) {
        String[] partes = texto.trim().split("-");
        if (partes.length != 2) {
            throw new IllegalArgumentException("Horario no valido (use HH:mm-HH:mm): " + texto);
        }
        LocalTime inicio = LocalTime.parse(partes[0].trim());
        LocalTime fin = LocalTime.parse(partes[1].trim());
        if (!inicio.isBefore(fin)) {
            throw new IllegalArgumentException("El inicio debe ser antes del fin: " + texto);
        }
        return new LocalTime[] {inicio, fin};
    }
}
