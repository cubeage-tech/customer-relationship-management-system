import PageHeader from '../../components/common/PageHeader';
import {
  ROLE_DESCRIPTIONS,
  ROLE_LABELS,
  USER_ROLES,
} from '../../core/constants/app.constant';
import { MODULES, ROLE_DATA_SCOPE, SCOPE_LABELS } from '../../core/constants/permission.constant';
import { getRolePermissions } from '../../core/utils/permission';

const MODULE_COLUMNS = [
  { key: MODULES.CUSTOMERS, label: 'Customers' },
  { key: MODULES.LEADS, label: 'Leads' },
  { key: MODULES.OPPORTUNITIES, label: 'Opportunities' },
  { key: MODULES.QUOTATIONS, label: 'Quotations' },
  { key: MODULES.SERVICE_TICKETS, label: 'Service Tickets' },
  { key: MODULES.REPORTS, label: 'Reports' },
  { key: MODULES.SETTINGS, label: 'Admin Settings' },
];

const ROLES = Object.values(USER_ROLES);

const Roles = () => {
  return (
    <section>
      <PageHeader
        title="Roles & Permissions"
        subtitle="The CRM role matrix. Role names shown here map directly to the internal role names used for access checks."
      />

      <div className="overflow-x-auto bg-white border border-slate-200 rounded-lg">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-100 text-left">
            <tr>
              <th className="p-3 font-semibold">Role</th>
              {MODULE_COLUMNS.map((column) => (
                <th key={column.key} className="p-3 font-semibold">
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ROLES.map((role) => (
              <tr key={role} className="border-t border-slate-200 align-top">
                <td className="p-3">
                  <p className="font-medium text-slate-900">{ROLE_LABELS[role]}</p>
                  <code className="text-xs text-slate-500">{role}</code>
                  <p className="text-xs text-slate-500 mt-1 max-w-xs">
                    {ROLE_DESCRIPTIONS[role]}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">
                    {getRolePermissions(role).length} permissions
                  </p>
                </td>
                {MODULE_COLUMNS.map((column) => (
                  <td key={column.key} className="p-3 text-slate-600">
                    {SCOPE_LABELS[ROLE_DATA_SCOPE[role][column.key]]}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
};

export default Roles;
