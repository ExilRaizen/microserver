import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
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
    this.testResult.set('Consultando...');
    const url = `${environment.apiConfig.pedidosApiUrl}/api/pedidos/crear`;

    this.http.get(url, { responseType: 'text' }).subscribe({
      next: (res) => this.testResult.set(`OK: ${res}`),
      error: (err) => this.testResult.set(`Error ${err.status}: ${JSON.stringify(err.error)}`)
    });
  }
}
