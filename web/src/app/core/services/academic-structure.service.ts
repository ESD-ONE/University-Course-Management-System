import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ACADEMIC_API_BASE_URL } from '../config/api.config';
import {
  AcademicYearResponse,
  CourseAcademicYearResponse,
  CourseCatalogResponse,
  CourseResponse,
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
  SemesterResponse,
  UpdateAcademicYearRequest,
  UpdateCourseAcademicYearRequest,
  UpdateCourseRequest,
  UpdateDegreeProgramAcademicYearRequest,
  UpdateDegreeProgramRequest,
  UpdateSemesterRequest,
} from '../models/academic-structure.model';

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

  activateAcademicYear(yearId: string): Observable<AcademicYearResponse> {
    return this.http.patch<AcademicYearResponse>(`${ACADEMIC_API_BASE_URL}/academic-years/${yearId}/activate`, {});
  }

  /** departmentId: the "Reference Degree Details" page's admin department filter. */
  listDegreePrograms(departmentId?: string | null): Observable<DegreeProgramResponse[]> {
    const params: Record<string, string> = departmentId ? { departmentId } : {};
    return this.http.get<DegreeProgramResponse[]>(`${ACADEMIC_API_BASE_URL}/degree-programs`, { params });
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

  listCourseCatalog(): Observable<CourseCatalogResponse[]> {
    return this.http.get<CourseCatalogResponse[]>(`${ACADEMIC_API_BASE_URL}/course-catalog`);
  }

  createCourseCatalogEntry(request: CreateCourseCatalogRequest): Observable<CourseCatalogResponse> {
    return this.http.post<CourseCatalogResponse>(`${ACADEMIC_API_BASE_URL}/course-catalog`, request);
  }

  /** degreeProgramId: the Courses page's admin filter. */
  listCourses(mine = false, degreeProgramId?: string | null): Observable<CourseResponse[]> {
    const params: Record<string, string> = { mine: String(mine) };
    if (degreeProgramId) params['degreeProgramId'] = degreeProgramId;
    return this.http.get<CourseResponse[]>(`${ACADEMIC_API_BASE_URL}/courses`, { params });
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
