import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly secciones = [
    { ruta: '/dashboard', icono: 'insights', nombre: 'Dashboard' },
    { ruta: '/carga', icono: 'upload_file', nombre: 'Carga de archivos' },
    { ruta: '/grafo', icono: 'hub', nombre: 'Explorador de grafos' },
    { ruta: '/buscador', icono: 'search', nombre: 'Buscador' },
  ];
}
