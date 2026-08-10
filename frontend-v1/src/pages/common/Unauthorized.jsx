import { Link } from 'react-router-dom';
import { useAuth } from '../../core/hooks/useAuth';
import { getRoleHomeRoute } from '../../core/constants/routes.constant';
import { getRoleLabel } from '../../core/utils/permission';
import { NOTIFICATION_MESSAGES } from '../../core/constants/notification.constant';

const Unauthorized = () => {
  const { user } = useAuth();

  return (
    <section className="p-10 text-center">
      <h1 className="text-2xl font-bold text-slate-900">Access denied</h1>
      <p className="text-slate-600 mt-2">{NOTIFICATION_MESSAGES.ACCESS_DENIED}</p>
      {user?.role && (
        <p className="text-sm text-slate-500 mt-1">
          You are signed in as {getRoleLabel(user.role)}.
        </p>
      )}
      <Link
        to={getRoleHomeRoute(user?.role)}
        className="inline-block mt-5 text-blue-600 hover:underline"
      >
        Back to my dashboard
      </Link>
    </section>
  );
};

export default Unauthorized;
