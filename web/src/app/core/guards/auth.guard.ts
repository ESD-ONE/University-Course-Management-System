import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  const queryParams = auth.consumeExpiredNotice() ? { expired: 1 } : {};
  return inject(Router).createUrlTree(['/login'], { queryParams });
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/app']);
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.currentUser()?.role === 'ADMIN') {
    return true;
  }
  return inject(Router).createUrlTree(['/app']);
};

export const lecturerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.currentUser()?.role === 'LECTURER') {
    return true;
  }
  return inject(Router).createUrlTree(['/app']);
};

export const studentGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.currentUser()?.role === 'STUDENT') {
    return true;
  }
  return inject(Router).createUrlTree(['/app']);
};

/** Admins and lecturers — e.g. Registrations, which the wireframe gives lecturers too. */
export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const role = auth.currentUser()?.role;
  if (role === 'ADMIN' || role === 'LECTURER') {
    return true;
  }
  return inject(Router).createUrlTree(['/app']);
};

/** Admins, plus anyone holding a DEPARTMENT-scoped role_scope_assignment (e.g. a
 * "Department Head") — the real authority academic-structure-service's PermissionResolver
 * already grants them over their department's degree programs/courses. See gap G-01. */
export const departmentScopedGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.currentUser()?.role === 'ADMIN' || auth.hasDepartmentScope()) {
    return true;
  }
  return inject(Router).createUrlTree(['/app']);
};
