import { Navigate } from 'react-router-dom';
import { storage } from '../utils/storage';

interface PublicRouteProps {
  children: React.ReactNode;
}

export const PublicRoute = ({ children }: PublicRouteProps): React.ReactNode => {
  if (storage.isAuthenticated()) {
    return <Navigate to="/dashboard" />;
  }
  return children;
};
