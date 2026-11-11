/** Mirrors Spring Data's Page<T> JSON shape, as returned by every paginated list endpoint. */
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page index, 0-based
  size: number;
}

/** The page/size pair every paginated list request sends. */
export interface PageQuery {
  page?: number;
  size?: number;
}
