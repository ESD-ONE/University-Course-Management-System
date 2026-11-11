export type ContentItemType = 'NOTE' | 'SLIDES' | 'READING' | 'VIDEO' | 'LINK';
export type ContentItemStatus = 'DRAFT' | 'PUBLISHED';
export type SessionStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED';
export type DeliveryMode = 'ONLINE' | 'PHYSICAL' | 'HYBRID';
export type WeekStatus = 'DRAFT' | 'PUBLISHED';

export interface SessionResponse {
  id: string;
  weekId: string;
  title: string;
  description: string | null;
  startsAt: string;
  endsAt: string;
  deliveryMode: DeliveryMode;
  meetingUrl: string | null;
  location: string | null;
  recordingUrl: string | null;
  status: SessionStatus;
}

export interface ItemResponse {
  id: string;
  weekId: string;
  sessionId: string | null;
  type: ContentItemType;
  title: string;
  description: string | null;
  fileName: string | null;
  sizeBytes: number;
  externalUrl: string | null;
  sortOrder: number;
  status: ContentItemStatus;
  publishedAt: string | null;
}

export interface WeekResponse {
  id: string;
  weekNumber: number;
  startDate: string;
  endDate: string;
  title: string | null;
  status: WeekStatus;
  sessions: SessionResponse[];
  items: ItemResponse[];
}

export interface CreateWeekRequest {
  weekNumber: number;
  startDate: string;
  endDate: string;
  title?: string | null;
}

export interface UpdateWeekRequest {
  startDate: string;
  endDate: string;
  title?: string | null;
  status: WeekStatus;
}

export interface CreateSessionRequest {
  title: string;
  description?: string | null;
  startsAt: string;
  endsAt: string;
  deliveryMode: DeliveryMode;
  meetingUrl?: string | null;
  location?: string | null;
}

export interface UpdateSessionRequest extends CreateSessionRequest {
  recordingUrl?: string | null;
  status: SessionStatus;
}

export interface CreateLinkItemRequest {
  weekId: string;
  sessionId?: string | null;
  type: ContentItemType;
  title: string;
  description?: string | null;
  externalUrl: string;
}

export interface UpdateItemRequest {
  title: string;
  description?: string | null;
  sortOrder?: number;
}
