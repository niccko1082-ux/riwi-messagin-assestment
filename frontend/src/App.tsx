import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { AppPage } from './pages/AppPage';
import { LoginPage } from './pages/LoginPage';

export default function App() {
  const { authenticated } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={authenticated ? <Navigate to="/" /> : <LoginPage />} />
      <Route path="/" element={authenticated ? <AppPage /> : <Navigate to="/login" />} />
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  );
}
