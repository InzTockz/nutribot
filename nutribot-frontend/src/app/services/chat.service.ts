import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ChatResponse {
  answer: string;
  accion: string; // 'NINGUNA' | 'BOTON' | 'PREGUNTAR'
  interaccionId: number | null; // Sprint 4: id de la interaccion registrada (para valorarla en HU13)
}

/** HU09/HU10: consulta pendiente registrada en la bitacora */
export interface PendingQuery {
  id: number;
  nombre: string;
  telefono: string;
  consulta: string;
  estado: string;
  createdAt: string;
  atendidaAt?: string | null; // HU11: fecha en que el asesor la marco como atendida
}

/** HU12/HU13: un punto del grafico de tendencia */
export interface MetricaDia {
  fecha: string;
  consultas: number;
  tiempoPromedioSeg: number | null;
  derivaciones: number;
}

/** HU12/HU13: indicadores del panel (los porcentajes son null si no hay datos) */
export interface Metricas {
  desde: string;
  hasta: string;
  totalConsultas: number;
  tiempoPromedioSeg: number | null;
  derivaciones: number;
  porcentajeDerivacion: number | null;
  errores: number;
  fiabilidad: number | null;
  porCamino: Record<string, number>;
  valoradas: number;
  utiles: number;
  satisfaccion: number | null;
  revisadas: number;
  correctas: number;
  precision: number | null;
  pendientesRevision: number;
  serie: MetricaDia[];
}

/** HU13: interaccion pendiente de revision por el administrador */
export interface InteraccionRevision {
  id: number;
  pregunta: string;
  respuesta: string | null;
  camino: string;
  accion: string;
  tiempoMs: number | null;
  valoracion: string | null;
  revision: string | null;
  createdAt: string;
}

export interface KnowledgeDocument {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  chunkCount: number;
  status: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class ChatService {

  // URL del backend Spring Boot
  private readonly apiUrl = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  /** HU01/HU05: enviar una consulta al chat (id de conversacion + pregunta anterior para contextualizar) */
  ask(message: string, conversationId: string, previousQuestion: string): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.apiUrl}/api/chat`, {
      message,
      conversationId,
      previousQuestion
    });
  }

  /** HU06: subir un documento para indexarlo */
  uploadDocument(file: File): Observable<KnowledgeDocument> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<KnowledgeDocument>(`${this.apiUrl}/api/admin/documents`, form);
  }

  /** HU06: listar documentos cargados */
  listDocuments(): Observable<KnowledgeDocument[]> {
    return this.http.get<KnowledgeDocument[]>(`${this.apiUrl}/api/admin/documents`);
  }

  /** HU06: eliminar un documento por id */
  deleteDocument(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/admin/documents/${id}`);
  }

  /** HU07: obtener el enlace de WhatsApp del asesor (con mensaje prellenado) */
  getAdvisorWhatsapp(message?: string): Observable<{ phone: string; url: string }> {
    const params = message ? `?message=${encodeURIComponent(message)}` : '';
    return this.http.get<{ phone: string; url: string }>(
      `${this.apiUrl}/api/advisor/whatsapp${params}`
    );
  }

  /** HU09: registrar una consulta pendiente en la bitacora (cuando no hay asesor) */
  registrarBitacora(nombre: string, telefono: string, consulta: string): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/api/pending-queries`, { nombre, telefono, consulta });
  }

  /** HU10: listar las consultas pendientes de la bitacora (vista del asesor) */
  listPendingQueries(): Observable<PendingQuery[]> {
    return this.http.get<PendingQuery[]>(`${this.apiUrl}/api/pending-queries`);
  }

  /** HU11: marcar una consulta como atendida (o reabrirla) */
  cambiarEstado(id: number, estado: 'PENDIENTE' | 'ATENDIDA'): Observable<PendingQuery> {
    return this.http.patch<PendingQuery>(`${this.apiUrl}/api/pending-queries/${id}/estado`, { estado });
  }

  /** HU11: descargar la bitacora en Excel, con los mismos filtros de la vista */
  exportarBitacora(estado: string, fecha: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/api/pending-queries/export`, {
      params: this.params({ estado, fecha }),
      responseType: 'blob'
    });
  }

  /** HU12/HU13: indicadores del panel de metricas para un rango de fechas (yyyy-MM-dd) */
  getMetricas(desde: string, hasta: string): Observable<Metricas> {
    return this.http.get<Metricas>(`${this.apiUrl}/api/metrics`, { params: this.params({ desde, hasta }) });
  }

  /** HU13: descargar el reporte de metricas en Excel */
  exportarReporte(desde: string, hasta: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/api/metrics/export`, {
      params: this.params({ desde, hasta }),
      responseType: 'blob'
    });
  }

  /** HU13: el cliente valora una respuesta del bot */
  valorar(id: number, valoracion: 'UTIL' | 'NO_UTIL'): Observable<unknown> {
    return this.http.patch(`${this.apiUrl}/api/interacciones/${id}/valoracion`, { valoracion });
  }

  /** HU13: respuestas aun no revisadas por el administrador */
  listarParaRevision(desde: string, hasta: string): Observable<InteraccionRevision[]> {
    return this.http.get<InteraccionRevision[]>(`${this.apiUrl}/api/interacciones/revision`, {
      params: this.params({ desde, hasta })
    });
  }

  /** HU13: el administrador marca una respuesta como correcta o incorrecta (precision) */
  revisar(id: number, revision: 'CORRECTA' | 'INCORRECTA'): Observable<unknown> {
    return this.http.patch(`${this.apiUrl}/api/interacciones/${id}/revision`, { revision });
  }

  /** Arma los parametros de la URL omitiendo los vacios */
  private params(valores: Record<string, string>): HttpParams {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(valores)) {
      if (valor) {
        params = params.set(clave, valor);
      }
    }
    return params;
  }
}
