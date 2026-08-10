import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { Layout } from "./components/Layout";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { LibraryPage } from "./pages/LibraryPage";
import { PublicBankPage } from "./pages/PublicBankPage";
import { CollectionsPage } from "./pages/CollectionsPage";
import { CollectionDetailPage } from "./pages/CollectionDetailPage";
import { StatsPage } from "./pages/StatsPage";
import { TelegramPage } from "./pages/TelegramPage";

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route index element={<LibraryPage />} />
            <Route path="/public" element={<PublicBankPage />} />
            <Route path="/collections" element={<CollectionsPage />} />
            <Route path="/collections/:id" element={<CollectionDetailPage />} />
            <Route path="/stats" element={<StatsPage />} />
            <Route path="/telegram" element={<TelegramPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
