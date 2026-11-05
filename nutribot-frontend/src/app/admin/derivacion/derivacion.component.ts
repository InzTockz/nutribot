import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiAdminService } from '../../core/api-admin.service';
import { Interaccion } from '../../core/modelos';
import { fechaIso } from '../../shared/descargar';

interface Explicacion { titulo: string; detalle: string; }

/**
 * Sprint 4 — Clasificacion y derivacion (ADMIN y ASESOR): por que NutriBot derivo cada consulta.
 * Muestra solo lo que el sistema realmente decide: tipo de consulta (camino), regla aplicada y accion.
 * NutriBot no usa un clasificador con "porcentaje de confianza": decide con reglas y con el umbral
 * de similitud del RAG, y asi se explica aqui.
 */
@Component({
  selector: 'app-derivacion',
  imports: [CommonModule, FormsModule],
  templateUrl: './derivacion.component.html'
})
export class DerivacionComponent implements OnInit {

  desde = '';
  hasta = '';
  lista: Interaccion[] = [];
  sel: Interaccion | null = null;
  error = '';

  constructor(private api: ApiAdminService) {}

  ngOnInit(): void {
    const hoy = new Date();
    const inicio = new Date();
    inicio.setDate(hoy.getDate() - 29);
    this.desde = fechaIso(inicio);
    this.hasta = fechaIso(hoy);
    this.cargar();
  }

  cargar(): void {
    this.error = '';
    this.api.derivaciones(this.desde, this.hasta).subscribe({
      next: (l) => { this.lista = l; this.sel = l[0] ?? null; },
      error: (err) => (this.error = err?.error?.message || 'No se pudieron cargar las derivaciones.')
    });
  }

  /** Paso 2: como se clasifico la consulta */
  clasificacion(i: Interaccion): Explicacion {
    switch (i.camino) {
      case 'PRECIO': return { titulo: 'Precio o disponibilidad', detalle: 'La consulta menciona precio, stock o compra: la búsqueda se restringe a la lista de precios y al producto consultado.' };
      case 'ASESOR': return { titulo: 'Pedido de asesor', detalle: 'El cliente pidió hablar con una persona: no se consulta la base de conocimiento.' };
      case 'UBICACION': return { titulo: 'Dirección o dónde comprar', detalle: 'El cliente preguntó por la dirección, una tienda o dónde comprar: se responde con el mensaje oficial (no hay tienda; las ventas son por WhatsApp).' };
      case 'SIN_INFO': return { titulo: 'Sin información', detalle: 'La búsqueda no encontró fragmentos parecidos o el tema de la consulta no aparece en la base de conocimiento (DEF-16).' };
      case 'REFORMULACION': return { titulo: 'Reformulación', detalle: 'El cliente pidió explicar mejor la respuesta anterior: se responde con el historial.' };
      default: return { titulo: 'Consulta general', detalle: 'Información técnica o de la empresa: búsqueda en toda la base de conocimiento.' };
    }
  }

  /** Paso 3: regla que llevo a derivar u ofrecer el asesor */
  regla(i: Interaccion): Explicacion {
    if (i.camino === 'ASESOR') {
      return { titulo: 'El cliente lo pidió', detalle: 'El mensaje contiene una palabra como «asesor», «humano», «vendedor» o «una persona».' };
    }
    if (i.camino === 'UBICACION') {
      return { titulo: 'Venta solo por WhatsApp', detalle: 'Battilana no tiene tienda ni punto de venta: se da la dirección de la planta y se ofrece un asesor por WhatsApp.' };
    }
    if (i.camino === 'SIN_INFO') {
      return { titulo: 'Baja certeza', detalle: 'Ningún fragmento superó el umbral (0.40) o ninguna palabra clave de la consulta aparece en la base de conocimiento: se deriva en lugar de inventar una respuesta.' };
    }
    if (i.camino === 'PRECIO' && i.accion === 'PREGUNTAR') {
      return { titulo: 'Precio fijo y en stock', detalle: 'Se ofrece un asesor para concretar la compra; solo se deriva si el cliente confirma.' };
    }
    if (i.camino === 'PRECIO') {
      return { titulo: 'Precio variable, sin precio o sin stock', detalle: 'La respuesta indica que el precio varía o que hay que consultar disponibilidad: se deriva directamente.' };
    }
    return { titulo: 'El bot no tenía el dato', detalle: 'La respuesta contiene una señal de incertidumbre («no tengo esa información», «consultar»...): se deriva.' };
  }

  colorCamino(c: string): string {
    return c === 'PRECIO' ? 'azul' : c === 'SIN_INFO' ? 'rojo' : (c === 'ASESOR' || c === 'UBICACION') ? 'gris' : 'verde';
  }
}
