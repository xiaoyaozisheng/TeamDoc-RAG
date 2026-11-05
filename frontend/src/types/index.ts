export interface AuthRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  tokenType: string;
}

export interface PermissionResponse {
  id: number;
  name: string;
  description: string;
  resource: string;
  action: string;
}

export interface RoleResponse {
  id: number;
  name: string;
  description: string;
  permissions: PermissionResponse[];
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  roles: RoleResponse[];
  permissions: string[];
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  details?: Record<string, string>;
}

// ===== RAG =====

export interface DocumentItem {
  id: number;
  name: string;
  type: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  errorMsg: string | null;
  sizeBytes: number;
  uploadedByEmail: string | null;
  createdAt: string;
}

export interface Citation {
  chunkId: number;
  documentId: number;
  documentName: string;
  pageNumber: number;
  index: number;
}

export interface ChatAnswer {
  conversationId: number;
  answer: string;
  citations: Citation[];
  rejected: boolean;
}

export interface RetrievalHit {
  chunkId: number;
  documentId: number;
  documentName: string;
  pageNumber: number;
  content: string;
  vectorScore?: number | null;
  keywordScore?: number | null;
  rrfScore?: number | null;
}

export interface EvalQuestionItem {
  id: number;
  question: string;
  expectedAnswer: string;
  expectedDocId: number;
  negative: boolean;
}

export interface EvalMetrics {
  questionCount: number;
  positiveCount: number;
  recallAt5: number;
  negativeCount: number;
  rejectionAccuracy: number;
}

export interface EvalRunItem {
  id: number;
  questionCount: number;
  createdAt: string;
  metrics: EvalMetrics;
}