/*!
 * NutriBot — script para integrar el chat en cualquier pagina web (Sprint 4).
 *
 * Uso: pegar UNA linea antes de </body> en la web de Battilana:
 *   <script src="https://SERVIDOR-DE-NUTRIBOT/nutribot.js" defer></script>
 *
 * Agrega un boton flotante verde; al pulsarlo abre el chat (/chat) en un iframe.
 * El iframe solo carga la vista del cliente: no da acceso al panel administrativo.
 */
(function () {
  if (window.__nutribotCargado) { return; }
  window.__nutribotCargado = true;

  var script = document.currentScript;
  var origen = (script && script.getAttribute('data-origen')) || (script ? new URL(script.src).origin : '');

  var estilo = document.createElement('style');
  estilo.textContent =
    '.nutribot-boton{position:fixed;right:24px;bottom:24px;width:60px;height:60px;border-radius:50%;border:0;' +
    'background:#1b6b2f;color:#fff;font-size:26px;cursor:pointer;box-shadow:0 8px 24px rgba(20,60,30,.35);z-index:2147483646}' +
    '.nutribot-marco{position:fixed;right:24px;bottom:96px;width:380px;height:600px;max-width:calc(100vw - 32px);' +
    'max-height:calc(100vh - 120px);border:0;border-radius:16px;box-shadow:0 18px 50px rgba(20,40,28,.25);' +
    'background:#fff;z-index:2147483647;display:none}' +
    '.nutribot-marco.abierto{display:block}' +
    '@media(max-width:500px){.nutribot-marco{right:0;bottom:0;width:100vw;height:100vh;max-width:none;max-height:none;border-radius:0}}';
  document.head.appendChild(estilo);

  var marco = document.createElement('iframe');
  marco.className = 'nutribot-marco';
  marco.title = 'Chat con NutriBot';
  marco.setAttribute('loading', 'lazy');

  var boton = document.createElement('button');
  boton.className = 'nutribot-boton';
  boton.type = 'button';
  boton.setAttribute('aria-label', 'Abrir el chat con NutriBot');
  boton.textContent = '💬';
  boton.addEventListener('click', function () {
    if (!marco.src) { marco.src = origen + '/chat'; }   // se carga recien al abrirlo
    var abierto = marco.classList.toggle('abierto');
    boton.textContent = abierto ? '✕' : '💬';
    boton.setAttribute('aria-label', abierto ? 'Cerrar el chat' : 'Abrir el chat con NutriBot');
  });

  function montar() { document.body.appendChild(marco); document.body.appendChild(boton); }
  if (document.body) { montar(); } else { document.addEventListener('DOMContentLoaded', montar); }
})();
