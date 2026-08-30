import { Component, ElementRef, HostListener, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideCircleQuestionMark } from '@lucide/angular';

@Component({
  selector: 'app-help-hint',
  standalone: true,
  imports: [CommonModule, LucideCircleQuestionMark],
  template: `
    <span class="help-hint">
      <button
        type="button"
        class="help-trigger"
        (click)="toggle($event)"
        [attr.aria-expanded]="open"
        aria-label="Show help"
      >
        <svg lucideCircleQuestionMark size="14"></svg>
      </button>
      <div class="help-panel" *ngIf="open" role="note">{{ text }}</div>
    </span>
  `
})
export class HelpHintComponent {
  @Input({ required: true }) text!: string;

  open = false;
  private readonly el = inject(ElementRef);

  toggle(event: Event): void {
    event.stopPropagation();
    this.open = !this.open;
  }

  @HostListener('document:click', ['$event'])
  closeOnOutsideClick(event: MouseEvent): void {
    if (this.open && !this.el.nativeElement.contains(event.target)) {
      this.open = false;
    }
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.open = false;
  }
}
