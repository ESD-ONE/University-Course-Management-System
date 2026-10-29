export interface NotificationResponse {
  id: string;
  type: string;
  category: 'REGISTRATION' | 'ASSIGNMENT' | 'GRADE' | 'SYSTEM';
  title: string;
  body: string;
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  items: NotificationResponse[];
  unreadCount: number;
  page: number;
  size: number;
  total: number;
}

export interface NotificationPreferences {
  emailEnabled: boolean;
  assignmentAlerts: boolean;
  gradeAlerts: boolean;
  registrationAlerts: boolean;
}
