import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiAdminService } from '../../core/api-admin.service';
import { PendingQuery } from '../../core/modelos';
import { descargarArchivo, fechaIso } from '../../shared/descargar';

/**
 * Sprint 4 — Consultas pendientes (ADMIN y ASESOR). HU10 y HU11 en su propia pantalla,
 * con indicadores, busqueda y WhatsApp directo al numero del cliente.
 */
@Component({
  selector: 'app-consultas',
  imports: [CommonModule, FormsModule],
  templateUrl: './consultas.component.html'
})
export class ConsultasComponent implements OnInit {

  consultas: PendingQuery[] = [];
  filtroEstado = 'TODOS';
  filtroFecha = '';
  busqueda = '';
  mensaje = '';
  error = '';

  constructor(private api: ApiAdminService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.mensaje = '';
    this.api.listarConsultas().subscribe({
      next: (lista) => (this.consultas = lista),
      error: () => (this.error = 'No se pudo cargar la bitácora.')
    });
  }

  /** HU10: filtros de estado y fecha + busqueda por cliente, numero o consulta */
  get filtradas(): PendingQuery[] {
    const q = this.busqueda.trim().toLowerCase();
    return this.consultas.filter((p) =>
      (this.filtroEstado === 'TODOS' || p.estado === this.filtroEstado) &&
      (!this.filtroFecha || (p.createdAt ?? '').startsWith(this.filtroFecha)) &&
      (!q || `${p.nombre} ${p.telefono} ${p.consulta}`.toLowerCase().includes(q)));
  }

  get totalPendientes(): number {
    return this.consultas.filter((p) => p.estado === 'PENDIENTE').length;
  }

  get atendidasHoy(): number {
    const hoy = fechaIso(new Date());
    return this.consultas.filter((p) => p.estado === 'ATENDIDA' && (p.atendidaAt ?? '').startsWith(hoy)).length;
  }

  /** Promedio de (fecha de atencion - fecha de registro) de las atendidas en los ultimos 7 dias */
  get tiempoAtencion(): string {
    const limite = Date.now() - 7 * 86_400_000;
    const minutos = this.consultas
      .filter((p) => p.estado === 'ATENDIDA' && p.atendidaAt && new Date(p.atendidaAt).getTime() >= limite)
      .map((p) => (new Date(p.atendidaAt!).getTime() - new Date(p.createdAt).getTime()) / 60_000);
    if (!minutos.length) {
      return '—';
    }
    const prom = Math.round(minutos.reduce((s, m) => s + m, 0) / minutos.length);
    return prom >= 60 ? `${Math.floor(prom / 60)} h ${prom % 60} min` : `${prom} min`;
  }

  limpiar(): void {
    this.filtroEstado = 'TODOS';
    this.filtroFecha = '';
    this.busqueda = '';
  }

  /** HU11: marcar como atendida o reabrir */
  cambiarEstado(p: PendingQuery): void {
    const nuevo = p.estado === 'ATENDIDA' ? 'PENDIENTE' : 'ATENDIDA';
    this.api.cambiarEstado(p.id, nuevo).subscribe({
      next: (actualizada) => {
        Object.assign(p, actualizada);
        this.mensaje = nuevo === 'ATENDIDA' ? `Consulta #${p.id} marcada como atendida.` : `Consulta #${p.id} reabierta.`;
      },
      error: () => (this.error = 'No se pudo actualizar el estado.')
    });
  }

  /** HU11: Excel con los filtros de estado y fecha */
  exportar(): void {
    this.api.exportarBitacora(this.filtroEstado, this.filtroFecha).subscribe({
      next: (blob) => descargarArchivo(blob, `bitacora-${fechaIso(new Date())}.xlsx`),
      error: () => (this.error = 'No se pudo exportar la bitácora.')
    });
  }

  /** Enlace de WhatsApp al numero del cliente, con un saludo y su consulta ya escritos */
  whatsapp(p: PendingQuery): string {
    let numero = (p.telefono ?? '').replace(/\D/g, '');
    if (numero.length === 9) {
      numero = '51' + numero;   // celular peruano sin codigo de pais
    }
    const texto = `Hola ${p.nombre}, te escribimos de Battilana Nutrición por tu consulta: "${p.consulta}".`;
    return `https://wa.me/${numero}?text=${encodeURIComponent(texto)}`;
  }
}
