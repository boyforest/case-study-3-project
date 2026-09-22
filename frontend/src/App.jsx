import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import LoginPage from './pages/LoginPage'
import MembersPage from './pages/admin/MembersPage'
import ProductsPage from './pages/admin/ProductsPage'
import { getUser } from './utils/auth'

function RequireAuth({ roles, children }) {
  const user = getUser()
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/shop" replace />
  }
  return children
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/shop" element={<div>Shop page (story 06)</div>} />
          <Route path="/my-order" element={<div>My orders (story 08)</div>} />
          <Route path="/admin/members" element={<RequireAuth roles={['COORDINATOR']}><MembersPage /></RequireAuth>} />
          <Route path="/admin/products" element={<RequireAuth roles={['COORDINATOR']}><ProductsPage /></RequireAuth>} />
          <Route path="/admin/rounds" element={<RequireAuth roles={['COORDINATOR']}><div>Rounds (story 05)</div></RequireAuth>} />
          <Route path="/admin/orders" element={<RequireAuth roles={['COORDINATOR']}><div>Round orders (story 09)</div></RequireAuth>} />
          <Route path="/admin/totals" element={<RequireAuth roles={['COORDINATOR']}><div>Round totals (story 10)</div></RequireAuth>} />
        </Route>
        <Route path="*" element={<Navigate to="/shop" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
