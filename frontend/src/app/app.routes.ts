import { Routes } from '@angular/router';

// Cada pantalla se carga al entrar en ella para que el bundle inicial sea liviano
export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'carga',
    loadComponent: () => import('./pages/carga/carga').then((m) => m.Carga),
    title: 'Carga de archivos',
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
    title: 'Dashboard',
  },
  {
    path: 'grafo',
    loadComponent: () => import('./pages/grafo/grafo').then((m) => m.Grafo),
    title: 'Explorador de grafos',
  },
  {
    path: 'buscador',
    loadComponent: () => import('./pages/buscador/buscador').then((m) => m.Buscador),
    title: 'Buscador',
  },
  { path: '**', redirectTo: 'dashboard' },
];
