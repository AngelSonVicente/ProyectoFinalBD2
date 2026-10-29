import { Routes } from '@angular/router';
import { Buscador } from './pages/buscador/buscador';
import { Carga } from './pages/carga/carga';
import { Dashboard } from './pages/dashboard/dashboard';
import { Grafo } from './pages/grafo/grafo';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'carga', component: Carga, title: 'Carga de archivos' },
  { path: 'dashboard', component: Dashboard, title: 'Dashboard' },
  { path: 'grafo', component: Grafo, title: 'Explorador de grafos' },
  { path: 'buscador', component: Buscador, title: 'Buscador' },
  { path: '**', redirectTo: 'dashboard' },
];
