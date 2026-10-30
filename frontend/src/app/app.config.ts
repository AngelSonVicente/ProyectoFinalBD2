import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideRouter } from '@angular/router';
import { provideEchartsCore } from 'ngx-echarts';

import { routes } from './app.routes';
import { errorInterceptor } from './core/error.interceptor';
import { PaginadorEs } from './core/paginador-es';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([errorInterceptor])),
    { provide: MatPaginatorIntl, useClass: PaginadorEs },
    // Carga ECharts de forma diferida para no inflar el bundle inicial
    provideEchartsCore({ echarts: () => import('echarts') }),
  ],
};
