import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2, UsersRound } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import Toast from '../../components/common/Toast';
import { useToast } from '../../core/hooks/useToast';
import { USER_ROLES } from '../../core/constants/app.constant';
import { QUERY_KEYS } from '../../core/query/queryKeys';
import { apiErrorMessage, isForbidden } from '../../core/utils/apiError';
import { listUsers } from '../../core/services/user.service';
import {
  createSalesTeam,
  deleteSalesTeam,
  listSalesTeams,
  setSalesTeamMembers,
  updateSalesTeam,
} from '../../core/services/salesTeam.service';

const inputClass =
  'w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20';

const SkeletonCard = () => (
  <div className="animate-pulse rounded-lg border border-slate-200 bg-white p-5" aria-hidden="true">
    <div className="h-4 w-40 rounded bg-slate-200" />
    <div className="mt-3 h-3 w-56 rounded bg-slate-100" />
    <div className="mt-4 h-3 w-full rounded bg-slate-100" />
  </div>
);

const ManagerSelect = ({ id, value, onChange, managers }) => (
  <select id={id} className={inputClass} value={value ?? ''} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}>
    <option value="">No manager</option>
    {managers.map((manager) => (
      <option key={manager.id} value={manager.id}>{manager.fullName}</option>
    ))}
  </select>
);

const TeamEditor = ({ team, managers, executives, teamNameById, onSave, onCancel, saving }) => {
  const [name, setName] = useState(team.name);
  const [managerId, setManagerId] = useState(team.managerId);
  const [memberIds, setMemberIds] = useState(() => new Set(team.members.map((member) => member.id)));

  const toggle = (userId) =>
    setMemberIds((current) => {
      const next = new Set(current);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });

  return (
    <form
      className="mt-4 space-y-4 border-t border-slate-100 pt-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave({ name: name.trim(), managerId, memberIds: [...memberIds] });
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={`team-name-${team.id}`} className="mb-1 block text-sm font-medium text-slate-700">Team name</label>
          <input id={`team-name-${team.id}`} className={inputClass} value={name} maxLength={100} required onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label htmlFor={`team-manager-${team.id}`} className="mb-1 block text-sm font-medium text-slate-700">Manager</label>
          <ManagerSelect id={`team-manager-${team.id}`} value={managerId} onChange={setManagerId} managers={managers} />
        </div>
      </div>

      <fieldset>
        <legend className="mb-2 text-sm font-medium text-slate-700">Members (sales executives)</legend>
        {executives.length === 0 ? (
          <p className="text-sm text-slate-500">There are no sales executives in your company yet.</p>
        ) : (
          <div className="grid gap-2 sm:grid-cols-2">
            {executives.map((executive) => {
              const otherTeam = executive.teamName && executive.teamId !== team.id ? teamNameById[executive.teamId] : null;
              return (
                <label key={executive.id} className="flex items-start gap-2 rounded-md border border-slate-200 p-2 text-sm hover:bg-slate-50">
                  <input type="checkbox" className="mt-0.5" checked={memberIds.has(executive.id)} onChange={() => toggle(executive.id)} />
                  <span>
                    <span className="font-medium text-slate-800">{executive.fullName}</span>
                    {otherTeam && memberIds.has(executive.id) && (
                      <span className="block text-xs text-amber-700">Will move from {otherTeam}</span>
                    )}
                  </span>
                </label>
              );
            })}
          </div>
        )}
      </fieldset>

      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={onCancel} disabled={saving}>Cancel</Button>
        <Button type="submit" loading={saving} disabled={!name.trim()}>Save team</Button>
      </div>
    </form>
  );
};

const Teams = () => {
  const queryClient = useQueryClient();
  const { toast, showSuccess, showError, hide } = useToast();
  const [newName, setNewName] = useState('');
  const [newManagerId, setNewManagerId] = useState(null);
  const [editingId, setEditingId] = useState(null);

  const teamsQuery = useQuery({ queryKey: QUERY_KEYS.salesTeams, queryFn: listSalesTeams });
  const usersQuery = useQuery({ queryKey: QUERY_KEYS.users, queryFn: listUsers });

  const teams = useMemo(() => teamsQuery.data ?? [], [teamsQuery.data]);
  const users = useMemo(() => usersQuery.data ?? [], [usersQuery.data]);

  const teamNameById = useMemo(() => Object.fromEntries(teams.map((team) => [team.id, team.name])), [teams]);
  const managers = useMemo(() => users.filter((user) => user.role === USER_ROLES.SALES_MANAGER), [users]);
  const executives = useMemo(() => {
    const teamByMember = new Map(teams.flatMap((team) => team.members.map((member) => [member.id, team])));
    return users
      .filter((user) => user.role === USER_ROLES.SALES_EXECUTIVE)
      .map((user) => {
        const team = teamByMember.get(user.id);
        return { ...user, teamId: team?.id ?? null, teamName: team?.name ?? null };
      });
  }, [users, teams]);

  const refreshTeams = () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.salesTeams });

  const createMutation = useMutation({
    mutationFn: createSalesTeam,
    onSuccess: (team) => {
      showSuccess(`Team "${team.name}" created.`);
      setNewName('');
      setNewManagerId(null);
      refreshTeams();
    },
    onError: (error) => showError(apiErrorMessage(error, 'The team could not be created.')),
  });

  const saveMutation = useMutation({
    mutationFn: async ({ teamId, name, managerId, memberIds }) => {
      await updateSalesTeam(teamId, { name, managerId });
      return setSalesTeamMembers(teamId, memberIds);
    },
    onSuccess: (team) => {
      showSuccess(`Team "${team.name}" saved.`);
      setEditingId(null);
      refreshTeams();
    },
    onError: (error) => {
      showError(apiErrorMessage(error, 'The team could not be saved.'));
      refreshTeams();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteSalesTeam,
    onSuccess: () => {
      showSuccess('Team deleted. Its members now have no team.');
      refreshTeams();
    },
    onError: (error) => showError(apiErrorMessage(error, 'The team could not be deleted.')),
  });

  const confirmDelete = (team) => {
    if (window.confirm(`Delete "${team.name}"? Its members keep their records but will no longer belong to a team.`)) {
      deleteMutation.mutate(team.id);
    }
  };

  const loading = teamsQuery.isPending || usersQuery.isPending;
  const loadError = teamsQuery.error || usersQuery.error;

  return (
    <section>
      <Toast toast={toast} onClose={hide} />
      <PageHeader
        title="Sales teams"
        subtitle="A sales manager sees the customers, leads, deals and quotations owned by the members of the teams they manage."
      />

      <form
        className="mb-6 rounded-lg border border-slate-200 bg-white p-5"
        onSubmit={(event) => {
          event.preventDefault();
          createMutation.mutate({ name: newName.trim(), managerId: newManagerId });
        }}
        aria-labelledby="new-team-heading"
      >
        <h2 id="new-team-heading" className="mb-3 font-semibold text-slate-900">New team</h2>
        <div className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
          <div>
            <label htmlFor="new-team-name" className="mb-1 block text-sm font-medium text-slate-700">Team name</label>
            <input id="new-team-name" className={inputClass} value={newName} maxLength={100} required placeholder="e.g. North region" onChange={(e) => setNewName(e.target.value)} />
          </div>
          <div>
            <label htmlFor="new-team-manager" className="mb-1 block text-sm font-medium text-slate-700">Manager</label>
            <ManagerSelect id="new-team-manager" value={newManagerId} onChange={setNewManagerId} managers={managers} />
          </div>
          <Button type="submit" icon={Plus} loading={createMutation.isPending} disabled={!newName.trim()}>Create team</Button>
        </div>
      </form>

      {loading ? (
        <div className="space-y-4" role="status" aria-label="Loading teams">
          <SkeletonCard />
          <SkeletonCard />
        </div>
      ) : loadError ? (
        <EmptyState
          title={isForbidden(loadError) ? 'Not available for your role' : 'Teams could not be loaded'}
          message={apiErrorMessage(loadError)}
          action={!isForbidden(loadError) && <Button variant="outline" onClick={() => { teamsQuery.refetch(); usersQuery.refetch(); }}>Try again</Button>}
        />
      ) : teams.length === 0 ? (
        <EmptyState
          title="No teams yet"
          message="Until a sales manager leads a team, they can see every record in your company. Create a team above to limit that."
        />
      ) : (
        <ul className="space-y-4">
          {teams.map((team) => (
            <li key={team.id} className="rounded-lg border border-slate-200 bg-white p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="flex items-center gap-2 font-semibold text-slate-900">
                    <UsersRound size={18} className="text-slate-400" aria-hidden="true" />
                    {team.name}
                  </h3>
                  <p className="mt-1 text-sm text-slate-500">
                    Manager: <span className="text-slate-700">{team.managerName || 'None — no one is scoped to this team'}</span>
                    {' · '}{team.members.length} {team.members.length === 1 ? 'member' : 'members'}
                  </p>
                  {team.members.length > 0 && editingId !== team.id && (
                    <p className="mt-2 text-sm text-slate-600">{team.members.map((member) => member.fullName).join(', ')}</p>
                  )}
                </div>
                {editingId !== team.id && (
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" icon={Pencil} onClick={() => setEditingId(team.id)}>Edit</Button>
                    <Button variant="ghost" size="sm" icon={Trash2} onClick={() => confirmDelete(team)} aria-label={`Delete ${team.name}`} disabled={deleteMutation.isPending}>Delete</Button>
                  </div>
                )}
              </div>
              {editingId === team.id && (
                <TeamEditor
                  team={team}
                  managers={managers}
                  executives={executives}
                  teamNameById={teamNameById}
                  saving={saveMutation.isPending}
                  onCancel={() => setEditingId(null)}
                  onSave={(values) => saveMutation.mutate({ teamId: team.id, ...values })}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
};

export default Teams;
