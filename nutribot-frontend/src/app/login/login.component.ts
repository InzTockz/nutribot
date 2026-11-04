import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';

/** Sprint 4 (enabler de seguridad): inicio de sesion del personal de Battilana. */
@Component({
  selector: 'app-login',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {

  username = '';
  password = '';
  cargando = false;
  error = '';
  aviso = '';

  constructor(private auth: AuthService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void {
    if (this.auth.estaAutenticado) {
      this.router.navigateByUrl(this.auth.rutaInicio());
      return;
    }
    if (this.route.snapshot.queryParamMap.get('vencida')) {
      this.aviso = 'Tu sesión venció. Vuelve a iniciar sesión.';
    }
  }

  ingresar(): void {
    if (!this.username.trim() || !this.password || this.cargando) {
      return;
    }
    this.cargando = true;
    this.error = '';
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        this.password = '';
        // Solo se regresa a rutas del panel: evita redirecciones a sitios externos
        const volver = this.route.snapshot.queryParamMap.get('volver');
        this.router.navigateByUrl(volver && volver.startsWith('/admin') ? volver : this.auth.rutaInicio());
      },
      error: (err: HttpErrorResponse) => {
        this.password = '';
        this.cargando = false;
        this.error = err.status === 401
          ? 'Usuario o contraseña incorrectos.'
          : 'No se pudo conectar con el servidor. Intenta nuevamente.';
      }
    });
  }
}
