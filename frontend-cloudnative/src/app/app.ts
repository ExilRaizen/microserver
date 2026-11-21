import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { MsalBroadcastService, MsalService } from '@azure/msal-angular';
import { InteractionStatus } from '@azure/msal-browser';
import { filter } from 'rxjs/operators';

import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private msalService = inject(MsalService);
  private msalBroadcastService = inject(MsalBroadcastService);
  private destroyRef = inject(DestroyRef);
  private http = inject(HttpClient);

  isLoggedIn = signal(false);
  testResult = signal('');
  // Respuesta completa en JSON para los botones de Kafka
  detalle = signal('');

  ngOnInit(): void {
    this.msalService.handleRedirectObservable().subscribe();

    this.msalBroadcastService.inProgress$
      .pipe(
        filter((status: InteractionStatus) => status === InteractionStatus.None),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        this.isLoggedIn.set(this.msalService.instance.getAllAccounts().length > 0);
      });
  }

  login(): void {
    this.msalService.loginRedirect();
  }

  logout(): void {
    this.msalService.logoutRedirect();
  }

  probarPedido(): void {
    this.detalle.set('');
    this.testResult.set('Creando pedido...');
    const url = `${environment.apiConfig.pedidosApiUrl}/api/pedidos`;
    const body = { producto: 'Plan Minecraft 4GB', cantidad: 1 };

    // El pedido se guarda y se publica en RabbitMQ y en Kafka.
    // Si el plan no tiene stock según el catálogo local de Pedidos, responde 409.
    this.http.post<{ id: number }>(url, body).subscribe({
      next: (res) => this.testResult.set(`OK: pedido #${res.id} creado y enviado a RabbitMQ y Kafka`),
      error: (err) => this.testResult.set(this.mensajeError(err))
    });
  }

  probarCatalogo(): void {
    this.detalle.set('');
    this.testResult.set('Consultando catálogo...');
    const url = `${environment.apiConfig.catalogoApiUrl}/api/catalogo`;

    this.http.get<unknown[]>(url).subscribe({
      next: (res) => this.testResult.set(`OK: ${res.length} plan(es) en el catálogo`),
      error: (err) => this.testResult.set(this.mensajeError(err))
    });
  }

  probarSoporte(): void {
    this.detalle.set('');
    this.testResult.set('Creando ticket de prueba...');
    const url = `${environment.apiConfig.soporteApiUrl}/api/soporte`;
    const body = { asunto: 'Prueba desde el frontend', mensaje: 'Este es un ticket de prueba.' };

    this.http.post<{ id: number }>(url, body).subscribe({
      next: (res) => this.testResult.set(`OK: ticket #${res.id} creado`),
      error: (err) => this.testResult.set(this.mensajeError(err))
    });
  }

  // ========== Kafka ==========

  verAnaliticaPlanes(): void {
    this.consultar('Analítica por plan', `${environment.apiConfig.soporteApiUrl}/api/analitica/planes`);
  }

  verAnaliticaClientes(): void {
    this.consultar('Analítica por cliente', `${environment.apiConfig.soporteApiUrl}/api/analitica/clientes`);
  }

  verCatalogoLocal(): void {
    this.consultar('Catálogo local de Pedidos (desde catalogo.planes)',
      `${environment.apiConfig.pedidosApiUrl}/api/pedidos/catalogo-local`);
  }

  verTopicsKafka(): void {
    this.consultar('Tópicos de Kafka', `${environment.apiConfig.soporteApiUrl}/api/kafka/topics`);
  }

  verGruposKafka(): void {
    this.consultar('Grupos de consumidores', `${environment.apiConfig.soporteApiUrl}/api/kafka/consumer-groups`);
  }

  private consultar(titulo: string, url: string): void {
    this.testResult.set(`Consultando ${titulo.toLowerCase()}...`);
    this.detalle.set('');
    this.http.get<unknown>(url).subscribe({
      next: (res) => {
        this.testResult.set(`OK: ${titulo}`);
        this.detalle.set(JSON.stringify(res, null, 2));
      },
      error: (err) => this.testResult.set(this.mensajeError(err))
    });
  }

  /**
   * Muestra solo el motivo del error: "message" en las respuestas de Spring
   * y "detalles" en las de Soporte (ErrorResponse).
   */
  private mensajeError(err: HttpErrorResponse): string {
    const cuerpo = err.error;
    const motivo = cuerpo?.detalles?.join(', ') ?? cuerpo?.message ?? cuerpo?.error ?? err.message;
    return `Error ${err.status}: ${motivo}`;
  }
}
