import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiAdminService } from '../../core/api-admin.service';
import { KnowledgeDocument } from '../../core/modelos';

/** Sprint 4 — Base de conocimiento (ADMIN). Misma funcionalidad de HU06, en su propia pantalla. */
@Component({
  selector: 'app-base-conocimiento',
  imports: [CommonModule],
  templateUrl: './base-conocimiento.component.html'
})
export class BaseConocimientoComponent implements OnInit {

  documentos: KnowledgeDocument[] = [];
  seleccionado: File | null = null;
  subiendo = false;
  mensaje = '';
  error = '';

  constructor(private api: ApiAdminService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.api.listarDocumentos().subscribe({
      next: (docs) => (this.documentos = docs),
      error: () => (this.error = 'No se pudo cargar la lista de documentos.')
    });
  }

  get totalFragmentos(): number {
    return this.documentos.reduce((s, d) => s + (d.chunkCount ?? 0), 0);
  }

  get ultimo(): KnowledgeDocument | null {
    return this.documentos.reduce<KnowledgeDocument | null>(
      (u, d) => (!u || d.createdAt > u.createdAt ? d : u), null);
  }

  elegir(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.seleccionado = input.files && input.files.length ? input.files[0] : null;
    this.mensaje = '';
    this.error = '';
  }

  subir(input: HTMLInputElement): void {
    if (!this.seleccionado || this.subiendo) {
      return;
    }
    const reemplaza = this.documentos.some((d) => d.fileName === this.seleccionado!.name);
    this.subiendo = true;
    this.api.subirDocumento(this.seleccionado).subscribe({
      next: (doc) => {
        this.mensaje = `"${doc.fileName}" ${reemplaza ? 'reemplazado' : 'indexado'} en ${doc.chunkCount} fragmento(s).`;
        this.subiendo = false;
        this.seleccionado = null;
        input.value = '';
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.error = err?.error?.message || 'Error al subir el documento.';
        this.subiendo = false;
      }
    });
  }

  eliminar(d: KnowledgeDocument): void {
    if (!confirm(`¿Eliminar "${d.fileName}" de la base de conocimiento? El bot dejará de usar su información.`)) {
      return;
    }
    this.api.eliminarDocumento(d.id).subscribe({
      next: () => { this.mensaje = `"${d.fileName}" eliminado.`; this.cargar(); },
      error: () => (this.error = 'No se pudo eliminar el documento.')
    });
  }

  tipo(d: KnowledgeDocument): string {
    const i = d.fileName.lastIndexOf('.');
    return i >= 0 ? d.fileName.substring(i + 1).toUpperCase() : '—';
  }

  tamano(bytes: number): string {
    return bytes >= 1_048_576 ? `${(bytes / 1_048_576).toFixed(1)} MB` : `${(bytes / 1024).toFixed(1)} KB`;
  }
}
