import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ChatComponent } from './chat.component';
import { ApiPublicaService } from '../../core/api-publica.service';
import { ChatResponse } from '../../core/modelos';

/** DEF-15: el chat obedece al backend para decidir entre WhatsApp y el formulario de bitacora. */
describe('ChatComponent — derivación según el horario (DEF-15)', () => {
  let respuesta: ChatResponse;
  const api = {
    preguntar: () => of(respuesta),
    valorar: () => of(undefined),
    registrarConsulta: () => of({}),
    enlaceWhatsapp: () => of({ phone: '', url: '' })
  };

  function crear(): ChatComponent {
    TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [{ provide: ApiPublicaService, useValue: api }]
    });
    return TestBed.createComponent(ChatComponent).componentInstance;
  }

  function preguntar(chat: ChatComponent, texto: string): void {
    chat.input = texto;
    chat.send();
  }

  it('en horario muestra el botón de WhatsApp', () => {
    respuesta = { answer: 'Con gusto...', accion: 'BOTON', interaccionId: 1, asesorDisponible: true };
    const chat = crear();
    preguntar(chat, 'Quiero hablar con un asesor');
    const bot = chat.messages[chat.messages.length - 1];
    expect(bot.derivar).toBeTrue();
    expect(bot.pedirDatos).toBeFalsy();
  });

  it('fuera de horario muestra el formulario de datos', () => {
    respuesta = { answer: 'Fuera del horario...', accion: 'BOTON', interaccionId: 2, asesorDisponible: false };
    const chat = crear();
    preguntar(chat, 'Quiero hablar con un asesor');
    const bot = chat.messages[chat.messages.length - 1];
    expect(bot.pedirDatos).toBeTrue();
    expect(bot.derivar).toBeFalsy();
  });

  it('si el cliente acepta la derivación (PREGUNTAR → sí) fuera de horario, también pide los datos', () => {
    respuesta = { answer: 'S/ 95.00 ¿Deseas que te derive...?', accion: 'PREGUNTAR', interaccionId: 3, asesorDisponible: false };
    const chat = crear();
    preguntar(chat, '¿Cuánto cuesta el alimento para gallinas?');
    preguntar(chat, 'sí');
    const bot = chat.messages[chat.messages.length - 1];
    expect(bot.pedirDatos).toBeTrue();
    expect(bot.text).toContain('no hay asesores disponibles');
  });

  it('el botón de enviar datos solo se activa con un celular válido (DEF-11)', () => {
    const chat = crear();
    chat.bitacoraNombre = 'Juan';
    chat.bitacoraTelefono = 'Gonzales';
    expect(chat.bitacoraValida).toBeFalse();
    chat.bitacoraTelefono = '+51 987 654 321';
    expect(chat.bitacoraValida).toBeTrue();
  });
});
