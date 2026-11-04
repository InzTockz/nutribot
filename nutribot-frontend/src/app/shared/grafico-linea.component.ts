import { AfterViewInit, Component, ElementRef, Input, NgZone, OnChanges, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface PuntoSerie {
  fecha: string;          // yyyy-MM-dd
  valor: number | null;   // null = dia sin dato (corta la linea en lugar de bajarla a cero)
}

interface Punto { x: number; y: number; fecha: string; valor: number; }

/**
 * Sprint 4: grafico de tendencia reutilizable (Dashboard y Metricas), dibujado con SVG sin librerias.
 * Uso: <nb-grafico-linea [datos]="serie" [decimales]="1" />
 *
 * DEF-13: antes el dibujo tenia un ancho fijo de 640 y se estiraba proporcionalmente,
 * asi que en pantallas anchas crecia tambien en alto (grafico gigante). Ahora se mide el ancho real
 * del contenedor (ResizeObserver) y se vuelve a dibujar con ese ancho; el alto queda fijo en 240 px.
 */
@Component({
  selector: 'nb-grafico-linea',
  imports: [CommonModule],
  template: `
    <svg [attr.viewBox]="'0 0 ' + ancho + ' ' + alto" role="img" [attr.aria-label]="etiqueta">
      <line *ngFor="let t of ticksY" class="guia" [attr.x1]="m.izq" [attr.x2]="ancho - m.der" [attr.y1]="t.y" [attr.y2]="t.y" />
      <text *ngFor="let t of ticksY" class="eje" text-anchor="end" [attr.x]="m.izq - 8" [attr.y]="t.y + 4">{{ t.texto }}</text>
      <text *ngFor="let e of etiquetasX" class="eje" text-anchor="middle" [attr.x]="e.x" [attr.y]="alto - 8">{{ e.texto }}</text>
      <path *ngIf="area" class="area" [attr.d]="area" />
      <polyline *ngFor="let t of tramos" class="linea" [attr.points]="t" />
      <circle *ngFor="let p of puntos" class="punto" r="3.5" [attr.cx]="p.x" [attr.cy]="p.y">
        <title>{{ p.fecha }}: {{ p.valor }}</title>
      </circle>
    </svg>`,
  styles: [`
    :host { display: block; width: 100%; }
    svg { width: 100%; height: 240px; display: block; }
    .guia { stroke: #eef1ef; stroke-width: 1; }
    .eje { fill: #8a958e; font-size: 11px; }
    .area { fill: #2e7d32; opacity: .08; }
    .linea { fill: none; stroke: #2e7d32; stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
    .punto { fill: #fff; stroke: #2e7d32; stroke-width: 2; }
  `]
})
export class GraficoLineaComponent implements OnChanges, AfterViewInit, OnDestroy {

  @Input() datos: PuntoSerie[] = [];
  @Input() decimales = 0;
  @Input() etiqueta = 'Gráfico de tendencia';

  ancho = 640;              // se reemplaza por el ancho real del contenedor
  readonly alto = 240;
  private static readonly ANCHO_MINIMO = 320;
  private observador?: ResizeObserver;

  constructor(private host: ElementRef<HTMLElement>, private zone: NgZone) {}
  readonly m = { izq: 46, der: 14, sup: 14, inf: 30 };

  puntos: Punto[] = [];
  tramos: string[] = [];
  area = '';
  ticksY: { y: number; texto: string }[] = [];
  etiquetasX: { x: number; texto: string }[] = [];

  ngAfterViewInit(): void {
    if (typeof ResizeObserver === 'undefined') {
      return;   // navegador antiguo o pruebas: se queda con 640
    }
    this.observador = new ResizeObserver((entradas) => {
      const medido = Math.max(GraficoLineaComponent.ANCHO_MINIMO, Math.round(entradas[0]?.contentRect.width ?? 0));
      if (Math.abs(medido - this.ancho) >= 1) {      // solo si de verdad cambio el ancho
        this.zone.run(() => {
          this.ancho = medido;
          this.dibujar();
        });
      }
    });
    this.observador.observe(this.host.nativeElement);
  }

  ngOnDestroy(): void {
    this.observador?.disconnect();
  }

  ngOnChanges(): void {
    this.dibujar();
  }

  private dibujar(): void {
    this.puntos = [];
    this.tramos = [];
    this.area = '';
    this.ticksY = [];
    this.etiquetasX = [];
    const datos = this.datos ?? [];
    if (!datos.length) {
      return;
    }

    const maximo = Math.max(1, ...datos.map((d) => d.valor ?? 0));
    const anchoUtil = this.ancho - this.m.izq - this.m.der;
    const altoUtil = this.alto - this.m.sup - this.m.inf;
    const paso = datos.length > 1 ? anchoUtil / (datos.length - 1) : 0;
    const xDe = (i: number) => this.m.izq + (datos.length > 1 ? i * paso : anchoUtil / 2);
    const yDe = (v: number) => this.m.sup + altoUtil - (v / maximo) * altoUtil;
    const base = this.m.sup + altoUtil;

    this.ticksY = [0, maximo / 2, maximo].map((v) => ({ y: yDe(v), texto: v.toFixed(this.decimales) }));

    let tramo: Punto[] = [];
    const cerrar = () => {
      if (tramo.length) {
        this.tramos.push(tramo.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' '));
        if (!this.area && tramo.length > 1) {
          this.area = `M${tramo[0].x},${base} ` + tramo.map((p) => `L${p.x},${p.y}`).join(' ') + ` L${tramo[tramo.length - 1].x},${base} Z`;
        }
      }
      tramo = [];
    };
    datos.forEach((d, i) => {
      if (d.valor === null) { cerrar(); return; }
      const p: Punto = { x: xDe(i), y: yDe(d.valor), fecha: d.fecha, valor: d.valor };
      this.puntos.push(p);
      tramo.push(p);
    });
    cerrar();

    const cada = Math.max(1, Math.ceil(datos.length / 6));
    datos.forEach((d, i) => {
      if (i % cada === 0 || i === datos.length - 1) {
        const [, mm, dd] = d.fecha.split('-');
        this.etiquetasX.push({ x: xDe(i), texto: `${dd}/${mm}` });
      }
    });
  }
}
