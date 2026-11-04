import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from './config';
import { Interaccion, KnowledgeDocument, Metricas, PendingQuery } from './modelos';

/**
 * Sprint 4: API PRIVADA (/api/admin/**). La usa solo el panel administrativo.
 * El AuthInterceptor agrega el token a cada peticion; sin token el backend responde 401.
 */
@Injectable({ providedIn: 'root' })
export class ApiAdminService {

  private readonly base = `${API_URL}/api/admin`;

  constructor(private http: HttpClient) {}

  // ----- HU06: base de conocimiento (ADMIN) -----
  listarDocumentos(): Observable<KnowledgeDocument[]> {
    return this.http.get<KnowledgeDocument[]>(`${this.base}/documents`);
  }

  subirDocumento(file: File): Observable<KnowledgeDocument> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<KnowledgeDocument>(`${this.base}/documents`, form);
  }

  eliminarDocumento(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/documents/${id}`);
  }

  // ----- HU10/HU11: bitacora (ADMIN y ASESOR) -----
  listarConsultas(): Observable<PendingQuery[]> {
    return this.http.get<PendingQuery[]>(`${this.base}/pending-queries`);
  }

  cambiarEstado(id: number, estado: 'PENDIENTE' | 'ATENDIDA'): Observable<PendingQuery> {
    return this.http.patch<PendingQuery>(`${this.base}/pending-queries/${id}/estado`, { estado });
  }

  exportarBitacora(estado: string, fecha: string): Observable<Blob> {
    return this.http.get(`${this.base}/pending-queries/export`, {
      params: parametros({ estado, fecha }),
      responseType: 'blob'
    });
  }

  // ----- HU07/HU08: derivaciones (ADMIN y ASESOR) -----
  derivaciones(desde: string, hasta: string): Observable<Interaccion[]> {
    return this.http.get<Interaccion[]>(`${this.base}/derivaciones`, { params: parametros({ desde, hasta }) });
  }

  // ----- HU12/HU13: metricas y revision (ADMIN) -----
  metricas(desde: string, hasta: string): Observable<Metricas> {
    return this.http.get<Metricas>(`${this.base}/metrics`, { params: parametros({ desde, hasta }) });
  }

  exportarReporte(desde: string, hasta: string): Observable<Blob> {
    return this.http.get(`${this.base}/metrics/export`, {
      params: parametros({ desde, hasta }),
      responseType: 'blob'
    });
  }

  paraRevision(desde: string, hasta: string): Observable<Interaccion[]> {
    return this.http.get<Interaccion[]>(`${this.base}/interacciones/revision`, { params: parametros({ desde, hasta }) });
  }

  revisar(id: number, revision: 'CORRECTA' | 'INCORRECTA'): Observable<unknown> {
    return this.http.patch(`${this.base}/interacciones/${id}/revision`, { revision });
  }
}

/** Arma los parametros de la URL omitiendo los vacios */
function parametros(valores: Record<string, string>): HttpParams {
  let params = new HttpParams();
  for (const [clave, valor] of Object.entries(valores)) {
    if (valor) {
      params = params.set(clave, valor);
    }
  }
  return params;
}
