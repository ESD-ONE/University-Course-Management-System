import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MyEnrollmentsComponent } from './my-enrollments.component';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { CourseEnrollmentResponse } from '../../core/models/enrollment.model';

describe('MyEnrollmentsComponent', () => {
  let enrollment: jasmine.SpyObj<EnrollmentService>;

  function row(p: Partial<CourseEnrollmentResponse>): CourseEnrollmentResponse {
    return { id: 'e1', courseCode: 'CS101', status: 'ENROLLED', credits: 15, ...p } as CourseEnrollmentResponse;
  }

  function make(): MyEnrollmentsComponent {
    return TestBed.createComponent(MyEnrollmentsComponent).componentInstance;
  }

  beforeEach(() => {
    enrollment = jasmine.createSpyObj<EnrollmentService>('EnrollmentService', ['myEnrollments', 'drop']);
    enrollment.myEnrollments.and.returnValue(of([row({ id: 'e1', credits: 15 }), row({ id: 'e2', status: 'WAITLISTED', credits: 10 })]));
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: EnrollmentService, useValue: enrollment }] });
  });

  it('loads rows on construction', () => {
    const c = make();
    expect(c.rows().length).toBe(2);
    expect(c.loading()).toBeFalse();
  });

  it('enrolledCredits sums only ENROLLED rows', () => {
    const c = make();
    expect(c.enrolledCredits()).toBe(15); // the waitlisted 10 is excluded
  });

  it('records a load error', () => {
    enrollment.myEnrollments.and.returnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loadError()).toBeTruthy();
  });

  it('drop removes the enrollment and reloads', () => {
    enrollment.drop.and.returnValue(of(void 0));
    const c = make();
    enrollment.myEnrollments.calls.reset();
    c.drop(row({ id: 'e1' }));
    expect(enrollment.drop).toHaveBeenCalledWith('e1');
    expect(c.toast()?.message).toContain('Dropped CS101');
    expect(enrollment.myEnrollments).toHaveBeenCalled();
    expect(c.confirming()).toBeNull();
  });

  it('drop surfaces an error as a toast', () => {
    enrollment.drop.and.returnValue(throwError(() => new Error('nope')));
    const c = make();
    c.drop(row({ id: 'e1' }));
    expect(c.toast()?.error).toBeTrue();
    expect(c.busy()).toBeFalse();
  });
});
