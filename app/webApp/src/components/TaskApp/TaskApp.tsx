import { useEffect, useMemo, useRef, useState } from 'react';
import type {
  WebTask,
  WebTaskConflict,
  WebTaskItem,
  WebTaskProject,
  WebTaskProjectConflict,
  WebTaskProjectItem,
  WebTaskSnapshot,
} from 'shared-logic';
import type { TaskActions } from '../../taskActions.ts';
import {
  formatDueDate,
  formatLastSynced,
  formatReminder,
  isOverdue,
  localDateString,
  searchTasks,
} from '../../taskView.ts';
import type { TaskSmartView } from '../../taskView.ts';
import {
  getReminderPermission,
  requestReminderPermission,
  scheduleForegroundReminders,
  showForegroundReminder,
} from '../../reminders.ts';
import type { WebReminderPermission } from '../../reminders.ts';
import {
  AddIcon,
  CheckIcon,
  ChevronIcon,
  DeleteIcon,
  EditIcon,
  FolderIcon,
  InboxIcon,
  SearchIcon,
  SyncIcon,
  WarningIcon,
} from '../Icons.tsx';
import {
  TaskEditorDialog,
} from '../TaskEditor/TaskEditorDialog.tsx';
import type { TaskEditorValues } from '../TaskEditor/TaskEditorDialog.tsx';
import { TaskConflictDialog } from '../TaskConflict/TaskConflictDialog.tsx';
import {
  ProjectEditorDialog,
} from '../ProjectEditor/ProjectEditorDialog.tsx';
import type {
  ProjectEditorValues,
} from '../ProjectEditor/ProjectEditorDialog.tsx';
import {
  ProjectConflictDialog,
} from '../ProjectConflict/ProjectConflictDialog.tsx';

interface TaskAppProps {
  actions: TaskActions;
  isOnline: boolean;
  snapshot: WebTaskSnapshot;
}

type EditorState =
  | { mode: 'create' }
  | { mode: 'edit'; task: WebTask };

type ProjectEditorState =
  | { mode: 'create' }
  | { mode: 'edit'; item: WebTaskProjectItem };

const filters: Array<{ value: TaskSmartView; label: string }> = [
  { value: 'all', label: 'All tasks' },
  { value: 'inbox', label: 'Inbox' },
  { value: 'today', label: 'Today' },
  { value: 'upcoming', label: 'Upcoming' },
  { value: 'completed', label: 'Completed' },
];

export function TaskApp({
  actions,
  isOnline,
  snapshot,
}: TaskAppProps) {
  const [filter, setFilter] = useState<TaskSmartView>('inbox');
  const [selectedProjectId, setSelectedProjectId] = useState<string>();
  const [query, setQuery] = useState('');
  const [editor, setEditor] = useState<EditorState>();
  const [projectEditor, setProjectEditor] = useState<ProjectEditorState>();
  const [selectedConflict, setSelectedConflict] =
    useState<WebTaskConflict>();
  const [selectedProjectConflict, setSelectedProjectConflict] =
    useState<WebTaskProjectConflict>();
  const requestedInitialSync = useRef(false);
  const wasOnline = useRef(isOnline);
  const [reminderPermission, setReminderPermission] =
    useState<WebReminderPermission>(getReminderPermission);
  const [reminderMessage, setReminderMessage] = useState<string>();
  const notifiedReminders = useRef(new Set<string>());

  useEffect(() => {
    const reconnected = isOnline && !wasOnline.current;
    if (
      snapshot.isReady &&
      isOnline &&
      (!requestedInitialSync.current || reconnected)
    ) {
      requestedInitialSync.current = true;
      actions.sync();
    }
    wasOnline.current = isOnline;
  }, [actions, isOnline, snapshot.isReady]);

  useEffect(() => {
    if (
      selectedConflict &&
      !snapshot.conflicts.some(
        (conflict) => conflict.taskId === selectedConflict.taskId,
      )
    ) {
      setSelectedConflict(undefined);
    }
  }, [selectedConflict, snapshot.conflicts]);

  useEffect(() => {
    if (
      selectedProjectConflict &&
      !snapshot.projectConflicts.some(
        (conflict) =>
          conflict.projectId === selectedProjectConflict.projectId,
      )
    ) {
      setSelectedProjectConflict(undefined);
    }
  }, [selectedProjectConflict, snapshot.projectConflicts]);

  useEffect(() => {
    if (
      selectedProjectId &&
      !snapshot.projects.some(
        ({ project }) => project.id === selectedProjectId,
      )
    ) {
      setSelectedProjectId(undefined);
    }
  }, [selectedProjectId, snapshot.projects]);

  useEffect(() => {
    if (reminderPermission !== 'granted') return undefined;
    return scheduleForegroundReminders(snapshot.tasks, (task) => {
      const reminderKey = `${task.id}:${task.reminderAt}`;
      if (notifiedReminders.current.has(reminderKey)) return;
      notifiedReminders.current.add(reminderKey);
      if (!showForegroundReminder(task)) {
        setReminderMessage(
          'This browser needs a service worker for persistent notifications. Background push is not configured yet.',
        );
      }
    });
  }, [reminderPermission, snapshot.tasks]);

  const enableReminders = async () => {
    const permission = await requestReminderPermission();
    setReminderPermission(permission);
    setReminderMessage(
      permission === 'denied'
        ? 'Notifications are blocked. Allow them in your browser settings to enable reminders.'
        : permission === 'unsupported'
          ? 'This browser does not support notifications.'
          : undefined,
    );
  };
  const reminderStatusMessage =
    reminderMessage ??
    (reminderPermission === 'denied'
      ? 'Notifications are blocked. Allow them in your browser settings to enable reminders.'
       : reminderPermission === 'unsupported'
         ? 'This browser does not support notifications.'
         : undefined);

  const today = localDateString(new Date());
  const timeZone =
    Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

  const counts = useMemo(
    () =>
      Object.fromEntries(
        filters.map(({ value }) => [
          value,
          actions.plannedTasks(value, today, timeZone).length,
        ]),
      ) as Record<TaskSmartView, number>,
    [actions, snapshot.tasks, timeZone, today],
  );

  const selectedProject = snapshot.projects.find(
    ({ project }) => project.id === selectedProjectId,
  )?.project;

  const projectTaskCounts = useMemo(() => {
    const countsByProject = new Map<string, number>();
    snapshot.tasks.forEach(({ task }) => {
      if (task.projectId) {
        countsByProject.set(
          task.projectId,
          (countsByProject.get(task.projectId) ?? 0) + 1,
        );
      }
    });
    return countsByProject;
  }, [snapshot.tasks]);

  const projectsById = useMemo(
    () =>
      new Map(
        snapshot.projects.map(({ project }) => [project.id, project]),
      ),
    [snapshot.projects],
  );

  const visibleTasks = useMemo(
    () => {
      const candidates = selectedProjectId
        ? snapshot.tasks.filter(
            ({ task }) => task.projectId === selectedProjectId,
          )
        : actions.plannedTasks(filter, today, timeZone);
      return searchTasks(candidates, query);
    },
    [
      actions,
      filter,
      query,
      selectedProjectId,
      snapshot.tasks,
      timeZone,
      today,
    ],
  );

  const clearableCompletedCount = snapshot.tasks.filter(
    ({ task, syncState }) => task.isCompleted && syncState !== 'conflict',
  ).length;

  const saveTask = (values: TaskEditorValues) => {
    if (editor?.mode === 'edit') {
      actions.update(
        editor.task.id,
        values.title,
        values.notes,
        values.priority,
        values.dueDate,
        values.dueAt,
        values.reminderAt,
        values.isCompleted,
        values.projectId,
      );
    } else {
      actions.create(
        values.title,
        values.notes,
        values.priority,
        values.dueDate,
        values.dueAt,
        values.reminderAt,
        values.projectId,
      );
    }
    setEditor(undefined);
  };

  const saveProject = ({ name, color }: ProjectEditorValues) => {
    if (projectEditor?.mode === 'edit') {
      actions.updateProject(projectEditor.item.project.id, name, color);
    } else {
      actions.createProject(name, color);
    }
    setProjectEditor(undefined);
  };

  const deleteProject = (item: WebTaskProjectItem) => {
    const taskCount = projectTaskCounts.get(item.project.id) ?? 0;
    if (
      window.confirm(
        `Delete “${item.project.name}”? ${
          taskCount === 0
            ? 'The project will be removed from this device and the server.'
            : `${taskCount} ${
                taskCount === 1 ? 'task' : 'tasks'
              } will move to Inbox.`
        }`,
      )
    ) {
      actions.deleteProject(item.project.id);
      setProjectEditor(undefined);
      if (selectedProjectId === item.project.id) {
        setSelectedProjectId(undefined);
        setFilter('inbox');
      }
    }
  };

  const chooseFilter = (value: TaskSmartView) => {
    setSelectedProjectId(undefined);
    setFilter(value);
  };

  const clearCompleted = () => {
    if (
      clearableCompletedCount > 0 &&
      window.confirm(
        `Delete ${clearableCompletedCount} completed ${
          clearableCompletedCount === 1 ? 'task' : 'tasks'
        } from this device and the server?`,
      )
    ) {
      actions.clearCompleted();
    }
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">
            <CheckIcon />
          </span>
          <div>
            <strong>Taskflow</strong>
            <span>KMP native-first</span>
          </div>
        </div>

        <div className="sidebar-scroll">
          <nav aria-label="Task filters" className="filter-nav">
            {filters.map(({ value, label }) => (
              <button
                aria-current={
                  !selectedProjectId && filter === value
                    ? 'page'
                    : undefined
                }
                className={
                  !selectedProjectId && filter === value
                    ? 'filter-button active'
                    : 'filter-button'
                }
                key={value}
                onClick={() => chooseFilter(value)}
                type="button"
              >
                <span>{label}</span>
                <span className="filter-count">{counts[value]}</span>
              </button>
            ))}
          </nav>

          <nav aria-label="Projects" className="project-nav">
            <div className="project-nav-heading">
              <span>Projects</span>
              <button
                aria-label="Create project"
                className="sidebar-icon-button"
                disabled={!snapshot.isReady}
                onClick={() => setProjectEditor({ mode: 'create' })}
                type="button"
              >
                <AddIcon />
              </button>
            </div>

            {snapshot.projects.length > 0 ? (
              <ul className="project-list">
                {snapshot.projects.map((item) => {
                  const { project, syncState } = item;
                  const hasConflict = syncState === 'conflict';
                  const conflict = snapshot.projectConflicts.find(
                    (candidate) => candidate.projectId === project.id,
                  );
                  return (
                    <li
                      className={[
                        'project-row',
                        selectedProjectId === project.id ? 'active' : '',
                        hasConflict ? 'has-conflict' : '',
                      ].join(' ')}
                      key={project.id}
                    >
                      <button
                        aria-current={
                          selectedProjectId === project.id
                            ? 'page'
                            : undefined
                        }
                        className="project-button"
                        onClick={() => setSelectedProjectId(project.id)}
                        type="button"
                      >
                        <span
                          className={`project-dot project-color-${project.color}`}
                        />
                        <span className="project-name">{project.name}</span>
                        <span className="filter-count">
                          {projectTaskCounts.get(project.id) ?? 0}
                        </span>
                      </button>
                      <button
                        aria-label={
                          hasConflict
                            ? `Resolve ${project.name} conflict`
                            : `Edit ${project.name}`
                        }
                        className="sidebar-icon-button project-action"
                        onClick={() => {
                          if (hasConflict && conflict) {
                            setSelectedProjectConflict(conflict);
                          } else {
                            setProjectEditor({ mode: 'edit', item });
                          }
                        }}
                        type="button"
                      >
                        {hasConflict ? <WarningIcon /> : <EditIcon />}
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className="project-nav-empty">
                Group related tasks without changing the shared workflow.
              </p>
            )}

            {snapshot.projectConflicts
              .filter(
                (conflict) =>
                  !snapshot.projects.some(
                    ({ project }) => project.id === conflict.projectId,
                  ),
              )
              .map((conflict) => (
                <button
                  className="detached-project-conflict"
                  key={conflict.projectId}
                  onClick={() => setSelectedProjectConflict(conflict)}
                  type="button"
                >
                  <WarningIcon />
                  Resolve deleted project
                </button>
              ))}
          </nav>
        </div>

        <div className="sidebar-footer">
          <SyncSummary
            isOnline={isOnline}
            lastSyncedAt={snapshot.lastSyncedAt}
            pendingCount={snapshot.pendingCount}
            phase={snapshot.syncPhase}
          />
          <p>
            Native React UI backed by shared Kotlin sync and browser IndexedDB.
          </p>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="page-heading">
            <p className="eyebrow">My workspace</p>
            <h1>
              {selectedProject?.name ??
                filters.find(({ value }) => value === filter)?.label}
            </h1>
          </div>

          <div className="topbar-actions">
            <label className="search-field">
              <SearchIcon />
              <span className="sr-only">Search tasks</span>
              <input
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search tasks"
                type="search"
                value={query}
              />
            </label>
            <button
              aria-label="Synchronize tasks"
              className="button button-secondary sync-button"
              disabled={
                !snapshot.isReady ||
                !isOnline ||
                snapshot.syncPhase === 'syncing'
              }
              onClick={actions.sync}
              type="button"
            >
              <SyncIcon
                className={snapshot.syncPhase === 'syncing' ? 'spinning' : undefined}
              />
              <span>Sync</span>
            </button>
            <button
              className="button button-secondary reminder-button"
              disabled={
                reminderPermission === 'unsupported' ||
                reminderPermission === 'denied' ||
                reminderPermission === 'granted'
              }
              onClick={enableReminders}
              type="button"
            >
              {reminderPermission === 'granted'
                ? 'Reminders on'
                : reminderPermission === 'denied'
                  ? 'Reminders blocked'
                  : reminderPermission === 'unsupported'
                    ? 'Reminders unavailable'
                    : 'Enable reminders'}
            </button>
            <button
              className="button button-primary add-button"
              disabled={!snapshot.isReady}
              onClick={() => setEditor({ mode: 'create' })}
              type="button"
            >
              <AddIcon />
              <span>New task</span>
            </button>
          </div>
        </header>

        <div className="content-column">
          {!isOnline && (
            <StatusBanner
              detail="Changes remain in IndexedDB and will sync when this browser reconnects."
              kind="offline"
              title="You are offline"
            />
          )}

          {reminderStatusMessage && (
            <StatusBanner
              action={
                reminderPermission === 'denied' ? (
                  <span className="status-note">Browser settings required</span>
                ) : undefined
              }
              detail={reminderStatusMessage}
              kind="offline"
              title="Browser reminders"
            />
          )}

          {snapshot.lastError && snapshot.syncPhase === 'failed' && (
            <StatusBanner
              action={
                isOnline ? (
                  <button className="text-button" onClick={actions.sync} type="button">
                    Try again
                  </button>
                ) : undefined
              }
              detail={snapshot.lastError}
              kind="error"
              title="Could not synchronize"
            />
          )}

          {snapshot.conflicts.length > 0 && (
            <button
              className="conflict-banner"
              onClick={() => setSelectedConflict(snapshot.conflicts[0])}
              type="button"
            >
              <span className="warning-symbol">
                <WarningIcon />
              </span>
              <span>
                <strong>
                  {snapshot.conflicts.length}{' '}
                  {snapshot.conflicts.length === 1
                    ? 'task needs'
                    : 'tasks need'}{' '}
                  your attention
                </strong>
                <small>Review changes made on multiple devices.</small>
              </span>
              <ChevronIcon />
            </button>
          )}

          {snapshot.projectConflicts.length > 0 && (
            <button
              className="conflict-banner project-conflict-banner"
              onClick={() =>
                setSelectedProjectConflict(
                  selectedProjectId
                    ? snapshot.projectConflicts.find(
                        (conflict) =>
                          conflict.projectId === selectedProjectId,
                      ) ?? snapshot.projectConflicts[0]
                    : snapshot.projectConflicts[0],
                )
              }
              type="button"
            >
              <span className="warning-symbol">
                <FolderIcon />
              </span>
              <span>
                <strong>
                  {snapshot.projectConflicts.length}{' '}
                  {snapshot.projectConflicts.length === 1
                    ? 'project needs'
                    : 'projects need'}{' '}
                  your attention
                </strong>
                <small>
                  Resolve the project before editing its name or color.
                </small>
              </span>
              <ChevronIcon />
            </button>
          )}

          <section aria-labelledby="task-list-heading" className="task-section">
            <div className="section-heading">
              <div>
                <h2 id="task-list-heading">
                  {query ? `Results for “${query}”` : 'Tasks'}
                </h2>
                <p>
                  {visibleTasks.length}{' '}
                  {visibleTasks.length === 1 ? 'task' : 'tasks'} shown
                </p>
              </div>
              {clearableCompletedCount > 0 && (
                <button className="text-button" onClick={clearCompleted} type="button">
                  Clear completed
                </button>
              )}
            </div>

            {!snapshot.isReady && !snapshot.lastError ? (
              <TaskListSkeleton />
            ) : visibleTasks.length > 0 ? (
              <ul className="task-list">
                {visibleTasks.map((item) => (
                  <TaskRow
                    actions={actions}
                    item={item}
                    key={item.task.id}
                    onEdit={(task) => setEditor({ mode: 'edit', task })}
                    onResolve={(taskId) => {
                      const conflict = snapshot.conflicts.find(
                        (candidate) => candidate.taskId === taskId,
                      );
                      if (conflict) setSelectedConflict(conflict);
                    }}
                    project={
                      item.task.projectId
                        ? projectsById.get(item.task.projectId)
                        : undefined
                    }
                  />
                ))}
              </ul>
            ) : (
              <EmptyState
                hasQuery={Boolean(query.trim())}
                filter={filter}
                projectName={selectedProject?.name}
              />
            )}
          </section>
        </div>
      </main>

      {snapshot.actionError && (
        <div className="toast" role="alert">
          <WarningIcon />
          <span>{snapshot.actionError}</span>
          <button onClick={actions.clearActionError} type="button">
            Dismiss
          </button>
        </div>
      )}

      {editor && (
        <TaskEditorDialog
          heading={editor.mode === 'create' ? 'Create a task' : 'Edit task'}
          initialProjectId={
            editor.mode === 'create' ? selectedProjectId : undefined
          }
          initialTask={editor.mode === 'edit' ? editor.task : undefined}
          onCancel={() => setEditor(undefined)}
          onSubmit={saveTask}
          projects={snapshot.projects}
          submitLabel={editor.mode === 'create' ? 'Create task' : 'Save changes'}
        />
      )}

      {projectEditor && (
        <ProjectEditorDialog
          heading={
            projectEditor.mode === 'create'
              ? 'Create a project'
              : 'Edit project'
          }
          initialProject={
            projectEditor.mode === 'edit'
              ? projectEditor.item.project
              : undefined
          }
          onCancel={() => setProjectEditor(undefined)}
          onDelete={
            projectEditor.mode === 'edit'
              ? () => deleteProject(projectEditor.item)
              : undefined
          }
          onSubmit={saveProject}
          submitLabel={
            projectEditor.mode === 'create'
              ? 'Create project'
              : 'Save changes'
          }
        />
      )}

      {selectedConflict && (
        <TaskConflictDialog
          actions={actions}
          conflict={selectedConflict}
          onClose={() => setSelectedConflict(undefined)}
          projects={snapshot.projects}
        />
      )}

      {selectedProjectConflict && (
        <ProjectConflictDialog
          actions={actions}
          conflict={selectedProjectConflict}
          onClose={() => setSelectedProjectConflict(undefined)}
        />
      )}
    </div>
  );
}

interface TaskRowProps {
  actions: TaskActions;
  item: WebTaskItem;
  onEdit(task: WebTask): void;
  onResolve(taskId: string): void;
  project?: WebTaskProject;
}

function TaskRow({
  actions,
  item,
  onEdit,
  onResolve,
  project,
}: TaskRowProps) {
  const { task, syncState } = item;
  const hasConflict = syncState === 'conflict';
  const dueLabel = formatDueDate(task.dueDate, task.dueAt);

  const deleteTask = () => {
    if (window.confirm(`Delete “${task.title}”?`)) {
      actions.delete(task.id);
    }
  };

  return (
    <li
      className={[
        'task-row',
        task.isCompleted ? 'completed' : '',
        hasConflict ? 'has-conflict' : '',
      ].join(' ')}
    >
      <label className="task-checkbox">
        <input
          aria-label={
            task.isCompleted
              ? `Mark ${task.title} active`
              : `Mark ${task.title} completed`
          }
          checked={task.isCompleted}
          disabled={hasConflict}
          onChange={() => actions.toggleCompleted(task.id)}
          type="checkbox"
        />
        <span>
          <CheckIcon />
        </span>
      </label>

      <button
        className="task-body"
        disabled={hasConflict}
        onClick={() => onEdit(task)}
        type="button"
      >
        <span className="task-title-line">
          <strong>{task.title}</strong>
          {task.priority !== 'none' && (
            <span className={`priority-badge priority-${task.priority}`}>
              {task.priority}
            </span>
          )}
        </span>
        {task.notes && <span className="task-notes">{task.notes}</span>}
        <span className="task-meta">
          {project && (
            <span className="task-project">
              <span
                className={`project-dot project-color-${project.color}`}
              />
              {project.name}
            </span>
          )}
          {dueLabel && (
            <span className={isOverdue(task) ? 'due overdue' : 'due'}>
              {isOverdue(task) ? 'Overdue · ' : 'Due · '}
              {dueLabel}
            </span>
          )}
          {task.reminderAt && (
            <span className="reminder-meta">
              Reminder · {formatReminder(task.reminderAt)}
            </span>
          )}
          {syncState !== 'synced' && (
            <span className={`sync-state sync-${syncState}`}>
              {syncState === 'pending' ? 'Waiting to sync' : 'Conflict'}
            </span>
          )}
        </span>
      </button>

      {hasConflict ? (
        <button
          className="button button-warning"
          onClick={() => onResolve(task.id)}
          type="button"
        >
          Resolve
        </button>
      ) : (
        <button
          aria-label={`Delete ${task.title}`}
          className="icon-button delete-button"
          onClick={deleteTask}
          type="button"
        >
          <DeleteIcon />
        </button>
      )}
    </li>
  );
}

interface SyncSummaryProps {
  isOnline: boolean;
  lastSyncedAt: string | null | undefined;
  pendingCount: number;
  phase: string;
}

function SyncSummary({
  isOnline,
  lastSyncedAt,
  pendingCount,
  phase,
}: SyncSummaryProps) {
  const status = !isOnline
    ? 'Offline'
    : phase === 'syncing'
      ? 'Synchronizing'
      : pendingCount > 0
        ? `${pendingCount} pending`
        : 'Up to date';

  return (
    <div className="sync-summary">
      <span
        className={[
          'status-dot',
          !isOnline ? 'offline' : '',
          phase === 'syncing' ? 'syncing' : '',
          pendingCount > 0 ? 'pending' : '',
        ].join(' ')}
      />
      <span>
        <strong>{status}</strong>
        <small>{formatLastSynced(lastSyncedAt)}</small>
      </span>
    </div>
  );
}

interface StatusBannerProps {
  action?: React.ReactNode;
  detail: string;
  kind: 'offline' | 'error';
  title: string;
}

function StatusBanner({
  action,
  detail,
  kind,
  title,
}: StatusBannerProps) {
  return (
    <div className={`status-banner ${kind}`} role="status">
      <WarningIcon />
      <span>
        <strong>{title}</strong>
        <small>{detail}</small>
      </span>
      {action}
    </div>
  );
}

function TaskListSkeleton() {
  return (
    <div aria-label="Loading tasks" className="task-list skeleton-list" role="status">
      {[0, 1, 2].map((item) => (
        <div className="task-row skeleton-row" key={item}>
          <span className="skeleton skeleton-circle" />
          <span className="skeleton-lines">
            <span className="skeleton skeleton-title" />
            <span className="skeleton skeleton-copy" />
          </span>
        </div>
      ))}
    </div>
  );
}

interface EmptyStateProps {
  filter: TaskSmartView;
  hasQuery: boolean;
  projectName?: string;
}

function EmptyState({
  filter,
  hasQuery,
  projectName,
}: EmptyStateProps) {
  const message = hasQuery
    ? 'Try a different title or note.'
    : projectName
      ? `Tasks assigned to ${projectName} will appear here.`
      : filter === 'completed'
      ? 'Completed tasks will appear here.'
      : filter === 'inbox'
        ? 'Unscheduled tasks will wait here until you plan them.'
        : filter === 'today'
          ? 'Tasks due today and overdue tasks will appear here.'
          : filter === 'upcoming'
            ? 'Future tasks will appear here.'
            : 'Create a task to start planning your work.';

  return (
    <div className="empty-state">
      <span>
        <InboxIcon />
      </span>
      <h3>
        {hasQuery
          ? 'No matching tasks'
          : projectName
            ? `${projectName} is ready`
            : 'Nothing here yet'}
      </h3>
      <p>{message}</p>
    </div>
  );
}
