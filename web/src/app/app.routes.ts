import { Routes } from '@angular/router';
import {
  adminGuard,
  authGuard,
  departmentScopedGuard,
  guestGuard,
  lecturerGuard,
  staffGuard,
  studentGuard,
} from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users-list.component').then((m) => m.UsersListComponent),
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent),
      },
      {
        path: 'reference-data',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/reference-data/reference-data.component').then((m) => m.ReferenceDataComponent),
      },
      {
        path: 'academic-calendar',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/academic-structure/academic-calendar.component').then(
            (m) => m.AcademicCalendarComponent,
          ),
      },
      {
        path: 'reference-degree-details',
        canActivate: [lecturerGuard],
        loadComponent: () =>
          import('./features/academic-structure/reference-degree-details.component').then(
            (m) => m.ReferenceDegreeDetailsComponent,
          ),
      },
      {
        path: 'program-management',
        canActivate: [departmentScopedGuard],
        loadComponent: () =>
          import('./features/academic-structure/program-management.component').then(
            (m) => m.ProgramManagementComponent,
          ),
      },
      {
        path: 'courses',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/academic-structure/admin-courses.component').then((m) => m.AdminCoursesComponent),
      },
      {
        path: 'courses/create',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/academic-structure/create-course.component').then((m) => m.CreateCourseComponent),
      },
      {
        path: 'my-courses',
        canActivate: [lecturerGuard],
        loadComponent: () =>
          import('./features/academic-structure/lecturer-courses.component').then(
            (m) => m.LecturerCoursesComponent,
          ),
      },
      {
        path: 'my-courses/create',
        canActivate: [lecturerGuard],
        loadComponent: () =>
          import('./features/academic-structure/create-course.component').then((m) => m.CreateCourseComponent),
      },
      // Enrollment
      {
        path: 'registrations',
        canActivate: [staffGuard],
        loadComponent: () =>
          import('./features/enrollment/registrations.component').then((m) => m.RegistrationsComponent),
      },
      {
        path: 'students',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/enrollment/students.component').then((m) => m.StudentsComponent),
      },
      {
        path: 'browse',
        canActivate: [studentGuard],
        loadComponent: () =>
          import('./features/enrollment/browse-courses.component').then((m) => m.BrowseCoursesComponent),
      },
      {
        path: 'my-enrollments',
        canActivate: [studentGuard],
        loadComponent: () =>
          import('./features/enrollment/my-enrollments.component').then((m) => m.MyEnrollmentsComponent),
      },
      {
        path: 'my-program',
        canActivate: [studentGuard],
        loadComponent: () => import('./features/enrollment/my-program.component').then((m) => m.MyProgramComponent),
      },
      // Assignment & Grading
      {
        path: 'assignments',
        canActivate: [staffGuard],
        loadComponent: () =>
          import('./features/grading/lecturer-assignments.component').then((m) => m.LecturerAssignmentsComponent),
      },
      {
        path: 'grading',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/grading/grading.component').then((m) => m.GradingComponent),
      },
      {
        path: 'my-assignments',
        canActivate: [studentGuard],
        loadComponent: () =>
          import('./features/grading/my-assignments.component').then((m) => m.MyAssignmentsComponent),
      },
      {
        path: 'grades',
        canActivate: [studentGuard],
        loadComponent: () => import('./features/grading/grades.component').then((m) => m.GradesComponent),
      },
      // Analytics & Reporting, Roles & Permissions
      {
        path: 'reports',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/analytics/reports.component').then((m) => m.ReportsComponent),
      },
      {
        path: 'roles',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/roles/roles-permissions.component').then((m) => m.RolesPermissionsComponent),
      },
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
