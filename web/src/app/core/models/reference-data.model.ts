export type ReferenceStatus = 'ACTIVE' | 'INACTIVE';

export interface FacultyResponse {
  id: string;
  name: string;
  code: string;
  status: ReferenceStatus;
}

export interface CreateFacultyRequest {
  name: string;
  code: string;
  status?: ReferenceStatus;
}

export interface UpdateFacultyRequest {
  name: string;
  code: string;
  status: ReferenceStatus;
}

export interface DepartmentResponse {
  id: string;
  facultyId: string;
  facultyName: string;
  name: string;
  code: string;
  status: ReferenceStatus;
}

export interface CreateDepartmentRequest {
  name: string;
  code: string;
  facultyId: string;
  status?: ReferenceStatus;
}

export interface UpdateDepartmentRequest {
  name: string;
  code: string;
  facultyId: string;
  status: ReferenceStatus;
}

export interface GradingScaleResponse {
  id: string;
  name: string;
  bands: string;
  status: ReferenceStatus;
}

export interface CreateGradingScaleRequest {
  name: string;
  bands: string;
  status?: ReferenceStatus;
}

export interface UpdateGradingScaleRequest {
  name: string;
  bands: string;
  status: ReferenceStatus;
}

export interface CourseCategoryResponse {
  id: string;
  name: string;
  description: string;
  status: ReferenceStatus;
}

export interface CreateCourseCategoryRequest {
  name: string;
  description: string;
  status?: ReferenceStatus;
}

export interface UpdateCourseCategoryRequest {
  name: string;
  description: string;
  status: ReferenceStatus;
}
