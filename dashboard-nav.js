(() => {
  function activateTab(name) {
    document.querySelectorAll('.tab,.view').forEach(x => x.classList.remove('active'));
    const tab = document.querySelector(`.tab[data-tab="${name}"]`);
    const view = document.getElementById(name);
    if (tab) tab.classList.add('active');
    if (view) view.classList.add('active');
  }

  function nextSevenDaysTask(t) {
    if (t.completed || !t.dueAt) return false;
    const due = new Date(t.dueAt);
    const now = new Date();
    return due > endOfDay(now) && due <= new Date(Date.now() + 7 * 86400000);
  }

  function nextSevenDaysProject(p) {
    if (p.status === 'completed' || !p.dueDate) return false;
    const today = localDateKey();
    const weekEnd = localDateKey(new Date(Date.now() + 7 * 86400000));
    return p.dueDate > today && p.dueDate <= weekEnd;
  }

  renderCounts = function() {
    const openTasks = data.tasks.filter(t => !t.completed);
    const openProjects = data.projects.filter(p => p.status !== 'completed');
    const now = new Date();

    const taskOverdue = openTasks.filter(t => t.dueAt && new Date(t.dueAt) < now).length;
    const taskToday = openTasks.filter(t => t.dueAt && new Date(t.dueAt) >= startOfDay(now) && new Date(t.dueAt) <= endOfDay(now)).length;
    const taskUpcoming = openTasks.filter(nextSevenDaysTask).length;

    const projectOverdue = openProjects.filter(p => projectDueState(p) === 'overdue').length;
    const projectToday = openProjects.filter(p => projectDueState(p) === 'today').length;
    const projectUpcoming = openProjects.filter(nextSevenDaysProject).length;

    $('projectOverdueCount').textContent = projectOverdue;
    $('projectTodayCount').textContent = projectToday;
    $('projectUpcomingCount').textContent = projectUpcoming;
    $('taskOverdueCount').textContent = taskOverdue;
    $('taskTodayCount').textContent = taskToday;
    $('taskUpcomingCount').textContent = taskUpcoming;
  };

  renderProjects = function() {
    const filter = $('projectDueFilter')?.value || 'all';
    let ps = [...data.projects];
    if (filter === 'overdue') ps = ps.filter(p => projectDueState(p) === 'overdue');
    if (filter === 'today') ps = ps.filter(p => projectDueState(p) === 'today');
    if (filter === 'upcoming') ps = ps.filter(nextSevenDaysProject);
    ps.sort((a, b) => (a.status === 'completed') - (b.status === 'completed') || priorityRank(a.priority) - priorityRank(b.priority));

    $('projectList').innerHTML = ps.length ? ps.map(p => {
      const tasks = data.tasks.filter(t => t.projectId === p.id);
      const done = tasks.filter(t => t.completed).length;
      return `<article class="item-card ${p.status === 'completed' ? 'completed' : ''}"><div class="item-top"><div class="item-main"><div class="item-title">${esc(p.name)}</div><div class="meta"><span class="badge ${p.priority}">${esc(p.priority)}</span><span class="badge ${p.status === 'completed' ? 'complete' : ''}">${esc(p.status.replace('-', ' '))}</span>${p.dueDate ? `<span>Target ${new Date(p.dueDate + 'T12:00').toLocaleDateString()}</span>` : ''}</div>${p.nextAction ? `<div class="next-action"><strong>Next action:</strong> ${esc(p.nextAction)}</div>` : ''}${p.comments ? `<div class="comments">${esc(p.comments)}</div>` : ''}<div class="project-progress">${done} of ${tasks.length} tasks completed</div></div><div class="item-actions"><button class="icon-btn project-task" data-id="${p.id}" title="Add task">＋</button><button class="icon-btn project-edit" data-id="${p.id}" title="Edit">✎</button><button class="icon-btn project-delete" data-id="${p.id}" title="Delete">×</button></div></div></article>`;
    }).join('') : `<div class="empty">No projects match that filter.</div>`;

    document.querySelectorAll('.project-edit').forEach(b => b.onclick = () => openProject(b.dataset.id));
    document.querySelectorAll('.project-task').forEach(b => b.onclick = () => openTask('', b.dataset.id));
    document.querySelectorAll('.project-delete').forEach(b => b.onclick = () => deleteProject(b.dataset.id));
  };

  renderTasks = function() {
    let ts = [...data.tasks];
    const pf = $('taskProjectFilter').value;
    const sf = $('taskStatusFilter').value;
    const pr = $('taskPriorityFilter').value;
    const af = $('taskAssigneeFilter').value.trim().toLowerCase();

    if (pf !== 'all') ts = ts.filter(t => t.projectId === pf);
    if (pr !== 'all') ts = ts.filter(t => t.priority === pr);
    if (af) ts = ts.filter(t => (t.assignee || '').toLowerCase().includes(af));
    if (sf === 'open') ts = ts.filter(t => !t.completed);
    if (sf === 'completed') ts = ts.filter(t => t.completed);
    if (sf === 'overdue') ts = ts.filter(t => dueState(t) === 'overdue');
    if (sf === 'today') ts = ts.filter(t => dueState(t) === 'today');
    if (sf === 'upcoming') ts = ts.filter(nextSevenDaysTask);

    ts.sort(taskSort);
    $('taskList').innerHTML = ts.length ? ts.map(taskCard).join('') : `<div class="empty">No tasks match that filter.</div>`;
    bindTaskButtons($('taskList'));
  };

  function openSummary(kind, filter) {
    if (kind === 'project') {
      activateTab('projects');
      $('projectDueFilter').value = filter;
      renderProjects();
      document.getElementById('projectDueFilter').scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }

    activateTab('tasks');
    $('taskProjectFilter').value = 'all';
    $('taskStatusFilter').value = filter;
    $('taskPriorityFilter').value = 'all';
    $('taskAssigneeFilter').value = '';
    renderTasks();
    document.getElementById('taskStatusFilter').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  document.querySelectorAll('.summary-link').forEach(card => {
    card.addEventListener('click', () => openSummary(card.dataset.summaryKind, card.dataset.summaryFilter));
  });

  $('projectDueFilter').onchange = renderProjects;
  $('taskStatusFilter').onchange = renderTasks;

  renderCounts();
  renderProjects();
  renderTasks();
})();
