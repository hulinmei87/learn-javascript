const DEFAULT_TEMPLATE = "你好，我对{jobTitle}岗位感兴趣，希望进一步沟通。";

function pickTemplate(templates) {
  if (!Array.isArray(templates) || !templates.length) {
    return DEFAULT_TEMPLATE;
  }

  const index = Math.floor(Math.random() * templates.length);
  return templates[index] || DEFAULT_TEMPLATE;
}

export function renderGreeting(templates, job) {
  const template = pickTemplate(templates);
  const vars = {
    jobTitle: String(job.title ?? "该岗位"),
    companyName: String(job.companyName ?? "贵司"),
    location: String(job.locationText ?? ""),
    salary: String(job.salaryText ?? "")
  };

  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (_, key) => vars[key] ?? "");
}
