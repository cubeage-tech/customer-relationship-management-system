import React from "react";
import { Navigate, Outlet } from "react-router-dom";
import { useSelector } from "react-redux";
import RoutePath from "../core/constants/routes.constant";
import { USER_ROLES } from "../core/constants/app.constant";

const ProtectedRoute = ({ allowedRoles }) => {
  const { token, user } = useSelector((state) => state.auth);

  if (!token || !user) {
    return <Navigate to={RoutePath.AUTH_LOGIN} replace />;
  }

  // Check activeRole only, not all roles
  const activeRole = user?.activeRole;

  const mappedRole = (() => {
    if (activeRole === "ADMIN") return USER_ROLES.ADMIN;
    return null;
  })();

  // Only allow if the active role matches
  const hasAccess = allowedRoles.includes(mappedRole);

  if (!hasAccess) {
    return <Navigate to={RoutePath.UNAUTHORIZED} replace />;
  }

  return <Outlet />;
};
export default ProtectedRoute;
