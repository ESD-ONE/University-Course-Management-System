import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ACADEMIC_API_BASE_URL } from '../config/api.config';
import { PagedResponse } from '../models/pagination.model';
import {
  AcademicYearResponse,
  CourseAcademicYearResponse,
  CourseCatalogResponse,
  CourseResponse,
  CourseStatus,
  CreateAcademicYearRequest,
  CreateCourseAcademicYearRequest,
  CreateCourseCatalogRequest,
  CreateCourseRequest,
  CreateDegreeProgramAcademicYearRequest,
  CreateDegreeProgramRequest,
  CreateProgramCourseRequest,
  CreateSemesterRequest,
  DegreeProgramAcademicYearResponse,
  DegreeProgramResponse,
  ProgramCourseResponse,
  ProgramStatus,
  SemesterResponse,
  UpdateAcademicYearRequest,
  UpdateCourseAcademicYearRequest,
  UpdateCourseRequest,
  UpdateDegreeProgramAcademicYearRequest,
  UpdateDegreeProgramRequest,
  UpdateProgramCourseRequest,
  UpdateSemesterRequest,
  CourseOfferingResponse,
} from '../models/academic-structure.model';

export interface DegreeProgramListQuery {
  departmentId?: string | null;
  status?: ProgramStatus | '';
  q?: string;
  page?: number;
  size?: number;
}

export interface CourseListQuery {
  degreeProgramId?: string | null;
  yearOfStudy?: number | '';
  status?: CourseStatus | '';
  q?: string;
  page?: number;
  size?: number;
}

export interface CourseCatalogListQuery {
  departmentId?: string | null;
  q?: string;
  page?: number;
  size?: number;
}


@Injectable({ providedIn: 'root' })
export class AcademicStructureService {
  constructor(private http: HttpClient) {}

  listAcademicYears(): Observable<AcademicYearResponse[]> {
    return this.http.get<AcademicYearResponse[]>(`${ACADEMIC_API_BASE_URL}/academic-years`);
  }

  createAcademicYear(request: CreateAcademicYearRequest): Observable<AcademicYearResponse> {
    return this.http.post<AcademicYearResponse>(`${ACADEMIC_API_BASE_URL}/academic-years`, request);
  }

  updateAcademicYear(yearId: string, request: UpdateAcademicYearRequest): Observable<AcademicYearResponse> {
    return this.http.put<AcademicYearResponse>(`${ACADEMIC_API_BASE_URL}/academic-years/${yearId}`, request);
  }

  deleteAcademicYear(yearId: string): Observable<void> {
    return this.http.delete<void>(`${ACADEMIC_API_BASE_URL}/academic-years/${yearId}`);
  }

  addSemester(yearId: string, request: CreateSemesterRequest): Observable<SemesterResponse> {
    return this.http.post<SemesterResponse>(`${ACADEMIC_API_BASE_URL}/academic-years/${yearId}/semesters`, request);
  }

  updateSemester(yearId: string, semesterId: string, request: UpdateSemesterRequest): Observable<SemesterResponse> {
    return this.http.put<SemesterResponse>(`${ACADEMIC_API_BASE_URL}/academic-years/${yearId}/semesters/${semesterId}`, request);
  }

  deleteSemester(yearId: string, semesterId: string): Observable<void> {
    return this.http.delete<void>(`${ACADEMIC_API_BASE_URL}/academic-years/${yearId}/semesters/${semesterId}`);
  }

  /** departmentId/status/q are optional and combinable — departmentId is the "Reference
   * Degree Details" page's admin department filter, q a case-insensitive name/code search. */
  listDegreePrograms(query: DegreeProgramListQuery = {}): Observable<PagedResponse<DegreeProgramResponse>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
    if (query.departmentId) params = params.set('departmentId', query.departmentId);
    if (query.status) params = params.set('status', query.status);
    if (query.q) params = params.set('q', query.q);
    return this.http.get<PagedResponse<DegreeProgramResponse>>(`${ACADEMIC_API_BASE_URL}/degree-programs`, { params });
  }

  createDegreeProgram(request: CreateDegreeProgramRequest): Observable<DegreeProgramResponse> {
    return this.http.post<DegreeProgramResponse>(`${ACADEMIC_API_BASE_URL}/degree-programs`, request);
  }

  updateDegreeProgram(programId: string, request: UpdateDegreeProgramRequest): Observable<DegreeProgramResponse> {
    return this.http.put<DegreeProgramResponse>(`${ACADEMIC_API_BASE_URL}/degree-programs/${programId}`, request);
  }

  deleteDegreeProgram(programId: string): Observable<void> {
    return this.http.delete<void>(`${ACADEMIC_API_BASE_URL}/degree-programs/${programId}`);
  }

  listCurriculum(programId: string): Observable<ProgramCourseResponse[]> {
    return this.http.get<ProgramCourseResponse[]>(`${ACADEMIC_API_BASE_URL}/degree-programs/${programId}/curriculum`);
  }

  addCurriculumEntry(programId: string, request: CreateProgramCourseRequest): Observable<ProgramCourseResponse> {
    return this.http.post<ProgramCourseResponse>(
      `${ACADEMIC_API_BASE_URL}/degree-programs/${programId}/curriculum`,
      request,
    );
  }

  updateCurriculumEntry(programId: string, programCourseId: string, request: UpdateProgramCourseRequest): Observable<ProgramCourseResponse> {
    return this.http.put<ProgramCourseResponse>(
      `${ACADEMIC_API_BASE_URL}/degree-programs/${programId}/curriculum/${programCourseId}`,
      request,
    );
  }

  /** The "Degree Programs" screen's year filter — which reference degrees are
   * assigned/continuing for a given academic year. Omit academicYearId for "All Years". */
  listDegreeProgramAcademicYears(academicYearId?: string | null): Observable<DegreeProgramAcademicYearResponse[]> {
    const params: Record<string, string> = academicYearId ? { academicYearId } : {};
    return this.http.get<DegreeProgramAcademicYearResponse[]>(`${ACADEMIC_API_BASE_URL}/degree-program-academic-years`, {
      params,
    });
  }

  assignDegreeProgramToAcademicYear(
    programId: string,
    request: CreateDegreeProgramAcademicYearRequest,
  ): Observable<DegreeProgramAcademicYearResponse> {
    return this.http.post<DegreeProgramAcademicYearResponse>(
      `${ACADEMIC_API_BASE_URL}/degree-programs/${programId}/academic-years`,
      request,
    );
  }

  updateDegreeProgramAcademicYear(
    programId: string,
    assignmentId: string,
    request: UpdateDegreeProgramAcademicYearRequest,
  ): Observable<DegreeProgramAcademicYearResponse> {
    return this.http.put<DegreeProgramAcademicYearResponse>(
      `${ACADEMIC_API_BASE_URL}/degree-programs/${programId}/academic-years/${assignmentId}`,
      request,
    );
  }

  deleteDegreeProgramAcademicYear(programId: string, assignmentId: string): Observable<void> {
    return this.http.delete<void>(`${ACADEMIC_API_BASE_URL}/degree-programs/${programId}/academic-years/${assignmentId}`);
  }

  /** The "Degree Programs" screen's per-row expand — this assignment's courses for
   * that academic year, each with its resolved (overridden-or-default) lecturer. */
  listCoursesForAssignment(programId: string, assignmentId: string): Observable<CourseAcademicYearResponse[]> {
    return this.http.get<CourseAcademicYearResponse[]>(
      `${ACADEMIC_API_BASE_URL}/degree-programs/${programId}/academic-years/${assignmentId}/courses`,
    );
  }

  /** mine=true: offerings the caller teaches. */
  listCourseOfferings(filter: { mine?: boolean; status?: string; academicYearId?: string } = {}): Observable<CourseOfferingResponse[]> {
    let params = new HttpParams();
    if (filter.mine) params = params.set('mine', true);
    if (filter.status) params = params.set('status', filter.status);
    if (filter.academicYearId) params = params.set('academicYearId', filter.academicYearId);
    return this.http.get<CourseOfferingResponse[]>(`${ACADEMIC_API_BASE_URL}/course-offerings`, { params });
  }

  getCourseOffering(offeringId: string): Observable<CourseOfferingResponse> {
    return this.http.get<CourseOfferingResponse>(`${ACADEMIC_API_BASE_URL}/course-offerings/${offeringId}`);
  }

  getDegreeProgram(programId: string): Observable<DegreeProgramResponse> {
    return this.http.get<DegreeProgramResponse>(`${ACADEMIC_API_BASE_URL}/degree-programs/${programId}`);
  }

  /** departmentId/q are optional and combinable — q is a case-insensitive name/code search. */
  listCourseCatalog(query: CourseCatalogListQuery = {}): Observable<PagedResponse<CourseCatalogResponse>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
    if (query.departmentId) params = params.set('departmentId', query.departmentId);
    if (query.q) params = params.set('q', query.q);
    return this.http.get<PagedResponse<CourseCatalogResponse>>(`${ACADEMIC_API_BASE_URL}/course-catalog`, { params });
  }

  createCourseCatalogEntry(request: CreateCourseCatalogRequest): Observable<CourseCatalogResponse> {
    return this.http.post<CourseCatalogResponse>(`${ACADEMIC_API_BASE_URL}/course-catalog`, request);
  }

  /** degreeProgramId/yearOfStudy/status/q are all optional and combinable — q is a
   * case-insensitive catalog code/name search. */
  listCourses(mine: boolean, query: CourseListQuery = {}): Observable<PagedResponse<CourseResponse>> {
    let params = new HttpParams().set('mine', String(mine)).set('page', query.page ?? 0).set('size', query.size ?? 20);
    if (query.degreeProgramId) params = params.set('degreeProgramId', query.degreeProgramId);
    if (query.yearOfStudy) params = params.set('yearOfStudy', query.yearOfStudy);
    if (query.status) params = params.set('status', query.status);
    if (query.q) params = params.set('q', query.q);
    return this.http.get<PagedResponse<CourseResponse>>(`${ACADEMIC_API_BASE_URL}/courses`, { params });
  }

  createCourse(request: CreateCourseRequest): Observable<CourseResponse> {
    return this.http.post<CourseResponse>(`${ACADEMIC_API_BASE_URL}/courses`, request);
  }

  updateCourse(courseId: string, request: UpdateCourseRequest): Observable<CourseResponse> {
    return this.http.put<CourseResponse>(`${ACADEMIC_API_BASE_URL}/courses/${courseId}`, request);
  }

  deleteCourse(courseId: string): Observable<void> {
    return this.http.delete<void>(`${ACADEMIC_API_BASE_URL}/courses/${courseId}`);
  }

  listCourseAcademicYears(courseId: string): Observable<CourseAcademicYearResponse[]> {
    return this.http.get<CourseAcademicYearResponse[]>(`${ACADEMIC_API_BASE_URL}/courses/${courseId}/academic-years`);
  }

  assignCourseToAcademicYear(
    courseId: string,
    request: CreateCourseAcademicYearRequest,
  ): Observable<CourseAcademicYearResponse> {
    return this.http.post<CourseAcademicYearResponse>(
      `${ACADEMIC_API_BASE_URL}/courses/${courseId}/academic-years`,
      request,
    );
  }

  updateCourseAcademicYear(
    courseId: string,
    assignmentId: string,
    request: UpdateCourseAcademicYearRequest,
  ): Observable<CourseAcademicYearResponse> {
    return this.http.put<CourseAcademicYearResponse>(
      `${ACADEMIC_API_BASE_URL}/courses/${courseId}/academic-years/${assignmentId}`,
      request,
    );
  }

  deleteCourseAcademicYear(courseId: string, assignmentId: string): Observable<void> {
    return this.http.delete<void>(`${ACADEMIC_API_BASE_URL}/courses/${courseId}/academic-years/${assignmentId}`);
  }
}
