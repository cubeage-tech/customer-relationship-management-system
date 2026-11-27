import { Link } from 'react-router-dom';
import { CircleAlert } from 'lucide-react';

const PATHS = { customer: '/customers', lead: '/leads' };

/** VAL-1/VAL-3: the possible duplicates of what is being entered (see useDuplicateCheck). */
const DuplicateWarning = ({ matches }) => {
  if (!matches?.length) return null;
  return (
    <div role="status" className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
      <p className="flex items-center gap-1.5 font-medium"><CircleAlert size={15} aria-hidden="true" /> Possible duplicate</p>
      <ul className="mt-1 space-y-0.5">
        {matches.map((m, i) => (
          <li key={`${m.recordType}-${m.recordId ?? i}`}>
            {m.recordId ? (
              <Link to={`${PATHS[m.recordType]}/${m.recordId}`} className="underline" target="_blank" rel="noreferrer">{m.name}</Link>
            ) : (
              <span>A {m.recordType} owned by someone else</span>
            )}
            <span className="text-amber-700"> — same {m.matchedOn}</span>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default DuplicateWarning;
