import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiAdminService } from '../../core/api-admin.service';
import { Metricas, PendingQuery } from '../../core/modelos';
import { AuthService } from '../../core/auth.service';
import { GraficoLineaComponent, PuntoSerie } from '../../shared/grafico-linea.component';
import { fechaIso } from '../../shared/descargar';

/** Sprint 4 — Dashboard (ADMIN): lo que paso hoy y lo que requiere atencion. Solo datos reales. */
@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterLink, GraficoLineaComponent],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {

  hoy: Metricas | null = null;
  quincena: Metricas | null = null;
  pendientes: PendingQuery[] = [];
  serie: PuntoSerie[] = [];
  error = '';

  constructor(private api: ApiAdminService, public auth: AuthService) {}

  ngOnInit(): void {
    const hoy = new Date();
    const inicio = new Date();
    inicio.setDate(hoy.getDate() - 13);
    forkJoin({
      hoy: this.api.metricas(fechaIso(hoy), fechaIso(hoy)),
      quincena: this.api.metricas(fechaIso(inicio), fechaIso(hoy)),
      consultas: this.api.listarConsultas()
    }).subscribe({
      next: (r) => {
        this.hoy = r.hoy;
        this.quincena = r.quincena;
        this.serie = r.quincena.serie.map((d) => ({ fecha: d.fecha, valor: d.consultas }));
        // las mas antiguas primero: son las que mas urge devolver
        this.pendientes = r.consultas.filter((c) => c.estado === 'PENDIENTE').reverse();
      },
      error: () => (this.error = 'No se pudo cargar el resumen. Verifica que el backend esté corriendo.')
    });
  }

  saludo(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  }

  /** Horas transcurridas desde el registro de la consulta */
  horas(c: PendingQuery): number {
    return Math.floor((Date.now() - new Date(c.createdAt).getTime()) / 3_600_000);
  }

  pct(v: number | null | undefined): string {
    return v === null || v === undefined ? '—' : `${v.toFixed(1)} %`;
  }
}
