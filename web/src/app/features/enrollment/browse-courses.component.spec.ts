import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { BrowseCoursesComponent } from './browse-courses.component';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { CatalogEntry, CourseEnrollmentResponse } from '../../core/models/enrollment.model';

describe('BrowseCoursesComponent', () => {
  let enrollment: jasmine.SpyObj<EnrollmentService>;

  function entry(p: Partial<CatalogEntry>): CatalogEntry {
    return {
      offeringId: 'o1', code: 'CS101', name: 'Intro', academicYearName: '2025/2026',
      yearOfStudy: 1, seatsLeft: 5, openForEnrollment: true,
      ...p,
    } as CatalogEntry;
  }
  const CATALOG = [
    entry({ offeringId: 'o1', code: 'CS101', name: 'Intro to CS', yearOfStudy: 1, seatsLeft: 3 }),
    entry({ offeringId: 'o2', code: 'MA201', name: 'Calculus', yearOfStudy: 2, seatsLeft: 0, openForEnrollment: false }),
    entry({ offeringId: 'o3', code: 'CS301', name: 'Algorithms', academicYearName: '2024/2025', yearOfStudy: 3, seatsLeft: 10 }),
  ];

  function make(): BrowseCoursesComponent {
    return TestBed.createComponent(BrowseCoursesComponent).componentInstance;
  }

  beforeEach(() => {
    enrollment = jasmine.createSpyObj<EnrollmentService>('EnrollmentService', ['catalog', 'enroll']);
    enrollment.catalog.and.returnValue(of(CATALOG));
    TestBed.configureTestingModule({ providers: [{ provide: EnrollmentService, useValue: enrollment }] });
  });

  it('loads the catalog on construction', () => {
    const c = make();
    expect(c.entries().length).toBe(3);
    expect(c.loading()).toBeFalse();
  });

  it('records a load error', () => {
    enrollment.catalog.and.returnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loadError()).toBeTruthy();
    expect(c.loading()).toBeFalse();
  });

  it('filters by search text across code and name', () => {
    const c = make();
    c.onSearchChange('algo');
    expect(c.filtered().map((e) => e.offeringId)).toEqual(['o3']);
  });

  it('filters by academic year and year of study', () => {
    const c = make();
    c.onYearFilterChange('2025/2026');
    expect(c.filtered().map((e) => e.offeringId)).toEqual(['o1', 'o2']);
    c.onYearFilterChange('');
    c.onYearOfStudyFilterChange('3');
    expect(c.filtered().map((e) => e.offeringId)).toEqual(['o3']);
  });

  it('availableOnly keeps only open offerings with seats', () => {
    const c = make();
    c.onAvailableOnlyChange(true);
    expect(c.filtered().map((e) => e.offeringId)).toEqual(['o1', 'o3']); // o2 is full + closed
  });

  it('counts the active filters', () => {
    const c = make();
    expect(c.activeFilterCount()).toBe(0);
    c.onSearchChange('cs');
    c.onAvailableOnlyChange(true);
    expect(c.activeFilterCount()).toBe(2);
  });

  it('paginates the filtered result', () => {
    const c = make();
    c.onPageSizeChange(2);
    expect(c.pagedEntries().length).toBe(2);
    expect(c.totalPages()).toBe(2);
    c.goToPage(1);
    expect(c.pagedEntries().length).toBe(1);
  });

  it('enroll success shows a toast and reloads', () => {
    enrollment.enroll.and.returnValue(of({ status: 'ENROLLED' } as CourseEnrollmentResponse));
    const c = make();
    enrollment.catalog.calls.reset();
    c.enroll(CATALOG[0]);
    expect(enrollment.enroll).toHaveBeenCalledWith({ offeringId: 'o1', joinWaitlistIfFull: false });
    expect(c.toast()?.message).toContain('Enrolled in CS101');
    expect(enrollment.catalog).toHaveBeenCalled();
  });

  it('a WAITLISTED result is reported as such', () => {
    enrollment.enroll.and.returnValue(of({ status: 'WAITLISTED' } as CourseEnrollmentResponse));
    const c = make();
    c.enroll(CATALOG[0]);
    expect(c.toast()?.message).toContain('waitlist for CS101');
  });

  it('a COURSE_FULL error offers the waitlist instead of erroring', () => {
    enrollment.enroll.and.returnValue(
      throwError(() => new HttpErrorResponse({ error: { code: 'COURSE_FULL' }, status: 409 })),
    );
    const c = make();
    c.enroll(CATALOG[0]);
    expect(c.waitlistOffer()).toBe(CATALOG[0]);
    expect(c.toast()).toBeNull();
  });
});
