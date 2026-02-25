function toK(value, unit) {
  if (unit === "万" || unit === "W") {
    return value * 10;
  }

  return value;
}

function normalizeSalaryText(input) {
  return String(input ?? "")
    .replace(/\s+/g, "")
    .replace(/k/g, "K")
    .replace(/w/g, "W");
}

export function parseSalaryKRange(salaryText) {
  const text = normalizeSalaryText(salaryText);
  if (!text) {
    return null;
  }

  if (/面议|保密|待定|元\/天|\/天|元\/小时|\/小时/.test(text)) {
    return null;
  }

  const rangeMatch = text.match(/(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)(K|万|W)/);
  if (rangeMatch) {
    const minRaw = Number(rangeMatch[1]);
    const maxRaw = Number(rangeMatch[2]);
    const unit = rangeMatch[3];
    const minK = toK(minRaw, unit);
    const maxK = toK(maxRaw, unit);
    return {
      minK: Math.min(minK, maxK),
      maxK: Math.max(minK, maxK)
    };
  }

  const singleMatch = text.match(/(\d+(?:\.\d+)?)(K|万|W)/);
  if (singleMatch) {
    const number = toK(Number(singleMatch[1]), singleMatch[2]);
    return { minK: number, maxK: number };
  }

  return null;
}

export function salaryMatches(jobSalaryText, expectedSalary) {
  if (!expectedSalary || (expectedSalary.minK == null && expectedSalary.maxK == null)) {
    return true;
  }

  const parsed = parseSalaryKRange(jobSalaryText);
  if (!parsed) {
    return false;
  }

  const expectedMin = expectedSalary.minK != null ? Number(expectedSalary.minK) : Number.NEGATIVE_INFINITY;
  const expectedMax = expectedSalary.maxK != null ? Number(expectedSalary.maxK) : Number.POSITIVE_INFINITY;

  return parsed.maxK >= expectedMin && parsed.minK <= expectedMax;
}

export function locationMatches(jobLocationText, expectedLocations) {
  const required = Array.isArray(expectedLocations) ? expectedLocations : [];
  if (!required.length) {
    return true;
  }

  const haystack = String(jobLocationText ?? "").toLowerCase();
  return required.some((item) => haystack.includes(String(item).toLowerCase()));
}

export function jobMatches(job, search) {
  return (
    salaryMatches(job.salaryText, search.salary) &&
    locationMatches(job.locationText, search.locations)
  );
}

export function buildJobKey(job) {
  if (job.id) {
    return `id:${job.id}`;
  }

  const company = String(job.companyName ?? "").trim();
  const title = String(job.title ?? "").trim();
  const salary = String(job.salaryText ?? "").trim();
  const location = String(job.locationText ?? "").trim();
  return `fallback:${company}|${title}|${salary}|${location}`;
}
