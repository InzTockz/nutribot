import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from './config';
import { ChatResponse } from './modelos';

/**
 * Sprint 4: API PUBLICA (/api/public/**). La usa solo la vista del cliente (el chat).
 * No envia token: estas rutas no requieren iniciar sesion.
 */
@Injectable({ providedIn: 'root' })
export class ApiPublicaService {

  private readonly base = `${API_URL}/api/public`;

  constructor(private http: HttpClient) {}

  /** HU01/HU05: enviar una consulta (id de conversacion + pregunta anterior para contextualizar) */
  preguntar(message: string, conversationId: string, previousQuestion: string): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.base}/chat`, { message, conversationId, previousQuestion });
  }

  /** HU13: el cliente valora una respuesta */
  valorar(id: number, valoracion: 'UTIL' | 'NO_UTIL'): Observable<void> {
    return this.http.patch<void>(`${this.base}/interacciones/${id}/valoracion`, { valoracion });
  }

  /** HU07: enlace de WhatsApp del asesor con el mensaje prellenado */
  enlaceWhatsapp(message?: string): Observable<{ phone: string; url: string }> {
    const params = message ? new HttpParams().set('message', message) : undefined;
    return this.http.get<{ phone: string; url: string }>(`${this.base}/advisor/whatsapp`, { params });
  }

  /** HU09: dejar los datos cuando no hay asesor disponible */
  registrarConsulta(nombre: string, telefono: string, consulta: string): Observable<unknown> {
    return this.http.post(`${this.base}/pending-queries`, { nombre, telefono, consulta });
  }
}
