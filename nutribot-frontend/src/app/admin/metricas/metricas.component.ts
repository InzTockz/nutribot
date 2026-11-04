import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiAdminService } from '../../core/api-admin.service';
import { Interaccion, MetricaDia, Metricas } from '../../core/modelos';
import { descargarArchivo, fechaIso } from '../../shared/descargar';
import { GraficoLineaComponent, PuntoSerie } from '../../shared/grafico-linea.component';

/** Indicador que se dibuja en el grafico de tendencia */
type Serie = 'consultas' | 'tiempo' | 'derivaciones';

/**
 * Sprint 4 — Panel de metricas (ADMIN).
 *  - HU12: volumen, tiempo de respuesta y derivaciones, con grafico de tendencia y filtro por fecha.
 *  - HU13: satisfaccion (valoracion del cliente), precision (revision del administrador) y reporte en Excel.
 */
@Component({
  selector: 'app-metricas',
  imports: [CommonModule, FormsModule, GraficoLineaComponent],
  templateUrl: './metricas.component.html',
  styleUrl: './metricas.component.css'
})
export class MetricasComponent implements OnInit {

  desde = '';
  hasta = '';
  metricas: Metricas | null = null;
  pendientes: Interaccion[] = [];
  cargando = false;
  mensaje = '';

  serie: Serie = 'consultas';
  datosGrafico: PuntoSerie[] = [];

  readonly titulos: Record<Serie, string> = {
    consultas: 'Consultas por día',
    tiempo: 'Tiempo promedio de respuesta por día (s)',
    derivaciones: 'Derivaciones al asesor por día'
  };

  constructor(private api: ApiAdminService) {}

  ngOnInit(): void {
    const hoy = new Date();
    const inicio = new Date();
    inicio.setDate(hoy.getDate() - 29);
    this.hasta = fechaIso(hoy);
    this.desde = fechaIso(inicio);
    this.cargar();
  }

  cargar(): void {
    if (this.desde && this.hasta && this.desde > this.hasta) {
      this.mensaje = 'La fecha "desde" no puede ser posterior a "hasta".';
      return;
    }
    this.mensaje = '';
    this.cargarIndicadores();
    this.api.paraRevision(this.desde, this.hasta).subscribe({
      next: (lista) => (this.pendientes = lista),
      error: () => (this.pendientes = [])
    });
  }

  private cargarIndicadores(): void {
    this.cargando = true;
    this.api.metricas(this.desde, this.hasta).subscribe({
      next: (m) => {
        this.metricas = m;
        this.cargando = false;
        this.actualizarGrafico();
      },
      error: (err) => {
        this.mensaje = err?.error?.message || 'No se pudieron cargar las métricas.';
        this.cargando = false;
      }
    });
  }

  exportar(): void {
    this.api.exportarReporte(this.desde, this.hasta).subscribe({
      next: (blob) => descargarArchivo(blob, `reporte-nutribot_${this.desde}_${this.hasta}.xlsx`),
      error: () => (this.mensaje = 'No se pudo exportar el reporte.')
    });
  }

  /** HU13: el administrador marca la respuesta como correcta o incorrecta */
  revisar(i: Interaccion, revision: 'CORRECTA' | 'INCORRECTA'): void {
    this.api.revisar(i.id, revision).subscribe({
      next: () => {
        this.pendientes = this.pendientes.filter((p) => p.id !== i.id);
        this.cargarIndicadores();
      },
      error: () => (this.mensaje = 'No se pudo guardar la revisión.')
    });
  }

  cambiarSerie(serie: Serie): void {
    this.serie = serie;
    this.actualizarGrafico();
  }

  pct(valor: number | null | undefined): string {
    return valor === null || valor === undefined ? '—' : `${valor.toFixed(1)} %`;
  }

  caminos(m: Metricas): { nombre: string; cantidad: number }[] {
    return Object.entries(m.porCamino).map(([nombre, cantidad]) => ({ nombre, cantidad }));
  }

  private actualizarGrafico(): void {
    const valor = (d: MetricaDia): number | null =>
      this.serie === 'tiempo' ? d.tiempoPromedioSeg : this.serie === 'derivaciones' ? d.derivaciones : d.consultas;
    this.datosGrafico = (this.metricas?.serie ?? []).map((d) => ({ fecha: d.fecha, valor: valor(d) }));
  }
}
