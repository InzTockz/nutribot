import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService, InteraccionRevision, MetricaDia, Metricas } from '../services/chat.service';
import { descargarArchivo, fechaIso } from '../utils/descargar';

/** Indicador que se dibuja en el grafico de tendencia */
type Serie = 'consultas' | 'tiempo' | 'derivaciones';

interface Punto {
  x: number;
  y: number;
  fecha: string;
  valor: number;
}

/**
 * Sprint 4 — Panel de metricas del chatbot.
 *  - HU12: volumen, tiempo de respuesta y derivaciones, con grafico de tendencia y filtro por fecha.
 *  - HU13: satisfaccion (valoracion del cliente), precision (revision del administrador) y reporte en Excel.
 * El grafico se dibuja con SVG, sin librerias externas.
 */
@Component({
  selector: 'app-metricas',
  imports: [CommonModule, FormsModule],
  templateUrl: './metricas.component.html',
  styleUrl: './metricas.component.css'
})
export class MetricasComponent implements OnInit {

  // ----- Filtro por fecha (HU12) -----
  desde = '';
  hasta = '';

  metricas: Metricas | null = null;
  pendientes: InteraccionRevision[] = [];
  cargando = false;
  mensaje = '';

  // ----- Grafico de tendencia (HU12) -----
  serie: Serie = 'consultas';
  readonly ancho = 640;
  readonly alto = 220;
  readonly margen = { izq: 46, der: 14, sup: 14, inf: 30 };
  puntos: Punto[] = [];
  tramos: string[] = [];                               // polilineas (se cortan en los dias sin dato)
  ticksY: { y: number; etiqueta: string }[] = [];
  etiquetasX: { x: number; texto: string }[] = [];

  readonly titulos: Record<Serie, string> = {
    consultas: 'Consultas por día',
    tiempo: 'Tiempo promedio de respuesta por día (s)',
    derivaciones: 'Derivaciones al asesor por día'
  };

  constructor(private chatService: ChatService) {}

  ngOnInit(): void {
    const hoy = new Date();
    const inicio = new Date();
    inicio.setDate(hoy.getDate() - 29);
    this.hasta = fechaIso(hoy);
    this.desde = fechaIso(inicio);
    this.cargar();
  }

  /** Carga los indicadores y la lista de respuestas por revisar del rango elegido */
  cargar(): void {
    if (this.desde && this.hasta && this.desde > this.hasta) {
      this.mensaje = 'La fecha "desde" no puede ser posterior a "hasta".';
      return;
    }
    this.mensaje = '';
    this.cargarIndicadores();
    this.chatService.listarParaRevision(this.desde, this.hasta).subscribe({
      next: (lista) => (this.pendientes = lista),
      error: () => (this.pendientes = [])
    });
  }

  private cargarIndicadores(): void {
    this.cargando = true;
    this.chatService.getMetricas(this.desde, this.hasta).subscribe({
      next: (m) => {
        this.metricas = m;
        this.cargando = false;
        this.actualizarGrafico();
      },
      error: (err) => {
        this.mensaje = err?.error?.message || 'No se pudieron cargar las métricas. Verifica que el backend esté corriendo.';
        this.cargando = false;
      }
    });
  }

  /** HU13: reporte en Excel del rango elegido */
  exportar(): void {
    this.chatService.exportarReporte(this.desde, this.hasta).subscribe({
      next: (blob) => descargarArchivo(blob, `reporte-nutribot_${this.desde}_${this.hasta}.xlsx`),
      error: () => (this.mensaje = 'No se pudo exportar el reporte.')
    });
  }

  /** HU13: el administrador marca la respuesta como correcta o incorrecta */
  revisar(i: InteraccionRevision, revision: 'CORRECTA' | 'INCORRECTA'): void {
    this.chatService.revisar(i.id, revision).subscribe({
      next: () => {
        this.pendientes = this.pendientes.filter((p) => p.id !== i.id);
        this.cargarIndicadores();   // la precision cambia con cada revision
      },
      error: () => (this.mensaje = 'No se pudo guardar la revisión.')
    });
  }

  cambiarSerie(serie: Serie): void {
    this.serie = serie;
    this.actualizarGrafico();
  }

  /** Muestra un porcentaje, o un guion si no hay datos para calcularlo */
  pct(valor: number | null | undefined): string {
    return valor === null || valor === undefined ? '—' : `${valor.toFixed(1)} %`;
  }

  caminos(m: Metricas): { nombre: string; cantidad: number }[] {
    return Object.entries(m.porCamino).map(([nombre, cantidad]) => ({ nombre, cantidad }));
  }

  // ---------------------------------------------------------------- Grafico (SVG)

  private valor(d: MetricaDia): number | null {
    switch (this.serie) {
      case 'tiempo': return d.tiempoPromedioSeg;
      case 'derivaciones': return d.derivaciones;
      default: return d.consultas;
    }
  }

  /** Convierte la serie diaria en coordenadas del SVG */
  actualizarGrafico(): void {
    const datos = this.metricas?.serie ?? [];
    this.puntos = [];
    this.tramos = [];
    this.ticksY = [];
    this.etiquetasX = [];
    if (!datos.length) {
      return;
    }

    const valores = datos.map((d) => this.valor(d));
    const maximo = Math.max(1, ...valores.map((v) => v ?? 0));
    const anchoUtil = this.ancho - this.margen.izq - this.margen.der;
    const altoUtil = this.alto - this.margen.sup - this.margen.inf;
    const paso = datos.length > 1 ? anchoUtil / (datos.length - 1) : 0;
    const xDe = (i: number) => this.margen.izq + (datos.length > 1 ? i * paso : anchoUtil / 2);
    const yDe = (v: number) => this.margen.sup + altoUtil - (v / maximo) * altoUtil;

    // Lineas guia del eje Y: 0, mitad y maximo
    const decimales = this.serie === 'tiempo' ? 1 : 0;
    this.ticksY = [0, maximo / 2, maximo].map((v) => ({ y: yDe(v), etiqueta: v.toFixed(decimales) }));

    // Puntos y tramos: un dia sin dato (tiempo sin consultas) corta la linea en lugar de bajarla a cero
    let tramo: string[] = [];
    valores.forEach((v, i) => {
      if (v === null) {
        if (tramo.length) { this.tramos.push(tramo.join(' ')); }
        tramo = [];
        return;
      }
      const p: Punto = { x: xDe(i), y: yDe(v), fecha: datos[i].fecha, valor: v };
      this.puntos.push(p);
      tramo.push(`${p.x.toFixed(1)},${p.y.toFixed(1)}`);
    });
    if (tramo.length) { this.tramos.push(tramo.join(' ')); }

    // Hasta 6 etiquetas de fecha en el eje X (dd/MM)
    const cada = Math.max(1, Math.ceil(datos.length / 6));
    datos.forEach((d, i) => {
      if (i % cada === 0 || i === datos.length - 1) {
        const [, mm, dd] = d.fecha.split('-');
        this.etiquetasX.push({ x: xDe(i), texto: `${dd}/${mm}` });
      }
    });
  }
}
