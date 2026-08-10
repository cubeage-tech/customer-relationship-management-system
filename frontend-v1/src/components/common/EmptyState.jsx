/** Placeholder shown while a CRM module has no records to display. */
const EmptyState = ({ title = 'Nothing here yet', message, action }) => {
  return (
    <div className="bg-white border border-dashed border-slate-300 rounded-lg p-10 text-center">
      <h2 className="text-lg font-semibold text-slate-800">{title}</h2>
      {message && <p className="text-sm text-slate-500 mt-2">{message}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
};

export default EmptyState;
