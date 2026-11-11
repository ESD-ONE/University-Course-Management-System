import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BASE_API_BASE_URL } from '../config/api.config';
import { PagedResponse } from '../models/pagination.model';
import {
  CourseCategoryResponse,
  CreateCourseCategoryRequest,
  CreateDepartmentRequest,
  CreateFacultyRequest,
  CreateGradingScaleRequest,
  DepartmentResponse,
  FacultyResponse,
  GradingScaleResponse,
  ReferenceCounts,
  ReferenceListQuery,
  UpdateCourseCategoryRequest,
  UpdateDepartmentRequest,
  UpdateFacultyRequest,
  UpdateGradingScaleRequest,
} from '../models/reference-data.model';

function queryParams(query: ReferenceListQuery): HttpParams {
  let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
  if (query.status) params = params.set('status', query.status);
  if (query.q) params = params.set('q', query.q);
  return params;
}

@Injectable({ providedIn: 'root' })
export class ReferenceDataService {
  constructor(private http: HttpClient) {}

  listFaculties(query: ReferenceListQuery = {}): Observable<PagedResponse<FacultyResponse>> {
    return this.http.get<PagedResponse<FacultyResponse>>(`${BASE_API_BASE_URL}/faculties`, { params: queryParams(query) });
  }

  facultyCounts(): Observable<ReferenceCounts> {
    return this.http.get<ReferenceCounts>(`${BASE_API_BASE_URL}/faculties/counts`);
  }

  createFaculty(request: CreateFacultyRequest): Observable<FacultyResponse> {
    return this.http.post<FacultyResponse>(`${BASE_API_BASE_URL}/faculties`, request);
  }

  updateFaculty(id: string, request: UpdateFacultyRequest): Observable<FacultyResponse> {
    return this.http.put<FacultyResponse>(`${BASE_API_BASE_URL}/faculties/${id}`, request);
  }

  deleteFaculty(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE_API_BASE_URL}/faculties/${id}`);
  }

  listDepartments(facultyId: string | undefined, query: ReferenceListQuery = {}): Observable<PagedResponse<DepartmentResponse>> {
    let params = queryParams(query);
    if (facultyId) params = params.set('facultyId', facultyId);
    return this.http.get<PagedResponse<DepartmentResponse>>(`${BASE_API_BASE_URL}/departments`, { params });
  }

  departmentCounts(): Observable<ReferenceCounts> {
    return this.http.get<ReferenceCounts>(`${BASE_API_BASE_URL}/departments/counts`);
  }

  createDepartment(request: CreateDepartmentRequest): Observable<DepartmentResponse> {
    return this.http.post<DepartmentResponse>(`${BASE_API_BASE_URL}/departments`, request);
  }

  updateDepartment(id: string, request: UpdateDepartmentRequest): Observable<DepartmentResponse> {
    return this.http.put<DepartmentResponse>(`${BASE_API_BASE_URL}/departments/${id}`, request);
  }

  deleteDepartment(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE_API_BASE_URL}/departments/${id}`);
  }

  listGradingScales(query: ReferenceListQuery = {}): Observable<PagedResponse<GradingScaleResponse>> {
    return this.http.get<PagedResponse<GradingScaleResponse>>(`${BASE_API_BASE_URL}/grading-scales`, { params: queryParams(query) });
  }

  gradingScaleCounts(): Observable<ReferenceCounts> {
    return this.http.get<ReferenceCounts>(`${BASE_API_BASE_URL}/grading-scales/counts`);
  }

  createGradingScale(request: CreateGradingScaleRequest): Observable<GradingScaleResponse> {
    return this.http.post<GradingScaleResponse>(`${BASE_API_BASE_URL}/grading-scales`, request);
  }

  updateGradingScale(id: string, request: UpdateGradingScaleRequest): Observable<GradingScaleResponse> {
    return this.http.put<GradingScaleResponse>(`${BASE_API_BASE_URL}/grading-scales/${id}`, request);
  }

  deleteGradingScale(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE_API_BASE_URL}/grading-scales/${id}`);
  }

  listCourseCategories(query: ReferenceListQuery = {}): Observable<PagedResponse<CourseCategoryResponse>> {
    return this.http.get<PagedResponse<CourseCategoryResponse>>(`${BASE_API_BASE_URL}/course-categories`, { params: queryParams(query) });
  }

  courseCategoryCounts(): Observable<ReferenceCounts> {
    return this.http.get<ReferenceCounts>(`${BASE_API_BASE_URL}/course-categories/counts`);
  }

  createCourseCategory(request: CreateCourseCategoryRequest): Observable<CourseCategoryResponse> {
    return this.http.post<CourseCategoryResponse>(`${BASE_API_BASE_URL}/course-categories`, request);
  }

  updateCourseCategory(id: string, request: UpdateCourseCategoryRequest): Observable<CourseCategoryResponse> {
    return this.http.put<CourseCategoryResponse>(`${BASE_API_BASE_URL}/course-categories/${id}`, request);
  }

  deleteCourseCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE_API_BASE_URL}/course-categories/${id}`);
  }
}
