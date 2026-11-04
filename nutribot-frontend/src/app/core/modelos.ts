/** Tipos de datos que intercambian el frontend y el backend. */

export type Rol = 'ADMIN' | 'ASESOR';

/** Respuesta del chat. accion: NINGUNA | BOTON (mostrar asesor) | PREGUNTAR (ofrecer y esperar confirmacion) */
export interface ChatResponse {
  answer: string;
  accion: string;
  interaccionId: number | null;   // Sprint 4: para valorar la respuesta (HU13)
  asesorDisponible?: boolean;     // DEF-15: lo decide el backend segun el horario de atencion
}

/** Sprint 4: sesion del personal (lo que devuelve POST /api/auth/login) */
export interface Sesion {
  token: string;
  username: string;
  nombre: string;
  rol: Rol;
  expiraEnSegundos: number;
  expiraEn?: number;              // instante de vencimiento (ms), calculado al iniciar sesion
}

/** HU09-HU11: consulta registrada en la bitacora */
export interface PendingQuery {
  id: number;
  nombre: string;
  telefono: string;
  consulta: string;
  estado: string;
  createdAt: string;
  atendidaAt?: string | null;
}

/** HU06: documento de la base de conocimiento */
export interface KnowledgeDocument {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  chunkCount: number;
  status: string;
  createdAt: string;
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
  consultasMedidas: number;            // DEF-14: consultas con tiempo medido (sin error tecnico)
  bajoMeta: number;                    // DEF-14: de esas, cuantas respondieron en menos de 5 s
  porcentajeBajoMeta: number | null;   // DEF-14: bajoMeta / consultasMedidas * 100 (meta: 95 %)
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

/** Sprint 4: una interaccion del chat (revision HU13, derivaciones HU07/HU08) */
export interface Interaccion {
  id: number;
  conversationId: string;
  pregunta: string;
  respuesta: string | null;
  camino: string;
  accion: string;
  tiempoMs: number | null;
  valoracion: string | null;
  revision: string | null;
  createdAt: string;
}
