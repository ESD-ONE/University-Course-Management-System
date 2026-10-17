import { Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard, lecturerGuard } from './core/guards/auth.guard';

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
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/academic-structure/reference-degree-details.component').then(
            (m) => m.ReferenceDegreeDetailsComponent,
          ),
      },
      {
        path: 'degree-programs',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/academic-structure/degree-programs.component').then((m) => m.DegreeProgramsComponent),
      },
      {
        path: 'program-management',
        canActivate: [adminGuard],
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
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
