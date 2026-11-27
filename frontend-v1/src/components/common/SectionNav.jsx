import { NavLink } from 'react-router-dom';

/**
 * NAV-M07 / NAV-M08: the sub-screens of a module as tabs (e.g. Campaigns → List, Segments,
 * Promotions, Performance report). items: [{ to, label, end? }], already filtered by permission.
 */
const SectionNav = ({ items, label }) => {
  if (items.length < 2) return null;
  return (
    <nav aria-label={label} className="flex flex-wrap gap-2 mb-4">
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} end={item.end ?? true}
          className={({ isActive }) => `px-3 py-1.5 rounded-md text-sm border ${isActive
            ? 'border-primary/50 bg-primary/5 text-primary'
            : 'border-slate-200 bg-white text-slate-600 hover:border-primary/50'}`}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
};

export default SectionNav;
