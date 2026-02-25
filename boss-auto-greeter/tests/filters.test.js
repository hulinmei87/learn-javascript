import test from "node:test";
import assert from "node:assert/strict";
import {
  parseSalaryKRange,
  salaryMatches,
  locationMatches,
  jobMatches
} from "../src/filters.js";

test("parseSalaryKRange should parse K salary range", () => {
  const result = parseSalaryKRange("20-30K·14薪");
  assert.deepEqual(result, { minK: 20, maxK: 30 });
});

test("parseSalaryKRange should parse 万 salary range", () => {
  const result = parseSalaryKRange("2-3万");
  assert.deepEqual(result, { minK: 20, maxK: 30 });
});

test("parseSalaryKRange returns null for negotiable salary", () => {
  const result = parseSalaryKRange("薪资面议");
  assert.equal(result, null);
});

test("salaryMatches should return true when salary overlaps", () => {
  const matched = salaryMatches("18-28K", { minK: 20, maxK: 30 });
  assert.equal(matched, true);
});

test("salaryMatches should return false when salary outside expected range", () => {
  const matched = salaryMatches("8-12K", { minK: 20, maxK: 30 });
  assert.equal(matched, false);
});

test("locationMatches should match expected city fragment", () => {
  const matched = locationMatches("上海·浦东新区", ["浦东", "徐汇"]);
  assert.equal(matched, true);
});

test("jobMatches should validate salary and location simultaneously", () => {
  const matched = jobMatches(
    {
      salaryText: "25-40K",
      locationText: "北京·朝阳区"
    },
    {
      salary: { minK: 30, maxK: 50 },
      locations: ["北京"]
    }
  );

  assert.equal(matched, true);
});
