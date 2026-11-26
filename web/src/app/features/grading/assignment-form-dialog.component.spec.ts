import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AssignmentFormDialogComponent } from './assignment-form-dialog.component';
import { GradingService } from '../../core/services/grading.service';
import { CourseContentService } from '../../core/services/course-content.service';
import { AssignmentResponse } from '../../core/models/grading.model';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import { WeekResponse } from '../../core/models/course-content.model';

describe('AssignmentFormDialogComponent', () => {
  let grading: jasmine.SpyObj<GradingService>;
  let content: jasmine.SpyObj<CourseContentService>;

  const existing = {
    id: 'as-1',
    offeringId: 'off-2',
    courseCode: 'CS101',
    courseName: 'Algorithms',
    title: 'Essay 1',
    description: null,
    dueDate: '2030-01-15T10:00:00Z',
    maxMarks: 50,
    weekId: 'w-2',
  } as AssignmentResponse;

  function make(setup: (c: AssignmentFormDialogComponent) => void = () => {}): AssignmentFormDialogComponent {
    const c = TestBed.createComponent(AssignmentFormDialogComponent).componentInstance;
    c.offerings = [{ id: 'off-1' }, { id: 'off-2' }] as CourseOfferingResponse[];
    setup(c);
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    grading = jasmine.createSpyObj<GradingService>('GradingService', ['create', 'update', 'uploadBrief']);
    content = jasmine.createSpyObj<CourseContentService>('CourseContentService', ['listWeeks']);
    content.listWeeks.and.returnValue(of([]));
    TestBed.configureTestingModule({
      imports: [AssignmentFormDialogComponent],
      providers: [
        { provide: GradingService, useValue: grading },
        { provide: CourseContentService, useValue: content },
      ],
    });
  });

  it('starts a new assignment on the first course with 100 marks', () => {
    const c = make();
    expect(c.isEdit).toBeFalse();
    expect(c.courseLocked).toBeFalse();
    expect(c.form.getRawValue().offeringId).toBe('off-1');
    expect(c.form.getRawValue().maxMarks).toBe(100);
    expect(content.listWeeks).toHaveBeenCalledWith('off-1');
  });

  it('loads the weeks of the chosen course in week order and resets the week', () => {
    content.listWeeks.and.returnValue(of([{ id: 'w-2', weekNumber: 2 }, { id: 'w-1', weekNumber: 1 }] as WeekResponse[]));
    const c = make();
    c.form.controls.weekId.setValue('w-9');

    c.form.controls.offeringId.setValue('off-2');

    expect(content.listWeeks).toHaveBeenCalledWith('off-2');
    expect(c.weeks().map((w) => w.id)).toEqual(['w-1', 'w-2']);
    expect(c.form.getRawValue().weekId).toBe('');
  });

  it('fills the form from an existing assignment and locks its course', () => {
    const c = make((d) => (d.assignment = existing));
    expect(c.isEdit).toBeTrue();
    expect(c.form.controls.offeringId.disabled).toBeTrue();
    expect(c.form.getRawValue()).toEqual(
      jasmine.objectContaining({ offeringId: 'off-2', title: 'Essay 1', maxMarks: 50, weekId: 'w-2' }),
    );
    expect(c.courseLabel()).toBe('CS101 — Algorithms');
  });

  it('uses the fixed course and preset week from the course workspace', () => {
    const c = make((d) => {
      d.fixedOfferingId = 'off-2';
      d.fixedOfferingLabel = 'CS101 — Algorithms (2030)';
      d.presetWeekId = 'w-3';
    });
    expect(c.courseLocked).toBeTrue();
    expect(c.form.getRawValue().offeringId).toBe('off-2');
    expect(c.form.getRawValue().weekId).toBe('w-3');
    expect(c.courseLabel()).toBe('CS101 — Algorithms (2030)');
  });

  it('does not save an invalid form', () => {
    const c = make();
    c.save();
    expect(c.form.controls.title.touched).toBeTrue();
    expect(grading.create).not.toHaveBeenCalled();
  });

  it('creates the assignment with a trimmed title and emits it', () => {
    grading.create.and.returnValue(of(existing));
    const c = make();
    const emitted: AssignmentResponse[] = [];
    c.saved.subscribe((a) => emitted.push(a));

    c.form.patchValue({ title: '  Essay 1  ', dueDate: '2030-01-15T10:00', maxMarks: 50 });
    c.save();

    expect(grading.create).toHaveBeenCalledWith({
      offeringId: 'off-1',
      title: 'Essay 1',
      description: null,
      dueDate: new Date('2030-01-15T10:00').toISOString(),
      maxMarks: 50,
      weekId: null,
    });
    expect(grading.uploadBrief).not.toHaveBeenCalled();
    expect(emitted).toEqual([existing]);
    expect(c.saving()).toBeFalse();
  });

  it('updates an existing assignment instead of creating one', () => {
    grading.update.and.returnValue(of(existing));
    const c = make((d) => (d.assignment = existing));
    c.save();
    expect(grading.update).toHaveBeenCalledWith('as-1', jasmine.objectContaining({ offeringId: 'off-2', title: 'Essay 1' }));
    expect(grading.create).not.toHaveBeenCalled();
  });

  it('uploads the brief after saving when one was chosen', () => {
    const withBrief = { ...existing, briefFileName: 'brief.pdf' } as AssignmentResponse;
    grading.create.and.returnValue(of(existing));
    grading.uploadBrief.and.returnValue(of(withBrief));
    const c = make();
    const file = new File(['content'], 'brief.pdf');
    const emitted: AssignmentResponse[] = [];
    c.saved.subscribe((a) => emitted.push(a));

    c.brief.set(file);
    c.form.patchValue({ title: 'Essay 1' });
    c.save();

    expect(grading.uploadBrief).toHaveBeenCalledWith('as-1', file);
    expect(emitted).toEqual([withBrief]);
  });

  it('after a failed brief upload, saving again updates the assignment instead of creating a second one', () => {
    grading.create.and.returnValue(of(existing));
    grading.update.and.returnValue(of(existing));
    grading.uploadBrief.and.returnValue(throwError(() => new Error('network')));
    const c = make();
    c.brief.set(new File(['content'], 'brief.pdf'));
    c.form.patchValue({ title: 'Essay 1' });

    c.save();
    expect(c.error()).toContain('Save again to retry the upload.');
    expect(c.saving()).toBeFalse();

    c.save();
    expect(grading.create).toHaveBeenCalledTimes(1);
    expect(grading.update).toHaveBeenCalledTimes(1);
  });

  it('shows an error when the assignment itself cannot be saved', () => {
    grading.create.and.returnValue(throwError(() => new Error('network')));
    const c = make();
    c.form.patchValue({ title: 'Essay 1' });
    c.save();
    expect(c.error()).toBeTruthy();
    expect(c.error()).not.toContain('Save again');
    expect(grading.uploadBrief).not.toHaveBeenCalled();
  });

  it('rejects a brief larger than 20MB', () => {
    const c = make();
    const event = { target: { files: [{ name: 'big.pdf', size: 20 * 1024 * 1024 + 1 }], value: '' } } as unknown as Event;
    c.pickBrief(event);
    expect(c.brief()).toBeNull();
    expect(c.error()).toBe('That file is larger than 20MB.');
  });
});
