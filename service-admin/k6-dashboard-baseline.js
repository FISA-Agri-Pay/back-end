import http from "k6/http";
import { check, fail } from "k6";

export const options = {
  scenarios: {
    dashboard_summary: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RATE || 10),
      timeUnit: "1s",
      duration: __ENV.DURATION || "10m",
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 50),
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const baseUrl = __ENV.BASE_URL;
  if (!baseUrl) {
    fail("BASE_URL environment variable is required");
  }

  const token = __ENV.TOKEN;
  if (!token) {
    fail("TOKEN environment variable is required for /api/v1/admin/dashboard/summary");
  }

  const headers = { Authorization: `Bearer ${token}` };

  const response = http.get(`${baseUrl}/api/v1/admin/dashboard/summary`, {
    headers,
  });

  check(response, {
    "status is 200": (r) => r.status === 200,
  });
}
