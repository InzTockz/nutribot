import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Sprint 4: el componente raiz solo muestra la pantalla de la ruta actual.
 * Antes contenia todo el sistema en pestanas; ahora cada pantalla vive en su propia carpeta.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />'
})
export class AppComponent {}
