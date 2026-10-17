import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { REFERENCE_API_BASE_URL } from '../config/api.config';
import {
  CourseCategoryResponse,
  CreateCourseCategoryRequest,
  CreateDepartmentRequest,
  CreateFacultyRequest,
  CreateGradingScaleRequest,
  DepartmentResponse,
  FacultyResponse,
  GradingScaleResponse,
  UpdateCourseCategoryRequest,
  UpdateDepartmentRequest,
  UpdateFacultyRequest,
  UpdateGradingScaleRequest,
} from '../models/reference-data.model';

@Injectable({ providedIn: 'root' })
export class ReferenceDataService {
  constructor(private http: HttpClient) {}

  listFaculties(): Observable<FacultyResponse[]> {
    return this.http.get<FacultyResponse[]>(`${REFERENCE_API_BASE_URL}/faculties`);
  }

  createFaculty(request: CreateFacultyRequest): Observable<FacultyResponse> {
    return this.http.post<FacultyResponse>(`${REFERENCE_API_BASE_URL}/faculties`, request);
  }

  updateFaculty(id: string, request: UpdateFacultyRequest): Observable<FacultyResponse> {
    return this.http.put<FacultyResponse>(`${REFERENCE_API_BASE_URL}/faculties/${id}`, request);
  }

  deleteFaculty(id: string): Observable<void> {
    return this.http.delete<void>(`${REFERENCE_API_BASE_URL}/faculties/${id}`);
  }

  listDepartments(facultyId?: string): Observable<DepartmentResponse[]> {
    const params: Record<string, string> = facultyId ? { facultyId } : {};
    return this.http.get<DepartmentResponse[]>(`${REFERENCE_API_BASE_URL}/departments`, { params });
  }

  createDepartment(request: CreateDepartmentRequest): Observable<DepartmentResponse> {
    return this.http.post<DepartmentResponse>(`${REFERENCE_API_BASE_URL}/departments`, request);
  }

  updateDepartment(id: string, request: UpdateDepartmentRequest): Observable<DepartmentResponse> {
    return this.http.put<DepartmentResponse>(`${REFERENCE_API_BASE_URL}/departments/${id}`, request);
  }

  deleteDepartment(id: string): Observable<void> {
    return this.http.delete<void>(`${REFERENCE_API_BASE_URL}/departments/${id}`);
  }

  listGradingScales(): Observable<GradingScaleResponse[]> {
    return this.http.get<GradingScaleResponse[]>(`${REFERENCE_API_BASE_URL}/grading-scales`);
  }

  createGradingScale(request: CreateGradingScaleRequest): Observable<GradingScaleResponse> {
    return this.http.post<GradingScaleResponse>(`${REFERENCE_API_BASE_URL}/grading-scales`, request);
  }

  updateGradingScale(id: string, request: UpdateGradingScaleRequest): Observable<GradingScaleResponse> {
    return this.http.put<GradingScaleResponse>(`${REFERENCE_API_BASE_URL}/grading-scales/${id}`, request);
  }

  deleteGradingScale(id: string): Observable<void> {
    return this.http.delete<void>(`${REFERENCE_API_BASE_URL}/grading-scales/${id}`);
  }

  listCourseCategories(): Observable<CourseCategoryResponse[]> {
    return this.http.get<CourseCategoryResponse[]>(`${REFERENCE_API_BASE_URL}/course-categories`);
  }

  createCourseCategory(request: CreateCourseCategoryRequest): Observable<CourseCategoryResponse> {
    return this.http.post<CourseCategoryResponse>(`${REFERENCE_API_BASE_URL}/course-categories`, request);
  }

  updateCourseCategory(id: string, request: UpdateCourseCategoryRequest): Observable<CourseCategoryResponse> {
    return this.http.put<CourseCategoryResponse>(`${REFERENCE_API_BASE_URL}/course-categories/${id}`, request);
  }

  deleteCourseCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${REFERENCE_API_BASE_URL}/course-categories/${id}`);
  }
}
