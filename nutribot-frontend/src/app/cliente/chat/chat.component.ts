import { AfterViewChecked, Component, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiPublicaService } from '../../core/api-publica.service';
import { normalizarCelular } from '../../shared/telefono';

interface Message {
  role: 'user' | 'bot';
  text: string;
  derivar?: boolean;                  // muestra el boton de WhatsApp (hay asesor disponible)
  pedirDatos?: boolean;               // muestra el formulario de bitacora (no hay asesor) - HU09
  interaccionId?: number;             // Sprint 4: id de la interaccion en el backend
  valoracion?: 'UTIL' | 'NO_UTIL';    // HU13
}

/**
 * Sprint 4 — VISTA CLIENTE. Es lo unico que ve el ganadero o avicultor:
 * el chat con NutriBot, sin menus ni administracion. Funciona como pagina propia (/chat)
 * o dentro de la web de Battilana mediante el script public/nutribot.js.
 * Solo usa la API publica (/api/public/**).
 *
 * La logica de conversacion es la misma de los Sprints 1 a 3 (HU01-HU05, HU07-HU09) y HU13.
 */
@Component({
  selector: 'app-chat',
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent implements AfterViewChecked {

  @ViewChild('lista') private lista?: ElementRef<HTMLDivElement>;
  private debeBajar = false;

  messages: Message[] = [];
  input = '';
  loading = false;
  esperandoConfirmacion = false;   // el bot pregunto si desea un asesor (HU08)
  consultaParaAsesor = '';         // consulta original (contexto para el asesor)
  private readonly conversationId = crypto.randomUUID();   // HU05: memoria de la sesion

  /**
   * DEF-15: si hay un asesor atendiendo. Lo decide el BACKEND (horario de atencion en hora de Peru)
   * y llega en cada respuesta; antes se calculaba aqui con el reloj del navegador del cliente.
   */
  private asesorEnHorario = true;

  bitacoraNombre = '';
  bitacoraTelefono = '';
  enviandoBitacora = false;

  /** DEF-11: el boton solo se activa con un nombre (2+ letras) y un celular peruano valido */
  get celularValido(): boolean {
    return normalizarCelular(this.bitacoraTelefono) !== null;
  }

  get bitacoraValida(): boolean {
    return this.bitacoraNombre.trim().length >= 2 && this.celularValido;
  }

  /** Sprint 4: atajos para empezar la conversacion */
  readonly sugerencias = [
    { texto: 'Precios', consulta: '¿Cuánto cuesta el alimento para gallinas ponedoras?' },
    { texto: 'Fichas técnicas', consulta: '¿Qué dosis necesita una gallina ponedora?' },
    { texto: 'Horario y contacto', consulta: '¿Cuál es el horario de atención?' },
    { texto: 'Hablar con un asesor', consulta: 'Quiero hablar con un asesor' }
  ];

  constructor(private api: ApiPublicaService) {}

  ngAfterViewChecked(): void {
    if (this.debeBajar && this.lista) {
      this.lista.nativeElement.scrollTop = this.lista.nativeElement.scrollHeight;
      this.debeBajar = false;
    }
  }

  usarSugerencia(consulta: string): void {
    this.input = consulta;
    this.send();
  }

  send(): void {
    const text = this.input.trim();
    if (!text || this.loading) {
      return;
    }
    // HU05: la pregunta anterior ayuda a contextualizar los seguimientos
    const previousQuestion = this.messages.filter((m) => m.role === 'user').pop()?.text ?? '';
    this.agregar({ role: 'user', text });
    this.input = '';

    // HU08: si el bot pregunto si desea un asesor, se interpreta la respuesta
    if (this.esperandoConfirmacion) {
      this.esperandoConfirmacion = false;
      if (this.esAfirmacion(text)) {
        const msg: Message = {
          role: 'bot',
          text: this.asesorDisponible()
            ? 'Perfecto, te derivo con un asesor.'
            : 'En este momento no hay asesores disponibles. Déjanos tus datos y te contactaremos.'
        };
        this.marcarDerivacion(msg);
        this.agregar(msg);
        return;
      }
      if (this.esNegacion(text)) {
        this.agregar({ role: 'bot', text: 'De acuerdo, seguimos por aquí. ¿En qué más puedo ayudarte?' });
        return;
      }
      // si no es afirmacion ni negacion, se trata como una nueva consulta
    }

    this.loading = true;
    this.api.preguntar(text, this.conversationId, previousQuestion).subscribe({
      next: (res) => {
        this.asesorEnHorario = res.asesorDisponible !== false;   // DEF-15: manda el backend
        const msg: Message = { role: 'bot', text: res.answer, interaccionId: res.interaccionId ?? undefined };
        if (res.accion === 'BOTON') {
          this.marcarDerivacion(msg);
        }
        this.agregar(msg);
        if (res.accion === 'PREGUNTAR') {
          this.esperandoConfirmacion = true;
        }
        if (res.accion === 'BOTON' || res.accion === 'PREGUNTAR') {
          this.consultaParaAsesor = text;
        }
        this.loading = false;
      },
      error: () => {
        this.agregar({ role: 'bot', text: 'Ocurrió un error al consultar. Intenta nuevamente en unos minutos.' });
        this.loading = false;
      }
    });
  }

  /** HU13: el cliente valora la respuesta (una sola vez) */
  valorar(m: Message, valor: 'UTIL' | 'NO_UTIL'): void {
    if (!m.interaccionId || m.valoracion) {
      return;
    }
    m.valoracion = valor;
    this.api.valorar(m.interaccionId, valor).subscribe({ error: () => (m.valoracion = undefined) });
  }

  /** HU09: registrar los datos del cliente en la bitacora (DEF-11: celular validado) */
  enviarBitacora(): void {
    const nombre = this.bitacoraNombre.trim();
    const celular = normalizarCelular(this.bitacoraTelefono);
    if (nombre.length < 2 || !celular || this.enviandoBitacora) {
      return;
    }
    this.enviandoBitacora = true;
    this.api.registrarConsulta(nombre, celular, this.consultaParaAsesor).subscribe({
      next: () => {
        this.messages.forEach((m) => (m.pedirDatos = false));
        this.agregar({ role: 'bot', text: `Gracias ${nombre}. Tu consulta quedó registrada y un asesor te contactará al ${celular}.` });
        this.bitacoraNombre = '';
        this.bitacoraTelefono = '';
        this.enviandoBitacora = false;
      },
      error: (err) => {
        // el backend explica que fallo (por ejemplo, "Ingresa un número de celular válido...")
        const detalle = err?.error?.message;
        this.agregar({ role: 'bot', text: detalle ? `No pude registrar tus datos: ${detalle}` : 'No pude registrar tus datos. Intenta nuevamente.' });
        this.enviandoBitacora = false;
      }
    });
  }

  /** HU07: derivar a un asesor humano por WhatsApp, con la consulta original como contexto */
  talkToAdvisor(): void {
    const consulta = this.consultaParaAsesor || this.messages.filter((m) => m.role === 'user').pop()?.text || '';
    const msg = consulta
      ? `Hola, vengo del chat de NutriBot. Mi consulta: ${consulta}`
      : 'Hola, vengo del chat de NutriBot y me gustaría hablar con un asesor.';
    this.api.enlaceWhatsapp(msg).subscribe({
      next: (res) => {
        this.agregar({ role: 'bot', text: 'Te estoy derivando con un asesor. Te atenderá una persona por WhatsApp.' });
        window.open(res.url, '_blank', 'noopener');
      },
      error: () => this.agregar({ role: 'bot', text: 'No pude abrir WhatsApp en este momento. Intenta nuevamente.' })
    });
  }

  private agregar(m: Message): void {
    this.messages.push(m);
    this.debeBajar = true;
  }

  // WhatsApp si hay asesor disponible; formulario de bitacora si no (HU09)
  private marcarDerivacion(msg: Message): void {
    if (this.asesorDisponible()) {
      msg.derivar = true;
    } else {
      msg.pedirDatos = true;
    }
  }

  // DEF-15: el horario lo decide el backend (nutribot.asesor.* en application.yml)
  private asesorDisponible(): boolean {
    return this.asesorEnHorario;
  }

  private esAfirmacion(text: string): boolean {
    const t = text.toLowerCase().replace(/[.,!¡¿?]/g, ' ').trim();
    const palabras = t.split(/\s+/);
    const simples = ['si', 'sí', 'claro', 'dale', 'ok', 'okay', 'bueno', 'afirmativo',
                     'correcto', 'quiero', 'deseo', 'acepto', 'listo', 'sip', 'sii', 'obvio'];
    if (palabras.some((p) => simples.includes(p))) {
      return true;
    }
    return ['por favor', 'esta bien', 'de una', 'me interesa'].some((f) => t.includes(f));
  }

  private esNegacion(text: string): boolean {
    const t = text.toLowerCase().replace(/[.,!¡¿?]/g, ' ').trim();
    const palabras = t.split(/\s+/);
    const simples = ['no', 'nel', 'negativo', 'paso', 'nop'];
    if (palabras.some((p) => simples.includes(p))) {
      return true;
    }
    return ['ahorita no', 'asi estoy bien', 'asi esta bien', 'despues', 'luego'].some((f) => t.includes(f));
  }
}
