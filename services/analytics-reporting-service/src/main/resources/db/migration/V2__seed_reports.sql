-- The report library from the wireframe's adminReports() screen. Each name maps to a
-- generator in ReportGenerator; the library itself is fixed (no "create report" in the
-- design), only schedules are user-managed.
INSERT INTO reports (name, description, format) VALUES
    ('Enrollment Summary',    'Total enrollments by program and course offering', 'CSV'),
    ('Program Performance',   'Pass rates and average grades by degree program',  'PDF'),
    ('Course Pass Rate',      'Pass / fail breakdown per course offering',         'CSV'),
    ('Grade Distribution',    'Grade spread across all active courses',            'PDF'),
    ('Registration Activity', 'Who registered which student, and when',           'CSV'),
    ('User Activity',         'Every account with its role and status',            'CSV');
