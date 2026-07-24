import { useEffect, useMemo, useRef, useState } from 'react';
import type { WebTask, WebTaskConflict, WebTaskItem, WebTaskSnapshot } from 'shared-logic';
import type { TaskActions } from '../../taskActions.ts';
import {
  filterTasks,
  formatDueDate,
  formatLastSynced,
  isOverdue,
} from '../../taskView.ts';
import type { TaskFilter } from '../../taskView.ts';
import {
  AddIcon,
  CheckIcon,
  ChevronIcon,
  DeleteIcon,
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

interface TaskAppProps {
  actions: TaskActions;
  isOnline: boolean;
  snapshot: WebTaskSnapshot;
}

type EditorState =
  | { mode: 'create' }
  | { mode: 'edit'; task: WebTask };

const filters: Array<{ value: TaskFilter; label: string }> = [
  { value: 'all', label: 'All tasks' },
  { value: 'active', label: 'Active' },
  { value: 'completed', label: 'Completed' },
];

export function TaskApp({
  actions,
  isOnline,
  snapshot,
}: TaskAppProps) {
  const [filter, setFilter] = useState<TaskFilter>('all');
  const [query, setQuery] = useState('');
  const [editor, setEditor] = useState<EditorState>();
  const [selectedConflict, setSelectedConflict] =
    useState<WebTaskConflict>();
  const requestedInitialSync = useRef(false);
  const wasOnline = useRef(isOnline);

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

  const counts = useMemo(() => {
    const completed = snapshot.tasks.filter(
      ({ task }) => task.isCompleted,
    ).length;
    return {
      all: snapshot.tasks.length,
      active: snapshot.tasks.length - completed,
      completed,
    };
  }, [snapshot.tasks]);

  const visibleTasks = useMemo(
    () => filterTasks(snapshot.tasks, filter, query),
    [filter, query, snapshot.tasks],
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
        values.dueAt,
        values.isCompleted,
      );
    } else {
      actions.create(
        values.title,
        values.notes,
        values.priority,
        values.dueAt,
      );
    }
    setEditor(undefined);
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

        <nav aria-label="Task filters" className="filter-nav">
          {filters.map(({ value, label }) => (
            <button
              aria-current={filter === value ? 'page' : undefined}
              className={filter === value ? 'filter-button active' : 'filter-button'}
              key={value}
              onClick={() => setFilter(value)}
              type="button"
            >
              <span>{label}</span>
              <span className="filter-count">{counts[value]}</span>
            </button>
          ))}
        </nav>

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
            <h1>{filters.find(({ value }) => value === filter)?.label}</h1>
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

          {snapshot.conflictCount > 0 && (
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
                  {snapshot.conflictCount}{' '}
                  {snapshot.conflictCount === 1 ? 'task needs' : 'tasks need'} your
                  attention
                </strong>
                <small>Review changes made on multiple devices.</small>
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
                  />
                ))}
              </ul>
            ) : (
              <EmptyState hasQuery={Boolean(query.trim())} filter={filter} />
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
          initialTask={editor.mode === 'edit' ? editor.task : undefined}
          onCancel={() => setEditor(undefined)}
          onSubmit={saveTask}
          submitLabel={editor.mode === 'create' ? 'Create task' : 'Save changes'}
        />
      )}

      {selectedConflict && (
        <TaskConflictDialog
          actions={actions}
          conflict={selectedConflict}
          onClose={() => setSelectedConflict(undefined)}
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
}

function TaskRow({
  actions,
  item,
  onEdit,
  onResolve,
}: TaskRowProps) {
  const { task, syncState } = item;
  const hasConflict = syncState === 'conflict';
  const dueLabel = formatDueDate(task.dueAt);

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
          {dueLabel && (
            <span className={isOverdue(task) ? 'due overdue' : 'due'}>
              {isOverdue(task) ? 'Overdue · ' : 'Due · '}
              {dueLabel}
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
  filter: TaskFilter;
  hasQuery: boolean;
}

function EmptyState({ filter, hasQuery }: EmptyStateProps) {
  const message = hasQuery
    ? 'Try a different title or note.'
    : filter === 'completed'
      ? 'Completed tasks will appear here.'
      : filter === 'active'
        ? 'Everything is complete. Nicely done.'
        : 'Create a task to start planning your work.';

  return (
    <div className="empty-state">
      <span>
        <InboxIcon />
      </span>
      <h3>{hasQuery ? 'No matching tasks' : 'Nothing here yet'}</h3>
      <p>{message}</p>
    </div>
  );
}
