import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { DegreeProgramsComponent } from './degree-programs.component';
import { ReferenceDegreeDetailsComponent } from './reference-degree-details.component';

type ProgramManagementTab = 'reference' | 'years';

/** One nav entry, two tabs — the reference-degree catalog and the per-academic-year
 * assignment screen are different concerns but belong under the same "Program
 * Management" umbrella, so they're combined here instead of two separate sidebar links. */
@Component({
  selector: 'app-program-management',
  standalone: true,
  imports: [CommonModule, ReferenceDegreeDetailsComponent, DegreeProgramsComponent],
  templateUrl: './program-management.component.html',
})
export class ProgramManagementComponent {
  readonly tab = signal<ProgramManagementTab>('reference');

  selectTab(tab: ProgramManagementTab): void {
    this.tab.set(tab);
  }
}
