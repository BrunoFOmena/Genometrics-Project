import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';

@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly messages = inject(MessageService);

  info(message: string, durationMs = 3500): void {
    this.messages.add({ severity: 'info', summary: 'Info', detail: message, life: durationMs });
  }

  success(message: string, durationMs = 3500): void {
    this.messages.add({ severity: 'success', summary: 'Success', detail: message, life: durationMs });
  }

  error(message: string, durationMs = 5000): void {
    this.messages.add({ severity: 'error', summary: 'Error', detail: message, life: durationMs });
  }
}
