import { Directive, Input, OnInit, TemplateRef, ViewContainerRef, inject } from '@angular/core';
import { AuthService } from '../core/services/auth.service';

/**
 * Structural directive gating a template on a permission: `*can="'Course Content:Edit'"`
 * or, scoped, `*can="'Course Content:Edit'; scope: offering.degreeProgramId"`. UX only —
 * every endpoint still enforces its own check, so hiding a button here never substitutes
 * for a real server-side decision.
 */
@Directive({ selector: '[can]', standalone: true })
export class CanDirective implements OnInit {
  private auth = inject(AuthService);
  private viewContainer = inject(ViewContainerRef);
  private templateRef = inject(TemplateRef<unknown>);

  private permission = '';
  private scopeId?: string;
  private rendered = false;

  @Input() set can(value: string) {
    this.permission = value;
    this.evaluate();
  }

  @Input() set canScope(value: string | undefined) {
    this.scopeId = value;
    this.evaluate();
  }

  ngOnInit(): void {
    this.evaluate();
  }

  private evaluate(): void {
    const [module, actionKey] = this.permission.split(':');
    const allowed = !!module && !!actionKey && this.auth.can(module, actionKey, this.scopeId);
    if (allowed && !this.rendered) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.rendered = true;
    } else if (!allowed && this.rendered) {
      this.viewContainer.clear();
      this.rendered = false;
    }
  }
}
