import { Navigate } from 'react-router-dom';
import { storage } from '../utils/storage';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

export const ProtectedRoute = ({ children }: ProtectedRouteProps): React.ReactNode => {
  if (!storage.isAuthenticated()) {
    return <Navigate to="/login" />;
  }
  return children;
};
