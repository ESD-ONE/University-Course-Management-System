import {
  countActive,
  matchesText,
  numberOptions,
  paginate,
  plural,
  showingSummary,
  statusOptions,
  uniqueOptions,
} from './filters';

describe('filters', () => {
  it('countActive counts only values that are set', () => {
    expect(countActive('', null, undefined, false)).toBe(0);
    expect(countActive('ACTIVE', true, 0, '')).toBe(3);
  });

  it('matchesText is a case-insensitive "contains" across fields', () => {
    expect(matchesText('  ', 'anything')).toBeTrue();
    expect(matchesText('algo', 'CS101', 'Algorithms')).toBeTrue();
    expect(matchesText('101', null, 101)).toBeTrue();
    expect(matchesText('physics', 'CS101', 'Algorithms', undefined)).toBeFalse();
  });

  it('uniqueOptions keeps one option per distinct value, sorted by label', () => {
    const items = [
      { id: 'b', name: 'Week 10' },
      { id: 'a', name: 'Week 2' },
      { id: 'b', name: 'Week 10 (duplicate)' },
      { id: '', name: 'No id' },
    ];
    const options = uniqueOptions(
      items,
      (i) => i.id,
      (i) => i.name,
      (i) => i.id.toUpperCase(),
    );
    expect(options).toEqual([
      { value: 'a', label: 'Week 2', sublabel: 'A' },
      { value: 'b', label: 'Week 10', sublabel: 'B' },
    ]);
  });

  it('numberOptions drops blanks and duplicates and sorts ascending', () => {
    expect(numberOptions([4, null, 3, undefined, 4], (n) => `${n} years`)).toEqual([
      { value: '3', label: '3 years' },
      { value: '4', label: '4 years' },
    ]);
  });

  it('statusOptions turns enum names into readable labels', () => {
    expect(statusOptions(['ACTIVE', 'NOT_SUBMITTED'])).toEqual([
      { value: 'ACTIVE', label: 'Active' },
      { value: 'NOT_SUBMITTED', label: 'Not submitted' },
    ]);
  });

  it('plural adds an "s" for anything but one', () => {
    expect(plural(1, 'course')).toBe('1 course');
    expect(plural(0, 'course')).toBe('0 courses');
    expect(plural(2, 'course')).toBe('2 courses');
  });

  it('paginate returns one page of the array', () => {
    const items = [1, 2, 3, 4, 5];
    expect(paginate(items, 0, 2)).toEqual([1, 2]);
    expect(paginate(items, 2, 2)).toEqual([5]);
    expect(paginate(items, 3, 2)).toEqual([]);
  });

  it('showingSummary says "all" only when nothing is filtered out', () => {
    expect(showingSummary(3, 3, 'student')).toBe('Showing all 3 students');
    expect(showingSummary(1, 3, 'student')).toBe('Showing 1 of 3 students');
  });
});
