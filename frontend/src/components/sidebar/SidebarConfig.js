import {
  LayoutDashboard,
  Factory,
  Users,
  Package,
  ShoppingCart,
} from "lucide-react";
import { USER_ROLES } from "../../core/constants/app.constant";
import RoutePath from "../../core/constants/routes.constant";

export const sidebarMenus = {

  [USER_ROLES.SUPER_ADMIN]: [

    {
      label: "Dashboard",
      icon: LayoutDashboard,
      path: RoutePath.ADMIN_DASHBOARD,
    },

    {
      label: "Industries",
      icon: Factory,
      path: RoutePath.ADMIN_INDUSTRIES,
    },

    {
      label: "Dealers",
      icon: Users,
      path: RoutePath.ADMIN_DEALERS,
    },

    {
      label: "Buyers",
      icon: Users,
      path: RoutePath.ADMIN_BUYERS,
    },

  ],

  [USER_ROLES.INDUSTRY]: [

    {
      label: "Dashboard",
      icon: LayoutDashboard,
      path: RoutePath.INDUSTRY_DASHBOARD,
    },

    {
      label: "My Scrap",
      icon: Package,
      path: RoutePath.INDUSTRY_MY_SCRAP,
    },

    {
      label: "Dealer Marketplace",
      icon: Package,
      path: RoutePath.INDUSTRY_DEALER_MARKETPLACE,
    },
    


  ],

  [USER_ROLES.DEALER]: [

    {
      label: "Dashboard",
      icon: LayoutDashboard,
      path: RoutePath.DEALER_DASHBOARD,
    },

    {
      label: "Marketplace",
      icon: Package,
      path: RoutePath.DEALER_MARKETPLACE,
    },

  ],

  [USER_ROLES.BUYER]: [

    {
      label: "Dashboard",
      icon: LayoutDashboard,
      path: RoutePath.BUYER_DASHBOARD,
    },

    {
      label: "Marketplace",
      icon: ShoppingCart,
      path: RoutePath.BUYER_MARKETPLACE,
    },

  ],

};
